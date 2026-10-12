package com.dsh.budgetbook;

/** JVM-side checks for the locale independent money maths used by the app. */
public class LogicTest {

    private static int fails = 0;

    private static void eq(String label, Object actual, Object expected) {
        boolean ok = (actual == null) ? (expected == null) : actual.equals(expected);
        if (ok) {
            System.out.println("ok    " + label + " -> " + actual);
        } else {
            fails++;
            System.out.println("FAIL  " + label + " expected=" + expected + " actual=" + actual);
        }
    }

    public static void main(String[] args) {
        eq("fmt 0", Money.fmt(0L), "0.00");
        eq("fmt 5", Money.fmt(5L), "0.05");
        eq("fmt 50", Money.fmt(50L), "0.50");
        eq("fmt 100", Money.fmt(100L), "1.00");
        eq("fmt 99999", Money.fmt(99999L), "999.99");
        eq("fmt 123456789", Money.fmt(123456789L), "1,234,567.89");
        eq("fmt negative", Money.fmt(-1234L), "-12.34");

        eq("signed income", Money.signed(500000L, true), "+5,000.00");
        eq("signed expense", Money.signed(1234L, false), "-12.34");

        eq("parse 12.34", Money.parse("12.34"), 1234L);
        eq("parse 12", Money.parse("12"), 1200L);
        eq("parse 1,234.5", Money.parse("1,234.5"), 123450L);
        eq("parse padded", Money.parse("  88.80  "), 8880L);
        eq("parse yuan sign", Money.parse("\u00A530.00"), 3000L);
        eq("parse empty", Money.parse(""), -1L);
        eq("parse null", Money.parse(null), -1L);
        eq("parse junk", Money.parse("abc"), -1L);
        eq("parse dot", Money.parse("."), -1L);
        eq("parse zero", Money.parse("0"), 0L);
        eq("parse sub-cent", Money.parse("0.001"), 0L);
        eq("parse too big", Money.parse("999999999"), -1L);

        // Mirrors the budget arithmetic in MainActivity.refresh().
        long budget = 300000L;
        long expense = 123456L;
        eq("remaining", Money.fmt(budget - expense), "1,765.44");
        eq("permille", expense * 1000L / budget, 411L);

        long over = 400000L;
        eq("overspend", Money.fmt(over - budget), "1,000.00");

        long bigSpend = 900000L;
        long clamped = bigSpend * 1000L / budget;
        eq("clamped permille", clamped > 1000L ? 1000L : clamped, 1000L);

        long income = 800000L;
        eq("income total", Money.YUAN + Money.fmt(income), "\u00A58,000.00");

        System.out.println(fails == 0 ? "LOGIC-OK" : ("LOGIC-FAILED (" + fails + ")"));
        if (fails != 0) {
            System.exit(1);
        }
    }
}
