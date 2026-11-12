package com.harman.gnssref.ui;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.harman.gnssref.R;
import com.harman.gnssref.service.GnssLoggerService;
import com.harman.gnssref.service.Session;
import com.harman.gnssref.util.SessionLoader;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SetupActivity extends AppCompatActivity {
    private static final int REQ_PERM = 11;
    private static final long[] DURATIONS = {15 * 60, 1500, 60 * 60, 0};
    private EditText label;
    private Spinner duration;
    private CheckBox cold;
    private Button resume;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);
        label = findViewById(R.id.label);
        duration = findViewById(R.id.duration);
        cold = findViewById(R.id.cold);
        resume = findViewById(R.id.resume);
        duration.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"15 minutes", "25 minutes (1,500 s, MSIL format)", "60 minutes", "Until I press stop"}));
        duration.setSelection(1);
        ((TextView) findViewById(R.id.checklist)).setText(
                "Before you press start:\n"
                + "1. Place the phone where the TCU antenna is (dashboard), screen up, and leave it there.\n"
                + "2. For a start-up time comparable with the TCU: switch mobile data and Wi-Fi OFF (otherwise the phone uses network assistance).\n"
                + "3. Location must be ON. Battery saver OFF.\n"
                + "4. Start the TCU log at the same time, so the two files cover the same minutes.");
        findViewById(R.id.start).setOnClickListener(v -> startClicked());
        findViewById(R.id.previous).setOnClickListener(v -> showPrevious());
        resume.setOnClickListener(v -> startActivity(new Intent(this, LiveActivity.class)));
    }

    @Override protected void onResume() {
        super.onResume();
        Session s = GnssLoggerService.getSession();
        resume.setVisibility(s != null && !s.finished ? android.view.View.VISIBLE : android.view.View.GONE);
    }

    private boolean hasPermissions() {
        boolean fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean notif = Build.VERSION.SDK_INT < 33
                || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        return fine && notif;
    }

    private void startClicked() {
        if (!hasPermissions()) {
            List<String> req = new ArrayList<>();
            req.add(Manifest.permission.ACCESS_FINE_LOCATION);
            req.add(Manifest.permission.ACCESS_COARSE_LOCATION);
            if (Build.VERSION.SDK_INT >= 33) req.add(Manifest.permission.POST_NOTIFICATIONS);
            ActivityCompat.requestPermissions(this, req.toArray(new String[0]), REQ_PERM);
            return;
        }
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (lm == null || !lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Toast.makeText(this, "Turn Location ON in the phone settings first", Toast.LENGTH_LONG).show();
            return;
        }
        Session running = GnssLoggerService.getSession();
        if (running != null && !running.finished) {
            startActivity(new Intent(this, LiveActivity.class));
            return;
        }
        Intent i = new Intent(this, GnssLoggerService.class).setAction(GnssLoggerService.ACTION_START)
                .putExtra(GnssLoggerService.EXTRA_LABEL, label.getText().toString())
                .putExtra(GnssLoggerService.EXTRA_DURATION_S, DURATIONS[duration.getSelectedItemPosition()])
                .putExtra(GnssLoggerService.EXTRA_COLD, cold.isChecked());
        ContextCompat.startForegroundService(this, i);
        startActivity(new Intent(this, LiveActivity.class));
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERM) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                startClicked();
            } else {
                Toast.makeText(this, "Precise location permission is needed to read the satellites", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void showPrevious() {
        File base = getExternalFilesDir(null);
        if (base == null) base = getFilesDir();
        File root = new File(base, "sessions");
        List<File> dirs = SessionLoader.listSessions(root);
        if (dirs.isEmpty()) {
            Toast.makeText(this, "No recorded sessions yet", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[dirs.size()];
        for (int i = 0; i < dirs.size(); i++) names[i] = dirs.get(i).getName();
        new AlertDialog.Builder(this).setTitle("Recorded sessions")
                .setItems(names, (d, which) -> startActivity(new Intent(this, SummaryActivity.class)
                        .putExtra(SummaryActivity.EXTRA_DIR, dirs.get(which).getAbsolutePath())))
                .show();
    }
}
