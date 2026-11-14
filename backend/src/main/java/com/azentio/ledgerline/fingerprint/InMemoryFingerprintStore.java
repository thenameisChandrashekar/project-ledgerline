package com.azentio.ledgerline.fingerprint;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** For unit tests and the offline evaluator. TTL is approximated by trimming on every access. */
public class InMemoryFingerprintStore implements DocumentFingerprintStore {

    private final Map<String, List<FingerprintEntry>> entries = new ConcurrentHashMap<>();

    @Override
    public List<FingerprintEntry> findSince(String key, Instant notBefore) {
        List<FingerprintEntry> list = entries.get(key);
        if (list == null) {
            return List.of();
        }
        list.removeIf(e -> e.submittedAt().isBefore(notBefore));
        return List.copyOf(list);
    }

    @Override
    public void add(String key, FingerprintEntry entry, Instant notBefore, Duration ttl) {
        List<FingerprintEntry> list = entries.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>());
        list.removeIf(e -> e.submittedAt().isBefore(notBefore) || e.member().equals(entry.member()));
        list.add(entry);
    }
}
