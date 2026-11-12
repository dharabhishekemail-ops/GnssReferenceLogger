package com.harman.gnssref.core;

/** The per-second record. Same columns as the per_second_metrics.csv produced from the TCU log. */
public final class EpochRecord {
    public final long elapsedS;          // seconds since session start
    public final long timeMillis;        // wall clock (UTC epoch millis)
    public final boolean fixValid;       // step 4: valid position this second
    public final int tracked;            // step 1
    public final int usable;             // step 2 (C/N0 >= 32 and elevation > 5)
    public final int strong;             // C/N0 >= 40 and elevation > 5
    public final int used;               // step 3
    public final double cn0AllAvg;       // NaN when nothing tracked
    public final double cn0UsedAvg;      // NaN when nothing used
    public final double hdop;            // NaN when not available (from NMEA GGA)
    public final double accuracyM;       // NaN when not available (Android estimate, metres)
    public final double lat;             // NaN when no fix
    public final double lon;
    public final double speedKmh;        // NaN when no fix
    public final int fixQuality;         // GGA quality when NMEA is available, else 1/0 from fixValid

    public EpochRecord(long elapsedS, long timeMillis, boolean fixValid, int tracked, int usable, int strong, int used,
                       double cn0AllAvg, double cn0UsedAvg, double hdop, double accuracyM, double lat, double lon,
                       double speedKmh, int fixQuality) {
        this.elapsedS = elapsedS;
        this.timeMillis = timeMillis;
        this.fixValid = fixValid;
        this.tracked = tracked;
        this.usable = usable;
        this.strong = strong;
        this.used = used;
        this.cn0AllAvg = cn0AllAvg;
        this.cn0UsedAvg = cn0UsedAvg;
        this.hdop = hdop;
        this.accuracyM = accuracyM;
        this.lat = lat;
        this.lon = lon;
        this.speedKmh = speedKmh;
        this.fixQuality = fixQuality;
    }
}
