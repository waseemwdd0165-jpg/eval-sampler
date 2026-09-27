package com.waseemansari.evalsampler;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static com.waseemansari.evalsampler.Check.equal;
import static com.waseemansari.evalsampler.Check.test;
import static com.waseemansari.evalsampler.Check.that;
import static com.waseemansari.evalsampler.Check.throwsWith;

public final class Tests {

    public static void main(String[] args) {
        allocation();
        sampling();
        csv();
        System.exit(Check.report());
    }

    // ---------------------------------------------------------------- helpers

    private static LinkedHashMap<String, Integer> sizes(Object... pairs) {
        LinkedHashMap<String, Integer> m = new LinkedHashMap<String, Integer>();
        for (int i = 0; i < pairs.length; i += 2) {
            m.put((String) pairs[i], (Integer) pairs[i + 1]);
        }
        return m;
    }

    /** A population of n items spread over the named strata in the given counts. */
    private static List<Item> population(Object... pairs) {
        List<Item> items = new ArrayList<Item>();
        int n = 0;
        for (int i = 0; i < pairs.length; i += 2) {
            String stratum = (String) pairs[i];
            int count = (Integer) pairs[i + 1];
            for (int k = 0; k < count; k++) {
                String id = String.format("r-%05d", n++);
                items.add(new Item(id, stratum, Arrays.asList(id, stratum)));
            }
        }
        return items;
    }

    private static List<String> ids(SampleResult r) {
        List<String> out = new ArrayList<String>();
        for (Item item : r.selected()) {
            out.add(item.id());
        }
        return out;
    }

    // ------------------------------------------------------------- allocation

    private static void allocation() {
        test("quotas add up to exactly the sample size", new Runnable() {
            public void run() {
                LinkedHashMap<String, Integer> q =
                        Allocation.allocate(sizes("a", 33, "b", 33, "c", 34), 10, 0);
                equal(10, Allocation.total(q), "total");
                equal(3, q.get("a"), "a");
                equal(3, q.get("b"), "b");
                equal(4, q.get("c"), "c");   // the largest remainder gets the spare row
            }
        });

        test("rounding never loses or invents a row, at any size", new Runnable() {
            public void run() {
                LinkedHashMap<String, Integer> pop = sizes("en", 4013, "hi", 907, "mr", 311, "ur", 77);
                for (int n = 1; n <= 400; n++) {
                    LinkedHashMap<String, Integer> q = Allocation.allocate(pop, n, 0);
                    equal(n, Allocation.total(q), "total for n=" + n);
                }
            }
        });

        test("a stratum is never asked for more rows than it holds", new Runnable() {
            public void run() {
                LinkedHashMap<String, Integer> pop = sizes("tiny", 2, "small", 17, "huge", 1000);
                for (int n = 1; n <= 300; n++) {
                    for (int min = 0; min <= 5; min++) {
                        LinkedHashMap<String, Integer> q;
                        try {
                            q = Allocation.allocate(pop, n, min);
                        } catch (IllegalArgumentException refused) {
                            continue;       // the minimum did not fit, which is its own test
                        }
                        for (Map.Entry<String, Integer> e : q.entrySet()) {
                            that(e.getValue() <= pop.get(e.getKey()),
                                    e.getKey() + " over-drawn at n=" + n + " min=" + min);
                        }
                        equal(n, Allocation.total(q), "total at n=" + n + " min=" + min);
                    }
                }
            }
        });

        test("without a minimum, a rare stratum is dropped entirely", new Runnable() {
            public void run() {
                LinkedHashMap<String, Integer> plain =
                        Allocation.allocate(sizes("rare", 5, "common", 995), 100, 0);
                equal(0, plain.get("rare"), "half a row rounds to none, and the rare case goes unseen");
                equal(100, plain.get("common"), "common takes the lot");

                LinkedHashMap<String, Integer> withMin =
                        Allocation.allocate(sizes("rare", 5, "common", 995), 100, 4);
                equal(4, withMin.get("rare"), "rare");
                equal(96, withMin.get("common"), "common pays for it");
                equal(100, Allocation.total(withMin), "total");
            }
        });

        test("the minimum is capped by what the stratum actually has", new Runnable() {
            public void run() {
                LinkedHashMap<String, Integer> q =
                        Allocation.allocate(sizes("rare", 3, "common", 997), 100, 10);
                equal(3, q.get("rare"), "rare only has three");
                equal(97, q.get("common"), "common");
                equal(100, Allocation.total(q), "total");
            }
        });

        test("an impossible minimum is refused, with the arithmetic in the message", new Runnable() {
            public void run() {
                throwsWith("at least 25", new Runnable() {
                    public void run() {
                        Allocation.allocate(sizes("a", 100, "b", 100, "c", 100, "d", 100, "e", 100), 10, 5);
                    }
                });
            }
        });

        test("asking for the whole population returns the whole population", new Runnable() {
            public void run() {
                LinkedHashMap<String, Integer> q = Allocation.allocate(sizes("a", 7, "b", 3), 10, 0);
                equal(7, q.get("a"), "a");
                equal(3, q.get("b"), "b");

                LinkedHashMap<String, Integer> more = Allocation.allocate(sizes("a", 7, "b", 3), 50, 0);
                equal(10, Allocation.total(more), "cannot draw more than exists");
            }
        });

        test("a sample size of zero draws nothing", new Runnable() {
            public void run() {
                equal(0, Allocation.total(Allocation.allocate(sizes("a", 7, "b", 3), 0, 0)), "total");
            }
        });

        test("the same question twice gives the same answer", new Runnable() {
            public void run() {
                LinkedHashMap<String, Integer> pop = sizes("a", 313, "b", 197, "c", 41, "d", 7);
                equal(Allocation.allocate(pop, 73, 2).toString(),
                      Allocation.allocate(pop, 73, 2).toString(), "allocation is a pure function");
            }
        });
    }

