package com.enterprise.crud.infrastructure.configuration;

import com.enterprise.crud.infrastructure.entrypoints.rest.idempotency.IdempotencyFilter;
import com.enterprise.crud.infrastructure.entrypoints.rest.idempotency.IdempotencyStore;
import com.enterprise.crud.infrastructure.redis.RedisIdempotencyStore;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IdempotencyConfiguration.IdempotencyProperties.class)
class IdempotencyConfiguration {

    /**
     * @param lockTtl   how long an in-progress claim lives; must exceed the slowest request, and bounds how long a
     *                  crashed request blocks its key
     * @param retention how long a successful response is replayed for the same key
     */
    @ConfigurationProperties("app.idempotency")
    record IdempotencyProperties(Duration lockTtl, Duration retention) {

        IdempotencyProperties {
            lockTtl = lockTtl == null ? Duration.ofSeconds(30) : lockTtl;
            retention = retention == null ? Duration.ofHours(24) : retention;
        }
    }

    @Bean
    IdempotencyStore idempotencyStore(StringRedisTemplate redis, JsonMapper jsonMapper) {
        return new RedisIdempotencyStore(redis, jsonMapper);
    }

    /** Only the versioned API is covered; POST is the only non-idempotent method it exposes. */
    @Bean
    FilterRegistrationBean<IdempotencyFilter> idempotencyFilter(IdempotencyStore store, JsonMapper jsonMapper,
            IdempotencyProperties properties, MeterRegistry meterRegistry) {
        var registration = new FilterRegistrationBean<>(new IdempotencyFilter(store, jsonMapper,
                properties.lockTtl(), properties.retention(), meterRegistry));
        registration.addUrlPatterns("/api/*");
        return registration;
    }
}
