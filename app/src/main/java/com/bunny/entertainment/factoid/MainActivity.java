package com.bunny.entertainment.factoid;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.bunny.entertainment.factoid.widgets.RandomFactsWidget;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class MainActivity extends AppCompatActivity {

    public SeekBar intervalSeekBar;
    private TextView intervalTextView;
    public TextView allowTextView;
    private static final int REQUEST_SCHEDULE_EXACT_ALARM = 1001;
    private static final long[] INTERVALS = {
            5 * 60 * 1000L,    // 5 minutes
            10 * 60 * 1000L,   // 10 minutes
            20 * 60 * 1000L,   // 20 minutes
            30 * 60 * 1000L,   // 30 minutes
            60 * 60 * 1000L,   // 1 hour
            2 * 60 * 60 * 1000L,   // 2 hours
            3 * 60 * 60 * 1000L,   // 3 hours
            4 * 60 * 60 * 1000L,   // 4 hours
            5 * 60 * 60 * 1000L,   // 5 hours
            6 * 60 * 60 * 1000L,   // 6 hours
            8 * 60 * 60 * 1000L,   // 8 hours
            10 * 60 * 60 * 1000L,  // 10 hours
            12 * 60 * 60 * 1000L   // 12 hours
    };
    private static final int SEEKBAR_STEPS = INTERVALS.length;
    private ActivityResultLauncher<Intent> alarmPermissionLauncher;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        intervalSeekBar = findViewById(R.id.intervalSeekBar);
        intervalTextView = findViewById(R.id.intervalTextView);
        allowTextView = findViewById(R.id.allowTextView);

        setupSeekBar();

        if (isFirstTime()) {
            showPermissionRequiredDialog();
        } else {
            checkAlarmPermission();
        }

        allowTextView.setOnClickListener(v -> showPermissionRequiredDialog());

        alarmPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> checkAlarmPermission()
        );


        intervalSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                long intervalMillis = INTERVALS[progress];
                updateIntervalText(intervalMillis);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                long intervalMillis = INTERVALS[seekBar.getProgress()];
                RandomFactsWidget.setUpdateInterval(MainActivity.this, intervalMillis);
                scheduleNextUpdate();
            }
        });
    }

    private boolean isFirstTime() {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        boolean isFirstTime = prefs.getBoolean("isFirstTime", true);
        if (isFirstTime) {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean("isFirstTime", false);
            editor.apply();
        }
        return isFirstTime;
    }

    private void updateIntervalText(long intervalMillis) {
        if (intervalMillis < 60 * 60 * 1000) {
            int minutes = (int) (intervalMillis / (60 * 1000));
            String minutesTxt = "Update interval: " + minutes + " minutes";
            intervalTextView.setText(minutesTxt);
        } else {
            int hours = (int) (intervalMillis / (60 * 60 * 1000));
            String hoursTxt = "Update interval: " + hours + " hours";
            intervalTextView.setText(hoursTxt);
        }
    }

    private long getUpdateIntervalMillis() {
        SharedPreferences prefs = getSharedPreferences(RandomFactsWidget.PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getLong(RandomFactsWidget.PREF_UPDATE_INTERVAL, INTERVALS[4]); // Default to 1 hour
    }

    private int millisToProgress(long millis) {
        for (int i = 0; i < INTERVALS.length; i++) {
            if (millis <= INTERVALS[i]) {
                return i;
            }
        }
        return INTERVALS.length - 1;
    }

    private void scheduleNextUpdate() {
        Intent intent = new Intent(this, RandomFactsWidget.class);
        intent.setAction(RandomFactsWidget.ACTION_AUTO_UPDATE);
        sendBroadcast(intent);
    }

    private void checkAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!RandomFactsWidget.canScheduleExactAlarms(this)) {
                disableAutoUpdate();
                showAllowTextView();
            } else {
                hideAllowTextView();
                setupSeekBar();
            }
        } else {
            hideAllowTextView();
            setupSeekBar();
        }
    }

    private void setupSeekBar() {
        long currentIntervalMillis = getUpdateIntervalMillis();
        if (currentIntervalMillis == 0) {
            currentIntervalMillis = 3600000; // Default to 1 hour if no interval is set
            RandomFactsWidget.setUpdateInterval(this, currentIntervalMillis);
        }
        int progress = millisToProgress(currentIntervalMillis);
        intervalSeekBar.setMax(SEEKBAR_STEPS - 1);
        intervalSeekBar.setProgress(progress);
        intervalSeekBar.setEnabled(true);
        updateIntervalText(currentIntervalMillis);
    }

    private void disableAutoUpdate() {
        RandomFactsWidget.setUpdateInterval(this, 0); // 0 means no auto-update
        intervalSeekBar.setEnabled(false);
        String updateInterval = "Update interval: Off (No permission)";
        intervalTextView.setText(updateInterval);
    }

    private void showPermissionRequiredDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Permission Required")
                .setMessage("Exact alarm permission is required for automatic updates. Without it, you'll need to manually update the widget.")
                .setPositiveButton("Allow", (dialog, which) -> {
                    Intent intent = RandomFactsWidget.getAlarmPermissionSettingsIntent(this);
                    if (intent != null) {
                        alarmPermissionLauncher.launch(intent);
                    }
                })
                .setNegativeButton("Deny", (dialog, which) -> {
                    Toast.makeText(this, "Auto-update disabled due to missing permission.", Toast.LENGTH_SHORT).show();
                    disableAutoUpdate();
                    showAllowTextView();
                })
                .setCancelable(false)
                .show();
    }

    private void showAllowTextView() {
        TextView allowTextView = findViewById(R.id.allowTextView);
        allowTextView.setVisibility(View.VISIBLE);
    }

    private void hideAllowTextView() {
        TextView allowTextView = findViewById(R.id.allowTextView);
        allowTextView.setVisibility(View.GONE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SCHEDULE_EXACT_ALARM) {
            if (RandomFactsWidget.canScheduleExactAlarms(this)) {
                Toast.makeText(this, "Exact alarms allowed", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Exact alarms not allowed, widget may update less precisely", Toast.LENGTH_LONG).show();
            }
        }
    }
    @Override
    protected void onResume() {
        super.onResume();
        checkAlarmPermission();
    }
}