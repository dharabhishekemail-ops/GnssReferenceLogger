package com.harman.gnssref.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The definitions used in the MSIL deck. Keep these in one place so the phone and the TCU analysis agree. */
public final class Metrics {
    public static final float USABLE_CN0 = 32f;     // dB-Hz, Quectel: a satellite is used at about 32..58 dB-Hz
    public static final float STRONG_CN0 = 40f;     // dB-Hz
    public static final float MIN_ELEVATION = 5f;   // degrees, Quectel: elevation must exceed 5 deg
    public static final int MIN_SATS_FOR_FIX = 4;

    private Metrics() {}

    /**
     * Android (and the TCU's GSV) report one entry per signal, so a dual-frequency satellite appears twice (L1 and L5).
     * Merge to one entry per satellite: keep the strongest C/N0, usedInFix if any signal is used. Entries without a
     * signal (C/N0 <= 0) are not tracked and are dropped.
     */
    public static List<SatelliteSample> dedupe(List<SatelliteSample> raw) {
        Map<String, SatelliteSample> best = new LinkedHashMap<>();
        for (SatelliteSample s : raw) {
            if (s.cn0 <= 0f) continue;
            SatelliteSample prev = best.get(s.key());
            if (prev == null) {
                best.put(s.key(), s);
            } else {
                float cn0 = Math.max(prev.cn0, s.cn0);
                boolean used = prev.usedInFix || s.usedInFix;
                SatelliteSample src = s.cn0 > prev.cn0 ? s : prev;
                best.put(s.key(), new SatelliteSample(src.system, src.svid, cn0, src.elevation, src.azimuth, used));
            }
        }
        return new ArrayList<>(best.values());
    }

    /** Build the per-second record from the satellite list and the position state of that second. */
    public static EpochRecord summarize(long elapsedS, long timeMillis, List<SatelliteSample> sats, boolean fixValid,
                                        double lat, double lon, double accuracyM, double speedKmh, double hdop, int fixQuality) {
        int tracked = 0, usable = 0, strong = 0, used = 0;
        double sumAll = 0, sumUsed = 0;
        for (SatelliteSample s : sats) {
            tracked++;
            sumAll += s.cn0;
            if (s.isUsable()) usable++;
            if (s.isStrong()) strong++;
            if (s.usedInFix) {
                used++;
                sumUsed += s.cn0;
            }
        }
        double cn0All = tracked > 0 ? sumAll / tracked : Double.NaN;
        double cn0Used = used > 0 ? sumUsed / used : Double.NaN;
        return new EpochRecord(elapsedS, timeMillis, fixValid, tracked, usable, strong, used, cn0All, cn0Used, hdop, accuracyM,
                fixValid ? lat : Double.NaN, fixValid ? lon : Double.NaN, fixValid ? speedKmh : Double.NaN, fixQuality);
    }

    /** Constellation name from the Android GnssStatus constant (kept here so the core stays Android-free). */
    public static String systemName(int androidConstellationType) {
        switch (androidConstellationType) {
            case 1: return "GPS";
            case 2: return "SBAS";
            case 3: return "GLONASS";
            case 4: return "QZSS";
            case 5: return "BeiDou";
            case 6: return "Galileo";
            case 7: return "NavIC";
            default: return "Unknown";
        }
    }

    /** NMEA talker id to system name, for the raw NMEA path. */
    public static String systemFromTalker(String talker) {
        switch (talker) {
            case "GP": return "GPS";
            case "GL": return "GLONASS";
            case "GA": return "Galileo";
            case "GB": case "BD": return "BeiDou";
            case "GQ": case "QZ": return "QZSS";
            case "GI": return "NavIC";
            default: return talker;
        }
    }
}
