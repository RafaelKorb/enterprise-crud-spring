package com.enterprise.crud.infrastructure.configuration;

import com.enterprise.crud.infrastructure.entrypoints.rest.tracing.TraceIdResponseFilter;
import io.micrometer.tracing.Tracer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration(proxyBeanMethods = false)
class ObservabilityConfiguration {

    /**
     * Ordered right after Spring's server observation filter ({@code HIGHEST_PRECEDENCE + 1}), which opens the
     * request span, and before the idempotency filter, so its own error responses also carry the header.
     */
    static final int TRACE_ID_FILTER_ORDER = Ordered.HIGHEST_PRECEDENCE + 2;

    @Bean
    FilterRegistrationBean<TraceIdResponseFilter> traceIdResponseFilter(Tracer tracer) {
        var registration = new FilterRegistrationBean<>(new TraceIdResponseFilter(tracer));
        registration.addUrlPatterns("/api/*");
        registration.setOrder(TRACE_ID_FILTER_ORDER);
        return registration;
    }
}
