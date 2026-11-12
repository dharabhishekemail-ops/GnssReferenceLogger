package com.harman.gnssref.core;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** CSV columns, kept compatible with per_second_metrics.csv from the TCU analysis (plus a few phone-only columns). */
public final class CsvFormat {
    private CsvFormat() {}

    public static final String HEADER = "time_local,time_utc_hhmmss,elapsed_s,position_valid,fix_quality_gga,satellites_used,satellites_tracked,"
            + "satellites_usable_cn0ge32_el_gt5,satellites_strong_cn0ge40,avg_cn0_all_tracked_dbhz,avg_cn0_used_dbhz,hdop,accuracy_m,lat,lon,speed_kmh";

    public static final String SATS_HEADER = "elapsed_s,system,svid,elevation_deg,azimuth_deg,cn0_dbhz,usable,used";

    private static final ThreadLocal<SimpleDateFormat> LOCAL = new ThreadLocal<SimpleDateFormat>() {
        @Override protected SimpleDateFormat initialValue() { return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US); }
    };
    private static final ThreadLocal<SimpleDateFormat> UTC = new ThreadLocal<SimpleDateFormat>() {
        @Override protected SimpleDateFormat initialValue() {
            SimpleDateFormat f = new SimpleDateFormat("HHmmss", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            return f;
        }
    };

    static String num(double v, int decimals) {
        if (Double.isNaN(v)) return "";
        return String.format(Locale.US, "%." + decimals + "f", v);
    }

    public static String line(EpochRecord r) {
        Date d = new Date(r.timeMillis);
        return LOCAL.get().format(d) + "," + UTC.get().format(d) + "," + r.elapsedS + "," + (r.fixValid ? "yes" : "no") + ","
                + r.fixQuality + "," + r.used + "," + r.tracked + "," + r.usable + "," + r.strong + ","
                + num(r.cn0AllAvg, 2) + "," + num(r.cn0UsedAvg, 2) + "," + num(r.hdop, 2) + "," + num(r.accuracyM, 1) + ","
                + num(r.lat, 7) + "," + num(r.lon, 7) + "," + num(r.speedKmh, 1);
    }

    public static void satLines(long elapsedS, List<SatelliteSample> sats, StringBuilder sb) {
        for (SatelliteSample s : sats) {
            sb.append(elapsedS).append(',').append(s.system).append(',').append(s.svid).append(',')
              .append(num(s.elevation, 0)).append(',').append(num(s.azimuth, 0)).append(',').append(num(s.cn0, 0)).append(',')
              .append(s.isUsable() ? 1 : 0).append(',').append(s.usedInFix ? 1 : 0).append('\n');
        }
    }

    /** Parse a line written by {@link #line(EpochRecord)} back into a record (used to reopen old sessions). */
    public static EpochRecord parse(String line) {
        String[] f = line.split(",", -1);
        if (f.length < 16) return null;
        try {
            long millis = LOCAL.get().parse(f[0]).getTime();
            return new EpochRecord(Long.parseLong(f[2]), millis, "yes".equals(f[3]), Integer.parseInt(f[6]), Integer.parseInt(f[7]),
                    Integer.parseInt(f[8]), Integer.parseInt(f[5]), d(f[9]), d(f[10]), d(f[11]), d(f[12]), d(f[13]), d(f[14]), d(f[15]),
                    Integer.parseInt(f[4]));
        } catch (Exception e) {
            return null;
        }
    }

    private static double d(String s) {
        return s.isEmpty() ? Double.NaN : Double.parseDouble(s);
    }
}
