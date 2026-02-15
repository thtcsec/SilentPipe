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
import android.view.View;
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

import java.io.File;
import java.util.Collections;

public class SettingsActivity extends AppCompatActivity {
    private static final String TAG = "SettingsActivity";
    
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
        setupCookieManagement();
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
        switchNotif.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    requestNotificationPermissionLauncher.launch(
                            Manifest.permission.POST_NOTIFICATIONS);
                }
            } else {
                // Cannot revoke programmatically, guide user to settings? For now just UI toggle.
                Toast.makeText(this, "Revoke permission in System Settings", Toast.LENGTH_LONG).show();
                switchNotif.setChecked(true); // Revert
            }
        });

        switchMic.setOnClickListener(v -> {
            if (switchMic.isChecked()) {
                requestMicPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
            } else {
                 Toast.makeText(this, "Revoke permission in System Settings", Toast.LENGTH_LONG).show();
                 switchMic.setChecked(true); // Revert
                //openAppSettings();
            }
        });
    }

    private void setupThemeAndLanguage() {
        android.view.View btnTheme = findViewById(R.id.btn_theme);
        btnTheme.setOnClickListener(v -> showThemeDialog());

        android.view.View btnLanguage = findViewById(R.id.btn_language);
        btnLanguage.setOnClickListener(v -> showLanguageDialog());
    }
    private void openAppSettings() {
        Intent intent = new Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);

        intent.setData(Uri.fromParts("package", getPackageName(), null));
        startActivity(intent);
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

    private void setupCacheCleaning() {
        View btnCleanCache = findViewById(R.id.btn_clean_cache);
        updateCacheSize();

        btnCleanCache.setOnClickListener(v -> {
            long size = 0;
            try {
                size += getDirSize(getCacheDir());
                size += getDirSize(getExternalCacheDir());
            } catch (Exception e) {
                android.util.Log.e(TAG, "Error calculating cache size", e);
            }

            deleteDir(getCacheDir());
            File external = getExternalCacheDir();
            if (external != null)
                deleteDir(external);
            
            String formattedSize = android.text.format.Formatter.formatFileSize(this, size);
            Toast.makeText(this, getString(R.string.cache_cleaned, formattedSize), Toast.LENGTH_SHORT).show();
            updateCacheSize();
        });
    }

    private boolean deleteDir(File dir) {
        if (dir != null && dir.isDirectory()) {
            String[] children = dir.list();
            if (children != null) {
                for (String child : children) {
                    boolean success = deleteDir(new File(dir, child));
                    if (!success) {
                        return false;
                    }
                }
            }
            return dir.delete();
        } else if (dir != null && dir.isFile()) {
            return dir.delete();
        } else {
            return false;
        }
    }

    private long getDirSize(File dir) {
        long size = 0;
        if (dir != null && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        size += getDirSize(file);
                    } else {
                        size += file.length();
                    }
                }
            }
        } else if (dir != null && dir.isFile()) {
            size = dir.length();
        }
        return size;
    }

    private void updateCacheSize() {
        TextView tvCache = findViewById(R.id.tv_cache_size);
        if (tvCache == null) return;
        
        new Thread(() -> {
            long size = 0;
            try {
                size += getDirSize(getCacheDir());
                size += getDirSize(getExternalCacheDir());
            } catch (Exception e) {
                android.util.Log.e(TAG, "Error updating cache size", e);
            }
            
            long finalSize = size;
            runOnUiThread(() -> {
                String formatted = android.text.format.Formatter.formatFileSize(this, finalSize);
                tvCache.setText(formatted);
            });
        }).start();
    }
    
    private void setupDataManagement() {
        setupCacheCleaning();
        // Backup/Restore removed from UI for now as per design or can be added back if needed
    }

    private void setupShortcuts() {
         // Shortcuts removed from new UI for cleanliness, or can be added back.
         // For now, focusing on the main requested items.
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
        android.view.View btnSkip = findViewById(R.id.btn_skip_time);
        updateSkipTimeText();
        btnSkip.setOnClickListener(v -> showSkipTimeDialog());
    }
    
    private void updateSkipTimeText() {
        TextView tvVal = findViewById(R.id.tv_skip_time_val);
        if (tvVal != null) {
            int current = prefs.getInt("pref_skip_time", 10);
            tvVal.setText(current + "s");
        }
    }

    private void showSkipTimeDialog() {
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
                    updateSkipTimeText();
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "Invalid number", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }


    // Service Binding ...

    private void setupCookieManagement() {
        View btnCookies = findViewById(R.id.btn_youtube_cookies);
        TextView tvStatus = findViewById(R.id.tv_cookies_status);
        if (btnCookies == null || tvStatus == null) return;

        String currentCookies = prefs.getString("pref_youtube_cookies", "");
        if (!currentCookies.isEmpty()) {
            tvStatus.setText("Cookies are set");
        }

        btnCookies.setOnClickListener(v -> {
            EditText input = new EditText(this);
            input.setHint("Paste Netscape-format cookies here...");
            input.setText(prefs.getString("pref_youtube_cookies", ""));
            input.setGravity(android.view.Gravity.TOP);
            input.setLines(10);
            input.setVerticalScrollBarEnabled(true);

            new AlertDialog.Builder(this)
                .setTitle("YouTube Cookies")
                .setMessage("Paste your exported Netscape cookies to bypass bot detection.")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    String cookies = input.getText().toString();
                    prefs.edit().putString("pref_youtube_cookies", cookies).apply();
                    tvStatus.setText(cookies.isEmpty() ? "Not set" : "Cookies are set");
                    Toast.makeText(this, "Cookies saved", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Clear", (dialog, which) -> {
                    prefs.edit().remove("pref_youtube_cookies").apply();
                    tvStatus.setText("Not set");
                    Toast.makeText(this, "Cookies cleared", Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("Cancel", null)
                .show();
        });
    }

    private void setupAbout() {
        try {
            String version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            TextView tvAbout = findViewById(R.id.tv_about_content);
            if (tvAbout != null) tvAbout.setText("SilentPipe v" + version + "\nAuthor: tuhoang / thtcsec");
        } catch (PackageManager.NameNotFoundException e) {
            android.util.Log.e(TAG, "Package name not found", e);
        }

        findViewById(R.id.btn_github).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/thtcsec/SilentPipe"));
            startActivity(intent);
        });
    }
}
