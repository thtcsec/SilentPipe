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
    }
}
