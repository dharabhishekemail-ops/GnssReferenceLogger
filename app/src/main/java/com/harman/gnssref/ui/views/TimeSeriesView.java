package com.harman.gnssref.ui.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Simple time chart: x = elapsed seconds, several series (filled area or line), orange dashed limit lines, a legend. */
public class TimeSeriesView extends View {
    public static final class Series {
        final String name; final float[] y; final int color; final boolean fill;
        public Series(String name, float[] y, int color, boolean fill) { this.name = name; this.y = y; this.color = color; this.fill = fill; }
    }
    public static final class Limit {
        final float y; final String label;
        public Limit(float y, String label) { this.y = y; this.label = label; }
    }

    private float[] x = new float[0];
    private final List<Series> series = new ArrayList<>();
    private final List<Limit> limits = new ArrayList<>();
    private float yMax = 10f;
    private String yLabel = "";
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    public TimeSeriesView(Context c) { super(c); }
    public TimeSeriesView(Context c, AttributeSet a) { super(c, a); }

    public void setData(float[] x, List<Series> series, List<Limit> limits, float yMax, String yLabel) {
        this.x = x == null ? new float[0] : x;
        this.series.clear(); if (series != null) this.series.addAll(series);
        this.limits.clear(); if (limits != null) this.limits.addAll(limits);
        this.yMax = yMax <= 0 ? 1 : yMax;
        this.yLabel = yLabel == null ? "" : yLabel;
        invalidate();
    }

    private static String mmss(float s) {
        int t = Math.round(s);
        if (t >= 3600) return String.format(Locale.US, "%d:%02d:%02d", t / 3600, (t % 3600) / 60, t % 60);
        return String.format(Locale.US, "%d:%02d", t / 60, t % 60);
    }

    @Override protected void onDraw(Canvas c) {
        float d = getResources().getDisplayMetrics().density;
        float w = getWidth(), h = getHeight();
        float left = 34 * d, right = w - 8 * d, top = 8 * d, bottom = h - (series.isEmpty() ? 22 : 36) * d;
        float pw = right - left, ph = bottom - top;
        if (pw <= 0 || ph <= 0) return;
        float xMax = x.length > 0 ? Math.max(x[x.length - 1], 10f) : 10f;
        // grid + y ticks
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(d); p.setColor(Palette.GRID);
        p.setTextSize(9 * d);
        int ticks = 4;
        for (int i = 0; i <= ticks; i++) {
            float yv = yMax * i / ticks;
            float yy = bottom - ph * i / ticks;
            c.drawLine(left, yy, right, yy, p);
        }
        p.setStyle(Paint.Style.FILL); p.setColor(Palette.INK2); p.setTextAlign(Paint.Align.RIGHT);
        for (int i = 0; i <= ticks; i++) {
            float yv = yMax * i / ticks;
            float yy = bottom - ph * i / ticks;
            c.drawText(yv == Math.floor(yv) ? String.format(Locale.US, "%.0f", yv) : String.format(Locale.US, "%.1f", yv), left - 3 * d, yy + 3 * d, p);
        }
        // x ticks
        p.setTextAlign(Paint.Align.CENTER);
        int nx = 5;
        for (int i = 0; i <= nx; i++) {
            float xv = xMax * i / nx;
            float xx = left + pw * i / nx;
            c.drawText(mmss(xv), xx, bottom + 11 * d, p);
        }
        if (!yLabel.isEmpty()) {
            p.setTextAlign(Paint.Align.LEFT); p.setColor(Palette.MUTED);
            c.drawText(yLabel, left + 2 * d, top + 9 * d, p);
        }
        // series
        for (Series s : series) {
            int n = Math.min(x.length, s.y.length);
            if (n == 0) continue;
            path.reset();
            boolean started = false;
            for (int i = 0; i < n; i++) {
                float yv = s.y[i];
                if (Float.isNaN(yv)) { started = false; continue; }
                float xx = left + pw * (x[i] / xMax);
                float yy = bottom - ph * Math.min(yv, yMax) / yMax;
                if (!started) { path.moveTo(xx, yy); started = true; } else path.lineTo(xx, yy);
            }
            if (s.fill) {
                Path fp = new Path();
                boolean st = false; float lastX = left;
                for (int i = 0; i < n; i++) {
                    float yv = s.y[i]; if (Float.isNaN(yv)) yv = 0;
                    float xx = left + pw * (x[i] / xMax);
                    float yy = bottom - ph * Math.min(yv, yMax) / yMax;
                    if (!st) { fp.moveTo(xx, bottom); fp.lineTo(xx, yy); st = true; } else fp.lineTo(xx, yy);
                    lastX = xx;
                }
                fp.lineTo(lastX, bottom); fp.close();
                p.setStyle(Paint.Style.FILL); p.setColor(s.color); p.setAlpha(230);
                c.drawPath(fp, p); p.setAlpha(255);
            } else {
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.6f * d); p.setColor(s.color);
                c.drawPath(path, p);
            }
        }
        // limits
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.4f * d); p.setColor(Palette.LIMIT);
        p.setPathEffect(new android.graphics.DashPathEffect(new float[]{6 * d, 4 * d}, 0));
        for (Limit l : limits) {
            float yy = bottom - ph * Math.min(l.y, yMax) / yMax;
            c.drawLine(left, yy, right, yy, p);
        }
        p.setPathEffect(null);
        p.setStyle(Paint.Style.FILL); p.setTextAlign(Paint.Align.RIGHT); p.setTextSize(8.5f * d);
        for (Limit l : limits) {
            float yy = bottom - ph * Math.min(l.y, yMax) / yMax;
            c.drawText(l.label, right - 2 * d, yy - 2 * d, p);
        }
        // legend
        if (!series.isEmpty()) {
            float lx = left, ly = h - 6 * d;
            p.setTextSize(9 * d); p.setTextAlign(Paint.Align.LEFT);
            for (Series s : series) {
                p.setStyle(Paint.Style.FILL); p.setColor(s.color);
                c.drawRect(lx, ly - 7 * d, lx + 10 * d, ly - 1 * d, p);
                p.setColor(Palette.INK2);
                c.drawText(s.name, lx + 13 * d, ly, p);
                lx += 13 * d + p.measureText(s.name) + 10 * d;
            }
        }
        // frame bottom
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(d); p.setColor(Palette.TRACKED);
        c.drawLine(left, bottom, right, bottom, p);
    }
}
