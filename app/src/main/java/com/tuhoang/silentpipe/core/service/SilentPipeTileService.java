package com.tuhoang.silentpipe.core.service;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.service.quicksettings.TileService;
import android.widget.Toast;

import com.tuhoang.silentpipe.ui.main.MainActivity;

public class SilentPipeTileService extends TileService {

    @Override
    @SuppressWarnings("deprecation") // For backward compatibility on API < 34
    public void onClick() {
        super.onClick();
        
        // Collapse status bar for older versions (pre-S) where it might be needed manually
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) {
             sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
        }
        
        // Start MainActivity with special action
        Intent intent = new Intent(this, MainActivity.class);
        intent.setAction("com.tuhoang.silentpipe.ACTION_PLAY_CLIPBOARD");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        
        try {
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                // API 34+ requires PendingIntent for startActivityAndCollapse
                android.app.PendingIntent pendingIntent = android.app.PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    android.app.PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_UPDATE_CURRENT
                );
                startActivityAndCollapse(pendingIntent);
            } else {
                // Older APIs use Intent
                startActivityAndCollapse(intent);
            }
        } catch (Exception e) {
            // Fallback
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }
    }
}
