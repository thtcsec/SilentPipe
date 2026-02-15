package com.tuhoang.silentpipe.ui.main;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.CompoundButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.tuhoang.silentpipe.R;
import com.tuhoang.silentpipe.core.audio.AudioEffectManager;
import com.tuhoang.silentpipe.core.service.PlaybackService;

public class EqualizerFragment extends BottomSheetDialogFragment {

    private PlaybackService playbackService;
    private LinearLayout bandsContainer;
    private com.google.android.material.switchmaterial.SwitchMaterial switchEnable;
    private SeekBar seekBassBoost;

    public void setPlaybackService(PlaybackService service) {
        this.playbackService = service;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_equalizer, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        bandsContainer = view.findViewById(R.id.layout_bands_container);
        switchEnable = view.findViewById(R.id.switch_eq_enable);
        seekBassBoost = view.findViewById(R.id.seek_bass_boost);

        if (playbackService != null) {
            setupEqualizerUI();
        } else {
             // In a real app we might bind here, but for now we assume it's passed or we can cast context if possible, 
             // but 'setPlaybackService' pattern is fine if MainActivity orchestrates it.
             // Or better: MainActivity finds this fragment and sets the service.
        }
    }

    private void setupEqualizerUI() {
        AudioEffectManager audioManager = playbackService.getAudioEffectManager();
        if (audioManager == null) return;

        // Bass Boost
        seekBassBoost.setProgress(audioManager.getBassBoostStrength());
        seekBassBoost.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) audioManager.setBassBoostStrength((short) progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        View view = getView();
        if (view != null) {
            TextView tvPreset = view.findViewById(R.id.tv_current_preset);
            if (tvPreset != null) {
                tvPreset.setText(audioManager.getCurrentPresetName());
            }
        }

        // Switch
        switchEnable.setChecked(audioManager.isEnabled());
        switchEnable.setOnCheckedChangeListener((buttonView, isChecked) -> {
            audioManager.setEnabled(isChecked);
            // Optional: Disable sliders if not enabled?
            // for now, just set the engine state.
        });

        // Bands
        short bands = audioManager.getNumberOfBands();
        short[] levelRange = audioManager.getBandLevelRange(); // [min, max] e.g., [-1500, 1500]
        short minLevel = levelRange[0];
        short maxLevel = levelRange[1];

        bandsContainer.removeAllViews();

        for (short i = 0; i < bands; i++) {
            final short bandIndex = i;
            View bandView = LayoutInflater.from(getContext()).inflate(R.layout.item_eq_band, bandsContainer, false);
            
            TextView textFreq = bandView.findViewById(R.id.text_frequency);
            SeekBar seekLevel = bandView.findViewById(R.id.seek_bar_level);

            int centerFreq = audioManager.getCenterFreq(bandIndex);
            textFreq.setText(formatFreq(centerFreq));

            // Setup SeekBar
            // Range is (max - min). Progress needs to be shifted.
            // If min = -1500, max = 1500. Range = 3000.
            // visual progress 0 -> real -1500
            // visual progress 1500 -> real 0
            // visual progress 3000 -> real 1500
            
            seekLevel.setMax(maxLevel - minLevel);
            seekLevel.setProgress(audioManager.getBandLevel(bandIndex) - minLevel);

            seekLevel.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        short level = (short) (progress + minLevel);
                        audioManager.setBandLevel(bandIndex, level);
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });

            bandsContainer.addView(bandView);
        }
        
        // Handle Maximize
        View btnMaximize = getView().findViewById(R.id.btn_eq_maximize);
        if (btnMaximize != null) {
            btnMaximize.setOnClickListener(v -> {
                dismiss(); // Limit screen clutter
                // Navigate in main Activity's host
                androidx.navigation.NavController navController = 
                    androidx.navigation.Navigation.findNavController(requireActivity(), R.id.nav_host_fragment);
                navController.navigate(R.id.navigation_advanced_eq);
            });
        }
    }

    private String formatFreq(int milliHertz) {
        int hertz = milliHertz / 1000;
        if (hertz < 1000) return hertz + "Hz";
        return (hertz / 1000) + "kHz";
    }
}
