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
import android.util.Log;

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
        
        updateThemeSummary();
        updateLanguageSummary();
        updateVisualizerStyleSummary();
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.settings_toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupPermissions() {
        final SwitchMaterial switchNotif = findViewById(R.id.switch_notification_permission);
        final SwitchMaterial switchMic = findViewById(R.id.switch_microphone_permission);

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

        switchNotif.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                    }
                }
            }
            // If turning OFF, we just let the UI reflect it. We can't revoke system rights anyway.
        });

        switchMic.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    requestMicPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
                }
            }
        });
    }

    private void setupThemeAndLanguage() {
        android.view.View btnTheme = findViewById(R.id.btn_theme);
        btnTheme.setOnClickListener(v -> showThemeDialog());

        android.view.View btnLanguage = findViewById(R.id.btn_language);
        btnLanguage.setOnClickListener(v -> showLanguageDialog());

        // Mini Player Style
        SwitchMaterial switchBottomPlayer = findViewById(R.id.switch_bottom_player);
        if (switchBottomPlayer != null) {
            switchBottomPlayer.setChecked(prefs.getBoolean("pref_use_bottom_player", false));
            switchBottomPlayer.setOnCheckedChangeListener((buttonView, isChecked) -> {
                prefs.edit().putBoolean("pref_use_bottom_player", isChecked).apply();
            });
        }
        
        // Video Mode
        SwitchMaterial switchVideo = findViewById(R.id.switch_show_video);
        if (switchVideo != null) {
            switchVideo.setChecked(prefs.getBoolean("pref_show_video", false));
            switchVideo.setOnCheckedChangeListener((buttonView, isChecked) -> {
                prefs.edit().putBoolean("pref_show_video", isChecked).apply();
            });
        }

        // Visualizer
        SwitchMaterial switchVisualizer = findViewById(R.id.switch_show_visualizer);
        if (switchVisualizer != null) {
            switchVisualizer.setChecked(prefs.getBoolean("pref_show_visualizer", false));
            switchVisualizer.setOnCheckedChangeListener((buttonView, isChecked) -> {
                prefs.edit().putBoolean("pref_show_visualizer", isChecked).apply();
            });
        }

        // Layout for Visualizer Style
        android.view.View btnVizStyle = findViewById(R.id.btn_visualizer_style);
        if (btnVizStyle != null) {
            btnVizStyle.setOnClickListener(v -> showVisualizerStyleDialog());
        }
    }
    private void openAppSettings() {
        Intent intent = new Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);

        intent.setData(Uri.fromParts("package", getPackageName(), null));
        startActivity(intent);
    }
    private void showThemeDialog() {
        String[] themes = {getString(R.string.theme_system), getString(R.string.theme_light), getString(R.string.theme_dark)};
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
                updateThemeSummary();
                dialog.dismiss();
            })
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .show();
    }

    private void updateThemeSummary() {
        TextView tvTheme = findViewById(R.id.tv_theme_val);
        if (tvTheme == null) return;
        
        int currentMode = androidx.appcompat.app.AppCompatDelegate.getDefaultNightMode();
        if (currentMode == androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO) {
            tvTheme.setText(R.string.theme_light);
        } else if (currentMode == androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES) {
            tvTheme.setText(R.string.theme_dark);
        } else {
            tvTheme.setText(R.string.theme_system);
        }
    }


    private void showLanguageDialog() {
        String[] languages = {"English", "Tiếng Việt"};
        String[] codes = {"en", "vi"};
        
        // Determine current selection based on app locales
        int checkedItem = 0;
        androidx.core.os.LocaleListCompat currentLocales = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales();
        if (!currentLocales.isEmpty()) {
            String primaryLocale = currentLocales.get(0).getLanguage();
            if ("vi".equals(primaryLocale)) checkedItem = 1;
        }

        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.pref_language_en)) // Reuse string resource or generic "Language"
            .setSingleChoiceItems(languages, checkedItem, (dialog, which) -> {
                if (which < codes.length) {
                    androidx.core.os.LocaleListCompat appLocale = androidx.core.os.LocaleListCompat.forLanguageTags(codes[which]);
                    androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(appLocale);
                    updateLanguageSummary();
                }
                dialog.dismiss();
            })
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .show();
    }

    private void updateLanguageSummary() {
        TextView tvLang = findViewById(R.id.tv_language_val);
        if (tvLang == null) return;
        
        androidx.core.os.LocaleListCompat currentLocales = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales();
        if (!currentLocales.isEmpty()) {
            String primaryLocale = currentLocales.get(0).getLanguage();
            if ("vi".equals(primaryLocale)) {
                tvLang.setText(R.string.vietnamese);
            } else {
                tvLang.setText(R.string.english);
            }
        } else {
            // Default to English if not set
            tvLang.setText(R.string.english);
        }
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
                Log.e(TAG, "Error calculating cache size", e);
            }

            deleteDir(getCacheDir());
            File external = getExternalCacheDir();
            if (external != null)
                deleteDir(external);
            
            String formattedSize = android.text.format.Formatter.formatFileSize(this, size);
            Toast.makeText(this, getString(R.string.cache_cleaned, formattedSize), Toast.LENGTH_SHORT).show();
            updateCacheSize();
        });
        
        setupCheckUpdate();
    }
    
    private void setupCheckUpdate() {
        View btnUpdate = findViewById(R.id.btn_check_update);
        if (btnUpdate != null) {
            btnUpdate.setOnClickListener(v -> checkForUpdate());
        }
    }

    private void checkForUpdate() {
        Toast.makeText(this, getString(R.string.update_checking), Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                java.net.URL url = new java.net.URL("https://api.github.com/repos/thtcsec/SilentPipe/releases/latest");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "SilentPipe-Android");
                
                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) response.append(line);
                    reader.close();
                    
                    org.json.JSONObject json = new org.json.JSONObject(response.toString());
                    String latestTag = json.getString("tag_name");
                    String currentTag = com.tuhoang.silentpipe.BuildConfig.VERSION_NAME;
                    
                    runOnUiThread(() -> {
                        if (!latestTag.equals(currentTag)) {
                            new AlertDialog.Builder(this)
                                .setTitle(getString(R.string.update_available_title))
                                .setMessage(getString(R.string.update_available_msg, latestTag, currentTag))
                                .setPositiveButton(getString(R.string.dialog_download), (d, w) -> {
                                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(json.optString("html_url")));
                                    startActivity(browserIntent);
                                })
                                .setNegativeButton(getString(R.string.dialog_cancel), null)
                                .show();
                        } else {
                            Toast.makeText(this, getString(R.string.up_to_date, currentTag), Toast.LENGTH_SHORT).show();
                        }
                    });
                } else {
                     runOnUiThread(() -> Toast.makeText(this, getString(R.string.update_failed, responseCode), Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                Log.e(TAG, "Error checking updates", e);
                runOnUiThread(() -> Toast.makeText(this, getString(R.string.update_error), Toast.LENGTH_SHORT).show());
            }
        }).start();
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
                Log.e(TAG, "Error updating cache size", e);
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
             EqualizerFragment eqFragment = new EqualizerFragment();
             // Fragment will fetch instance itself if null
             eqFragment.show(getSupportFragmentManager(), "Equalizer");
        });

        // HQ Audio
        SwitchMaterial switchHq = findViewById(R.id.switch_hq_audio);
        switchHq.setChecked(prefs.getBoolean("pref_hq_audio", false));
        switchHq.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("pref_hq_audio", isChecked).apply();
            Toast.makeText(this, getString(R.string.pref_hq_audio) + ": " + (isChecked ? "ON" : "OFF"), Toast.LENGTH_SHORT).show();
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
            .setTitle(getString(R.string.set_skip_interval))
            .setView(input)
            .setPositiveButton(getString(R.string.btn_set), (dialog, which) -> {
                try {
                    int val = Integer.parseInt(input.getText().toString());
                    if (val <= 0) val = 10;
                    prefs.edit().putInt("pref_skip_time", val).apply();
                    updateSkipTimeText();
                } catch (NumberFormatException e) {
                    Toast.makeText(this, getString(R.string.invalid_number), Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .show();
    }


    // Service Binding ...

    private void setupCookieManagement() {
        View btnCookies = findViewById(R.id.btn_youtube_cookies);
        TextView tvStatus = findViewById(R.id.tv_cookies_status);
        if (btnCookies == null || tvStatus == null) return;

        String currentCookies = prefs.getString("pref_youtube_cookies", "");
        if (!currentCookies.isEmpty()) {
            tvStatus.setText(getString(R.string.cookies_set));
        }

        btnCookies.setOnClickListener(v -> {
            EditText input = new EditText(this);
            input.setHint(getString(R.string.cookies_hint));
            input.setText(prefs.getString("pref_youtube_cookies", ""));
            input.setGravity(android.view.Gravity.TOP);
            input.setLines(10);
            input.setVerticalScrollBarEnabled(true);

            new AlertDialog.Builder(this)
                .setTitle(getString(R.string.pref_yt_cookies))
                .setMessage(getString(R.string.cookies_msg))
                .setView(input)
                .setPositiveButton(getString(R.string.btn_save), (dialog, which) -> {
                    String cookies = input.getText().toString();
                    prefs.edit().putString("pref_youtube_cookies", cookies).apply();
                    tvStatus.setText(cookies.isEmpty() ? getString(R.string.pref_not_set) : getString(R.string.cookies_set));
                    Toast.makeText(this, getString(R.string.cookies_saved), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(getString(R.string.btn_clear), (dialog, which) -> {
                    prefs.edit().remove("pref_youtube_cookies").apply();
                    tvStatus.setText(getString(R.string.pref_not_set));
                    Toast.makeText(this, getString(R.string.cookies_cleared), Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton(getString(R.string.dialog_cancel), null)
                .show();
        });
    }

    private void setupAbout() {
        try {
            String version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            TextView tvAbout = findViewById(R.id.tv_about_content);
            if (tvAbout != null) tvAbout.setText("SilentPipe v" + version + "\nAuthor: tuhoang / thtcsec");
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Package name not found", e);
        }

        findViewById(R.id.btn_github).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/thtcsec/SilentPipe"));
            startActivity(intent);
        });
        
        setupErrorLogs();
    }

    private void setupErrorLogs() {
        android.view.View btnLogs = findViewById(R.id.btn_error_logs);
        if (btnLogs != null) {
            btnLogs.setOnClickListener(v -> showErrorLogsDialog());
        }
    }

    private void showErrorLogsDialog() {
        java.util.List<String> logs = com.tuhoang.silentpipe.core.manager.ErrorLogManager.getInstance(this).getLogs();
        
        if (logs.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_error_logs), Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (String log : logs) {
            sb.append(log).append("\n\n");
        }

        TextView tv = new TextView(this);
        tv.setText(sb.toString());
        tv.setPadding(32, 32, 32, 32);
        tv.setTextIsSelectable(true);
        tv.setTypeface(android.graphics.Typeface.MONOSPACE);

        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        scrollView.addView(tv);

        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.error_logs_title))
            .setView(scrollView)
            .setPositiveButton(getString(R.string.btn_close), null)
            .setNeutralButton(getString(R.string.btn_clear), (dialog, which) -> {
                com.tuhoang.silentpipe.core.manager.ErrorLogManager.getInstance(this).clearLogs();
                Toast.makeText(this, getString(R.string.logs_cleared), Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton(getString(R.string.btn_copy_all), (dialog, which) -> {
                 android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                 android.content.ClipData clip = android.content.ClipData.newPlainText("Error Logs", sb.toString());
                 clipboard.setPrimaryClip(clip);
                 Toast.makeText(this, getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show();
            })
            .show();
    }

    private void showVisualizerStyleDialog() {
        String[] styles = {getString(R.string.visualizer_style_waveform), getString(R.string.visualizer_style_bars)};
        int checkedItem = prefs.getInt("pref_visualizer_style", 0);

        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.pref_visualizer_style))
            .setSingleChoiceItems(styles, checkedItem, (dialog, which) -> {
                prefs.edit().putInt("pref_visualizer_style", which).apply();
                updateVisualizerStyleSummary();
                dialog.dismiss();
            })
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .show();
    }

    private void updateVisualizerStyleSummary() {
        TextView tvViz = findViewById(R.id.tv_visualizer_style_val);
        if (tvViz == null) return;
        
        int styleIndex = prefs.getInt("pref_visualizer_style", 0);
        if (styleIndex == 1) {
            tvViz.setText(R.string.visualizer_style_bars);
        } else {
            tvViz.setText(R.string.visualizer_style_waveform);
        }
    }
}
