package com.enterprise.crud.infrastructure.observability;

import com.enterprise.crud.TestcontainersConfiguration;
import com.enterprise.crud.infrastructure.entrypoints.rest.idempotency.IdempotencyFilter;
import com.enterprise.crud.infrastructure.entrypoints.rest.tracing.TraceIdResponseFilter;
import com.jayway.jsonpath.JsonPath;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Tracing and metrics as wired in production: one trace per request spanning HTTP, SQL and Redis, the trace id
 * returned to the client, and RED plus idempotency metrics recorded.
 */
@SpringBootTest(properties = {
        "management.tracing.sampling.probability=1.0",
        "management.opentelemetry.tracing.export.schedule-delay=50ms"})
@AutoConfigureMockMvc
@AutoConfigureTracing
@AutoConfigureMetrics
@Import({TestcontainersConfiguration.class, ObservabilityIT.SpanCapture.class})
class ObservabilityIT {

    private static final String TRACE_ID = "[0-9a-f]{32}";

    @TestConfiguration(proxyBeanMethods = false)
    static class SpanCapture {

        /** Picked up by Boot's span processor next to (here: instead of) the OTLP exporter. */
        @Bean
        InMemorySpanExporter inMemorySpanExporter() {
            return InMemorySpanExporter.create();
        }
    }

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private InMemorySpanExporter spans;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM account");
        spans.reset();
    }

    @Test
    void oneTraceCoversHttpSqlAndRedisAndItsIdIsReturned() throws Exception {
        MvcTestResult result = open("52998224725", UUID.randomUUID().toString());

        String traceId = result.getResponse().getHeader(TraceIdResponseFilter.TRACE_ID_HEADER);
        assertThat(traceId).matches(TRACE_ID);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            List<SpanData> trace = spans.getFinishedSpanItems().stream()
                    .filter(span -> span.getTraceId().equals(traceId))
                    .toList();
            assertThat(trace).as("spans of trace %s: %s", traceId, describe(trace))
                    .anySatisfy(span -> assertThat(span.getKind()).isEqualTo(SpanKind.SERVER))
                    .anySatisfy(span -> assertThat(span.getName()).isEqualTo("query"))
                    .anySatisfy(span -> assertThat(span.getAttributes().asMap().toString()).contains("redis"));
        });
    }

    @Test
    void sqlSpansDoNotCarryParameterValues() throws Exception {
        open("52998224725", UUID.randomUUID().toString());

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(spans.getFinishedSpanItems())
                .filteredOn(span -> span.getName().equals("query"))
                .isNotEmpty()
                .allSatisfy(span -> assertThat(span.getAttributes().asMap().toString())
                        .doesNotContain("52998224725")));
    }

    @Test
    void errorResponsesFromFiltersAlsoCarryTheTraceId() {
        assertThat(mvc.post().uri("/api/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"documentNumber\": \"52998224725\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .headers().hasHeaderSatisfying(TraceIdResponseFilter.TRACE_ID_HEADER,
                        values -> assertThat(values.getFirst()).matches(TRACE_ID));
    }

    @Test
    void requestsAreRecordedAsRedMetrics() throws Exception {
        String id = JsonPath.read(open("52998224725", UUID.randomUUID().toString())
                .getResponse().getContentAsString(), "$.id");
        assertThat(mvc.get().uri("/api/v1/accounts/{id}", id)).hasStatusOk();
        assertThat(mvc.get().uri("/api/v1/accounts/{id}", UUID.randomUUID())).hasStatus(HttpStatus.NOT_FOUND);

        Timer ok = meterRegistry.get("http.server.requests")
                .tags("uri", "/api/{version}/accounts/{id}", "method", "GET", "status", "200").timer();
        Timer notFound = meterRegistry.get("http.server.requests")
                .tags("uri", "/api/{version}/accounts/{id}", "outcome", "CLIENT_ERROR").timer();

        assertThat(ok.count()).isEqualTo(1);
        assertThat(notFound.count()).isEqualTo(1);
    }

    @Test
    void idempotencyOutcomesAreCounted() throws Exception {
        double replayedBefore = idempotencyCount("replayed");
        double executedBefore = idempotencyCount("executed");

        open("52998224725", "same-key");
        open("52998224725", "same-key");

        assertThat(idempotencyCount("executed") - executedBefore).isEqualTo(1);
        assertThat(idempotencyCount("replayed") - replayedBefore).isEqualTo(1);
    }

    /** Documents a known gap: responses produced by filters never reach a handler, so they have no route. */
    @Test
    void filterProducedResponsesAreRecordedWithoutRoute() {
        double before = unknownRouteCount();

        assertThat(mvc.post().uri("/api/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"documentNumber\": \"52998224725\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST);

        assertThat(unknownRouteCount() - before).isEqualTo(1);
    }

    private double unknownRouteCount() {
        Timer timer = meterRegistry.find("http.server.requests").tags("uri", "UNKNOWN", "status", "400").timer();
        return timer == null ? 0 : timer.count();
    }

    private double idempotencyCount(String outcome) {
        return meterRegistry.get(IdempotencyFilter.METRIC_NAME).tag("outcome", outcome).counter().count();
    }

    private MvcTestResult open(String documentNumber, String idempotencyKey) {
        MvcTestResult result = mvc.post().uri("/api/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                .header(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                .content("{\"documentNumber\": \"%s\"}".formatted(documentNumber))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return result;
    }

    private static String describe(List<SpanData> trace) {
        return trace.stream().map(span -> span.getKind() + " " + span.getName() + " " + span.getAttributes().asMap())
                .toList().toString();
    }
}