    // --------------------------------------------------------------- sampling

    private static void sampling() {
        test("the same seed draws the same rows", new Runnable() {
            public void run() {
                List<Item> pop = population("en", 300, "hi", 120, "mr", 40);
                equal(ids(StratifiedSampler.draw(pop, 50, 0, "july-queue")),
                      ids(StratifiedSampler.draw(pop, 50, 0, "july-queue")), "same seed");
            }
        });

        test("a different seed draws a different sample", new Runnable() {
            public void run() {
                List<Item> pop = population("en", 300, "hi", 120, "mr", 40);
                that(!ids(StratifiedSampler.draw(pop, 50, 0, "july-queue"))
                        .equals(ids(StratifiedSampler.draw(pop, 50, 0, "august-queue"))),
                        "two seeds should not coincide");
            }
        });

        test("the order the rows arrive in does not change the sample", new Runnable() {
            public void run() {
                List<Item> pop = population("en", 300, "hi", 120, "mr", 40);
                List<Item> shuffled = new ArrayList<Item>(pop);
                Collections.shuffle(shuffled, new Random(9));
                equal(ids(StratifiedSampler.draw(pop, 50, 0, "july-queue")),
                      ids(StratifiedSampler.draw(shuffled, 50, 0, "july-queue")),
                      "an export in another order is the same population");
            }
        });

        test("a bigger sample contains the smaller one", new Runnable() {
            public void run() {
                List<Item> pop = population("en", 300, "hi", 120, "mr", 40);
                Set<String> first = new HashSet<String>(ids(StratifiedSampler.draw(pop, 40, 0, "s")));
                List<String> second = ids(StratifiedSampler.draw(pop, 90, 0, "s"));
                that(second.containsAll(first), "the first 40 must survive into the 90");
                equal(90, second.size(), "and the bigger draw is the size asked for");
            }
        });

        test("top-up holds when a minimum is in play as well", new Runnable() {
            public void run() {
                List<Item> pop = population("en", 500, "hi", 80, "rare", 9);
                Set<String> first = new HashSet<String>(ids(StratifiedSampler.draw(pop, 60, 5, "s")));
                that(ids(StratifiedSampler.draw(pop, 150, 5, "s")).containsAll(first),
                        "raising the size must not drop anything already rated");
            }
        });

        test("the relative order of the earlier rows is preserved too", new Runnable() {
            public void run() {
                List<Item> pop = population("en", 300, "hi", 120);
                List<String> small = ids(StratifiedSampler.draw(pop, 30, 0, "s"));
                List<String> large = ids(StratifiedSampler.draw(pop, 80, 0, "s"));
                List<String> filtered = new ArrayList<String>(large);
                filtered.retainAll(new HashSet<String>(small));
                equal(small, filtered, "a top-up inserts rows, it does not reorder them");
            }
        });

        test("each stratum gets exactly its quota", new Runnable() {
            public void run() {
                List<Item> pop = population("en", 300, "hi", 120, "mr", 40);
                SampleResult r = StratifiedSampler.draw(pop, 46, 0, "s");
                for (Map.Entry<String, Integer> e : r.quota().entrySet()) {
                    equal(e.getValue(), r.taken().get(e.getKey()), "drawn from " + e.getKey());
                }
                equal(46, r.size(), "total");
                that(r.notes().isEmpty(), "nothing to report");
            }
        });

        test("a population too small to fill the sample says so rather than pretending", new Runnable() {
            public void run() {
                List<Item> pop = population("en", 5, "hi", 3);
                SampleResult r = StratifiedSampler.draw(pop, 100, 0, "s");
                equal(8, r.size(), "it can only draw what exists");
                that(!r.notes().isEmpty(), "and it has to say so");
            }
        });

        test("duplicate ids are refused, because the draw could not be repeated", new Runnable() {
            public void run() {
                final List<Item> pop = new ArrayList<Item>(population("en", 3));
                pop.add(new Item("r-00001", "en", Arrays.asList("r-00001", "en")));
                throwsWith("appears twice", new Runnable() {
                    public void run() {
                        StratifiedSampler.draw(pop, 2, 0, "s");
                    }
                });
            }
        });

        test("an empty seed is refused", new Runnable() {
            public void run() {
                final List<Item> pop = population("en", 10);
                throwsWith("seed is required", new Runnable() {
                    public void run() {
                        StratifiedSampler.draw(pop, 2, 0, "");
                    }
                });
            }
        });

        test("the sort key is the published digest, not something private", new Runnable() {
            public void run() {
                // sha256("queue-2026-07" + 0x00 + "r-00042") starts aa2b0967d95fba8a;
                // the first eight bytes are 12261904752079518346, shifted right one.
                equal(6130952376039759173L, StratifiedSampler.key("queue-2026-07", "r-00042"),
                        "any language with SHA-256 can pick the same rows");
            }
        });

        test("the drawn queue interleaves the strata", new Runnable() {
            public void run() {
                List<Item> pop = population("en", 200, "hi", 200);
                SampleResult r = StratifiedSampler.draw(pop, 40, 0, "s");
                int changes = 0;
                for (int i = 1; i < r.selected().size(); i++) {
                    if (!r.selected().get(i).stratum().equals(r.selected().get(i - 1).stratum())) {
                        changes++;
                    }
                }
                that(changes > 5, "a rater should not do all of one language first, saw "
                        + changes + " changes");
            }
        });

        test("drawing everything returns everything, once", new Runnable() {
            public void run() {
                List<Item> pop = population("en", 12, "hi", 5);
                SampleResult r = StratifiedSampler.draw(pop, 17, 0, "s");
                equal(17, r.size(), "size");
                equal(17, new HashSet<String>(ids(r)).size(), "no row twice");
            }
        });
    }

