package com.tuhoang.silentpipe.ui.main;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.audiofx.PresetReverb;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.media3.common.PlaybackParameters;

import com.google.android.material.chip.Chip;
import com.google.android.material.slider.Slider;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.tuhoang.silentpipe.R;
import com.tuhoang.silentpipe.core.audio.AudioEffectManager;
import com.tuhoang.silentpipe.core.service.PlaybackService;

/**
 * Audio FX tab providing real-time audio effects on the currently playing media.
 * Effects: Nightcore (speed+pitch), Vocal Cut, Reverb, Bass Boost, Muffle, Fade.
 */
public class AudioFxFragment extends Fragment {

    private static final String TAG = "AudioFxFragment";
    private static final String PREF_NAME = "silentpipe_fx_prefs";

    // UI Elements
    private Slider sliderSpeed, sliderPitch, sliderVocalCut, sliderReverb;
    private Slider sliderBass, sliderMuffle, sliderFadeDuration;
    private SwitchMaterial switchVocalCut, switchReverb, switchMuffle;
    private TextView tvSpeedValue, tvPitchValue, tvBassValue, tvMuffleValue;
    private TextView tvReverbPreset, tvFxStatus, tvFadeDuration, tvVocalCutStrength;
    private Chip chipNightcorePreset;
    private View btnFadeIn, btnFadeOut, btnResetAll;

    // Effects state
    private PresetReverb presetReverb;
    private ValueAnimator fadeAnimator;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private SharedPreferences prefs;

    // Reverb preset names
    private static final String[] REVERB_NAMES = {
        "None", "Small Room", "Medium Room", "Large Room",
        "Medium Hall", "Large Hall", "Plate"
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_audio_fx, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        prefs = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        bindViews(view);
        restoreState();
        setupListeners();
        updateStatus();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateStatus();
    }

    private void bindViews(View view) {
        sliderSpeed = view.findViewById(R.id.slider_speed);
        sliderPitch = view.findViewById(R.id.slider_pitch);
        sliderVocalCut = view.findViewById(R.id.slider_vocal_cut);
        sliderReverb = view.findViewById(R.id.slider_reverb);
        sliderBass = view.findViewById(R.id.slider_bass);
        sliderMuffle = view.findViewById(R.id.slider_muffle);
        sliderFadeDuration = view.findViewById(R.id.slider_fade_duration);

        switchVocalCut = view.findViewById(R.id.switch_vocal_cut);
        switchReverb = view.findViewById(R.id.switch_reverb);
        switchMuffle = view.findViewById(R.id.switch_muffle);

        tvSpeedValue = view.findViewById(R.id.tv_speed_value);
        tvPitchValue = view.findViewById(R.id.tv_pitch_value);
        tvBassValue = view.findViewById(R.id.tv_bass_value);
        tvMuffleValue = view.findViewById(R.id.tv_muffle_value);
        tvReverbPreset = view.findViewById(R.id.tv_reverb_preset);
        tvFxStatus = view.findViewById(R.id.tv_fx_status);
        tvFadeDuration = view.findViewById(R.id.tv_fade_duration);
        tvVocalCutStrength = view.findViewById(R.id.tv_vocal_cut_strength);

        chipNightcorePreset = view.findViewById(R.id.chip_nightcore_preset);
        btnFadeIn = view.findViewById(R.id.btn_fade_in);
        btnFadeOut = view.findViewById(R.id.btn_fade_out);
        btnResetAll = view.findViewById(R.id.btn_reset_all);
    }

    private void restoreState() {
        float speed = prefs.getFloat("fx_speed", 1.0f);
        float pitch = prefs.getFloat("fx_pitch", 1.0f);
        int bass = prefs.getInt("fx_bass", 0);
        boolean vocalCut = prefs.getBoolean("fx_vocal_cut", false);
        int vocalStrength = prefs.getInt("fx_vocal_strength", 80);
        boolean reverb = prefs.getBoolean("fx_reverb", false);
        int reverbPreset = prefs.getInt("fx_reverb_preset", 0);
        boolean muffle = prefs.getBoolean("fx_muffle", false);
        int muffleVal = prefs.getInt("fx_muffle_value", 0);
        int fadeDuration = prefs.getInt("fx_fade_duration", 3);

        sliderSpeed.setValue(clamp(speed, 0.5f, 2.0f));
        sliderPitch.setValue(clamp(pitch, 0.5f, 2.0f));
        sliderBass.setValue(clamp(bass, 0, 1000));
        sliderVocalCut.setValue(clamp(vocalStrength, 0, 100));
        sliderReverb.setValue(clamp(reverbPreset, 0, 6));
        sliderMuffle.setValue(clamp(muffleVal, 0, 100));
        sliderFadeDuration.setValue(clamp(fadeDuration, 1, 10));

        switchVocalCut.setChecked(vocalCut);
        switchReverb.setChecked(reverb);
        switchMuffle.setChecked(muffle);

        sliderVocalCut.setEnabled(vocalCut);
        sliderReverb.setEnabled(reverb);
        sliderMuffle.setEnabled(muffle);

        updateSpeedLabel(speed);
        updatePitchLabel(pitch);
        updateBassLabel(bass);
        updateMuffleLabel(muffleVal);
        updateReverbLabel(reverbPreset);
        updateNightcoreChip(speed, pitch);
        updateFadeDurationLabel((int) sliderFadeDuration.getValue());
    }

