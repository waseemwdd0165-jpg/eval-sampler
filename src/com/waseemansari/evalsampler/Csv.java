package com.waseemansari.evalsampler;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

/**
 * A small CSV reader and writer.
 *
 * There is no dependency in this project, so this is here rather than a
 * library. It handles the three things that actually turn up in rating
 * exports: commas inside quoted fields, doubled quotes inside quoted fields,
 * and newlines inside quoted fields. A model response with a comma in it is
 * not an edge case, it is most of them.
 */
public final class Csv {

    private Csv() {
    }

    /** Reads the whole thing. Rows may have different widths; the caller decides what that means. */
    public static List<List<String>> read(Reader in) throws IOException {
        List<List<String>> rows = new ArrayList<List<String>>();
        List<String> row = new ArrayList<String>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean fieldStarted = false;
        int c;

        while ((c = in.read()) != -1) {
            char ch = (char) c;

            if (quoted) {
                if (ch == '"') {
                    int next = in.read();
                    if (next == '"') {
                        field.append('"');          // "" inside quotes is one quote
                    } else {
                        quoted = false;
                        if (next == -1) {
                            break;
                        }
                        // Put the character back through the same machinery.
                        if (next == ',') {
                            row.add(field.toString());
                            field.setLength(0);
                            fieldStarted = false;
                        } else if (next == '\n') {
                            row.add(field.toString());
                            field.setLength(0);
                            fieldStarted = false;
                            rows.add(row);
                            row = new ArrayList<String>();
                        } else if (next != '\r') {
                            field.append((char) next);
                        }
                    }
                } else {
                    field.append(ch);
                }
                continue;
            }

            if (ch == '"' && !fieldStarted) {
                quoted = true;
                fieldStarted = true;
            } else if (ch == ',') {
                row.add(field.toString());
                field.setLength(0);
                fieldStarted = false;
            } else if (ch == '\n') {
                row.add(field.toString());
                field.setLength(0);
                fieldStarted = false;
                rows.add(row);
                row = new ArrayList<String>();
            } else if (ch != '\r') {
                field.append(ch);
                fieldStarted = true;
            }
        }

        if (field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }

    public static void write(Writer out, List<List<String>> rows) throws IOException {
        for (List<String> row : rows) {
            for (int i = 0; i < row.size(); i++) {
                if (i > 0) {
                    out.write(',');
                }
                out.write(quote(row.get(i)));
            }
            out.write('\n');
        }
        out.flush();
    }

    /** Quotes a field only when it needs it, so a clean file stays readable. */
    public static String quote(String value) {
        if (value == null) {
            return "";
        }
        boolean needs = value.indexOf(',') >= 0 || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
        if (!needs) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
