package com.azentio.ledgerline.config;

import com.azentio.ledgerline.engine.RiskBander;
import com.azentio.ledgerline.engine.RuleEngine;
import com.azentio.ledgerline.engine.RulesConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@EnableConfigurationProperties(FingerprintProperties.class)
public class LedgerlineConfig {

    @Bean
    @ConfigurationProperties(prefix = "ledgerline.rules")
    public RulesConfig rulesConfig() {
        return new RulesConfig();
    }

    @Bean
    public RuleEngine ruleEngine(RulesConfig rulesConfig) {
        return RuleEngine.withDefaultRules(rulesConfig);
    }

    @Bean
    public RiskBander riskBander(RulesConfig rulesConfig) {
        return new RiskBander(rulesConfig);
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /** Bounded pool for fingerprint-store calls so a slow Redis cannot hold request threads. */
    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService fingerprintExecutor(FingerprintProperties properties) {
        return Executors.newFixedThreadPool(properties.getPoolSize());
    }
}