    private void setupListeners() {
        // ── Speed (Nightcore) ──
        sliderSpeed.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                updateSpeedLabel(value);
                updateNightcoreChip(value, sliderPitch.getValue());
                applyPlaybackParams(value, sliderPitch.getValue());
                prefs.edit().putFloat("fx_speed", value).apply();
            }
        });

        // ── Pitch ──
        sliderPitch.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                updatePitchLabel(value);
                updateNightcoreChip(sliderSpeed.getValue(), value);
                applyPlaybackParams(sliderSpeed.getValue(), value);
                prefs.edit().putFloat("fx_pitch", value).apply();
            }
        });

        // ── Nightcore preset chip ──
        chipNightcorePreset.setOnClickListener(v -> cycleNightcorePreset());

        // ── Vocal Cut ──
        switchVocalCut.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sliderVocalCut.setEnabled(isChecked);
            applyVocalCut(isChecked, (int) sliderVocalCut.getValue());
            prefs.edit().putBoolean("fx_vocal_cut", isChecked).apply();
        });

        sliderVocalCut.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser && switchVocalCut.isChecked()) {
                applyVocalCut(true, (int) value);
                prefs.edit().putInt("fx_vocal_strength", (int) value).apply();
            }
        });

        // ── Reverb ──
        switchReverb.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sliderReverb.setEnabled(isChecked);
            applyReverb(isChecked, (int) sliderReverb.getValue());
            prefs.edit().putBoolean("fx_reverb", isChecked).apply();
        });

        sliderReverb.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                int preset = (int) value;
                updateReverbLabel(preset);
                if (switchReverb.isChecked()) {
                    applyReverb(true, preset);
                }
                prefs.edit().putInt("fx_reverb_preset", preset).apply();
            }
        });

        // ── Bass Boost ──
        sliderBass.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                int strength = (int) value;
                updateBassLabel(strength);
                applyBassBoost((short) strength);
                prefs.edit().putInt("fx_bass", strength).apply();
            }
        });

        // ── Muffle ──
        switchMuffle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sliderMuffle.setEnabled(isChecked);
            applyMuffle(isChecked, (int) sliderMuffle.getValue());
            prefs.edit().putBoolean("fx_muffle", isChecked).apply();
        });

        sliderMuffle.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                int muffleVal = (int) value;
                updateMuffleLabel(muffleVal);
                if (switchMuffle.isChecked()) {
                    applyMuffle(true, muffleVal);
                }
                prefs.edit().putInt("fx_muffle_value", muffleVal).apply();
            }
        });

        // ── Fade ──
        sliderFadeDuration.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                updateFadeDurationLabel((int) value);
                prefs.edit().putInt("fx_fade_duration", (int) value).apply();
            }
        });

        btnFadeIn.setOnClickListener(v -> executeFade(true));
        btnFadeOut.setOnClickListener(v -> executeFade(false));

        // ── Reset ──
        btnResetAll.setOnClickListener(v -> resetAll());
    }

    // ══════════════════════════════════════════
    //  APPLY EFFECTS
    // ══════════════════════════════════════════

    private void applyPlaybackParams(float speed, float pitch) {
        PlaybackService service = PlaybackService.instance;
        if (service == null) return;

        try {
            androidx.media3.exoplayer.ExoPlayer player = getPlayer();
            if (player != null) {
                player.setPlaybackParameters(new PlaybackParameters(speed, pitch));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error applying playback params", e);
        }
    }

    private void applyVocalCut(boolean enabled, int strength) {
        // Vocal Cut: reduce mid-range frequencies (300Hz-3kHz) where vocals typically reside
        // Uses the existing equalizer bands to create a mid-scoop
        PlaybackService service = PlaybackService.instance;
        if (service == null) return;

        AudioEffectManager manager = service.getAudioEffectManager();
        if (manager == null) return;

        try {
            short bands = manager.getNumberOfBands();
            short[] levelRange = manager.getBandLevelRange();
            short minLevel = levelRange[0];

            for (short i = 0; i < bands; i++) {
                int freq = manager.getCenterFreq(i) / 1000; // Convert to Hz
                if (enabled && freq >= 300 && freq <= 4000) {
                    // Cut mid-frequencies proportional to strength
                    short cut = (short) (minLevel * strength / 100);
                    manager.setBandLevel(i, cut);
                } else if (!enabled) {
                    // Reset mid bands to 0
                    int freqHz = manager.getCenterFreq(i) / 1000;
                    if (freqHz >= 300 && freqHz <= 4000) {
                        manager.setBandLevel(i, (short) 0);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error applying vocal cut", e);
        }
    }

    private void applyReverb(boolean enabled, int presetIndex) {
        PlaybackService service = PlaybackService.instance;
        if (service == null) return;

        try {
            int sessionId = service.getAudioEffectManager() != null
                    ? service.getAudioEffectManager().getAudioSessionId() : 0;
            if (sessionId == 0) return;

            if (enabled && presetIndex > 0) {
                if (presetReverb == null) {
                    presetReverb = new PresetReverb(0, sessionId);
                }
                presetReverb.setPreset((short) (presetIndex - 1)); // 0=SmallRoom, etc.
                presetReverb.setEnabled(true);
            } else {
                if (presetReverb != null) {
                    presetReverb.setEnabled(false);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error applying reverb", e);
        }
    }

    private void applyBassBoost(short strength) {
        PlaybackService service = PlaybackService.instance;
        if (service == null) return;

        AudioEffectManager manager = service.getAudioEffectManager();
        if (manager != null) {
            manager.setBassBoostStrength(strength);
        }
    }

    private void applyMuffle(boolean enabled, int amount) {
        // Muffle: cut high frequencies to simulate muffled/underwater sound
        PlaybackService service = PlaybackService.instance;
        if (service == null) return;

        AudioEffectManager manager = service.getAudioEffectManager();
        if (manager == null) return;

        try {
            short bands = manager.getNumberOfBands();
            short[] levelRange = manager.getBandLevelRange();
            short minLevel = levelRange[0];

            for (short i = 0; i < bands; i++) {
                int freq = manager.getCenterFreq(i) / 1000; // Hz
                if (enabled && freq >= 2000) {
                    // Cut high frequencies proportional to amount and frequency
                    float factor = Math.min(1.0f, (float) (freq - 2000) / 12000f);
                    short cut = (short) (minLevel * factor * amount / 100);
                    manager.setBandLevel(i, cut);
                } else if (!enabled && freq >= 2000) {
                    manager.setBandLevel(i, (short) 0);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error applying muffle", e);
        }
    }

    private void executeFade(boolean fadeIn) {
        PlaybackService service = PlaybackService.instance;
        if (service == null) {
            showToast(getString(R.string.audio_fx_no_playback));
            return;
        }

        androidx.media3.exoplayer.ExoPlayer player = getPlayer();
        if (player == null || !player.isPlaying()) {
            showToast(getString(R.string.audio_fx_no_playback));
            return;
        }

        int durationSec = (int) sliderFadeDuration.getValue();
        long durationMs = durationSec * 1000L;

        // Cancel any ongoing fade
        if (fadeAnimator != null && fadeAnimator.isRunning()) {
            fadeAnimator.cancel();
        }

        float startVol = fadeIn ? 0f : 1f;
        float endVol = fadeIn ? 1f : 0f;

        // Set initial volume
        player.setVolume(startVol);

        fadeAnimator = ValueAnimator.ofFloat(startVol, endVol);
        fadeAnimator.setDuration(durationMs);
        fadeAnimator.addUpdateListener(animation -> {
            float vol = (float) animation.getAnimatedValue();
            androidx.media3.exoplayer.ExoPlayer p = getPlayer();
            if (p != null) {
                p.setVolume(vol);
            }
        });
        fadeAnimator.start();

        showToast(fadeIn ? "Fade In: " + durationSec + "s" : "Fade Out: " + durationSec + "s");
    }

    // ══════════════════════════════════════════
    //  UI HELPERS
    // ══════════════════════════════════════════

    private void updateSpeedLabel(float speed) {
        tvSpeedValue.setText(String.format("Speed: %.2fx", speed));
    }

    private void updatePitchLabel(float pitch) {
        tvPitchValue.setText(String.format("Pitch: %.2fx", pitch));
    }

    private void updateBassLabel(int strength) {
        tvBassValue.setText(String.format("%d%%", strength / 10));
    }

    private void updateMuffleLabel(int value) {
        tvMuffleValue.setText(String.format("%d%%", value));
    }

    private void updateReverbLabel(int preset) {
        if (preset >= 0 && preset < REVERB_NAMES.length) {
            tvReverbPreset.setText(REVERB_NAMES[preset]);
        }
    }

    private void updateFadeDurationLabel(int seconds) {
        tvFadeDuration.setText(String.format(getString(R.string.fx_fade_duration_format), seconds));
    }

    private void updateNightcoreChip(float speed, float pitch) {
        if (speed == 1.0f && pitch == 1.0f) {
            chipNightcorePreset.setText(R.string.fx_preset_normal);
        } else if (speed >= 1.2f && pitch >= 1.2f) {
            chipNightcorePreset.setText(R.string.fx_preset_nightcore);
        } else if (speed <= 0.8f && pitch <= 0.85f) {
            chipNightcorePreset.setText(R.string.fx_preset_slowed);
        } else {
            chipNightcorePreset.setText(R.string.fx_preset_custom);
        }
    }

    private void cycleNightcorePreset() {
        float currentSpeed = sliderSpeed.getValue();
        float currentPitch = sliderPitch.getValue();

        float newSpeed, newPitch;

        if (currentSpeed == 1.0f && currentPitch == 1.0f) {
            // Normal → Nightcore
            newSpeed = 1.25f;
            newPitch = 1.30f;
        } else if (currentSpeed >= 1.2f && currentPitch >= 1.2f) {
            // Nightcore → Slowed
            newSpeed = 0.75f;
            newPitch = 0.80f;
        } else {
            // Anything else → Normal
            newSpeed = 1.0f;
            newPitch = 1.0f;
        }

        sliderSpeed.setValue(newSpeed);
        sliderPitch.setValue(newPitch);
        updateSpeedLabel(newSpeed);
        updatePitchLabel(newPitch);
        updateNightcoreChip(newSpeed, newPitch);
        applyPlaybackParams(newSpeed, newPitch);
        prefs.edit().putFloat("fx_speed", newSpeed).putFloat("fx_pitch", newPitch).apply();
    }

    private void updateStatus() {
        PlaybackService service = PlaybackService.instance;
        if (service != null && service.getAudioEffectManager() != null) {
            tvFxStatus.setText(R.string.audio_fx_ready);
        } else {
            tvFxStatus.setText(R.string.audio_fx_no_playback);
        }
    }

    private void resetAll() {
        // Reset speed & pitch
        sliderSpeed.setValue(1.0f);
        sliderPitch.setValue(1.0f);
        updateSpeedLabel(1.0f);
        updatePitchLabel(1.0f);
        updateNightcoreChip(1.0f, 1.0f);
        applyPlaybackParams(1.0f, 1.0f);

        // Reset vocal cut
        switchVocalCut.setChecked(false);
        sliderVocalCut.setValue(80);
        applyVocalCut(false, 0);

        // Reset reverb
        switchReverb.setChecked(false);
        sliderReverb.setValue(0);
        updateReverbLabel(0);
        applyReverb(false, 0);

        // Reset bass
        sliderBass.setValue(0);
        updateBassLabel(0);
        applyBassBoost((short) 0);

        // Reset muffle
        switchMuffle.setChecked(false);
        sliderMuffle.setValue(0);
        updateMuffleLabel(0);
        applyMuffle(false, 0);

        // Reset fade
        sliderFadeDuration.setValue(3);
        updateFadeDurationLabel(3);

        // Reset volume
        androidx.media3.exoplayer.ExoPlayer player = getPlayer();
        if (player != null) player.setVolume(1.0f);

        // Clear prefs
        prefs.edit().clear().apply();

        showToast(getString(R.string.fx_reset_done));
    }

    @Nullable
    private androidx.media3.exoplayer.ExoPlayer getPlayer() {
        PlaybackService service = PlaybackService.instance;
        if (service == null) return null;
        return service.getPlayer();
    }

    private void showToast(String msg) {
        if (getContext() != null) {
            Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
        }
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (fadeAnimator != null) {
            fadeAnimator.cancel();
            fadeAnimator = null;
        }
        if (presetReverb != null) {
            try {
                presetReverb.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing reverb", e);
            }
            presetReverb = null;
        }
    }
}
