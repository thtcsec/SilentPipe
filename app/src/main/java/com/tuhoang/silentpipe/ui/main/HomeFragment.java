package com.tuhoang.silentpipe.ui.main;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.tuhoang.silentpipe.R;

public class HomeFragment extends Fragment {

    private EditText urlInput;
    private Button btnAnalyze;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        urlInput = view.findViewById(R.id.url_input);
        btnAnalyze = view.findViewById(R.id.btn_analyze);

        btnAnalyze.setOnClickListener(v -> {
            String url = urlInput.getText().toString().trim();
            if (!url.isEmpty()) {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).loadVideo(url);
                }
            } else {
                Toast.makeText(getContext(), "Please enter a URL", Toast.LENGTH_SHORT).show();
            }
        });

        com.google.android.material.textfield.TextInputLayout inputLayout = view.findViewById(R.id.url_input_layout);
        inputLayout.setEndIconOnClickListener(v -> {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            if (clipboard != null && clipboard.hasPrimaryClip()) {
                android.content.ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
                if (item != null && item.getText() != null) {
                    urlInput.setText(item.getText().toString());
                    Toast.makeText(getContext(), "Pasted from clipboard", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(getContext(), "Clipboard is empty", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
