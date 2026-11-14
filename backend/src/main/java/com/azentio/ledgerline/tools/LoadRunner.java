package com.azentio.ledgerline.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Posts freshly generated loan files with N concurrent clients and prints band counts, DOCUMENT_REUSED hits,
 * recycled-file detection and p50/p95 latency of POST /api/loan-files.
 * Usage: {@code LoadRunner [baseUrl] [count] [concurrency] [recycled]}.
 */
public final class LoadRunner {

    private LoadRunner() {
    }

    public static void main(String[] args) throws Exception {
        String baseUrl = args.length > 0 ? args[0] : "http://localhost:8080";
        int count = args.length > 1 ? Integer.parseInt(args[1]) : 1000;
        int concurrency = args.length > 2 ? Integer.parseInt(args[2]) : 10;
        int recycled = args.length > 3 ? Integer.parseInt(args[3]) : 50;

        String runId = "LOAD" + Long.toString(System.currentTimeMillis(), 36).toUpperCase();
        List<DataGenerator.GeneratedFile> files =
                new DataGenerator(System.nanoTime(), runId, Instant.now()).generate(count, recycled);
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

        List<Long> latenciesMicros = Collections.synchronizedList(new ArrayList<>());
        Map<String, AtomicInteger> bands = new ConcurrentHashMap<>();
        AtomicInteger reusedHits = new AtomicInteger();
        AtomicInteger recycledFlagged = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();

        // Files are queued in generation order; recycled files sit well after their source, so the source
        // is normally stored before its copy is evaluated even with concurrent clients.
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        long started = System.nanoTime();
        for (DataGenerator.GeneratedFile generated : files) {
            byte[] body = mapper.writeValueAsBytes(generated.file());
            pool.submit(() -> {
                HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/loan-files"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                        .build();
                long t0 = System.nanoTime();
                try {
                    HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                    latenciesMicros.add((System.nanoTime() - t0) / 1_000);
                    if (response.statusCode() >= 300) {
                        errors.incrementAndGet();
                        return;
                    }
                    JsonNode result = mapper.readTree(response.body());
                    bands.computeIfAbsent(result.get("band").asText(), b -> new AtomicInteger()).incrementAndGet();
                    boolean reused = false;
                    for (JsonNode hit : result.get("ruleHits")) {
                        reused |= "DOCUMENT_REUSED".equals(hit.get("ruleId").asText());
                    }
                    if (reused) {
                        reusedHits.incrementAndGet();
                        if (generated.pattern() == SeededPattern.RECYCLED_DOCUMENT) {
                            recycledFlagged.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                }
            });
        }
        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.MINUTES);
        double seconds = (System.nanoTime() - started) / 1e9;

        List<Long> sorted = new ArrayList<>(latenciesMicros);
        Collections.sort(sorted);
        Map<String, Integer> bandCounts = new TreeMap<>();
        bands.forEach((band, n) -> bandCounts.put(band, n.get()));
        System.out.printf("Posted %d files with %d clients in %.1fs (%.0f req/s), errors=%d%n", files.size(),
                concurrency, seconds, files.size() / seconds, errors.get());
        System.out.println("Bands: " + bandCounts);
        System.out.printf("DOCUMENT_REUSED hits: %d; seeded RECYCLED_DOCUMENT flagged: %d/%d%n", reusedHits.get(),
                recycledFlagged.get(), recycled);
        System.out.printf("Latency POST /api/loan-files: p50=%.1f ms, p95=%.1f ms, max=%.1f ms%n",
                percentile(sorted, 50), percentile(sorted, 95), sorted.isEmpty() ? 0 : sorted.get(sorted.size() - 1) / 1000.0);
    }

    private static double percentile(List<Long> sortedMicros, int p) {
        if (sortedMicros.isEmpty()) {
            return 0;
        }
        int index = (int) Math.ceil(p / 100.0 * sortedMicros.size()) - 1;
        return sortedMicros.get(Math.max(0, index)) / 1000.0;
    }
}
