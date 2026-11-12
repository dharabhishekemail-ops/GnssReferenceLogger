package com.harman.gnssref.ui.views;

import android.graphics.Color;

/** The one colour code used in the MSIL deck and in this app. */
public final class Palette {
    private Palette() {}
    public static final int USED = Color.parseColor("#0D366B");      // satellites used for the position
    public static final int USABLE = Color.parseColor("#5598E7");    // usable, not used
    public static final int TRACKED = Color.parseColor("#B9C0CA");   // tracked but too weak / too low
    public static final int LIMIT = Color.parseColor("#E8701A");     // a limit line
    public static final int GOOD = Color.parseColor("#0CA30C");      // position valid
    public static final int BAD = Color.parseColor("#D03B3B");       // no position
    public static final int NODATA = Color.parseColor("#E6E6E3");
    public static final int INK = Color.parseColor("#202020");
    public static final int INK2 = Color.parseColor("#52514E");
    public static final int MUTED = Color.parseColor("#898781");
    public static final int GRID = Color.parseColor("#E1E0D9");
    public static final int NAVY = Color.parseColor("#0C3362");
    public static final int TINT = Color.parseColor("#EEF2F8");
}
