package com.harman.gnssref.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Running average / min / max / median for one measure. NaN values are ignored. */
public final class Stat {
    private double sum = 0;
    private int n = 0;
    private double min = Double.NaN;
    private double max = Double.NaN;
    private final List<Double> values = new ArrayList<>();

    public void add(double v) {
        if (Double.isNaN(v)) return;
        sum += v;
        n++;
        if (Double.isNaN(min) || v < min) min = v;
        if (Double.isNaN(max) || v > max) max = v;
        values.add(v);
    }

    public int count() { return n; }
    public double avg() { return n == 0 ? Double.NaN : sum / n; }
    public double min() { return min; }
    public double max() { return max; }

    public double median() {
        if (values.isEmpty()) return Double.NaN;
        List<Double> c = new ArrayList<>(values);
        Collections.sort(c);
        int m = c.size() / 2;
        return c.size() % 2 == 1 ? c.get(m) : (c.get(m - 1) + c.get(m)) / 2.0;
    }

    /** Share of values at or below the limit, in percent. */
    public double pctAtOrBelow(double limit) {
        if (values.isEmpty()) return Double.NaN;
        int k = 0;
        for (double v : values) if (v <= limit) k++;
        return 100.0 * k / values.size();
    }

    /** Share of values at or above the limit, in percent. */
    public double pctAtOrAbove(double limit) {
        if (values.isEmpty()) return Double.NaN;
        int k = 0;
        for (double v : values) if (v >= limit) k++;
        return 100.0 * k / values.size();
    }

    public String fmt(String unit) {
        if (n == 0) return "n/a";
        return String.format(java.util.Locale.US, "%.1f%s (min %.0f, max %.0f)", avg(), unit, min, max);
    }
}
