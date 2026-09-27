package com.waseemansari.evalsampler;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

/** What was drawn, and what the draw could not honour. */
public final class SampleResult {

    private final List<Item> selected;
    private final LinkedHashMap<String, Integer> populationBy;
    private final LinkedHashMap<String, Integer> quota;
    private final LinkedHashMap<String, Integer> taken;
    private final List<String> notes;
    private final String seed;

    public SampleResult(List<Item> selected,
                        LinkedHashMap<String, Integer> populationBy,
                        LinkedHashMap<String, Integer> quota,
                        LinkedHashMap<String, Integer> taken,
                        List<String> notes,
                        String seed) {
        this.selected = Collections.unmodifiableList(selected);
        this.populationBy = populationBy;
        this.quota = quota;
        this.taken = taken;
        this.notes = Collections.unmodifiableList(notes);
        this.seed = seed;
    }

    public List<Item> selected() {
        return selected;
    }

    public LinkedHashMap<String, Integer> populationBy() {
        return populationBy;
    }

    public LinkedHashMap<String, Integer> quota() {
        return quota;
    }

    public LinkedHashMap<String, Integer> taken() {
        return taken;
    }

    /** Anything the caller should know: a stratum that could not fill its quota, and so on. */
    public List<String> notes() {
        return notes;
    }

    public String seed() {
        return seed;
    }

    public int size() {
        return selected.size();
    }
}
