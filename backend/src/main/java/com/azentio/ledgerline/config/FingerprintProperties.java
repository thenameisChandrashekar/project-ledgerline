package com.azentio.ledgerline.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ledgerline.fingerprint")
public class FingerprintProperties {

    /** redis (default) or memory. */
    private String store = "redis";
    /** Max time a fingerprint lookup may take before the signal is treated as degraded. */
    private long timeoutMs = 50;
    private int poolSize = 32;

    public String getStore() { return store; }
    public void setStore(String store) { this.store = store; }
    public long getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }
    public int getPoolSize() { return poolSize; }
    public void setPoolSize(int poolSize) { this.poolSize = poolSize; }
}
