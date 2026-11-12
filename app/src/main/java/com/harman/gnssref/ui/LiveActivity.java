package com.harman.gnssref.ui;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.harman.gnssref.R;
import com.harman.gnssref.core.EpochRecord;
import com.harman.gnssref.core.SessionStats;
import com.harman.gnssref.service.GnssLoggerService;
import com.harman.gnssref.service.Session;
import com.harman.gnssref.ui.views.Palette;
import com.harman.gnssref.ui.views.SkyView;
import com.harman.gnssref.ui.views.TimeSeriesView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LiveActivity extends AppCompatActivity {
    private static final int REQ_PHOTO = 21;
    private static final int WINDOW_S = 600;   // rolling chart window
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = this::refresh;
    private TextView status, elapsed, counters, signal, quality, ttff, error;
    private SkyView sky;
    private TimeSeriesView chart;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_live);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        status = findViewById(R.id.status);
        elapsed = findViewById(R.id.elapsed);
        counters = findViewById(R.id.counters);
        signal = findViewById(R.id.signal);
        quality = findViewById(R.id.quality);
        ttff = findViewById(R.id.ttff);
        error = findViewById(R.id.error);
        sky = findViewById(R.id.sky);
        chart = findViewById(R.id.chart);
        findViewById(R.id.stop).setOnClickListener(v -> {
            Intent i = new Intent(this, GnssLoggerService.class).setAction(GnssLoggerService.ACTION_STOP);
            startService(i);
        });
        findViewById(R.id.photo).setOnClickListener(v -> takePhoto());
    }

    @Override protected void onResume() { super.onResume(); handler.post(refresh); }
    @Override protected void onPause() { super.onPause(); handler.removeCallbacks(refresh); }

    private void refresh() {
        Session s = GnssLoggerService.getSession();
        if (s == null) { finish(); return; }
        if (s.finished) {
            startActivity(new Intent(this, SummaryActivity.class).putExtra(SummaryActivity.EXTRA_DIR, s.dir.getAbsolutePath()));
            finish();
            return;
        }
        SessionStats st = s.stats;
        EpochRecord r = st.last();
        long el = s.elapsedS();
        String plan = s.plannedDurationS > 0 ? String.format(Locale.US, " of %d s", s.plannedDurationS) : "";
        elapsed.setText(String.format(Locale.US, "%s  |  %d s recorded%s", s.label, st.seconds(), plan));
        if (r == null) {
            status.setText("Waiting for the first second of data...");
            status.setBackgroundColor(Palette.NODATA);
        } else {
            status.setText(r.fixValid ? "POSITION VALID" : "NO POSITION");
            status.setBackgroundColor(r.fixValid ? Palette.GOOD : Palette.BAD);
            counters.setText(String.format(Locale.US, "Tracked %d   |   Usable %d   |   Used %d", r.tracked, r.usable, r.used));
            signal.setText(String.format(Locale.US, "Signal: used %s dB-Hz   |   all tracked %s dB-Hz",
                    Double.isNaN(r.cn0UsedAvg) ? "-" : String.format(Locale.US, "%.1f", r.cn0UsedAvg),
                    Double.isNaN(r.cn0AllAvg) ? "-" : String.format(Locale.US, "%.1f", r.cn0AllAvg)));
            quality.setText(String.format(Locale.US, "HDOP %s   |   accuracy %s   |   speed %s",
                    Double.isNaN(r.hdop) ? "n/a" : String.format(Locale.US, "%.1f", r.hdop),
                    Double.isNaN(r.accuracyM) ? "-" : String.format(Locale.US, "%.0f m", r.accuracyM),
                    Double.isNaN(r.speedKmh) ? "-" : String.format(Locale.US, "%.0f km/h", r.speedKmh)));
        }
        long ff = st.firstFixElapsedS();
        ttff.setText(ff < 0 ? "First position: not yet" :
                String.format(Locale.US, "First position after %d s%s", ff,
                        st.ttffReportedMs() >= 0 ? String.format(Locale.US, " (Android TTFF %.1f s)", st.ttffReportedMs() / 1000.0) : ""));
        if (s.lastError != null) { error.setVisibility(View.VISIBLE); error.setText(s.lastError); } else error.setVisibility(View.GONE);
        sky.setSatellites(s.liveSky, "live sky view");
        // rolling chart
        List<EpochRecord> recs = st.records();
        int from = Math.max(0, recs.size() - WINDOW_S);
        int n = recs.size() - from;
        float[] x = new float[n], tr = new float[n], us = new float[n], ud = new float[n];
        for (int i = 0; i < n; i++) {
            EpochRecord e = recs.get(from + i);
            x[i] = e.elapsedS - (from > 0 ? recs.get(from).elapsedS : 0);
            tr[i] = e.tracked; us[i] = e.usable; ud[i] = e.fixValid ? e.used : Float.NaN;
        }
        List<TimeSeriesView.Series> series = new ArrayList<>();
        series.add(new TimeSeriesView.Series("tracked", tr, Palette.TRACKED, true));
        series.add(new TimeSeriesView.Series("usable", us, Palette.USABLE, true));
        series.add(new TimeSeriesView.Series("used", ud, Palette.USED, false));
        List<TimeSeriesView.Limit> limits = new ArrayList<>();
        limits.add(new TimeSeriesView.Limit(4, "4 needed"));
        chart.setData(x, series, limits, 40, from > 0 ? "last 10 minutes" : "since start");
        handler.postDelayed(refresh, 1000);
    }

    private void takePhoto() {
        Session s = GnssLoggerService.getSession();
        if (s == null) return;
        File photo = new File(s.dir, "photo.jpg");
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photo);
        Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT, uri);
        i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(i, REQ_PHOTO);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No camera app found", Toast.LENGTH_SHORT).show();
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PHOTO) {
            Toast.makeText(this, resultCode == RESULT_OK ? "Photo saved with the session" : "Photo not taken", Toast.LENGTH_SHORT).show();
        }
    }
}
