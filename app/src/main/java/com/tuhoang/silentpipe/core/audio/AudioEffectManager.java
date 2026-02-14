package com.tuhoang.silentpipe.core.audio;

import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.util.Log;

public class AudioEffectManager {
    private static final String TAG = "AudioEffectManager";
    private Equalizer equalizer;
    private BassBoost bassBoost;
    private int audioSessionId;

    // State persistence
    private short savedBassStrength = 0;
    private short[] savedBandLevels = null;
    private short savedPreset = -1;
    private boolean savedEnabled = true;

    public AudioEffectManager(int audioSessionId) {
        this.audioSessionId = audioSessionId;
        initEffects();
    }

    public void setAudioSessionId(int audioSessionId) {
        if (this.audioSessionId == audioSessionId && equalizer != null) return;
        
        Log.d(TAG, "Audio Session ID changed from " + this.audioSessionId + " to " + audioSessionId);
        this.audioSessionId = audioSessionId;
        
        // Use a temporary try block to release and re-init safely
        try {
            release();
            initEffects();
            restoreState();
        } catch (Exception e) {
            Log.e(TAG, "Failed to re-init audio session", e);
        }
    }

    private void initEffects() {
        try {
            equalizer = new Equalizer(0, audioSessionId);
            equalizer.setEnabled(savedEnabled);
            
            bassBoost = new BassBoost(0, audioSessionId);
            bassBoost.setEnabled(savedEnabled);
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Audio Effects", e);
        }
    }
    
    private void restoreState() {
        if (equalizer != null) {
            try {
                if (savedPreset != -1) {
                    equalizer.usePreset(savedPreset);
                } else if (savedBandLevels != null) {
                    for (short i = 0; i < savedBandLevels.length; i++) {
                        if (i < equalizer.getNumberOfBands()) {
                            equalizer.setBandLevel(i, savedBandLevels[i]);
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error restoring EQ state", e);
            }
        }
        
        if (bassBoost != null) {
             try {
                if (bassBoost.getStrengthSupported()) {
                    bassBoost.setStrength(savedBassStrength);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error restoring BassBoost", e);
            }
        }
    }

    public void setBandLevel(short band, short level) {
        if (equalizer != null) {
            try {
                equalizer.setBandLevel(band, level);
                // Update saved state
                if (savedBandLevels == null || savedBandLevels.length != equalizer.getNumberOfBands()) {
                    savedBandLevels = new short[equalizer.getNumberOfBands()];
                }
                savedBandLevels[band] = level;
                savedPreset = -1; // Custom overrides preset
            } catch (Exception e) {
                Log.e(TAG, "Error setting band level", e);
            }
        }
    }

    public short getBandLevel(short band) {
        // Return saved if available to be consistent, or read actual
        return equalizer != null ? equalizer.getBandLevel(band) : (savedBandLevels != null && band < savedBandLevels.length ? savedBandLevels[band] : 0);
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
        savedBassStrength = strength;
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
        return bassBoost != null ? bassBoost.getRoundedStrength() : savedBassStrength;
    }
    
    public void setEnabled(boolean enabled) {
        savedEnabled = enabled;
        if (equalizer != null) equalizer.setEnabled(enabled);
        if (bassBoost != null) bassBoost.setEnabled(enabled);
    }
    
    public boolean isEnabled() {
        return equalizer != null ? equalizer.getEnabled() : savedEnabled;
    }

    public java.util.List<String> getPresetNames() {
        java.util.List<String> presets = new java.util.ArrayList<>();
        // Add "Custom" as the first option or last? User asked for Custom.
        // Let's add "Custom" at end or beginning. Standard is often: Flat, Rock... Custom.
        if (equalizer != null) {
            short numberOfPresets = equalizer.getNumberOfPresets();
            for (short i = 0; i < numberOfPresets; i++) {
                presets.add(equalizer.getPresetName(i));
            }
        }
        presets.add("Custom");
        return presets;
    }

    public void usePreset(short presetIndex) {
        if (equalizer != null) {
            short numberOfPresets = equalizer.getNumberOfPresets();
            if (presetIndex < numberOfPresets && presetIndex >= 0) {
                 savedPreset = presetIndex;
                 savedBandLevels = null; // Reset custom levels since we are using a preset
                 try {
                    equalizer.usePreset(presetIndex);
                 } catch (Exception e) {
                    Log.e(TAG, "Error using preset", e);
                 }
            } else {
                // Must be "Custom"
                savedPreset = -1; 
                // Don't change bands, just marking as custom
            }
        }
    }

    public short getCurrentPreset() {
        if (savedPreset != -1) return savedPreset; // It's a real preset
        return (short) (equalizer != null ? equalizer.getNumberOfPresets() : 0); // Return index of "Custom" (which is size)
    }

    public String getCurrentPresetName() {
        if (savedPreset != -1 && equalizer != null) {
            try {
                return equalizer.getPresetName(savedPreset);
            } catch (Exception e) { return "Unknown"; }
        }
        return "Custom";
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
