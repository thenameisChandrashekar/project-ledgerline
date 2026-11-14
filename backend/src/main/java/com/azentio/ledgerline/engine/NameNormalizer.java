package com.azentio.ledgerline.engine;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Normalisation used for comparing employer and person names. */
public final class NameNormalizer {

    private static final Set<String> COMPANY_SUFFIXES = Set.of("PVT", "PRIVATE", "LTD", "LIMITED");

    private NameNormalizer() {
    }

    /** Upper-case, punctuation to spaces, collapse spaces, drop PVT/PRIVATE/LTD/LIMITED tokens. */
    public static String employer(String raw) {
        return Arrays.stream(tokens(raw))
                .filter(t -> !COMPANY_SUFFIXES.contains(t))
                .collect(Collectors.joining(" "));
    }

    /** Narration normalised the same way, so an employer can be found as a token sequence inside it. */
    public static boolean narrationContainsEmployer(String narration, String employer) {
        String normalisedEmployer = employer(employer);
        if (normalisedEmployer.isEmpty()) {
            return false;
        }
        return (" " + employer(narration) + " ").contains(" " + normalisedEmployer + " ");
    }

    /** Person name ignoring case, whitespace, dots and token order. */
    public static String person(String raw) {
        String withoutDots = raw == null ? "" : raw.replace(".", " ");
        return Arrays.stream(withoutDots.toUpperCase(Locale.ROOT).trim().split("\\s+"))
                .filter(t -> !t.isEmpty())
                .sorted()
                .collect(Collectors.joining(" "));
    }

    private static String[] tokens(String raw) {
        if (raw == null) {
            return new String[0];
        }
        String cleaned = raw.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", " ").trim();
        return cleaned.isEmpty() ? new String[0] : cleaned.split(" ");
    }
}
