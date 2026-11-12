package com.harman.gnssref.service;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.OnNmeaMessageListener;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.harman.gnssref.R;
import com.harman.gnssref.core.CsvFormat;
import com.harman.gnssref.core.EpochRecord;
import com.harman.gnssref.core.Metrics;
import com.harman.gnssref.core.NmeaParser;
import com.harman.gnssref.core.ReportBuilder;
import com.harman.gnssref.core.SatelliteSample;
import com.harman.gnssref.ui.LiveActivity;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Foreground service that records GNSS state once per second:
 *  - GnssStatus callback -> per-satellite system, id, C/N0, elevation, azimuth, used-in-fix (steps 1..3)
 *  - GPS provider location at 1 Hz -> position valid, lat/lon, speed, accuracy (step 4)
 *  - NMEA listener -> raw NMEA file and HDOP from GGA
 * Writes per_second.csv, satellites.csv, raw.nmea and summary.txt into the session folder.
 */
public class GnssLoggerService extends Service {
    private static final String TAG = "GnssLogger";
    public static final String ACTION_START = "com.harman.gnssref.START";
    public static final String ACTION_STOP = "com.harman.gnssref.STOP";
    public static final String EXTRA_LABEL = "label";
    public static final String EXTRA_DURATION_S = "duration_s";
    public static final String EXTRA_COLD = "cold";
    private static final String CHANNEL = "gnss_logging";
    private static final int NOTIF_ID = 1001;

    private static volatile Session current;
    public static Session getSession() { return current; }

    private LocationManager lm;
    private Handler handler;
    private final Runnable tick = this::onTick;

    private volatile GnssStatus latestStatus;
    private volatile long latestStatusUptime;
    private volatile Location latestLoc;
    private volatile long latestLocUptime;
    private volatile NmeaParser.Gga latestGga;
    private volatile long latestGgaUptime;

    private BufferedWriter csv, sats, nmea;
    private Session session;
    private long nextTickUptime;
    private boolean running = false;

    private final GnssStatus.Callback statusCb = new GnssStatus.Callback() {
        @Override public void onSatelliteStatusChanged(GnssStatus status) {
            latestStatus = status;
            latestStatusUptime = SystemClock.elapsedRealtime();
            if (session != null) {
                session.liveSky = toSamples(status);
                session.lastStatusMillis = System.currentTimeMillis();
            }
        }
        @Override public void onFirstFix(int ttffMillis) {
            if (session != null) session.stats.setTtffReportedMs(ttffMillis);
        }
    };

    private final LocationListener locListener = new LocationListener() {
        @Override public void onLocationChanged(Location location) {
            if (location == null) return;
            latestLoc = location;
            latestLocUptime = SystemClock.elapsedRealtime();
        }
        @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
        @Override public void onProviderEnabled(String provider) { }
        @Override public void onProviderDisabled(String provider) { if (session != null) session.lastError = "GPS provider disabled"; }
    };

