package com.tuhoang.silentpipe.ui.main;

import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputLayout;
import com.tuhoang.silentpipe.R;

public class QuickPlayActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Use a dialog theme if possible via manifest, but ensure content view is set
        setContentView(R.layout.activity_quick_play);

        EditText urlInput = findViewById(R.id.url_input);
        TextInputLayout layout = findViewById(R.id.url_input_layout);
        Button btnPlay = findViewById(R.id.btn_play);
        Button btnCancel = findViewById(R.id.btn_cancel);

        // Auto-paste if clipboard has URL
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null && clipboard.hasPrimaryClip()) {
            CharSequence text = clipboard.getPrimaryClip().getItemAt(0).getText();
            if (text != null && text.toString().startsWith("http")) {
                urlInput.setText(text);
                urlInput.setSelection(text.length());
            }
        }

        layout.setEndIconOnClickListener(v -> {
            if (clipboard != null && clipboard.hasPrimaryClip()) {
                CharSequence text = clipboard.getPrimaryClip().getItemAt(0).getText();
                if (text != null) {
                    urlInput.setText(text);
                }
            }
        });

        btnPlay.setOnClickListener(v -> {
            String url = urlInput.getText().toString().trim();
            if (!url.isEmpty()) {
                // Launch MainActivity with the URL intent
                Intent intent = new Intent(this, MainActivity.class);
                intent.setAction(Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_TEXT, url);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                finish();
            } else {
                Toast.makeText(this, "Please enter a URL", Toast.LENGTH_SHORT).show();
            }
        });

        btnCancel.setOnClickListener(v -> finish());
    }
}
