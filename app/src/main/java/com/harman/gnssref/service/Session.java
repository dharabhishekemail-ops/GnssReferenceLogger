package com.harman.gnssref.service;

import com.harman.gnssref.core.SatelliteSample;
import com.harman.gnssref.core.SessionStats;

import java.io.File;
import java.util.Collections;
import java.util.List;

/** In-memory state of the running (or last finished) session, read by the activities once per second. */
public final class Session {
    public final String label;
    public final File dir;
    public final long startMillis;
    public final long plannedDurationS;      // 0 = until stopped
    public final boolean coldStartRequested;
    public volatile String coldStartResult = "not requested";
    public final SessionStats stats = new SessionStats();
    public volatile boolean finished = false;
    public volatile boolean nmeaSeen = false;
    public volatile String gnssHardware = "";
    public volatile List<SatelliteSample> liveSky = Collections.emptyList();
    public volatile long lastStatusMillis = 0;
    public volatile String lastError = null;

    public Session(String label, File dir, long startMillis, long plannedDurationS, boolean coldStartRequested) {
        this.label = label;
        this.dir = dir;
        this.startMillis = startMillis;
        this.plannedDurationS = plannedDurationS;
        this.coldStartRequested = coldStartRequested;
    }

    public long elapsedS() {
        return (System.currentTimeMillis() - startMillis) / 1000;
    }
}
