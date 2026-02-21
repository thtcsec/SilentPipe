package com.tuhoang.silentpipe;

import android.app.Application;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

public class SilentPipeApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        
        // Preload Python to reduce latency when user shares a link
        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(this));
        }

        // Apply Material You Dynamic Colors
        com.google.android.material.color.DynamicColors.applyToActivitiesIfAvailable(this);

        // Apply Theme
        android.content.SharedPreferences prefs = getSharedPreferences("silentpipe_prefs", MODE_PRIVATE);
        int mode = prefs.getInt("pref_theme_mode", androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(mode);
    }
}
