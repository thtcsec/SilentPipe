package com.tuhoang.silentpipe.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;

public class BecomingNoisyReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
            // Logic to pause player would typically involve sending a command to the service
            // or using the MediaController if possible.
            // Since this receiver is often registered within the Service/Activity, 
            // the logic is better handled there or via MediaSession callback.
        }
    }
}
