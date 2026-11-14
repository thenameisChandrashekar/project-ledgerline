package com.azentio.ledgerline.fingerprint;

import java.time.Instant;

/** A statement sighting. The PAN is stored only as a SHA-256 digest, never in clear. */
public record FingerprintEntry(String fileId, String panDigest, Instant submittedAt) {

    String member() {
        return fileId + "|" + panDigest;
    }

    static FingerprintEntry fromMember(String member, double score) {
        int sep = member.lastIndexOf('|');
        return new FingerprintEntry(member.substring(0, sep), member.substring(sep + 1),
                Instant.ofEpochMilli((long) score));
    }
}
