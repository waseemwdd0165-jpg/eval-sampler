package com.waseemansari.evalsampler;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Draws a stratified sample that is reproducible, and that grows without
 * throwing away what it already drew.
 *
 * The trick is to not shuffle anything. Every item is given a sort key derived
 * from the seed and its own id, and the sample is simply the lowest keys in
 * each stratum. Two consequences follow, and they are the reason this exists:
 *
 *   Reproducible. The same seed and the same ids give the same sample, on any
 *   machine, in any order the file happens to arrive in. A second rater can be
 *   handed a seed instead of a file and draw the identical rows.
 *
 *   Nested. Ask for 800 rows after having rated 500 and the 800 contain those
 *   500. The keys did not move, so a bigger k simply reaches further down the
 *   same ordered list. A reshuffle would have thrown the earlier work away, and
 *   "sample the remainder separately" quietly biases the top-up, because the
 *   rows already rated are no longer eligible.
 *
 * The key is the first eight bytes of SHA-256 over the seed and the id.
 * String.hashCode would be stable too, but it clusters badly on ids that share
 * a prefix, which is what real ids look like. A published digest also means the
 * same rows can be picked by a script in another language.
 */
public final class StratifiedSampler {

    private StratifiedSampler() {
    }

    public static SampleResult draw(List<Item> population, int size, int minPerStratum, String seed) {
        if (seed == null || seed.isEmpty()) {
            throw new IllegalArgumentException("a seed is required, otherwise the draw cannot be repeated");
        }
        rejectDuplicateIds(population);

        // Sorted by stratum name, never by the order rows arrived in, so two
        // exports of the same data cannot allocate differently.
        TreeMap<String, List<Item>> grouped = new TreeMap<String, List<Item>>();
        for (Item item : population) {
            List<Item> bucket = grouped.get(item.stratum());
            if (bucket == null) {
                bucket = new ArrayList<Item>();
                grouped.put(item.stratum(), bucket);
            }
            bucket.add(item);
        }

        LinkedHashMap<String, Integer> sizes = new LinkedHashMap<String, Integer>();
        for (Map.Entry<String, List<Item>> e : grouped.entrySet()) {
            sizes.put(e.getKey(), e.getValue().size());
        }

        LinkedHashMap<String, Integer> quota = Allocation.allocate(sizes, size, minPerStratum);
        LinkedHashMap<String, Integer> taken = new LinkedHashMap<String, Integer>();
        List<String> notes = new ArrayList<String>();
        List<Item> selected = new ArrayList<Item>();

        for (Map.Entry<String, List<Item>> e : grouped.entrySet()) {
            String stratum = e.getKey();
            List<Item> bucket = new ArrayList<Item>(e.getValue());
            sortByKey(bucket, seed);

            int want = quota.containsKey(stratum) ? quota.get(stratum) : 0;
            int got = Math.min(want, bucket.size());
            selected.addAll(bucket.subList(0, got));
            taken.put(stratum, got);

            if (got < want) {
                notes.add(stratum + " was asked for " + want + " but holds only " + bucket.size());
            }
        }

        if (selected.size() < size) {
            notes.add("the sample is " + selected.size() + " rows, not " + size
                    + ", because the population could not fill it");
        }

        // One queue, strata interleaved, in key order. A rater working down the
        // file does not do all of one language first, so fatigue does not land
        // on one stratum.
        sortByKey(selected, seed);
        return new SampleResult(selected, sizes, quota, taken, notes, seed);
    }

    private static void sortByKey(List<Item> items, final String seed) {
        final Map<String, Long> keys = new java.util.HashMap<String, Long>();
        for (Item item : items) {
            keys.put(item.id(), key(seed, item.id()));
        }
        Collections.sort(items, new Comparator<Item>() {
            public int compare(Item a, Item b) {
                int c = Long.compare(keys.get(a.id()), keys.get(b.id()));
                return c != 0 ? c : a.id().compareTo(b.id());   // a digest collision must not be random
            }
        });
    }

    /** The first eight bytes of SHA-256 over seed, a null byte, and the id. */
    public static long key(String seed, String id) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update(seed.getBytes("UTF-8"));
            sha.update((byte) 0);
            byte[] digest = sha.digest(id.getBytes("UTF-8"));
            long value = 0;
            for (int i = 0; i < 8; i++) {
                value = (value << 8) | (digest[i] & 0xffL);
            }
            return value >>> 1;            // drop the sign bit, keep the ordering
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required and was not available", e);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 is required and was not available", e);
        }
    }

    private static void rejectDuplicateIds(List<Item> population) {
        Set<String> seen = new HashSet<String>();
        for (Item item : population) {
            if (!seen.add(item.id())) {
                throw new IllegalArgumentException(
                        "id '" + item.id() + "' appears twice; ids have to be unique or the draw "
                        + "cannot be repeated");
            }
        }
    }
}
