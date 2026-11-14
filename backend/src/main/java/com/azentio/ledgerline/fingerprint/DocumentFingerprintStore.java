package com.azentio.ledgerline.fingerprint;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Time-windowed store of statement fingerprints shared across loan files. */
public interface DocumentFingerprintStore {

    /** Entries recorded under {@code key} with submittedAt at or after {@code notBefore}; older ones may be trimmed. */
    List<FingerprintEntry> findSince(String key, Instant notBefore);

    /** Records an entry; the key expires after {@code ttl} so the store cannot grow without bound. */
    void add(String key, FingerprintEntry entry, Instant notBefore, Duration ttl);
}
