package com.tuhoang.silentpipe.core.service;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

import com.tuhoang.silentpipe.receiver.BecomingNoisyReceiver;
import com.tuhoang.silentpipe.ui.main.MainActivity;

public class PlaybackService extends MediaSessionService {

    private com.tuhoang.silentpipe.core.audio.AudioEffectManager audioEffectManager;
    public static PlaybackService instance;
    private ExoPlayer player;
    private MediaSession mediaSession;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        player = new ExoPlayer.Builder(this).build();
        
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build();
        player.setAudioAttributes(audioAttributes, true);
        
        // Initialize Audio Effects
        try {
            audioEffectManager = new com.tuhoang.silentpipe.core.audio.AudioEffectManager(player.getAudioSessionId());
        } catch (Exception e) {
            android.util.Log.e("PlaybackService", "Failed to init AudioFX", e);
        }

        createNotificationChannel();

        // Activity intent for notification click
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        mediaSession = new MediaSession.Builder(this, player)
                .setSessionActivity(pendingIntent)
                .build();
    }

    private void createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.app.NotificationChannel channel = new android.app.NotificationChannel(
                    "silentpipe_playback_v2",
                    "SilentPipe Playback",
                    android.app.NotificationManager.IMPORTANCE_HIGH); // High importance for visibility
            channel.setDescription("Shows media controls for SilentPipe");
            android.app.NotificationManager manager = getSystemService(android.app.NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Nullable
    @Override
    public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return mediaSession;
    }

    public com.tuhoang.silentpipe.core.audio.AudioEffectManager getAudioEffectManager() {
        return audioEffectManager;
    }

    @Override
    public void onDestroy() {
        instance = null;
        mediaSession.release();
        player.release();
        if (audioEffectManager != null) {
            audioEffectManager.release();
            audioEffectManager = null;
        }
        super.onDestroy();
    }
}
