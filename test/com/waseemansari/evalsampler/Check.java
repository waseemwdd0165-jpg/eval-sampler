package com.waseemansari.evalsampler;

import java.util.ArrayList;
import java.util.List;

/**
 * A twenty line test harness, so the project stays dependency free and anyone
 * with a JDK can run the tests without fetching anything.
 */
public final class Check {

    private static int passed = 0;
    private static final List<String> failures = new ArrayList<String>();

    public static void test(String name, Runnable body) {
        try {
            body.run();
            passed++;
        } catch (Throwable t) {
            failures.add(name + "\n      " + t.getMessage());
        }
    }

    public static void that(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError(what);
        }
    }

    public static void equal(Object expected, Object actual, String what) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(what + "\n      expected: " + expected + "\n      actual:   " + actual);
        }
    }

    public static void throwsWith(String fragment, Runnable body) {
        try {
            body.run();
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains(fragment)) {
                return;
            }
            throw new AssertionError("wrong message: " + e.getMessage());
        }
        throw new AssertionError("expected it to refuse, mentioning '" + fragment + "'");
    }

    public static int report() {
        System.out.println();
        if (failures.isEmpty()) {
            System.out.println(passed + " tests, all passed");
            return 0;
        }
        for (String f : failures) {
            System.out.println("  FAILED  " + f);
        }
        System.out.println();
        System.out.println(passed + " passed, " + failures.size() + " failed");
        return 1;
    }

    private Check() {
    }
}
