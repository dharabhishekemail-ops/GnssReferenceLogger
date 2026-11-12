package com.harman.gnssref.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Accumulates the per-second records of one session and produces the summary in the four steps
 * (tracked, usable, used, position) plus start-up time.
 */
public final class SessionStats {
    private final List<EpochRecord> records = new ArrayList<>();

    // step 1..3, over seconds WITH a valid position (same convention as the TCU analysis)
    private final Stat tracked = new Stat();
    private final Stat usable = new Stat();
    private final Stat strong = new Stat();
    private final Stat used = new Stat();
    private final Stat cn0All = new Stat();
    private final Stat cn0Used = new Stat();
    private final Stat hdop = new Stat();
    private final Stat accuracy = new Stat();
    // step 2 over ALL seconds (also while there is no position: this is what matters at a problem location)
    private final Stat usableAllSeconds = new Stat();
    private final Stat trackedAllSeconds = new Stat();

    private int fixedSeconds = 0;
    private long firstFixElapsedS = -1;
    private long steadyFixElapsedS = -1;    // first second after which the fix was never lost again (computed at the end)
    private int lossesAfterFirstFix = 0;    // number of times a valid position was lost after it had been acquired
    private int secondsWithoutFixAfterFirst = 0;
    private boolean lastValid = false;

    private long ttffReportedMs = -1;       // from Android onFirstFix()
    private List<SatelliteSample> weakestSky = Collections.emptyList();
    private long weakestElapsedS = -1;
    private int weakestUsable = Integer.MAX_VALUE;
    private List<SatelliteSample> lastSky = Collections.emptyList();

    public synchronized void add(EpochRecord r, List<SatelliteSample> sats) {
        records.add(r);
        trackedAllSeconds.add(r.tracked);
        usableAllSeconds.add(r.usable);
        lastSky = sats;
        if (r.fixValid) {
            fixedSeconds++;
            tracked.add(r.tracked);
            usable.add(r.usable);
            strong.add(r.strong);
            used.add(r.used);
            cn0All.add(r.cn0AllAvg);
            cn0Used.add(r.cn0UsedAvg);
            hdop.add(r.hdop);
            accuracy.add(r.accuracyM);
            if (firstFixElapsedS < 0) firstFixElapsedS = r.elapsedS;
        } else if (firstFixElapsedS >= 0) {
            secondsWithoutFixAfterFirst++;
            if (lastValid) lossesAfterFirstFix++;
        }
        lastValid = r.fixValid;
        // weakest second = fewest usable satellites after the first 15 s (start-up excluded)
        if (r.elapsedS >= 15 && r.tracked > 0 && r.usable < weakestUsable) {
            weakestUsable = r.usable;
            weakestSky = sats;
            weakestElapsedS = r.elapsedS;
        }
    }

    public synchronized void setTtffReportedMs(long ms) {
        if (ttffReportedMs < 0) ttffReportedMs = ms;
    }

    public synchronized List<EpochRecord> records() { return new ArrayList<>(records); }
    public synchronized int seconds() { return records.size(); }
    public synchronized int fixedSeconds() { return fixedSeconds; }
    public synchronized double fixPct() { return records.isEmpty() ? Double.NaN : 100.0 * fixedSeconds / records.size(); }
    public synchronized long firstFixElapsedS() { return firstFixElapsedS; }
    public synchronized long ttffReportedMs() { return ttffReportedMs; }
    public synchronized int lossesAfterFirstFix() { return lossesAfterFirstFix; }
    public synchronized int secondsWithoutFixAfterFirst() { return secondsWithoutFixAfterFirst; }
    public synchronized List<SatelliteSample> weakestSky() { return weakestSky; }
    public synchronized long weakestElapsedS() { return weakestElapsedS; }
    public synchronized List<SatelliteSample> lastSky() { return lastSky; }
    public synchronized EpochRecord last() { return records.isEmpty() ? null : records.get(records.size() - 1); }

    public Stat tracked() { return tracked; }
    public Stat usable() { return usable; }
    public Stat strong() { return strong; }
    public Stat used() { return used; }
    public Stat cn0All() { return cn0All; }
    public Stat cn0Used() { return cn0Used; }
    public Stat hdop() { return hdop; }
    public Stat accuracy() { return accuracy; }
    public Stat usableAllSeconds() { return usableAllSeconds; }
    public Stat trackedAllSeconds() { return trackedAllSeconds; }

    /** Seconds from start until the position was held continuously to the end of the longest final run (start-up time, stricter). */
    public synchronized long steadyFixElapsedS() {
        if (firstFixElapsedS < 0) return -1;
        // walk from the first fix; the steady point is the last transition no->yes that is followed by >= 10 s of valid fix
        long steady = firstFixElapsedS;
        boolean prev = false;
        for (int i = 0; i < records.size(); i++) {
            EpochRecord r = records.get(i);
            if (r.fixValid && !prev) {
                // candidate: check the next 10 records are valid
                boolean ok = true;
                for (int j = i; j < Math.min(records.size(), i + 10); j++) {
                    if (!records.get(j).fixValid) { ok = false; break; }
                }
                if (ok) { steady = r.elapsedS; break; }
            }
            prev = r.fixValid;
        }
        return steady;
    }

    /** Percent of seconds with a position in which at least k satellites were used. */
    public synchronized double usedAtLeastPct(int k) {
        if (fixedSeconds == 0) return Double.NaN;
        int c = 0;
        for (EpochRecord r : records) if (r.fixValid && r.used >= k) c++;
        return 100.0 * c / fixedSeconds;
    }

    /** Percent of ALL seconds in which at least k satellites were usable. */
    public synchronized double usableAtLeastPctAllSeconds(int k) {
        if (records.isEmpty()) return Double.NaN;
        int c = 0;
        for (EpochRecord r : records) if (r.usable >= k) c++;
        return 100.0 * c / records.size();
    }
}
