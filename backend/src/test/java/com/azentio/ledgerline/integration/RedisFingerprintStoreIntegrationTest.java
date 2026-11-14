package com.azentio.ledgerline.integration;

import com.azentio.ledgerline.fingerprint.FingerprintEntry;
import com.azentio.ledgerline.fingerprint.RedisFingerprintStore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Needs Docker. Run with: mvn test -Pintegration */
@Tag("integration")
@Testcontainers
class RedisFingerprintStoreIntegrationTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static LettuceConnectionFactory factory;
    static StringRedisTemplate template;

    @BeforeAll
    static void connect() {
        factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        factory.afterPropertiesSet();
        template = new StringRedisTemplate(factory);
    }

    @AfterAll
    static void close() {
        factory.destroy();
    }

    @Test
    void windowTrimsOldEntriesAndKeyExpires() {
        RedisFingerprintStore store = new RedisFingerprintStore(template);
        Instant now = Instant.parse("2026-10-01T00:00:00Z");
        Instant windowStart = now.minus(Duration.ofDays(180));
        store.add("k", new FingerprintEntry("OLD", "pan1", windowStart.minusSeconds(1)), windowStart.minus(Duration.ofDays(1)), Duration.ofDays(181));
        store.add("k", new FingerprintEntry("NEW", "pan2", now), windowStart, Duration.ofDays(181));

        assertThat(store.findSince("k", windowStart)).extracting(FingerprintEntry::fileId).containsExactly("NEW");
        assertThat(template.getExpire("k")).isPositive();
    }
}