    private final OnNmeaMessageListener nmeaListener = (message, timestamp) -> {
        if (message == null) return;
        if (session != null) session.nmeaSeen = true;
        try {
            if (nmea != null) nmea.write(message.endsWith("\n") ? message : message + "\n");
        } catch (IOException ignored) { }
        if (NmeaParser.checksumOk(message)) {
            NmeaParser.Gga g = NmeaParser.parseGga(message);
            if (g != null) {
                latestGga = g;
                latestGgaUptime = SystemClock.elapsedRealtime();
            }
        }
    };

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        handler = new Handler(Looper.getMainLooper());
        createChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || ACTION_STOP.equals(intent.getAction())) {
            finishSession("stopped by user");
            return START_NOT_STICKY;
        }
        if (ACTION_START.equals(intent.getAction()) && !running) {
            startSession(intent.getStringExtra(EXTRA_LABEL), intent.getLongExtra(EXTRA_DURATION_S, 0), intent.getBooleanExtra(EXTRA_COLD, false));
        }
        return START_STICKY;
    }

    private void startSession(String label, long durationS, boolean cold) {
        if (label == null || label.trim().isEmpty()) label = "session";
        String safe = label.trim().replaceAll("[^A-Za-z0-9._-]+", "_");
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File base = getExternalFilesDir(null);
        if (base == null) base = getFilesDir();
        File dir = new File(new File(base, "sessions"), stamp + "_" + safe);
        if (!dir.mkdirs() && !dir.isDirectory()) {
            Log.e(TAG, "cannot create " + dir);
        }
        session = new Session(label.trim(), dir, System.currentTimeMillis(), durationS, cold);
        current = session;
        startAsForeground();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            session.lastError = "Location permission not granted";
            finishSession("no permission");
            return;
        }
        try {
            csv = new BufferedWriter(new FileWriter(new File(dir, "per_second.csv")));
            csv.write(CsvFormat.HEADER); csv.write('\n');
            sats = new BufferedWriter(new FileWriter(new File(dir, "satellites.csv")));
            sats.write(CsvFormat.SATS_HEADER); sats.write('\n');
            nmea = new BufferedWriter(new FileWriter(new File(dir, "raw.nmea")));
        } catch (IOException e) {
            session.lastError = "Cannot write files: " + e.getMessage();
        }
        try {
            if (Build.VERSION.SDK_INT >= 28) session.gnssHardware = String.valueOf(lm.getGnssHardwareModelName());
        } catch (Exception ignored) { }
        try {
            if (cold) {
                // Works on many devices up to Android 9; later versions may ignore it. The result is written to the summary.
                boolean ok = lm.sendExtraCommand(LocationManager.GPS_PROVIDER, "delete_aiding_data", new Bundle());
                session.coldStartResult = ok ? "command accepted by the phone" : "command rejected by the phone";
            }
            lm.registerGnssStatusCallback(statusCb, handler);
            lm.addNmeaListener(nmeaListener, handler);
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0f, locListener, Looper.getMainLooper());
        } catch (SecurityException e) {
            session.lastError = "Location permission missing: " + e.getMessage();
        } catch (Exception e) {
            session.lastError = "GNSS start failed: " + e.getMessage();
        }
        running = true;
        nextTickUptime = SystemClock.elapsedRealtime() + 1000;
        handler.postDelayed(tick, 1000);
    }

    private void onTick() {
        if (!running || session == null) return;
        long now = SystemClock.elapsedRealtime();
        long elapsed = session.elapsedS();
        List<SatelliteSample> samples = (latestStatus != null && now - latestStatusUptime < 3000) ? toSamples(latestStatus) : new ArrayList<>();
        Location loc = latestLoc;
        boolean fixValid = loc != null && now - latestLocUptime < 2500;
        NmeaParser.Gga g = (latestGga != null && now - latestGgaUptime < 2500) ? latestGga : null;
        double hdop = g != null ? g.hdop : Double.NaN;
        int quality = g != null ? g.quality : (fixValid ? 1 : 0);
        double acc = (fixValid && loc.hasAccuracy()) ? loc.getAccuracy() : Double.NaN;
        double speed = (fixValid && loc.hasSpeed()) ? loc.getSpeed() * 3.6 : Double.NaN;
        EpochRecord r = Metrics.summarize(elapsed, System.currentTimeMillis(), samples, fixValid,
                fixValid ? loc.getLatitude() : Double.NaN, fixValid ? loc.getLongitude() : Double.NaN, acc, speed, hdop, quality);
        session.stats.add(r, samples);
        try {
            if (csv != null) { csv.write(CsvFormat.line(r)); csv.write('\n'); }
            if (sats != null) { StringBuilder sb = new StringBuilder(); CsvFormat.satLines(elapsed, samples, sb); sats.write(sb.toString()); }
            if (elapsed % 10 == 0) { if (csv != null) csv.flush(); if (sats != null) sats.flush(); if (nmea != null) nmea.flush(); }
        } catch (IOException e) {
            session.lastError = "Write failed: " + e.getMessage();
        }
        if (elapsed % 5 == 0) updateNotification(r);
        if (session.plannedDurationS > 0 && elapsed >= session.plannedDurationS) {
            finishSession("planned duration reached");
            return;
        }
        nextTickUptime += 1000;
        long delay = nextTickUptime - SystemClock.elapsedRealtime();
        if (delay < 50) { delay = 50; nextTickUptime = SystemClock.elapsedRealtime() + 50; }
        handler.postDelayed(tick, delay);
    }

    private void finishSession(String reason) {
        if (!running) { stopSelf(); return; }
        running = false;
        handler.removeCallbacks(tick);
        try { lm.unregisterGnssStatusCallback(statusCb); } catch (Exception ignored) { }
        try { lm.removeNmeaListener(nmeaListener); } catch (Exception ignored) { }
        try { lm.removeUpdates(locListener); } catch (Exception ignored) { }
        try {
            if (csv != null) csv.close();
            if (sats != null) sats.close();
            if (nmea != null) nmea.close();
        } catch (IOException ignored) { }
        csv = null; sats = null; nmea = null;
        if (session != null) {
            writeSummary(session, this);
            writeSky(session);
            session.finished = true;
            Log.i(TAG, "session finished: " + reason);
        }
        stopForeground(true);
        stopSelf();
    }

    public static ReportBuilder.Context reportContext(Session s) {
        ReportBuilder.Context c = new ReportBuilder.Context();
        c.label = s.label;
        c.device = Build.MANUFACTURER + " " + Build.MODEL;
        c.android = Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")";
        c.gnssHardware = s.gnssHardware;
        c.startMillis = s.startMillis;
        c.plannedDurationS = s.plannedDurationS;
        c.coldStartRequested = s.coldStartRequested;
        c.coldStartResult = s.coldStartResult;
        c.nmeaAvailable = s.nmeaSeen;
        return c;
    }

    private static void writeSummary(Session s, Context ctx) {
        try (BufferedWriter w = new BufferedWriter(new FileWriter(new File(s.dir, "summary.txt")))) {
            w.write(ReportBuilder.build(reportContext(s), s.stats));
        } catch (IOException e) {
            Log.e(TAG, "summary write failed", e);
        }
    }

    /** Sky snapshots so the summary screen can show them after the app was restarted. */
    private static void writeSky(Session s) {
        writeSkyFile(new File(s.dir, "sky_weakest.csv"), s.stats.weakestElapsedS(), s.stats.weakestSky());
        writeSkyFile(new File(s.dir, "sky_last.csv"), s.stats.last() == null ? -1 : s.stats.last().elapsedS, s.stats.lastSky());
    }

    private static void writeSkyFile(File f, long elapsed, List<SatelliteSample> sky) {
        try (BufferedWriter w = new BufferedWriter(new FileWriter(f))) {
            w.write(CsvFormat.SATS_HEADER); w.write('\n');
            StringBuilder sb = new StringBuilder();
            CsvFormat.satLines(elapsed, sky, sb);
            w.write(sb.toString());
        } catch (IOException e) {
            Log.e(TAG, "sky write failed", e);
        }
    }

    static List<SatelliteSample> toSamples(GnssStatus st) {
        List<SatelliteSample> raw = new ArrayList<>();
        int n = st.getSatelliteCount();
        for (int i = 0; i < n; i++) {
            raw.add(new SatelliteSample(Metrics.systemName(st.getConstellationType(i)), st.getSvid(i), st.getCn0DbHz(i),
                    st.getElevationDegrees(i), st.getAzimuthDegrees(i), st.usedInFix(i)));
        }
        return Metrics.dedupe(raw);
    }

    // ---- notification ----
    private void createChannel() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null && nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "GNSS logging", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Shows while a GNSS reference session is recording");
            nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNotification(String text) {
        Intent open = new Intent(this, LiveActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stop = new Intent(this, GnssLoggerService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 1, stop, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_gnss)
                .setContentTitle("GNSS reference session running")
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pi)
                .addAction(0, "Stop", stopPi)
                .build();
    }

    private void startAsForeground() {
        Notification n = buildNotification("Starting...");
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }

    private void updateNotification(EpochRecord r) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;
        String text = String.format(Locale.US, "%d s | %s | tracked %d, usable %d, used %d", r.elapsedS,
                r.fixValid ? "position valid" : "NO position", r.tracked, r.usable, r.used);
        nm.notify(NOTIF_ID, buildNotification(text));
    }

    @Override public void onDestroy() {
        if (running) finishSession("service destroyed");
        super.onDestroy();
    }
}
