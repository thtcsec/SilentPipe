package com.tuhoang.silentpipe.ui.main;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.slider.Slider;
import com.tuhoang.silentpipe.R;

import java.util.List;
import java.util.Locale;

public class SpeedPickerFragment extends BottomSheetDialogFragment {

    private Slider sliderSpeed;
    private TextView tvCurrentSpeed;
    private ChipGroup chipGroupSpeeds;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_speed_picker, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        sliderSpeed = view.findViewById(R.id.slider_speed);
        tvCurrentSpeed = view.findViewById(R.id.tv_current_speed);
        chipGroupSpeeds = view.findViewById(R.id.chip_group_speeds);
        View btnReset = view.findViewById(R.id.btn_reset_speed);

        float currentSpeed = 1.0f;
        if (getActivity() instanceof MainActivity) {
            currentSpeed = ((MainActivity) getActivity()).getCurrentSpeed();
        }

        updateUI(currentSpeed);

        sliderSpeed.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                applySpeed(value);
            }
        });

        chipGroupSpeeds.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (!checkedIds.isEmpty()) {
                int id = checkedIds.get(0);
                float speed = 1.0f;
                if (id == R.id.chip_0_5) speed = 0.5f;
                else if (id == R.id.chip_0_75) speed = 0.75f;
                else if (id == R.id.chip_1_0) speed = 1.0f;
                else if (id == R.id.chip_1_25) speed = 1.25f;
                else if (id == R.id.chip_1_5) speed = 1.5f;
                else if (id == R.id.chip_2_0) speed = 2.0f;
                
                applySpeed(speed);
            }
        });

        btnReset.setOnClickListener(v -> applySpeed(1.0f));
    }

    private void applySpeed(float speed) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setSpeed(speed);
            updateUI(speed);
        }
    }

    private void updateUI(float speed) {
        tvCurrentSpeed.setText(String.format(Locale.US, "%.2fx", speed));
        sliderSpeed.setValue(Math.max(0.25f, Math.min(speed, 3.0f)));
        
        // Chip selection (optional if we want to show which chip matches)
        // For simplicity, we just clear chips if it's a custom value
    }
}
