package com.harman.gnssref.ui.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/** Green/red strip: one column per second, green = valid position, red = no position. */
public class StatusStripView extends View {
    private boolean[] valid = new boolean[0];
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    public StatusStripView(Context c) { super(c); }
    public StatusStripView(Context c, AttributeSet a) { super(c, a); }

    public void setData(boolean[] v) { valid = v == null ? new boolean[0] : v; invalidate(); }

    @Override protected void onDraw(Canvas c) {
        float d = getResources().getDisplayMetrics().density;
        float w = getWidth(), h = getHeight();
        float left = 34 * d, right = w - 8 * d, top = 4 * d, bottom = h - 14 * d;
        int n = valid.length;
        if (n == 0) {
            p.setStyle(Paint.Style.FILL); p.setColor(Palette.NODATA); c.drawRect(left, top, right, bottom, p); return;
        }
        float cw = (right - left) / n;
        p.setStyle(Paint.Style.FILL);
        int i = 0;
        while (i < n) {
            int j = i;
            while (j + 1 < n && valid[j + 1] == valid[i]) j++;
            p.setColor(valid[i] ? Palette.GOOD : Palette.BAD);
            c.drawRect(left + i * cw, top, left + (j + 1) * cw, bottom, p);
            i = j + 1;
        }
        p.setColor(Palette.INK2); p.setTextSize(9 * d); p.setTextAlign(Paint.Align.LEFT);
        c.drawText("position valid (green) / no position (red), one column per second", left, h - 3 * d, p);
    }
}
