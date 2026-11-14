package com.azentio.ledgerline.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes generated loan files as JSON plus a labels.csv sidecar (fileId,seededPattern).
 * Usage: {@code GenerateCli <count> <outDir> [seed] [recycled]}.
 */
public final class GenerateCli {

    private GenerateCli() {
    }

    public static void main(String[] args) throws IOException {
        int count = args.length > 0 ? Integer.parseInt(args[0]) : 330;
        Path out = Path.of(args.length > 1 ? args[1] : "data/generated");
        long seed = args.length > 2 ? Long.parseLong(args[2]) : 42L;
        int recycled = args.length > 3 ? Integer.parseInt(args[3]) : 30;

        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        Path filesDir = Files.createDirectories(out.resolve("files"));
        List<String> labels = new ArrayList<>(List.of("fileId,seededPattern"));
        for (DataGenerator.GeneratedFile generated : new DataGenerator(seed, "LF", Instant.now()).generate(count, recycled)) {
            mapper.writerWithDefaultPrettyPrinter()
                    .writeValue(filesDir.resolve(generated.file().fileId() + ".json").toFile(), generated.file());
            labels.add(generated.file().fileId() + "," + generated.pattern());
        }
        Files.write(out.resolve("labels.csv"), labels);
        System.out.printf("Wrote %d loan files to %s and labels to %s%n", count, filesDir, out.resolve("labels.csv"));
    }
}
