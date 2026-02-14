package com.tuhoang.silentpipe.core.audio;

import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.util.Log;

public class AudioEffectManager {
    private static final String TAG = "AudioEffectManager";
    private Equalizer equalizer;
    private BassBoost bassBoost;
    private int audioSessionId;

    public AudioEffectManager(int audioSessionId) {
        this.audioSessionId = audioSessionId;
        initEffects();
    }

    private void initEffects() {
        try {
            equalizer = new Equalizer(0, audioSessionId);
            equalizer.setEnabled(true);
            
            bassBoost = new BassBoost(0, audioSessionId);
            bassBoost.setEnabled(true);
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Audio Effects", e);
        }
    }

    public void setBandLevel(short band, short level) {
        if (equalizer != null) {
            try {
                equalizer.setBandLevel(band, level);
            } catch (Exception e) {
                Log.e(TAG, "Error setting band level", e);
            }
        }
    }

    public short getBandLevel(short band) {
        return equalizer != null ? equalizer.getBandLevel(band) : 0;
    }

    public short getNumberOfBands() {
        return equalizer != null ? equalizer.getNumberOfBands() : 0;
    }

    public short[] getBandLevelRange() {
        return equalizer != null ? equalizer.getBandLevelRange() : new short[]{0, 0};
    }

    public int getCenterFreq(short band) {
        return equalizer != null ? equalizer.getCenterFreq(band) : 0;
    }

    public void setBassBoostStrength(short strength) {
        if (bassBoost != null) {
            try {
                if (bassBoost.getStrengthSupported()) {
                    bassBoost.setStrength(strength);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error setting bass boost", e);
            }
        }
    }

    public short getBassBoostStrength() {
        return bassBoost != null ? bassBoost.getRoundedStrength() : 0;
    }
    
    public void setEnabled(boolean enabled) {
        if (equalizer != null) equalizer.setEnabled(enabled);
        if (bassBoost != null) bassBoost.setEnabled(enabled);
    }
    
    public boolean isEnabled() {
        return equalizer != null && equalizer.getEnabled();
    }

    public void release() {
        if (equalizer != null) {
            equalizer.release();
            equalizer = null;
        }
        if (bassBoost != null) {
            bassBoost.release();
            bassBoost = null;
        }
    }
}
