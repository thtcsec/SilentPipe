package com.tuhoang.silentpipe.ui.main;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import com.tuhoang.silentpipe.R;
import com.tuhoang.silentpipe.core.service.PlaybackService;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private PlayerView playerView;
    private MediaController mediaController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // Stub logic for Python init
        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(this));
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        playerView = findViewById(R.id.player_view);
    }

    @Override
    protected void onStart() {
        super.onStart();
        SessionToken sessionToken = new SessionToken(this, new ComponentName(this, PlaybackService.class));
        ListenableFuture<MediaController> controllerFuture =
                new MediaController.Builder(this, sessionToken).buildAsync();
        controllerFuture.addListener(() -> {
            try {
                mediaController = controllerFuture.get();
                playerView.setPlayer(mediaController);
                // Handle intent after controller is ready
                handleIntent(getIntent());
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, MoreExecutors.directExecutor());
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mediaController != null) {
            mediaController.release();
            mediaController = null;
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (mediaController != null) {
            handleIntent(intent);
        }
    }

    private void handleIntent(Intent intent) {
        if (Intent.ACTION_SEND.equals(intent.getAction()) && "text/plain".equals(intent.getType())) {
            String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (sharedText != null) {
                String normalizedUrl = normalizeUrl(sharedText);
                if (normalizedUrl != null) {
                    loadVideo(normalizedUrl);
                }
            }
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction()) && "text/plain".equals(intent.getType())) {
            ArrayList<String> sharedTexts = intent.getStringArrayListExtra(Intent.EXTRA_TEXT);
            if (sharedTexts != null && !sharedTexts.isEmpty()) {
                String normalizedUrl = normalizeUrl(sharedTexts.get(0)); // Handle first for now
                if (normalizedUrl != null) {
                    loadVideo(normalizedUrl);
                }
            }
        }
    }

    private String normalizeUrl(String url) {
        if (url == null) return null;

        // Xử lý YouTube (Shorts, mobile, v.v.)
        if (url.contains("youtu.be/") || url.contains("youtube.com/")) {
            Pattern pattern = Pattern.compile("(?:v=|/v/|/embed/|/shorts/|youtu.be/|/watch\\?v=)([^&\\n\\?#]+)");
            Matcher matcher = pattern.matcher(url);
            if (matcher.find()) {
                return "https://www.youtube.com/watch?v=" + matcher.group(1);
            }
        }

        // Xử lý TikTok
        if (url.contains("tiktok.com")) {
            Pattern pattern = Pattern.compile("https://[\\w-]+\\.tiktok\\.com/[\\w/]+");
            Matcher matcher = pattern.matcher(url);
            if (matcher.find()) {
                return matcher.group(0);
            }
        }

        return url;
    }

    private void loadVideo(String url) {
        new Thread(() -> {
            try {
                Python py = Python.getInstance();
                PyObject module = py.getModule("extractor");
                PyObject result = module.callAttr("extract_info", url);

                if (result.containsKey("url")) {
                    String streamUrl = result.get("url").toString();
                    runOnUiThread(() -> {
                        if (mediaController != null) {
                            MediaItem mediaItem = MediaItem.fromUri(streamUrl);
                            mediaController.setMediaItem(mediaItem);
                            mediaController.prepare();
                            mediaController.play();
                        }
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Lỗi tải video: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
}
