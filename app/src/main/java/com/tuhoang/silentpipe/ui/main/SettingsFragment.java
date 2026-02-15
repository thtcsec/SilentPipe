package com.tuhoang.silentpipe.ui.main;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tuhoang.silentpipe.R;
import com.tuhoang.silentpipe.data.AppDatabase;
import com.tuhoang.silentpipe.data.FavoriteItem;

import java.io.FileOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.List;

public class SettingsFragment extends Fragment {
    private static final String TAG = "SettingsFragment";
    private ActivityResultLauncher<Intent> exportLauncher;
    private ActivityResultLauncher<Intent> importLauncher;
    private Gson gson = new Gson();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        exportLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    backupFavorites(result.getData().getData());
                }
            }
        );

        importLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    restoreFavorites(result.getData().getData());
                }
            }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Button btnBackup = view.findViewById(R.id.btn_backup);
        Button btnRestore = view.findViewById(R.id.btn_restore);

        btnBackup.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, "silentpipe_backup_" + System.currentTimeMillis() + ".json");
            exportLauncher.launch(intent);
        });

        btnRestore.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            importLauncher.launch(intent);
        });

        view.findViewById(R.id.btn_add_shortcut_home).setOnClickListener(v -> addHomeScreenShortcut());
        view.findViewById(R.id.btn_add_qs_tile).setOnClickListener(v -> requestAddTile());
        
        // Link to real Equalizer
        View btnEq = view.findViewById(R.id.btn_equalizer);
        
        if (btnEq != null) {
            btnEq.setOnClickListener(v -> {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).showEqualizer();
                } else {
                    Toast.makeText(getContext(), "Error: Not attached to MainActivity", Toast.LENGTH_SHORT).show();
                }
            });

        }
        
        // HQ Audio Switch
        com.google.android.material.switchmaterial.SwitchMaterial switchHq = view.findViewById(R.id.switch_hq_audio);
        if (switchHq != null) {
            android.content.SharedPreferences prefs = requireContext().getSharedPreferences("silentpipe_prefs", android.content.Context.MODE_PRIVATE);
            switchHq.setChecked(prefs.getBoolean("pref_hq_audio", false));
            
            switchHq.setOnCheckedChangeListener((buttonView, isChecked) -> {
                prefs.edit().putBoolean("pref_hq_audio", isChecked).apply();
            });
        }
        
        // Skip Time Button
        Button btnSkip = view.findViewById(R.id.btn_skip_time);
        if (btnSkip != null) {
            android.content.SharedPreferences prefs = requireContext().getSharedPreferences("silentpipe_prefs", android.content.Context.MODE_PRIVATE);
            int currentSkip = prefs.getInt("pref_skip_time", 10);
            btnSkip.setText("Skip Interval: " + currentSkip + "s");
            
            btnSkip.setOnClickListener(v -> {
                final String[] options = {"5s", "10s", "15s", "30s", "60s"};
                final int[] values = {5, 10, 15, 30, 60};
                
                new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("Select Skip Interval")
                    .setItems(options, (dialog, which) -> {
                        int val = values[which];
                        prefs.edit().putInt("pref_skip_time", val).apply();
                        btnSkip.setText("Skip Interval: " + val + "s");
                        Toast.makeText(getContext(), "Skip time set to " + val + "s", Toast.LENGTH_SHORT).show();
                    })
                    .show();
            });
        }



        // Theme Switcher
        Button btnTheme = view.findViewById(R.id.btn_theme);
        if (btnTheme != null) {
            android.content.SharedPreferences prefs = requireContext().getSharedPreferences("silentpipe_prefs", android.content.Context.MODE_PRIVATE);
            int currentMode = prefs.getInt("pref_theme_mode", androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
            updateThemeButtonText(btnTheme, currentMode);

            btnTheme.setOnClickListener(v -> {
                String[] modes = {getString(R.string.system_default), getString(R.string.light), getString(R.string.dark)};
                int checkedItem = 0;
                if (currentMode == androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO) checkedItem = 1;
                else if (currentMode == androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES) checkedItem = 2;

                new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle(R.string.select_app_theme)
                    .setSingleChoiceItems(modes, checkedItem, (dialog, which) -> {
                        int newMode = androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                        if (which == 1) newMode = androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO;
                        else if (which == 2) newMode = androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES;

                        prefs.edit().putInt("pref_theme_mode", newMode).apply();
                        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(newMode);
                        updateThemeButtonText(btnTheme, newMode);
                        dialog.dismiss();
                    })
                    .show();
            });
        }

        // Language Switcher (Added)
        Button btnLanguage = view.findViewById(R.id.btn_language);
        if (btnLanguage != null) {
            // Check current locale
            androidx.core.os.LocaleListCompat currentLocales = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales();
            String currentLang = "en"; // Default
            if (!currentLocales.isEmpty()) {
                currentLang = currentLocales.get(0).getLanguage();
            }
            updateLanguageButtonText(btnLanguage, currentLang);

            btnLanguage.setOnClickListener(v -> {
                String[] languages = {getString(R.string.english), getString(R.string.vietnamese)};
                String finalCurrentLang = !currentLocales.isEmpty() ? currentLocales.get(0).getLanguage() : "en";
                int checkedItem = finalCurrentLang.equals("vi") ? 1 : 0;

                new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle(R.string.select_language)
                        .setSingleChoiceItems(languages, checkedItem, (dialog, which) -> {
                            String newLang = which == 1 ? "vi" : "en";
                            androidx.core.os.LocaleListCompat appLocale = androidx.core.os.LocaleListCompat.forLanguageTags(newLang);
                            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(appLocale);
                            // No need to update button text manually, activity will recreate
                            dialog.dismiss();
                        })
                        .show();
            });
        }

        // Permission Logic
        SwitchMaterial switchNoti = view.findViewById(R.id.switch_notification_permission);

        // check notification permission
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            boolean hasNoti = androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED;
            switchNoti.setChecked(hasNoti);
            switchNoti.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked && !hasNoti) {
                    requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
                } else if (!isChecked) {
                    // User wants to disable, guide them to settings
                     Intent intent = new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, requireContext().getPackageName());
                    startActivity(intent);
                }
            });
        } else {
            switchNoti.setEnabled(false);
            switchNoti.setText(R.string.pref_allow_notifications_13);
        }

        // Check Microphone Permission
        SwitchMaterial switchMic = view.findViewById(R.id.switch_microphone_permission);
        if (switchMic != null) {
            boolean hasMic = androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED;
            switchMic.setChecked(hasMic);
            switchMic.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked && !hasMic) {
                    requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 102);
                } else if (!isChecked) {
                    Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", requireContext().getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                }
            });
        }

        view.findViewById(R.id.btn_github).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/thtcsec/SilentPipe"));
            startActivity(intent);
        });
        
        // Update About Text with Version
        android.widget.TextView tvAbout = view.findViewById(R.id.tv_about_content);
        if (tvAbout != null) {
             String version = "1.0";
             try {
                 android.content.pm.PackageInfo pInfo = requireContext().getPackageManager().getPackageInfo(requireContext().getPackageName(), 0);
                 version = pInfo.versionName;
             } catch (android.content.pm.PackageManager.NameNotFoundException e) {
                 e.printStackTrace();
             }
             tvAbout.setText(getString(R.string.pref_about_content, version));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getView() != null) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                SwitchMaterial switchNoti = getView().findViewById(R.id.switch_notification_permission);
                if (switchNoti != null) {
                     boolean hasNoti = androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED;
                     switchNoti.setChecked(hasNoti);
                }
            }
            
            SwitchMaterial switchMic = getView().findViewById(R.id.switch_microphone_permission);
            if (switchMic != null) {
                boolean hasMic = androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED;
                switchMic.setChecked(hasMic);
            }
        }
    }

    private void updateThemeButtonText(Button btn, int mode) {
        String text = getString(R.string.pref_theme_default);
        if (mode == androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO) text = getString(R.string.pref_theme_light);
        else if (mode == androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES) text = getString(R.string.pref_theme_dark);
        btn.setText(text);
    }

    private void updateLanguageButtonText(Button btn, String langCode) {
        String text = getString(R.string.pref_language_en);
        if ("vi".equals(langCode)) text = getString(R.string.pref_language_vi);
        btn.setText(text);
    }

    private void addHomeScreenShortcut() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.content.pm.ShortcutManager shortcutManager = requireContext().getSystemService(android.content.pm.ShortcutManager.class);
            if (shortcutManager != null && shortcutManager.isRequestPinShortcutSupported()) {
                Intent intent = new Intent(requireContext(), com.tuhoang.silentpipe.ui.main.MainActivity.class);
                intent.setAction("com.tuhoang.silentpipe.ACTION_PLAY_CLIPBOARD");
                
                android.content.pm.ShortcutInfo pinShortcutInfo = new android.content.pm.ShortcutInfo.Builder(requireContext(), "clipboard_play")
                        .setShortLabel("SilentPipe Clip")
                        .setLongLabel("Play from Clipboard")
                        .setIcon(android.graphics.drawable.Icon.createWithResource(requireContext(), R.mipmap.ic_launcher))
                        .setIntent(intent)
                        .build();

                // Create the Intent that will be broadcast when the user adds the shortcut
                // We don't really need a callback for this simple case, but we need a PendingIntent
                android.app.PendingIntent successCallback = android.app.PendingIntent.getBroadcast(
                        requireContext(), 0, new Intent(requireContext(), com.tuhoang.silentpipe.receiver.ShareActivity.class), // Dummy receiver
                        android.app.PendingIntent.FLAG_IMMUTABLE);

                shortcutManager.requestPinShortcut(pinShortcutInfo, successCallback.getIntentSender());
                Toast.makeText(getContext(), "Requesting to pin shortcut...", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), "Pinning shortcuts not supported on this device", Toast.LENGTH_SHORT).show();
            }
        } else {
             Toast.makeText(getContext(), "Requires Android 8.0+", Toast.LENGTH_SHORT).show();
        }
    }

    private void requestAddTile() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            android.app.StatusBarManager statusBarManager = requireContext().getSystemService(android.app.StatusBarManager.class);
            if (statusBarManager != null) {
                statusBarManager.requestAddTileService(
                        new android.content.ComponentName(requireContext(), com.tuhoang.silentpipe.core.service.SilentPipeTileService.class),
                        "SilentPipe",
                        android.graphics.drawable.Icon.createWithResource(requireContext(), R.mipmap.ic_launcher),
                        requireContext().getMainExecutor(),
                        result -> {
                             // Request result callback
                             // TILE_ADD_REQUEST_RESULT_TILE_ADDED = 2
                             // TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED = 1
                             // TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED = 0
                             String msg = "Tile request processed: " + result;
                             // We can't Toast from background thread easily here without loopers/handlers if executor is mismatched
                        }
                );
            }
        } else {
            Toast.makeText(getContext(), "Requires Android 13+", Toast.LENGTH_SHORT).show();
        }
    }

    private void backupFavorites(Uri uri) {
        new Thread(() -> {
            try {
                AppDatabase db = androidx.room.Room.databaseBuilder(
                        requireContext().getApplicationContext(),
                        AppDatabase.class, "silentpipe-db").build();
                List<FavoriteItem> items = db.favoriteDao().getAll();
                String json = gson.toJson(items);

                ParcelFileDescriptor pfd = requireContext().getContentResolver().openFileDescriptor(uri, "w");
                FileOutputStream fos = new FileOutputStream(pfd.getFileDescriptor());
                fos.write(json.getBytes());
                fos.close();
                pfd.close();

                requireActivity().runOnUiThread(() -> 
                    Toast.makeText(getContext(), "Backup Successful!", Toast.LENGTH_SHORT).show()
                );
            } catch (Exception e) {
                android.util.Log.e(TAG, "Error handling preference change", e);
                requireActivity().runOnUiThread(() -> 
                    Toast.makeText(getContext(), "Backup Failed: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
            }
        }).start();
    }

    private void restoreFavorites(Uri uri) {
        new Thread(() -> {
            try {
                StringBuilder stringBuilder = new StringBuilder();
                BufferedReader reader = new BufferedReader(new InputStreamReader(requireContext().getContentResolver().openInputStream(uri)));
                String line;
                while ((line = reader.readLine()) != null) {
                    stringBuilder.append(line);
                }
                reader.close();

                Type listType = new TypeToken<List<FavoriteItem>>(){}.getType();
                List<FavoriteItem> items = gson.fromJson(stringBuilder.toString(), listType);

                if (items != null) {
                    AppDatabase db = androidx.room.Room.databaseBuilder(
                        requireContext().getApplicationContext(),
                        AppDatabase.class, "silentpipe-db").build();
                    
                    for (FavoriteItem item : items) {
                        try {
                            // Simple check to avoid crashing, though conflict strategy could be used
                             if (!db.favoriteDao().isFavorite(item.url)) {
                                 item.id = 0; // Reset ID to let AutoIncrement handle it
                                 db.favoriteDao().insert(item);
                             }
                        } catch (Exception ignore) {}
                    }
                }

                requireActivity().runOnUiThread(() -> 
                    Toast.makeText(getContext(), "Restore Successful!", Toast.LENGTH_SHORT).show()
                );
            } catch (Exception e) {
                android.util.Log.e(TAG, "Error handling preference change", e);
                requireActivity().runOnUiThread(() -> 
                    Toast.makeText(getContext(), "Restore Failed: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
            }
        }).start();
    }
}
