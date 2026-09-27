package com.waseemansari.evalsampler;

import java.util.Collections;
import java.util.List;

/**
 * One row of the population: one thing that could be sent to a rater.
 *
 * The whole original row is kept so the output file can carry the same columns
 * the input had. A sample that strips the text off the rows is no use to the
 * person who has to read them.
 */
public final class Item {

    private final String id;
    private final String stratum;
    private final List<String> row;

    public Item(String id, String stratum, List<String> row) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("an item needs an id");
        }
        this.id = id;
        this.stratum = stratum == null ? "" : stratum;
        this.row = Collections.unmodifiableList(row);
    }

    public String id() {
        return id;
    }

    public String stratum() {
        return stratum;
    }

    public List<String> row() {
        return row;
    }

    @Override
    public String toString() {
        return id + " [" + stratum + "]";
    }
}
