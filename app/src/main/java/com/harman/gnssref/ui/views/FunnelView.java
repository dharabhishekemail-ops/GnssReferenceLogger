package com.harman.gnssref.ui.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.Locale;

/** Four horizontal bars: tracked, usable, strong, used (average), with min/max text. */
public class FunnelView extends View {
    private final String[] names = {"Tracked", "Usable (>=32 dB-Hz, >5°)", "Strong (>=40 dB-Hz)", "Used for the position"};
    private final int[] colors = {Palette.TRACKED, Palette.USABLE, Palette.USABLE, Palette.USED};
    private final double[] avg = new double[4], min = new double[4], max = new double[4];
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    public FunnelView(Context c) { super(c); }
    public FunnelView(Context c, AttributeSet a) { super(c, a); }

    public void setData(double[] avg, double[] min, double[] max) {
        for (int i = 0; i < 4; i++) { this.avg[i] = avg[i]; this.min[i] = min[i]; this.max[i] = max[i]; }
        invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        float d = getResources().getDisplayMetrics().density;
        float w = getWidth(), h = getHeight();
        float rowH = h / 4f;
        float left = 8 * d, right = w - 8 * d;
        double scale = 1;
        for (double v : avg) if (!Double.isNaN(v)) scale = Math.max(scale, v);
        scale = Math.max(scale, 4) * 1.05;
        float barMaxW = (right - left) * 0.55f;
        for (int i = 0; i < 4; i++) {
            float y0 = i * rowH + 4 * d, y1 = (i + 1) * rowH - 4 * d;
            p.setStyle(Paint.Style.FILL); p.setColor(Palette.INK2); p.setTextSize(10 * d); p.setTextAlign(Paint.Align.LEFT);
            c.drawText(names[i], left, y0 + 10 * d, p);
            float bw = Double.isNaN(avg[i]) ? 0 : (float) (barMaxW * avg[i] / scale);
            p.setColor(colors[i]);
            c.drawRect(left, y0 + 14 * d, left + bw, y1, p);
            p.setColor(Palette.INK); p.setTextSize(10.5f * d);
            String t = Double.isNaN(avg[i]) ? "n/a" : String.format(Locale.US, "%.1f avg  (min %.0f, max %.0f)", avg[i], min[i], max[i]);
            c.drawText(t, left + bw + 6 * d, (y0 + 14 * d + y1) / 2 + 4 * d, p);
        }
        // 4-satellite limit
        float xl = left + (float) (barMaxW * 4 / scale);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.4f * d); p.setColor(Palette.LIMIT);
        p.setPathEffect(new android.graphics.DashPathEffect(new float[]{6 * d, 4 * d}, 0));
        c.drawLine(xl, 14 * d, xl, h, p);
        p.setPathEffect(null);
    }
}
