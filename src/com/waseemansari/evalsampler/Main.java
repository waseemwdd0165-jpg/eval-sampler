package com.waseemansari.evalsampler;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.FileInputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * eval-sampler [file.csv] --size N [options]
 *
 *   --stratum COL   column to stratify on            (default: the second column)
 *   --id COL        column holding the row id        (default: id)
 *   --seed TEXT     anything; the same text redraws the same rows
 *   --min N         smallest look at any one stratum (default: 0)
 *   --out FILE      write the drawn rows here        (default: stdout is a summary only)
 */
public final class Main {

    public static void main(String[] args) {
        try {
            System.exit(run(args));
        } catch (IllegalArgumentException e) {
            System.err.println("eval-sampler: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println("eval-sampler: " + e);
            System.exit(2);
        }
    }

    static int run(String[] args) throws Exception {
        String path = null;
        for (String a : args) {
            if (!a.startsWith("--") && path == null) {
                path = a;
            }
        }
        if (path == null) {
            System.err.println("usage: eval-sampler <file.csv> --size N [--stratum COL] [--id COL]"
                    + " [--seed TEXT] [--min N] [--out FILE]");
            return 1;
        }

        int size = Integer.parseInt(value(args, "--size", "100"));
        int min = Integer.parseInt(value(args, "--min", "0"));
        String seed = value(args, "--seed", "default");
        String idColumn = value(args, "--id", "id");
        String stratumColumn = value(args, "--stratum", null);
        String out = value(args, "--out", null);

        List<List<String>> rows;
        Reader in = new InputStreamReader(new FileInputStream(path), "UTF-8");
        try {
            rows = Csv.read(in);
        } finally {
            in.close();
        }
        if (rows.isEmpty()) {
            throw new IllegalArgumentException(path + " is empty");
        }

        List<String> header = rows.get(0);
        int idAt = indexOf(header, idColumn);
        if (idAt < 0) {
            throw new IllegalArgumentException("no column called '" + idColumn + "' in " + path);
        }
        int stratumAt;
        if (stratumColumn == null) {
            stratumAt = header.size() > 1 ? 1 : 0;
            stratumColumn = header.get(stratumAt);
        } else {
            stratumAt = indexOf(header, stratumColumn);
            if (stratumAt < 0) {
                throw new IllegalArgumentException("no column called '" + stratumColumn + "' in " + path);
            }
        }

        List<Item> population = new ArrayList<Item>();
        for (int i = 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (row.size() <= Math.max(idAt, stratumAt)) {
                throw new IllegalArgumentException("line " + (i + 1) + " has " + row.size()
                        + " field(s), too few to read '" + idColumn + "' and '" + stratumColumn + "'");
            }
            population.add(new Item(row.get(idAt), row.get(stratumAt), row));
        }

        SampleResult result = StratifiedSampler.draw(population, size, min, seed);

        System.out.println(path + "  " + population.size() + " rows, stratified on "
                + stratumColumn + ", seed '" + seed + "'");
        System.out.println();
        System.out.printf("  %-22s %10s %8s %8s%n", stratumColumn, "population", "quota", "drawn");
        for (Map.Entry<String, Integer> e : result.populationBy().entrySet()) {
            String name = e.getKey();
            System.out.printf("  %-22s %10d %8d %8d%n", name.isEmpty() ? "(blank)" : name,
                    e.getValue(),
                    orZero(result.quota(), name),
                    orZero(result.taken(), name));
        }
        System.out.println();
        System.out.println("  " + result.size() + " row(s) drawn");
        for (String note : result.notes()) {
            System.out.println("  note: " + note);
        }

        if (out != null) {
            List<List<String>> outRows = new ArrayList<List<String>>();
            outRows.add(header);
            for (Item item : result.selected()) {
                outRows.add(item.row());
            }
            Writer w = new BufferedWriter(new FileWriter(out));
            try {
                Csv.write(w, outRows);
            } finally {
                w.close();
            }
            System.out.println("  written to " + out);
        }
        return 0;
    }

    private static int orZero(LinkedHashMap<String, Integer> map, String key) {
        Integer v = map.get(key);
        return v == null ? 0 : v;
    }

    private static int indexOf(List<String> header, String column) {
        for (int i = 0; i < header.size(); i++) {
            if (header.get(i).trim().equalsIgnoreCase(column)) {
                return i;
            }
        }
        return -1;
    }

    private static String value(String[] args, String name, String fallback) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals(name)) {
                return args[i + 1];
            }
        }
        return fallback;
    }
}
