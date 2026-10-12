package com.dsh.budgetbook;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Money is always stored as a long number of cents (fen), never as a float.
 * Formatting is done by hand so it never depends on the device locale.
 */
public final class Money {

    /** Yuan sign, written as an escape so the source file stays pure ASCII. */
    public static final String YUAN = "\u00A5";

    private static final BigDecimal MAX = new BigDecimal("99999999");

    private Money() {
    }

    /** 1234567 -> "12,345.67" */
    public static String fmt(long cents) {
        boolean negative = cents < 0L;
        long value = negative ? -cents : cents;
        long yuan = value / 100L;
        long fen = value % 100L;

        String digits = Long.toString(yuan);
        StringBuilder sb = new StringBuilder();
        int len = digits.length();
        for (int i = 0; i < len; i++) {
            sb.append(digits.charAt(i));
            int rest = len - i - 1;
            if (rest > 0 && rest % 3 == 0) {
                sb.append(',');
            }
        }
        sb.append('.');
        if (fen < 10L) {
            sb.append('0');
        }
        sb.append(fen);
        return (negative ? "-" : "") + sb.toString();
    }

    /** "+12.00" for income, "-12.00" for expense. */
    public static String signed(long cents, boolean income) {
        return (income ? "+" : "-") + fmt(cents);
    }

    /** Returns cents, or -1 when the text is not a usable positive amount. */
    public static long parse(String raw) {
        if (raw == null) {
            return -1L;
        }
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if ((c >= '0' && c <= '9') || c == '.') {
                digits.append(c);
            }
        }
        String s = digits.toString();
        if (s.length() == 0 || s.equals(".")) {
            return -1L;
        }
        try {
            BigDecimal bd = new BigDecimal(s);
            if (bd.signum() < 0) {
                return -1L;
            }
            bd = bd.setScale(2, RoundingMode.HALF_UP);
            if (bd.compareTo(MAX) > 0) {
                return -1L;
            }
            return bd.movePointRight(2).longValueExact();
        } catch (Exception e) {
            return -1L;
        }
    }
}
