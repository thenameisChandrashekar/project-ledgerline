package com.azentio.ledgerline.masking;

/** Masks identifiers for API responses and logs: only the last four characters stay visible. */
public final class Masking {

    private Masking() {
    }

    /** ABCDE1234F becomes ******234F. */
    public static String pan(String pan) {
        return mask(pan, '*');
    }

    /** 001234564821 becomes XXXXXXXX4821. */
    public static String account(String accountNumber) {
        return mask(accountNumber, 'X');
    }

    private static String mask(String value, char maskChar) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        int visible = Math.min(4, value.length());
        return String.valueOf(maskChar).repeat(Math.max(4, value.length() - visible)) + value.substring(value.length() - visible);
    }
}
