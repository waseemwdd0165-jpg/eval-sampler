package com.waseemansari.evalsampler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How many of the n slots each stratum gets.
 *
 * Three things have to hold at once and they pull against each other:
 *
 *   1. the quotas add up to exactly n, not n plus or minus a few from rounding
 *   2. no stratum is asked for more rows than it has
 *   3. a small stratum still gets looked at
 *
 * Proportional allocation on its own satisfies none of them. Rounding each
 * share independently loses or gains rows; a stratum with four items gets a
 * quota of nine; and a stratum that is one percent of the population gets
 * nothing at all, which is exactly the stratum where the surprises live.
 */
public final class Allocation {

    private Allocation() {
    }

    /**
     * @param sizes          stratum name to how many items it holds, in a stable order
     * @param n              how many rows the sample should hold in total
     * @param minPerStratum  the smallest worthwhile look at a stratum, capped by its size
     */
    public static LinkedHashMap<String, Integer> allocate(
            LinkedHashMap<String, Integer> sizes, int n, int minPerStratum) {

        if (n < 0) {
            throw new IllegalArgumentException("sample size cannot be negative");
        }
        if (minPerStratum < 0) {
            throw new IllegalArgumentException("minimum per stratum cannot be negative");
        }
        LinkedHashMap<String, Integer> quota = new LinkedHashMap<String, Integer>();
        if (sizes.isEmpty()) {
            return quota;
        }

        long population = 0;
        for (int size : sizes.values()) {
            population += size;
        }
        if (n >= population) {
            quota.putAll(sizes);            // asking for everything, so take everything
            return quota;
        }

        // The floor each stratum is entitled to: the minimum, or all of it if
        // the stratum is smaller than the minimum.
        LinkedHashMap<String, Integer> floor = new LinkedHashMap<String, Integer>();
        long floorTotal = 0;
        for (Map.Entry<String, Integer> e : sizes.entrySet()) {
            int f = Math.min(minPerStratum, e.getValue());
            floor.put(e.getKey(), f);
            floorTotal += f;
        }
        if (floorTotal > n) {
            throw new IllegalArgumentException(
                    "a minimum of " + minPerStratum + " across " + sizes.size()
                    + " strata needs at least " + floorTotal + " rows, but the sample size is " + n);
        }

        // Largest remainder: floor of the exact share, then hand out what is
        // left over to whoever was cut by the most.
        List<String> names = new ArrayList<String>(sizes.keySet());
        final Map<String, Double> remainder = new LinkedHashMap<String, Double>();
        int handedOut = 0;
        for (String name : names) {
            double exact = (double) n * sizes.get(name) / (double) population;
            int base = (int) Math.floor(exact);
            quota.put(name, base);
            remainder.put(name, exact - base);
            handedOut += base;
        }

        final Map<String, Integer> sizeRef = sizes;
        List<String> byRemainder = new ArrayList<String>(names);
        byRemainder.sort(new Comparator<String>() {
            public int compare(String a, String b) {
                int c = Double.compare(remainder.get(b), remainder.get(a));
                if (c != 0) {
                    return c;
                }
                c = Integer.compare(sizeRef.get(b), sizeRef.get(a));   // bigger stratum first
                return c != 0 ? c : a.compareTo(b);                    // then by name, so it is reproducible
            }
        });
        int leftOver = n - handedOut;
        for (int i = 0; i < leftOver; i++) {
            String name = byRemainder.get(i % byRemainder.size());
            quota.put(name, quota.get(name) + 1);
        }

        // Now the two constraints proportionality knows nothing about.
        for (String name : names) {
            int want = Math.max(quota.get(name), floor.get(name));
            quota.put(name, Math.min(want, sizes.get(name)));
        }

        balance(quota, sizes, floor, n);
        return quota;
    }

    /**
     * Raising the small strata and capping the large ones has almost certainly
     * moved the total off n. Move single rows until it is back, taking from
     * whoever is furthest above its floor and giving to whoever has the most
     * room left.
     */
    private static void balance(LinkedHashMap<String, Integer> quota,
                                Map<String, Integer> sizes,
                                Map<String, Integer> floor,
                                int n) {
        int guard = 0;
        int limit = 4 * (n + quota.size() + 1);

        while (total(quota) > n && guard++ < limit) {
            String take = null;
            int best = 0;
            for (String name : quota.keySet()) {
                int spare = quota.get(name) - floor.get(name);
                if (spare > best || (spare == best && spare > 0 && take != null
                        && betterToTakeFrom(name, take, quota))) {
                    best = spare;
                    take = name;
                }
            }
            if (take == null || best <= 0) {
                break;                       // everything is on its floor already
            }
            quota.put(take, quota.get(take) - 1);
        }

        while (total(quota) < n && guard++ < limit) {
            String give = null;
            int best = 0;
            for (String name : quota.keySet()) {
                int room = sizes.get(name) - quota.get(name);
                if (room > best || (room == best && room > 0 && give != null
                        && betterToGiveTo(name, give, sizes))) {
                    best = room;
                    give = name;
                }
            }
            if (give == null || best <= 0) {
                break;                       // no stratum has a spare row
            }
            quota.put(give, quota.get(give) + 1);
        }
    }

    private static boolean betterToTakeFrom(String candidate, String current,
                                            Map<String, Integer> quota) {
        int c = Integer.compare(quota.get(candidate), quota.get(current));
        return c > 0 || (c == 0 && candidate.compareTo(current) < 0);
    }

    private static boolean betterToGiveTo(String candidate, String current,
                                          Map<String, Integer> sizes) {
        int c = Integer.compare(sizes.get(candidate), sizes.get(current));
        return c > 0 || (c == 0 && candidate.compareTo(current) < 0);
    }

    public static int total(Map<String, Integer> quota) {
        int sum = 0;
        for (int v : quota.values()) {
            sum += v;
        }
        return sum;
    }
}
