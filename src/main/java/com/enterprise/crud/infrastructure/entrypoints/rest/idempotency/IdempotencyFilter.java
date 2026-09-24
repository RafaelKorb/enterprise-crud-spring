package com.enterprise.crud.infrastructure.entrypoints.rest.idempotency;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Makes POST requests safe to retry, following the IETF {@code Idempotency-Key} header draft (see
 * docs/architecture-flow.md, section 2).
 * <p>
 * The key is claimed atomically before the request runs. Only successful responses are stored and replayed: a
 * failed mutation leaves no state behind, so its key is released and the client may retry with it. If the store is
 * unavailable the request is refused with 503 rather than risk applying a mutation twice.
 * <p>
 * Every decision is counted in {@value #METRIC_NAME}, tagged by {@link Outcome}, so replay and conflict rates are
 * visible next to the HTTP RED metrics.
 */
public class IdempotencyFilter extends OncePerRequestFilter {

    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    public static final String REPLAYED_HEADER = "Idempotent-Replayed";

    public static final String METRIC_NAME = "idempotency.requests";

    static final int MAX_KEY_LENGTH = 255;

    enum Outcome {
        /** Request without a usable key, rejected with 400. */
        MISSING_KEY,
        /** Store unreachable, rejected with 503. */
        UNAVAILABLE,
        /** Key reused with a different request, rejected with 422. */
        MISMATCH,
        /** Key still claimed by another request, rejected with 409. */
        IN_PROGRESS,
        /** Stored response replayed without running the request. */
        REPLAYED,
        /** Request ran and its 2xx response was stored. */
        EXECUTED,
        /** Request ran but failed, so the key was released for a retry. */
        RELEASED
    }

    private static final Logger log = LoggerFactory.getLogger(IdempotencyFilter.class);
    private static final List<String> REPLAYED_RESPONSE_HEADERS = List.of(HttpHeaders.CONTENT_TYPE,
            HttpHeaders.LOCATION);

    private final IdempotencyStore store;
    private final JsonMapper jsonMapper;
    private final Duration lockTtl;
    private final Duration retention;
    private final Map<Outcome, Counter> outcomes = new EnumMap<>(Outcome.class);

    public IdempotencyFilter(IdempotencyStore store, JsonMapper jsonMapper, Duration lockTtl, Duration retention,
            MeterRegistry meterRegistry) {
        this.store = store;
        this.jsonMapper = jsonMapper;
        this.lockTtl = lockTtl;
        this.retention = retention;
        for (Outcome outcome : Outcome.values()) {
            outcomes.put(outcome, Counter.builder(METRIC_NAME)
                    .description("Idempotency-Key decisions on POST requests")
                    .tag("outcome", outcome.name().toLowerCase(Locale.ROOT))
                    .register(meterRegistry));
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.POST.matches(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = request.getHeader(IDEMPOTENCY_KEY_HEADER);
        if (key == null || key.isBlank() || key.length() > MAX_KEY_LENGTH) {
            count(Outcome.MISSING_KEY);
            writeProblem(response, HttpStatus.BAD_REQUEST,
                    "Header %s is required on POST requests (1 to %d characters)"
                            .formatted(IDEMPOTENCY_KEY_HEADER, MAX_KEY_LENGTH));
            return;
        }

        CachedBodyRequest cachedRequest = new CachedBodyRequest(request);
        String storeKey = "idempotency:%s:%s:%s".formatted(request.getMethod(), request.getRequestURI(), key);
        String fingerprint = fingerprint(cachedRequest);

        Optional<IdempotencyRecord> existing;
        try {
            existing = store.reserve(storeKey, fingerprint, lockTtl);
        } catch (DataAccessException e) {
            log.warn("Idempotency store unavailable, refusing {} {}", request.getMethod(), request.getRequestURI(), e);
            count(Outcome.UNAVAILABLE);
            writeProblem(response, HttpStatus.SERVICE_UNAVAILABLE,
                    "Request cannot be processed safely right now; retry later with the same Idempotency-Key");
            return;
        }
        if (existing.isPresent()) {
            answerFromExisting(existing.get(), fingerprint, response);
            return;
        }

        ContentCachingResponseWrapper cachedResponse = new ContentCachingResponseWrapper(response);
        try {
            chain.doFilter(cachedRequest, cachedResponse);
        } catch (IOException | ServletException | RuntimeException e) {
            count(Outcome.RELEASED);
            releaseQuietly(storeKey);
            throw e;
        }
        if (HttpStatusCode.valueOf(cachedResponse.getStatus()).is2xxSuccessful()) {
            count(Outcome.EXECUTED);
            completeQuietly(storeKey, IdempotencyRecord.completed(fingerprint, cachedResponse.getStatus(),
                    replayableHeaders(cachedResponse),
                    new String(cachedResponse.getContentAsByteArray(), StandardCharsets.UTF_8)));
        } else {
            count(Outcome.RELEASED);
            releaseQuietly(storeKey);
        }
        cachedResponse.copyBodyToResponse();
    }

    private void answerFromExisting(IdempotencyRecord record, String fingerprint, HttpServletResponse response)
            throws IOException {
        if (!record.fingerprint().equals(fingerprint)) {
            count(Outcome.MISMATCH);
            writeProblem(response, HttpStatus.UNPROCESSABLE_CONTENT,
                    "Idempotency-Key was already used with a different request");
        } else if (!record.isCompleted()) {
            count(Outcome.IN_PROGRESS);
            writeProblem(response, HttpStatus.CONFLICT,
                    "A request with this Idempotency-Key is still being processed");
        } else {
            count(Outcome.REPLAYED);
            response.setStatus(record.status());
            record.headers().forEach(response::setHeader);
            response.setHeader(REPLAYED_HEADER, "true");
            if (record.body() != null) {
                response.getOutputStream().write(record.body().getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    private void count(Outcome outcome) {
        outcomes.get(outcome).increment();
    }

    /** The response was already produced, so a store failure here must not turn it into an error. */
    private void completeQuietly(String storeKey, IdempotencyRecord record) {
        try {
            store.complete(storeKey, record, retention);
        } catch (DataAccessException e) {
            log.error("Could not store idempotent response for {}; a retry would be processed again", storeKey, e);
        }
    }

    /** If release fails the claim still expires after the lock TTL. */
    private void releaseQuietly(String storeKey) {
        try {
            store.release(storeKey);
        } catch (DataAccessException e) {
            log.warn("Could not release idempotency key {}; it expires after {}", storeKey, lockTtl, e);
        }
    }

    private static Map<String, String> replayableHeaders(HttpServletResponse response) {
        Map<String, String> headers = new LinkedHashMap<>();
        for (String name : REPLAYED_RESPONSE_HEADERS) {
            String value = response.getHeader(name);
            if (value != null) {
                headers.put(name, value);
            }
        }
        return headers;
    }

    private static String fingerprint(CachedBodyRequest request) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(request.getMethod().getBytes(StandardCharsets.UTF_8));
            digest.update(request.getRequestURI().getBytes(StandardCharsets.UTF_8));
            digest.update(request.body());
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every Java platform", e);
        }
    }

    private void writeProblem(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), ProblemDetail.forStatusAndDetail(status, detail));
    }

    /** Reads the body up front so it can be fingerprinted and still be read by the controller. */
    private static final class CachedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        CachedBodyRequest(HttpServletRequest request) throws IOException {
            super(request);
            this.body = request.getInputStream().readAllBytes();
        }

        byte[] body() {
            return body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream source = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return source.read();
                }

                @Override
                public int read(byte[] b, int off, int len) {
                    return source.read(b, off, len);
                }

                @Override
                public boolean isFinished() {
                    return source.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException("Async reads are not supported");
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            String encoding = getCharacterEncoding();
            return new BufferedReader(new InputStreamReader(getInputStream(),
                    encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding)));
        }
    }
}
