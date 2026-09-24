package com.enterprise.crud.infrastructure.entrypoints.rest.idempotency;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdempotencyFilterTest {

    private static final String URI = "/api/v1/accounts/42/debits";
    private static final String BODY = "{\"amount\": 10.00}";

    private final InMemoryIdempotencyStore store = new InMemoryIdempotencyStore();
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final IdempotencyFilter filter = new IdempotencyFilter(store, JsonMapper.builder().build(),
            Duration.ofSeconds(30), Duration.ofHours(24), meterRegistry);

    /** Stands in for the controller: counts executions and answers with the configured status. */
    private final AtomicInteger executions = new AtomicInteger();
    private int handlerStatus = 201;

    @Test
    void postWithoutKeyIsRejectedBeforeReachingTheController() throws Exception {
        MockHttpServletResponse response = send(post(null, BODY));

        assertEquals(400, response.getStatus());
        assertEquals(MediaType.APPLICATION_PROBLEM_JSON_VALUE, response.getContentType());
        assertEquals(0, executions.get());
        assertEquals(1, count(IdempotencyFilter.Outcome.MISSING_KEY));
    }

    @Test
    void oversizedKeyIsRejected() throws Exception {
        String key = "k".repeat(IdempotencyFilter.MAX_KEY_LENGTH + 1);

        assertEquals(400, send(post(key, BODY)).getStatus());
        assertEquals(0, executions.get());
    }

    @Test
    void retryWithSameKeyReplaysTheFirstResponseWithoutExecutingAgain() throws Exception {
        MockHttpServletResponse first = send(post("k1", BODY));
        MockHttpServletResponse retry = send(post("k1", BODY));

        assertEquals(1, executions.get());
        assertEquals(201, retry.getStatus());
        assertEquals(first.getContentAsString(), retry.getContentAsString());
        assertEquals("/api/v1/accounts/42", retry.getHeader("Location"));
        assertEquals(MediaType.APPLICATION_JSON_VALUE, retry.getContentType());
        assertEquals("true", retry.getHeader(IdempotencyFilter.REPLAYED_HEADER));
        assertNull(first.getHeader(IdempotencyFilter.REPLAYED_HEADER));
        assertEquals(1, count(IdempotencyFilter.Outcome.EXECUTED));
        assertEquals(1, count(IdempotencyFilter.Outcome.REPLAYED));
    }

    @Test
    void controllerReadsTheBodyAfterItWasFingerprinted() throws Exception {
        MockHttpServletResponse response = send(post("k1", BODY));

        assertTrue(response.getContentAsString().contains("\"echo\":" + BODY.length()));
    }

    @Test
    void sameKeyWithDifferentBodyIsUnprocessable() throws Exception {
        send(post("k1", BODY));

        MockHttpServletResponse response = send(post("k1", "{\"amount\": 99.00}"));

        assertEquals(422, response.getStatus());
        assertEquals(1, executions.get());
        assertEquals(1, count(IdempotencyFilter.Outcome.MISMATCH));
    }

    @Test
    void sameKeyOnAnotherResourceIsIndependent() throws Exception {
        send(post("k1", BODY));

        MockHttpServletRequest other = post("k1", BODY);
        other.setRequestURI("/api/v1/accounts/43/debits");
        send(other);

        assertEquals(2, executions.get());
    }

    @Test
    void keyStillInProgressIsConflict() throws Exception {
        store.reserve("idempotency:POST:" + URI + ":k1", fingerprintOf(BODY), Duration.ofSeconds(30));

        MockHttpServletResponse response = send(post("k1", BODY));

        assertEquals(409, response.getStatus());
        assertEquals(0, executions.get());
        assertEquals(1, count(IdempotencyFilter.Outcome.IN_PROGRESS));
    }

    @Test
    void failedRequestReleasesTheKeySoItCanBeRetried() throws Exception {
        handlerStatus = 422;
        assertEquals(422, send(post("k1", BODY)).getStatus());
        assertTrue(store.records.isEmpty());

        handlerStatus = 201;
        assertEquals(201, send(post("k1", BODY)).getStatus());
        assertEquals(2, executions.get());
        assertEquals(1, count(IdempotencyFilter.Outcome.RELEASED));
        assertEquals(1, count(IdempotencyFilter.Outcome.EXECUTED));
    }

    @Test
    void exceptionFromControllerReleasesTheKey() {
        MockFilterChain failing = new MockFilterChain(new HttpServlet() {
            @Override
            protected void service(HttpServletRequest req, HttpServletResponse resp) {
                throw new IllegalStateException("boom");
            }
        });

        assertThrows(IllegalStateException.class,
                () -> filter.doFilter(post("k1", BODY), new MockHttpServletResponse(), failing));
        assertTrue(store.records.isEmpty());
    }

    @Test
    void storeOutageFailsClosed() throws Exception {
        store.unavailable = true;

        MockHttpServletResponse response = send(post("k1", BODY));

        assertEquals(503, response.getStatus());
        assertEquals(0, executions.get());
        assertEquals(1, count(IdempotencyFilter.Outcome.UNAVAILABLE));
    }

    @Test
    void otherMethodsAreNotIntercepted() throws Exception {
        MockHttpServletRequest put = post(null, "{\"status\": \"BLOCKED\"}");
        put.setMethod("PUT");

        assertEquals(201, send(put).getStatus());
        assertEquals(1, executions.get());
        assertTrue(store.records.isEmpty());
    }

    private double count(IdempotencyFilter.Outcome outcome) {
        return meterRegistry.get(IdempotencyFilter.METRIC_NAME)
                .tag("outcome", outcome.name().toLowerCase(Locale.ROOT)).counter().count();
    }

    private MockHttpServletResponse send(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain(new HttpServlet() {
            @Override
            protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                int bodyLength = req.getInputStream().readAllBytes().length;
                int execution = executions.incrementAndGet();
                resp.setStatus(handlerStatus);
                resp.setContentType(MediaType.APPLICATION_JSON_VALUE);
                resp.setHeader("Location", "/api/v1/accounts/42");
                resp.getWriter().write("{\"execution\":%d,\"echo\":%d}".formatted(execution, bodyLength));
            }
        }));
        return response;
    }

    private static MockHttpServletRequest post(String key, String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", URI);
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        if (key != null) {
            request.addHeader(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, key);
        }
        return request;
    }

    /** Captures the fingerprint the filter computes by letting it claim a throwaway key. */
    private String fingerprintOf(String body) throws Exception {
        InMemoryIdempotencyStore probe = new InMemoryIdempotencyStore();
        new IdempotencyFilter(probe, JsonMapper.builder().build(), Duration.ofSeconds(30), Duration.ofHours(24),
                new SimpleMeterRegistry())
                .doFilter(post("probe", body), new MockHttpServletResponse(), (req, res) -> {
                });
        return probe.records.values().iterator().next().fingerprint();
    }

    private static final class InMemoryIdempotencyStore implements IdempotencyStore {

        final Map<String, IdempotencyRecord> records = new ConcurrentHashMap<>();
        boolean unavailable;

        @Override
        public Optional<IdempotencyRecord> reserve(String key, String fingerprint, Duration lockTtl) {
            failIfUnavailable();
            return Optional.ofNullable(records.putIfAbsent(key, IdempotencyRecord.inProgress(fingerprint)));
        }

        @Override
        public void complete(String key, IdempotencyRecord record, Duration retention) {
            failIfUnavailable();
            records.put(key, record);
        }

        @Override
        public void release(String key) {
            failIfUnavailable();
            records.remove(key);
        }

        private void failIfUnavailable() {
            if (unavailable) {
                throw new DataAccessResourceFailureException("redis down");
            }
        }
    }
}