    // -------------------------------------------------------------------- csv

    private static void csv() {
        test("a comma inside quotes is part of the field", new Runnable() {
            public void run() {
                try {
                    List<List<String>> rows = Csv.read(new StringReader("id,text\n1,\"yes, of course\"\n"));
                    equal(2, rows.get(1).size(), "two fields");
                    equal("yes, of course", rows.get(1).get(1), "the text");
                } catch (Exception e) {
                    throw new AssertionError(e.toString());
                }
            }
        });

        test("a doubled quote inside quotes is one quote", new Runnable() {
            public void run() {
                try {
                    List<List<String>> rows = Csv.read(new StringReader("id,text\n1,\"he said \"\"no\"\"\"\n"));
                    equal("he said \"no\"", rows.get(1).get(1), "the text");
                } catch (Exception e) {
                    throw new AssertionError(e.toString());
                }
            }
        });

        test("a newline inside quotes does not end the row", new Runnable() {
            public void run() {
                try {
                    List<List<String>> rows = Csv.read(new StringReader("id,text\n1,\"line one\nline two\"\n"));
                    equal(2, rows.size(), "still two rows");
                    equal("line one\nline two", rows.get(1).get(1), "the text");
                } catch (Exception e) {
                    throw new AssertionError(e.toString());
                }
            }
        });

        test("carriage returns from a Windows export are dropped", new Runnable() {
            public void run() {
                try {
                    List<List<String>> rows = Csv.read(new StringReader("id,text\r\n1,plain\r\n"));
                    equal("text", rows.get(0).get(1), "header");
                    equal("plain", rows.get(1).get(1), "value");
                } catch (Exception e) {
                    throw new AssertionError(e.toString());
                }
            }
        });

        test("what is written can be read back", new Runnable() {
            public void run() {
                try {
                    List<List<String>> rows = new ArrayList<List<String>>();
                    rows.add(Arrays.asList("id", "text"));
                    rows.add(Arrays.asList("1", "a, b and \"c\"\nnext line"));
                    StringWriter w = new StringWriter();
                    Csv.write(w, rows);
                    equal(rows, Csv.read(new StringReader(w.toString())), "round trip");
                } catch (Exception e) {
                    throw new AssertionError(e.toString());
                }
            }
        });

        test("a clean field is left alone", new Runnable() {
            public void run() {
                equal("plain", Csv.quote("plain"), "no quotes added for nothing");
            }
        });
    }

    private Tests() {
    }
}
