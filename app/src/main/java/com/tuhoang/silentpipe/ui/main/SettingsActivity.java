package com.tuhoang.silentpipe.ui.main;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.tuhoang.silentpipe.R;
import com.tuhoang.silentpipe.core.service.PlaybackService;

import java.util.Collections;

public class SettingsActivity extends AppCompatActivity {
    
    private SharedPreferences prefs;
    private ActivityResultLauncher<String> requestNotificationPermissionLauncher;
    private ActivityResultLauncher<String> requestMicPermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences("silentpipe_prefs", Context.MODE_PRIVATE);

        setupToolbar();
        setupPermissions();
        setupThemeAndLanguage();
        setupDataManagement();
        setupShortcuts();
        setupAudioSettings();
        setupAbout();
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.settings_toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupPermissions() {
        SwitchMaterial switchNotif = findViewById(R.id.switch_notification_permission);
        SwitchMaterial switchMic = findViewById(R.id.switch_microphone_permission);

        // Check current status
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            switchNotif.setChecked(ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED);
        } else {
            switchNotif.setEnabled(false); // Not needed below Android 13
            switchNotif.setChecked(true);
        }

        switchMic.setChecked(ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED);

        // Register Launchers
        requestNotificationPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                switchNotif.setChecked(isGranted);
                if (!isGranted) Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show();
            }
        );

        requestMicPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                switchMic.setChecked(isGranted);
                if (!isGranted) Toast.makeText(this, "Microphone permission denied", Toast.LENGTH_SHORT).show();
            }
        );

        // Listeners
        switchNotif.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (switchNotif.isChecked()) {
                    requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                } else {
                    // Cannot revoke programmatically, guide user to settings? For now just UI toggle.
                    Toast.makeText(this, "Revoke permission in System Settings", Toast.LENGTH_LONG).show();
                    switchNotif.setChecked(true); // Revert
                }
            }
        });

        switchMic.setOnClickListener(v -> {
            if (switchMic.isChecked()) {
                requestMicPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
            } else {
                 Toast.makeText(this, "Revoke permission in System Settings", Toast.LENGTH_LONG).show();
                 switchMic.setChecked(true); // Revert
            }
        });
    }

    private void setupThemeAndLanguage() {
        Button btnTheme = findViewById(R.id.btn_theme);
        btnTheme.setOnClickListener(v -> showThemeDialog());

        Button btnLanguage = findViewById(R.id.btn_language);
        btnLanguage.setOnClickListener(v -> showLanguageDialog());
    }

    private void showThemeDialog() {
        String[] themes = {"System Default", "Light", "Dark"};
        int currentMode = androidx.appcompat.app.AppCompatDelegate.getDefaultNightMode();
        int checkedItem = 0;
        if (currentMode == androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO) checkedItem = 1;
        else if (currentMode == androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES) checkedItem = 2;

        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.pref_theme_default)) // Reuse string resource
            .setSingleChoiceItems(themes, checkedItem, (dialog, which) -> {
                int mode = androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                if (which == 1) mode = androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO;
                else if (which == 2) mode = androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES;
                
                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(mode);
                dialog.dismiss();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showLanguageDialog() {
        String[] languages = {"English", "Tiếng Việt"};
        String[] codes = {"en", "vi"};
        
        // Determine current selection based on current locale
        int checkedItem = 0;
        String currentParams = androidx.core.os.LocaleListCompat.getAdjustedDefault().toLanguageTags();
        if (currentParams.contains("vi")) checkedItem = 1;

        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.pref_language_en)) // Reuse string resource or generic "Language"
            .setSingleChoiceItems(languages, checkedItem, (dialog, which) -> {
                if (which < codes.length) {
                    androidx.core.os.LocaleListCompat appLocale = androidx.core.os.LocaleListCompat.forLanguageTags(codes[which]);
                    androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(appLocale);
                }
                dialog.dismiss();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void setupDataManagement() {
        findViewById(R.id.btn_backup).setOnClickListener(v -> Toast.makeText(this, "Backup feature coming soon!", Toast.LENGTH_SHORT).show());
        findViewById(R.id.btn_restore).setOnClickListener(v -> Toast.makeText(this, "Restore feature coming soon!", Toast.LENGTH_SHORT).show());
    }

    private void setupShortcuts() {
        findViewById(R.id.btn_add_shortcut_home).setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ShortcutManager shortcutManager = getSystemService(ShortcutManager.class);
                if (shortcutManager.isRequestPinShortcutSupported()) {
                    Intent intent = new Intent(this, com.tuhoang.silentpipe.ui.main.MainActivity.class);
                    intent.setAction(Intent.ACTION_MAIN);
                    
                    ShortcutInfo pinShortcutInfo = new ShortcutInfo.Builder(this, "main_shortcut")
                            .setShortLabel(getString(R.string.app_name))
                            .setIcon(Icon.createWithResource(this, R.mipmap.ic_launcher))
                            .setIntent(intent)
                            .build();

                    Intent pinnedShortcutCallbackIntent = shortcutManager.createShortcutResultIntent(pinShortcutInfo);
                    // android.app.PendingIntent successCallback = android.app.PendingIntent.getBroadcast(this, 0, pinnedShortcutCallbackIntent, android.app.PendingIntent.FLAG_IMMUTABLE);

                    shortcutManager.requestPinShortcut(pinShortcutInfo, null);
                    Toast.makeText(this, "Requesting shortcut...", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Not supported on this Android version", Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.btn_add_qs_tile).setOnClickListener(v -> 
            Toast.makeText(this, "Add 'SilentPipe' from your Quick Settings panel edit menu.", Toast.LENGTH_LONG).show()
        );
    }

    private void setupAudioSettings() {
        // Equalizer
        findViewById(R.id.btn_equalizer).setOnClickListener(v -> {
             PlaybackService service = PlaybackService.instance;
             if (service != null) {
                 EqualizerFragment eqFragment = new EqualizerFragment();
                 eqFragment.setPlaybackService(service);
                 eqFragment.show(getSupportFragmentManager(), "Equalizer");
             } else {
                 Toast.makeText(this, "Play something first to enable Equalizer", Toast.LENGTH_SHORT).show();
             }
        });

        // HQ Audio
        SwitchMaterial switchHq = findViewById(R.id.switch_hq_audio);
        switchHq.setChecked(prefs.getBoolean("pref_hq_audio", false));
        switchHq.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("pref_hq_audio", isChecked).apply();
            Toast.makeText(this, "HQ Audio " + (isChecked ? "Enabled" : "Disabled"), Toast.LENGTH_SHORT).show();
        });

        // Skip Time
        Button btnSkip = findViewById(R.id.btn_skip_time);
        updateSkipTimeButton(btnSkip);
        btnSkip.setOnClickListener(v -> showSkipTimeDialog(btnSkip));
    }
    
    private void updateSkipTimeButton(Button btn) {
        int current = prefs.getInt("pref_skip_time", 10);
        btn.setText("Skip Interval: " + current + "s");
    }

    private void showSkipTimeDialog(Button btn) {
        EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setHint("Seconds (e.g. 10)");
        
        new AlertDialog.Builder(this)
            .setTitle("Set Skip Interval")
            .setView(input)
            .setPositiveButton("Set", (dialog, which) -> {
                try {
                    int val = Integer.parseInt(input.getText().toString());
                    if (val <= 0) val = 10;
                    prefs.edit().putInt("pref_skip_time", val).apply();
                    updateSkipTimeButton(btn);
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "Invalid number", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }


    // Service Binding to ensure PlaybackService creates AudioFlinger session
    private boolean isBound = false;
    private android.content.ServiceConnection connection = new android.content.ServiceConnection() {
        @Override
        public void onServiceConnected(android.content.ComponentName className, android.os.IBinder service) {
            isBound = true;
        }
        @Override
        public void onServiceDisconnected(android.content.ComponentName arg0) {
            isBound = false;
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        // Bind to service to ensure it's alive for Equalizer
        Intent intent = new Intent(this, PlaybackService.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isBound) {
            unbindService(connection);
            isBound = false;
        }
    }

    private void setupAbout() {
        try {
            String version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            TextView tvAbout = findViewById(R.id.tv_about_content);
            tvAbout.setText("SilentPipe v" + version + "\nAuthor: tuhoang / thtcsec");
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        findViewById(R.id.btn_github).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/thtcsec/SilentPipe"));
            startActivity(intent);
        });
    }
}
