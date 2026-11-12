package com.harman.gnssref.ui.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import com.harman.gnssref.core.SatelliteSample;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Polar sky view: centre = overhead, edge = horizon, north up. Dark blue used, light blue usable, white too weak/low. */
public class SkyView extends View {
    private List<SatelliteSample> sats = Collections.emptyList();
    private String title = "";
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    public SkyView(Context c) { super(c); }
    public SkyView(Context c, AttributeSet a) { super(c, a); }

    public void setSatellites(List<SatelliteSample> s, String title) {
        this.sats = s == null ? Collections.<SatelliteSample>emptyList() : s;
        this.title = title == null ? "" : title;
        invalidate();
    }

    @Override protected void onMeasure(int w, int h) {
        int size = MeasureSpec.getSize(w);
        int hs = MeasureSpec.getSize(h);
        if (MeasureSpec.getMode(h) != MeasureSpec.UNSPECIFIED && hs > 0) size = Math.min(size, hs);
        setMeasuredDimension(size, size);
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float d = getResources().getDisplayMetrics().density;
        float cx = w / 2f, cy = h / 2f + 6 * d;
        float r = Math.min(w, h) / 2f - 22 * d;
        p.setStyle(Paint.Style.FILL); p.setColor(Palette.TINT);
        c.drawCircle(cx, cy, r, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.2f * d); p.setColor(Palette.TRACKED);
        c.drawCircle(cx, cy, r, p);
        c.drawCircle(cx, cy, r * (1 - 30f / 90f), p);
        c.drawCircle(cx, cy, r * (1 - 60f / 90f), p);
        c.drawLine(cx - r, cy, cx + r, cy, p);
        c.drawLine(cx, cy - r, cx, cy + r, p);
        p.setStyle(Paint.Style.FILL); p.setColor(Palette.INK2); p.setTextSize(11 * d); p.setTextAlign(Paint.Align.CENTER);
        c.drawText("N", cx, cy - r - 6 * d, p);
        c.drawText("S", cx, cy + r + 13 * d, p);
        p.setTextAlign(Paint.Align.LEFT); c.drawText("E", cx + r + 4 * d, cy + 4 * d, p);
        p.setTextAlign(Paint.Align.RIGHT); c.drawText("W", cx - r - 4 * d, cy + 4 * d, p);
        p.setTextAlign(Paint.Align.CENTER); p.setColor(Palette.MUTED); p.setTextSize(8.5f * d);
        c.drawText("30°", cx + 10 * d, cy - r * (1 - 30f / 90f) + 10 * d, p);
        c.drawText("60°", cx + 10 * d, cy - r * (1 - 60f / 90f) + 10 * d, p);
        if (!title.isEmpty()) {
            p.setColor(Palette.INK); p.setTextSize(11 * d); p.setTextAlign(Paint.Align.CENTER);
            c.drawText(title, cx, 12 * d, p);
        }
        float rad = 9 * d;
        for (SatelliteSample s : sats) {
            if (s.elevation <= 0 && s.azimuth <= 0) continue;
            double a = Math.toRadians(s.azimuth);
            float rr = r * (1 - Math.max(0, Math.min(90, s.elevation)) / 90f);
            float x = (float) (cx + rr * Math.sin(a)), y = (float) (cy - rr * Math.cos(a));
            p.setStyle(Paint.Style.FILL);
            if (s.usedInFix) {
                p.setColor(Palette.USED); c.drawCircle(x, y, rad, p); p.setColor(0xFFFFFFFF);
            } else if (s.isUsable()) {
                p.setColor(Palette.USABLE); c.drawCircle(x, y, rad, p); p.setColor(0xFFFFFFFF);
            } else {
                p.setColor(0xFFFFFFFF); c.drawCircle(x, y, rad, p);
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.5f * d); p.setColor(Palette.TRACKED); c.drawCircle(x, y, rad, p);
                p.setStyle(Paint.Style.FILL); p.setColor(Palette.INK2);
            }
            p.setTextSize(7.5f * d); p.setTextAlign(Paint.Align.CENTER);
            c.drawText(String.format(Locale.US, "%.0f", s.cn0), x, y + 2.8f * d, p);
        }
        // legend
        float ly = h - 4 * d; float lx = 6 * d;
        p.setTextSize(9 * d); p.setTextAlign(Paint.Align.LEFT);
        lx = legendDot(c, lx, ly, Palette.USED, "used", d, true);
        lx = legendDot(c, lx, ly, Palette.USABLE, "usable", d, true);
        legendDot(c, lx, ly, Palette.TRACKED, "too weak / low", d, false);
    }

    private float legendDot(Canvas c, float x, float y, int color, String label, float d, boolean filled) {
        p.setStyle(filled ? Paint.Style.FILL : Paint.Style.STROKE); p.setStrokeWidth(1.5f * d); p.setColor(color);
        c.drawCircle(x + 4 * d, y - 3.5f * d, 4 * d, p);
        p.setStyle(Paint.Style.FILL); p.setColor(Palette.INK2);
        c.drawText(label, x + 11 * d, y, p);
        return x + 11 * d + p.measureText(label) + 10 * d;
    }
}
