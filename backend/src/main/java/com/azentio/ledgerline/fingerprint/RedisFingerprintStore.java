package com.azentio.ledgerline.fingerprint;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Sorted set per hash, member {@code fileId|panDigest}, score = submittedAt epoch millis.
 * Every call trims entries older than the window; every write refreshes the key TTL.
 */
public class RedisFingerprintStore implements DocumentFingerprintStore {

    private final StringRedisTemplate redis;

    public RedisFingerprintStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public List<FingerprintEntry> findSince(String key, Instant notBefore) {
        ZSetOperations<String, String> zset = redis.opsForZSet();
        zset.removeRangeByScore(key, Double.NEGATIVE_INFINITY, notBefore.toEpochMilli() - 1);
        Set<ZSetOperations.TypedTuple<String>> found =
                zset.rangeByScoreWithScores(key, notBefore.toEpochMilli(), Double.POSITIVE_INFINITY);
        if (found == null) {
            return List.of();
        }
        return found.stream().map(t -> FingerprintEntry.fromMember(t.getValue(), t.getScore())).toList();
    }

    @Override
    public void add(String key, FingerprintEntry entry, Instant notBefore, Duration ttl) {
        ZSetOperations<String, String> zset = redis.opsForZSet();
        zset.add(key, entry.member(), entry.submittedAt().toEpochMilli());
        zset.removeRangeByScore(key, Double.NEGATIVE_INFINITY, notBefore.toEpochMilli() - 1);
        redis.expire(key, ttl);
    }
}
