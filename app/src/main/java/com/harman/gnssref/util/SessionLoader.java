package com.harman.gnssref.util;

import com.harman.gnssref.core.CsvFormat;
import com.harman.gnssref.core.EpochRecord;
import com.harman.gnssref.core.SatelliteSample;
import com.harman.gnssref.core.SessionStats;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Reads a finished session folder back from disk so old sessions can be opened and shared. */
public final class SessionLoader {
    private SessionLoader() {}

    public static final class Loaded {
        public final File dir;
        public final SessionStats stats = new SessionStats();
        public String summaryText = "";
        public List<SatelliteSample> weakestSky = Collections.emptyList();
        public List<SatelliteSample> lastSky = Collections.emptyList();
        public long weakestElapsed = -1;
        Loaded(File dir) { this.dir = dir; }
    }

    public static Loaded load(File dir) {
        Loaded l = new Loaded(dir);
        File csv = new File(dir, "per_second.csv");
        if (csv.isFile()) {
            try (BufferedReader br = new BufferedReader(new FileReader(csv))) {
                String line = br.readLine(); // header
                while ((line = br.readLine()) != null) {
                    EpochRecord r = CsvFormat.parse(line);
                    if (r != null) l.stats.add(r, Collections.<SatelliteSample>emptyList());
                }
            } catch (IOException ignored) { }
        }
        File sum = new File(dir, "summary.txt");
        if (sum.isFile()) l.summaryText = readAll(sum);
        l.weakestSky = readSky(new File(dir, "sky_weakest.csv"));
        l.lastSky = readSky(new File(dir, "sky_last.csv"));
        if (!l.weakestSky.isEmpty()) l.weakestElapsed = l.stats.weakestElapsedS();
        return l;
    }

    public static String readAll(File f) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
        } catch (IOException ignored) { }
        return sb.toString();
    }

    public static List<SatelliteSample> readSky(File f) {
        List<SatelliteSample> out = new ArrayList<>();
        if (!f.isFile()) return out;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line = br.readLine();
            while ((line = br.readLine()) != null) {
                String[] p = line.split(",", -1);
                if (p.length < 8) continue;
                try {
                    out.add(new SatelliteSample(p[1], Integer.parseInt(p[2]), Float.parseFloat(p[5]), Float.parseFloat(p[3]), Float.parseFloat(p[4]), "1".equals(p[7])));
                } catch (NumberFormatException ignored) { }
            }
        } catch (IOException ignored) { }
        return out;
    }

    /** Session folders, newest first. */
    public static List<File> listSessions(File root) {
        List<File> dirs = new ArrayList<>();
        File[] all = root.listFiles();
        if (all == null) return dirs;
        for (File f : all) if (f.isDirectory() && new File(f, "per_second.csv").isFile()) dirs.add(f);
        Collections.sort(dirs, (a, b) -> b.getName().compareTo(a.getName()));
        return dirs;
    }
}
