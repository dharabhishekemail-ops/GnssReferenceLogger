package com.harman.gnssref.core;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Plain-text summary in the same four steps and wording as the MSIL deck. */
public final class ReportBuilder {
    private ReportBuilder() {}

    public static final class Context {
        public String label = "";
        public String device = "";
        public String android = "";
        public String gnssHardware = "";
        public long startMillis;
        public long plannedDurationS;
        public boolean coldStartRequested;
        public String coldStartResult = "not requested";
        public boolean nmeaAvailable;
    }

    private static String f1(double v) { return Double.isNaN(v) ? "n/a" : String.format(Locale.US, "%.1f", v); }
    private static String f0(double v) { return Double.isNaN(v) ? "n/a" : String.format(Locale.US, "%.0f", v); }
    private static String pct(double v) { return Double.isNaN(v) ? "n/a" : String.format(Locale.US, "%.1f%%", v); }

    public static String build(Context c, SessionStats s) {
        StringBuilder b = new StringBuilder();
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss zzz", Locale.US);
        b.append("GNSS Reference Logger - session summary\n");
        b.append("=======================================\n");
        b.append("Label:            ").append(c.label).append('\n');
        b.append("Device:           ").append(c.device).append(", Android ").append(c.android).append('\n');
        if (c.gnssHardware != null && !c.gnssHardware.isEmpty()) b.append("GNSS hardware:    ").append(c.gnssHardware).append('\n');
        b.append("Start:            ").append(df.format(new Date(c.startMillis))).append('\n');
        b.append("Recorded:         ").append(s.seconds()).append(" one-second records (planned ").append(c.plannedDurationS).append(" s)\n");
        b.append("HDOP source:      ").append(c.nmeaAvailable ? "NMEA GGA from the phone receiver" : "not available on this phone (accuracy in metres used instead)").append('\n');
        b.append('\n');

        b.append("Step 1  Tracked (signal heard)\n");
        b.append("  All seconds:            ").append(s.trackedAllSeconds().fmt("")).append('\n');
        b.append("  Seconds with position:  ").append(s.tracked().fmt("")).append('\n');
        b.append("Step 2  Usable (C/N0 >= 32 dB-Hz and elevation > 5 deg)\n");
        b.append("  All seconds:            ").append(s.usableAllSeconds().fmt("")).append('\n');
        b.append("  Seconds with position:  ").append(s.usable().fmt("")).append('\n');
        b.append("  Strong (>= 40 dB-Hz):   ").append(s.strong().fmt("")).append('\n');
        b.append("  Seconds with >= 4 usable: ").append(pct(s.usableAtLeastPctAllSeconds(Metrics.MIN_SATS_FOR_FIX))).append(" of all seconds\n");
        b.append("  Signal of satellites used:    ").append(f1(s.cn0Used().avg())).append(" dB-Hz average (per-second ").append(f1(s.cn0Used().min())).append(" to ").append(f1(s.cn0Used().max())).append(")\n");
        b.append("  Signal of all tracked:        ").append(f1(s.cn0All().avg())).append(" dB-Hz average (per-second ").append(f1(s.cn0All().min())).append(" to ").append(f1(s.cn0All().max())).append(")\n");
        b.append("Step 3  Used for the position\n");
        b.append("  Seconds with position:  ").append(s.used().fmt("")).append('\n');
        b.append("  >= 4 used:              ").append(pct(s.usedAtLeastPct(4))).append(";  >= 6 used: ").append(pct(s.usedAtLeastPct(6))).append('\n');
        b.append("Step 4  Position\n");
        b.append("  Valid position:         ").append(s.fixedSeconds()).append(" of ").append(s.seconds()).append(" seconds (").append(pct(s.fixPct())).append(")\n");
        b.append("  Losses after first fix: ").append(s.lossesAfterFirstFix()).append(" (").append(s.secondsWithoutFixAfterFirst()).append(" s without position after the first fix)\n");
        b.append("  HDOP:                   median ").append(f1(s.hdop().median())).append(", <= 2 for ").append(pct(s.hdop().pctAtOrBelow(2))).append(", <= 5 for ").append(pct(s.hdop().pctAtOrBelow(5))).append('\n');
        b.append("  Accuracy (phone estimate): median ").append(f1(s.accuracy().median())).append(" m, <= 5 m for ").append(pct(s.accuracy().pctAtOrBelow(5))).append('\n');
        b.append('\n');
        b.append("Start-up (TTFF)\n");
        b.append("  Requested start type:   ").append(c.coldStartRequested ? "cold (assistance data clear requested: " + c.coldStartResult + ")" : "as found (hot/warm depending on how long the phone had been idle)").append('\n');
        b.append("  First valid position:   ").append(s.firstFixElapsedS() < 0 ? "none" : s.firstFixElapsedS() + " s after session start").append('\n');
        b.append("  Steady position:        ").append(s.steadyFixElapsedS() < 0 ? "none" : s.steadyFixElapsedS() + " s after session start").append('\n');
        b.append("  TTFF reported by Android: ").append(s.ttffReportedMs() < 0 ? "not reported" : f1(s.ttffReportedMs() / 1000.0) + " s").append('\n');
        b.append('\n');
        b.append("Weakest second (fewest usable satellites after start-up): ").append(s.weakestElapsedS() < 0 ? "n/a" : s.weakestElapsedS() + " s, " + s.weakestSky().size() + " tracked, " + countUsable(s) + " usable").append('\n');
        b.append('\n');
        b.append("How to read this against the TCU\n");
        b.append("  - A phone normally uses network assistance (A-GPS). Its start-up time is comparable with the TCU only when mobile data\n");
        b.append("    and Wi-Fi were off during the test, or when the TCU also has assistance data enabled.\n");
        b.append("  - The phone must lie at the TCU antenna position and record at the same time as the TCU.\n");
        b.append("  - Different receiver and antenna: compare step 2 (usable satellites) and step 4 (position valid) at the same moment,\n");
        b.append("    not the absolute tracked count. Dual-frequency phones hear more signals.\n");
        return b.toString();
    }

    private static int countUsable(SessionStats s) {
        int k = 0;
        for (SatelliteSample x : s.weakestSky()) if (x.isUsable()) k++;
        return k;
    }
}
