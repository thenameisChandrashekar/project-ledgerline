package com.azentio.ledgerline.config;

import com.azentio.ledgerline.engine.RulesConfig;
import com.azentio.ledgerline.fingerprint.DocumentFingerprintStore;
import com.azentio.ledgerline.fingerprint.DocumentReuseChecker;
import com.azentio.ledgerline.fingerprint.InMemoryFingerprintStore;
import com.azentio.ledgerline.fingerprint.RedisFingerprintStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;

@Configuration
public class FingerprintConfig {

    @Bean
    @ConditionalOnProperty(name = "ledgerline.fingerprint.store", havingValue = "redis", matchIfMissing = true)
    public DocumentFingerprintStore redisFingerprintStore(StringRedisTemplate redis) {
        return new RedisFingerprintStore(redis);
    }

    @Bean
    @ConditionalOnProperty(name = "ledgerline.fingerprint.store", havingValue = "memory")
    public DocumentFingerprintStore inMemoryFingerprintStore() {
        return new InMemoryFingerprintStore();
    }

    @Bean
    public DocumentReuseChecker documentReuseChecker(DocumentFingerprintStore store, Clock clock, RulesConfig rules) {
        return new DocumentReuseChecker(store, clock, rules);
    }
}
