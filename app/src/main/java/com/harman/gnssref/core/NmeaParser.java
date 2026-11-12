package com.harman.gnssref.core;

import java.util.ArrayList;
import java.util.List;

/** Minimal NMEA 0183 parsing: checksum, GGA (fix quality, satellites, HDOP) and GSV (satellite list). */
public final class NmeaParser {
    private NmeaParser() {}

    public static final class Gga {
        public final String utc;      // hhmmss.ss
        public final int quality;     // 0 = no fix, 1 = GPS, 2 = DGNSS, ...
        public final int numSv;       // capped at 12 by the standard
        public final double hdop;     // NaN if empty
        public final double lat, lon; // NaN if empty

        Gga(String utc, int quality, int numSv, double hdop, double lat, double lon) {
            this.utc = utc; this.quality = quality; this.numSv = numSv; this.hdop = hdop; this.lat = lat; this.lon = lon;
        }
    }

    /** True when the sentence is $...*HH with a correct XOR checksum. */
    public static boolean checksumOk(String line) {
        if (line == null) return false;
        String s = line.trim();
        int star = s.lastIndexOf('*');
        if (!s.startsWith("$") || star < 0 || star + 3 > s.length()) return false;
        int x = 0;
        for (int i = 1; i < star; i++) x ^= s.charAt(i);
        try {
            return x == Integer.parseInt(s.substring(star + 1, star + 3), 16);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** Sentence type without talker, e.g. "GGA" for $GNGGA, or null. */
    public static String type(String line) {
        if (line == null || line.length() < 7 || line.charAt(0) != '$') return null;
        int comma = line.indexOf(',');
        if (comma < 6) return null;
        return line.substring(3, comma);
    }

    public static String talker(String line) {
        return (line != null && line.length() >= 3) ? line.substring(1, 3) : "";
    }

    private static String[] fields(String line) {
        String s = line.trim();
        int star = s.lastIndexOf('*');
        if (star > 0) s = s.substring(0, star);
        return s.split(",", -1);
    }

    private static double dm2deg(String v, String hemi) {
        if (v == null || v.isEmpty()) return Double.NaN;
        double x = Double.parseDouble(v);
        int d = (int) (x / 100);
        double deg = d + (x - d * 100) / 60.0;
        if ("S".equals(hemi) || "W".equals(hemi)) deg = -deg;
        return deg;
    }

    /** Parse a GGA sentence (checksum must have been checked by the caller). Returns null if it is not GGA. */
    public static Gga parseGga(String line) {
        if (!"GGA".equals(type(line))) return null;
        String[] f = fields(line);
        if (f.length < 9) return null;
        try {
            int q = f[6].isEmpty() ? 0 : Integer.parseInt(f[6]);
            int n = f[7].isEmpty() ? 0 : Integer.parseInt(f[7]);
            double h = f[8].isEmpty() ? Double.NaN : Double.parseDouble(f[8]);
            double lat = dm2deg(f[2], f[3]);
            double lon = dm2deg(f[4], f[5]);
            return new Gga(f[1], q, n, h, lat, lon);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Parse one GSV sentence into satellite samples (usedInFix unknown here, set false; the caller can mark used
     * satellites from GSA). Returns an empty list for non-GSV sentences.
     */
    public static List<SatelliteSample> parseGsv(String line) {
        List<SatelliteSample> out = new ArrayList<>();
        if (!"GSV".equals(type(line))) return out;
        String system = Metrics.systemFromTalker(talker(line));
        String[] f = fields(line);
        int n = f.length - 4;
        if (n % 4 == 1) n--;    // trailing signal id (NMEA 4.10)
        for (int i = 0; i + 3 < n + 1 && 4 + i + 3 < f.length; i += 4) {
            String sv = f[4 + i], el = f[5 + i], az = f[6 + i], cn = f[7 + i];
            if (sv.isEmpty()) continue;
            try {
                int svid = Integer.parseInt(sv);
                float e = el.isEmpty() ? 0f : Float.parseFloat(el);
                float a = az.isEmpty() ? 0f : Float.parseFloat(az);
                float c = cn.isEmpty() ? 0f : Float.parseFloat(cn);
                out.add(new SatelliteSample(system, svid, c, e, a, false));
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    /** Satellite ids used in the fix from a GSA sentence, with the system id (NMEA 4.10 field 18) if present. */
    public static List<String> parseGsaUsedKeys(String line) {
        List<String> keys = new ArrayList<>();
        if (!"GSA".equals(type(line))) return keys;
        String[] f = fields(line);
        String system = null;
        if (f.length > 18 && !f[18].isEmpty()) {
            switch (f[18]) {
                case "1": system = "GPS"; break;
                case "2": system = "GLONASS"; break;
                case "3": system = "Galileo"; break;
                case "4": system = "BeiDou"; break;
                case "5": system = "QZSS"; break;
                case "6": system = "NavIC"; break;
                default: system = null;
            }
        }
        if (system == null) system = Metrics.systemFromTalker(talker(line));
        for (int i = 3; i < Math.min(15, f.length); i++) {
            if (f[i].isEmpty()) continue;
            try {
                keys.add(system + ":" + Integer.parseInt(f[i].trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return keys;
    }
}
