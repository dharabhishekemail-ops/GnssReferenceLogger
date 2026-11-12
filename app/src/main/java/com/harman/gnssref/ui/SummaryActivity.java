package com.harman.gnssref.ui;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.harman.gnssref.R;
import com.harman.gnssref.core.EpochRecord;
import com.harman.gnssref.core.ReportBuilder;
import com.harman.gnssref.core.SatelliteSample;
import com.harman.gnssref.core.SessionStats;
import com.harman.gnssref.service.GnssLoggerService;
import com.harman.gnssref.service.Session;
import com.harman.gnssref.ui.views.FunnelView;
import com.harman.gnssref.ui.views.Palette;
import com.harman.gnssref.ui.views.SkyView;
import com.harman.gnssref.ui.views.StatusStripView;
import com.harman.gnssref.ui.views.TimeSeriesView;
import com.harman.gnssref.util.SessionLoader;
import com.harman.gnssref.util.ZipShare;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SummaryActivity extends AppCompatActivity {
    public static final String EXTRA_DIR = "dir";
    private static final int REQ_PHOTO = 31;
    private File dir;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_summary);
        dir = new File(getIntent().getStringExtra(EXTRA_DIR));
        findViewById(R.id.share).setOnClickListener(v -> {
            try { ZipShare.share(this, dir, "GNSS reference session " + dir.getName()); }
            catch (Exception e) { Toast.makeText(this, "Share failed: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
        });
        findViewById(R.id.photo).setOnClickListener(v -> takePhoto());
        findViewById(R.id.newSession).setOnClickListener(v -> {
            startActivity(new Intent(this, SetupActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
            finish();
        });
        render();
    }

    @Override protected void onResume() { super.onResume(); showPhoto(); }

    private void render() {
        SessionStats st;
        String summaryText;
        List<SatelliteSample> weakest, last;
        String label;
        Session live = GnssLoggerService.getSession();
        if (live != null && live.dir.equals(dir) && live.finished) {
            st = live.stats;
            summaryText = ReportBuilder.build(GnssLoggerService.reportContext(live), st);
            weakest = st.weakestSky(); last = st.lastSky(); label = live.label;
        } else {
            SessionLoader.Loaded l = SessionLoader.load(dir);
            st = l.stats; summaryText = l.summaryText; weakest = l.weakestSky; last = l.lastSky; label = dir.getName();
        }
        ((TextView) findViewById(R.id.title)).setText(label);

        // headline tiles
        setTile(R.id.tile1, pct(st.fixPct()), String.format(Locale.US, "of %d seconds had a valid position; %d losses after the first fix", st.seconds(), st.lossesAfterFirstFix()));
        setTile(R.id.tile2, String.format(Locale.US, "%s > %s > %s", f1(st.tracked().avg()), f1(st.usable().avg()), f1(st.used().avg())),
                String.format(Locale.US, "tracked > usable > used, average; usable min %s, max %s", f0(st.usable().min()), f0(st.usable().max())));
        setTile(R.id.tile3, f1(st.cn0Used().avg()) + " dB-Hz", "average signal of the satellites used; all tracked " + f1(st.cn0All().avg()) + " dB-Hz");
        long ff = st.firstFixElapsedS(), sf = st.steadyFixElapsedS();
        setTile(R.id.tile4, ff < 0 ? "no fix" : (sf + " s"), ff < 0 ? "no position during the session" :
                String.format(Locale.US, "to a steady position (first fix %d s%s)", ff,
                        st.ttffReportedMs() >= 0 ? String.format(Locale.US, ", Android TTFF %.1f s", st.ttffReportedMs() / 1000.0) : ""));

        List<EpochRecord> recs = st.records();
        int n = recs.size();
        float[] x = new float[n], tr = new float[n], us = new float[n], ud = new float[n], cAll = new float[n], cUsed = new float[n], hd = new float[n], acc = new float[n];
        boolean[] valid = new boolean[n];
        boolean anyHdop = false;
        for (int i = 0; i < n; i++) {
            EpochRecord r = recs.get(i);
            x[i] = r.elapsedS; tr[i] = r.tracked; us[i] = r.usable; ud[i] = r.fixValid ? r.used : Float.NaN;
            cAll[i] = (float) r.cn0AllAvg; cUsed[i] = (float) r.cn0UsedAvg;
            hd[i] = (float) r.hdop; acc[i] = (float) r.accuracyM; valid[i] = r.fixValid;
            if (!Double.isNaN(r.hdop)) anyHdop = true;
        }
        ((StatusStripView) findViewById(R.id.strip)).setData(valid);

        TimeSeriesView satChart = findViewById(R.id.satChart);
        List<TimeSeriesView.Series> s1 = new ArrayList<>();
        s1.add(new TimeSeriesView.Series("tracked", tr, Palette.TRACKED, true));
        s1.add(new TimeSeriesView.Series("usable (>=32 dB-Hz, >5°)", us, Palette.USABLE, true));
        s1.add(new TimeSeriesView.Series("used for the position", ud, Palette.USED, false));
        List<TimeSeriesView.Limit> l1 = new ArrayList<>(); l1.add(new TimeSeriesView.Limit(4, "4 needed"));
        satChart.setData(x, s1, l1, 40, "Steps 1-3: satellites");

        TimeSeriesView sigChart = findViewById(R.id.signalChart);
        List<TimeSeriesView.Series> s2 = new ArrayList<>();
        s2.add(new TimeSeriesView.Series("average of all tracked", cAll, Palette.TRACKED, false));
        s2.add(new TimeSeriesView.Series("average of satellites used", cUsed, Palette.USED, false));
        List<TimeSeriesView.Limit> l2 = new ArrayList<>(); l2.add(new TimeSeriesView.Limit(32, "32 dB-Hz: minimum to be used")); l2.add(new TimeSeriesView.Limit(40, "40 dB-Hz: strong"));
        sigChart.setData(x, s2, l2, 50, "Step 2: signal strength, C/N0 (dB-Hz)");

        TimeSeriesView qChart = findViewById(R.id.qualityChart);
        List<TimeSeriesView.Series> s3 = new ArrayList<>();
        List<TimeSeriesView.Limit> l3 = new ArrayList<>();
        if (anyHdop) {
            s3.add(new TimeSeriesView.Series("HDOP (lower is better)", hd, Palette.USED, false));
            l3.add(new TimeSeriesView.Limit(2, "2 or less: good")); l3.add(new TimeSeriesView.Limit(5, "above 5: poor"));
            qChart.setData(x, s3, l3, 6, "Step 4: position quality indicator (HDOP)");
        } else {
            s3.add(new TimeSeriesView.Series("accuracy estimate (m, lower is better)", acc, Palette.USED, false));
            l3.add(new TimeSeriesView.Limit(5, "5 m")); l3.add(new TimeSeriesView.Limit(10, "10 m"));
            qChart.setData(x, s3, l3, 20, "Step 4: accuracy estimate from the phone (m)");
        }

        ((FunnelView) findViewById(R.id.funnel)).setData(
                new double[]{st.tracked().avg(), st.usable().avg(), st.strong().avg(), st.used().avg()},
                new double[]{st.tracked().min(), st.usable().min(), st.strong().min(), st.used().min()},
                new double[]{st.tracked().max(), st.usable().max(), st.strong().max(), st.used().max()});

        ((SkyView) findViewById(R.id.skyWeak)).setSatellites(weakest, st.weakestElapsedS() < 0 ? "weakest second" : "weakest second (" + st.weakestElapsedS() + " s)");
        ((SkyView) findViewById(R.id.skyLast)).setSatellites(last, "last second");
        ((TextView) findViewById(R.id.summaryText)).setText(summaryText);
        showPhoto();
    }

    private void setTile(int id, String big, String small) {
        View tile = findViewById(id);
        ((TextView) tile.findViewById(R.id.big)).setText(big);
        ((TextView) tile.findViewById(R.id.small)).setText(small);
    }

    private void showPhoto() {
        ImageView iv = findViewById(R.id.photoView);
        File f = new File(dir, "photo.jpg");
        if (f.isFile() && f.length() > 0) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = 4;
            Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            if (b != null) { iv.setImageBitmap(b); iv.setVisibility(View.VISIBLE); return; }
        }
        iv.setVisibility(View.GONE);
    }

    private void takePhoto() {
        File photo = new File(dir, "photo.jpg");
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photo);
        Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT, uri);
        i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivityForResult(i, REQ_PHOTO); }
        catch (ActivityNotFoundException e) { Toast.makeText(this, "No camera app found", Toast.LENGTH_SHORT).show(); }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PHOTO) showPhoto();
    }

    private static String f1(double v) { return Double.isNaN(v) ? "-" : String.format(Locale.US, "%.1f", v); }
    private static String f0(double v) { return Double.isNaN(v) ? "-" : String.format(Locale.US, "%.0f", v); }
    private static String pct(double v) { return Double.isNaN(v) ? "-" : String.format(Locale.US, "%.1f%%", v); }
}
