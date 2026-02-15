package com.tuhoang.silentpipe.ui.main;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.tuhoang.silentpipe.R;
import com.tuhoang.silentpipe.core.audio.AudioEffectManager;
import com.tuhoang.silentpipe.core.service.PlaybackService;

import java.util.List;

public class AdvancedEqualizerFragment extends Fragment {

    private LinearLayout bandsContainer;
    private SwitchMaterial switchEnable;
    private SeekBar seekBassBoost;
    private Spinner spinnerPresets;
    private View btnBack;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_advanced_equalizer, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        bandsContainer = view.findViewById(R.id.layout_bands_container);
        switchEnable = view.findViewById(R.id.switch_eq_enable);
        seekBassBoost = view.findViewById(R.id.seek_bass_boost);
        spinnerPresets = view.findViewById(R.id.spinner_presets);
        btnBack = view.findViewById(R.id.btn_back);

        btnBack.setOnClickListener(v -> Navigation.findNavController(view).navigateUp());

        setupEqualizerUI();
    }

    private void setupEqualizerUI() {
        PlaybackService service = PlaybackService.instance;
        if (service == null) {
            Toast.makeText(getContext(), "Service not bound", Toast.LENGTH_SHORT).show();
            return;
        }

        AudioEffectManager audioManager = service.getAudioEffectManager();
        if (audioManager == null) return;

        // Switch
        switchEnable.setChecked(audioManager.isEnabled());
        switchEnable.setOnCheckedChangeListener((buttonView, isChecked) -> audioManager.setEnabled(isChecked));

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

        // Presets
        List<String> presets = audioManager.getPresetNames();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, presets);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerPresets.setAdapter(adapter);

        short currentPreset = audioManager.getCurrentPreset();
        if (currentPreset >= 0 && currentPreset < presets.size() - 1) { // -1 because Custom is last
             spinnerPresets.setSelection(currentPreset);
        } else {
             // Select Custom (last item)
             spinnerPresets.setSelection(presets.size() - 1);
        }

        spinnerPresets.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                 // Check if it is "Custom"
                 if (position == presets.size() - 1) {
                     // Custom selected, do nothing special, just let user adjust bands
                     // effectively usePreset with out of bounds index triggers Custom logic in manager
                     audioManager.usePreset((short) position); 
                 } else {
                    audioManager.usePreset((short) position);
                    // Refresh sliders to match preset
                    refreshBands(audioManager);
                 }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Bands
        refreshBands(audioManager);
    }
    
    private void refreshBands(AudioEffectManager audioManager) {
        bandsContainer.removeAllViews();
        short bands = audioManager.getNumberOfBands();
        short[] levelRange = audioManager.getBandLevelRange(); 
        short minLevel = levelRange[0];
        short maxLevel = levelRange[1];

        for (short i = 0; i < bands; i++) {
            final short bandIndex = i;
            View bandView = LayoutInflater.from(getContext()).inflate(R.layout.item_eq_band, bandsContainer, false);
            
            TextView textFreq = bandView.findViewById(R.id.text_frequency);
            TextView textLevel = bandView.findViewById(R.id.text_level);
            SeekBar seekLevel = bandView.findViewById(R.id.seek_bar_level);

            // Make text white for this dark fragment
            textFreq.setTextColor(getResources().getColor(android.R.color.white, null));
            textLevel.setTextColor(getResources().getColor(android.R.color.darker_gray, null));

            int centerFreq = audioManager.getCenterFreq(bandIndex);
            textFreq.setText(formatFreq(centerFreq));
            
            short currentLevel = audioManager.getBandLevel(bandIndex);
            textLevel.setText((currentLevel / 100) + "dB");

            seekLevel.setMax(maxLevel - minLevel);
            seekLevel.setProgress(currentLevel - minLevel);

            seekLevel.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    short level = (short) (progress + minLevel);
                    textLevel.setText((level / 100) + "dB");
                    
                    if (fromUser) {
                        audioManager.setBandLevel(bandIndex, level);
                        // Switch spinner to Custom if not already
                        if (spinnerPresets.getSelectedItemPosition() != spinnerPresets.getAdapter().getCount() - 1) {
                            spinnerPresets.setSelection(spinnerPresets.getAdapter().getCount() - 1);
                        }
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });

            bandsContainer.addView(bandView);
        }
    }

    private String formatFreq(int milliHertz) {
        int hertz = milliHertz / 1000;
        if (hertz < 1000) return hertz + "Hz";
        return (hertz / 1000) + "kHz";
    }
}
