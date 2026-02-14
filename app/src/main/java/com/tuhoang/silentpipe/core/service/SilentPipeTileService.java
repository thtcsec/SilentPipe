package com.tuhoang.silentpipe.core.service;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.service.quicksettings.TileService;
import android.widget.Toast;

import com.tuhoang.silentpipe.ui.main.MainActivity;

public class SilentPipeTileService extends TileService {

    @SuppressLint("StartActivityAndCollapseDeprecated")
    @Override
    public void onClick() {
        super.onClick();
        
        // Collapse status bar
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) {
             sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
        }
        
        // Start MainActivity with special action
        Intent intent = new Intent(this, MainActivity.class);
        intent.setAction("com.tuhoang.silentpipe.ACTION_PLAY_CLIPBOARD");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        
        try {
            startActivityAndCollapse(intent);
        } catch (Exception e) {
            // Fallback for older versions or if startActivityAndCollapse fails
            startActivity(intent);
        }
    }
}
