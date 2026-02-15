package com.tuhoang.silentpipe.ui.manager;

import android.content.Context;
import android.content.ClipboardManager;
import android.content.SharedPreferences;
import com.google.android.material.snackbar.Snackbar;
import com.tuhoang.silentpipe.R;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClipboardHelper {
    private final android.app.Activity activity;
    private final Callback callback;
    private String lastClipboardText = "";

    public interface Callback {
        void onUrlFound(String url);
        void onCheckComplete();
    }

    public ClipboardHelper(android.app.Activity activity, Callback callback) {
        this.activity = activity;
        this.callback = callback;
    }

    public void checkClipboard(boolean fromResume) {
        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        String foundUrl = null;
        String clipText = "";
        
        if (clipboard != null && clipboard.hasPrimaryClip()) {
            android.content.ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
            if (item != null && item.getText() != null) {
                clipText = item.getText().toString();
                String url = normalizeUrl(clipText);
                if (url != null) {
                    foundUrl = url;
                }
            }
        }
        
        // Persist and ignore if same text and is automatic check
        SharedPreferences prefs = activity.getSharedPreferences("silentpipe_prefs", Context.MODE_PRIVATE);
        String savedLastClip = prefs.getString("last_clipboard_text", "");
        
        if (fromResume && clipText.equals(savedLastClip)) {
            return;
        }
        
        // Update persistent state
        prefs.edit().putString("last_clipboard_text", clipText).apply();
        lastClipboardText = clipText;

        if (foundUrl != null) {
             final String urlToPlay = foundUrl;
             if (fromResume) {
                 // Non-intrusive Snackbar
                 Snackbar.make(
                     activity.findViewById(R.id.main), 
                     "Link detected! Play?", 
                     Snackbar.LENGTH_LONG)
                     .setAnchorView(R.id.bottom_nav) 
                     .setAction("PLAY", v -> callback.onUrlFound(urlToPlay))
                     .show();
             } else {
                 // Explicit intent will be handled by caller usually, but helper acts as detector
                 callback.onUrlFound(urlToPlay);
             }
        } else if (!fromResume) {
             callback.onUrlFound(""); // Signal explicit check failed or empty
        }
        callback.onCheckComplete();
    }

    public String normalizeUrl(String url) {
        if (url == null) return null;

        if (url.contains("spotify.com")) return url;

        Pattern ytPattern = Pattern.compile("(?:v=|/v/|/embed/|/shorts/|youtu.be/|/watch\\?v=)([^&\\n\\?#]+)");
        Matcher ytMatcher = ytPattern.matcher(url);
        if (ytMatcher.find()) {
            return "https://www.youtube.com/watch?v=" + ytMatcher.group(1);
        }

        if (url.contains("tiktok.com")) {
            Pattern tiktokPattern = Pattern.compile("https://(?:www\\.|vt\\.|vm\\.)?tiktok\\.com/[^\\s]+");
            Matcher tiktokMatcher = tiktokPattern.matcher(url);
            if (tiktokMatcher.find()) {
                return tiktokMatcher.group(0);
            }
        }

        if (url.startsWith("http")) return url;

        return null;
    }
}
