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

        // Setup Navigation
        androidx.navigation.fragment.NavHostFragment navHostFragment =
                (androidx.navigation.fragment.NavHostFragment) getSupportFragmentManager()
                        .findFragmentById(R.id.nav_host_fragment);
        androidx.navigation.NavController navController = navHostFragment.getNavController();
        com.google.android.material.bottomnavigation.BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        androidx.navigation.ui.NavigationUI.setupWithNavController(bottomNav, navController);

        // Setup Buttons
        com.google.android.material.floatingactionbutton.FloatingActionButton fabFavorite = findViewById(R.id.fab_favorite);
        fabFavorite.setOnClickListener(v -> addToFavorites());
        
        com.google.android.material.floatingactionbutton.FloatingActionButton fabSpeed = findViewById(R.id.fab_speed);
        fabSpeed.setOnClickListener(v -> showSpeedDialog());

        com.google.android.material.floatingactionbutton.FloatingActionButton fabDownload = findViewById(R.id.fab_download);
        fabDownload.setOnClickListener(v -> downloadMedia());

        // Setup Player Toggle Buttons
        com.google.android.material.floatingactionbutton.FloatingActionButton fabMinimize = findViewById(R.id.fab_minimize);
        com.google.android.material.floatingactionbutton.FloatingActionButton fabRestore = findViewById(R.id.fab_restore);

        fabMinimize.setOnClickListener(v -> togglePlayer(false));
        fabRestore.setOnClickListener(v -> togglePlayer(true));
    }

    private void togglePlayer(boolean show) {
        android.view.View[] viewsToAnimate = {
            playerView,
            findViewById(R.id.fab_favorite),
            findViewById(R.id.fab_speed),
            findViewById(R.id.fab_download),
            findViewById(R.id.fab_minimize)
        };
        
        android.view.View restoreBtn = findViewById(R.id.fab_restore);

        if (show) {
            restoreBtn.setVisibility(android.view.View.GONE);
            for (android.view.View view : viewsToAnimate) {
                view.setAlpha(0f);
                view.setVisibility(android.view.View.VISIBLE);
                view.animate().alpha(1f).setDuration(200).start();
            }
        } else {
             for (android.view.View view : viewsToAnimate) {
                view.animate().alpha(0f).setDuration(200).withEndAction(() -> view.setVisibility(android.view.View.GONE)).start();
            }
            restoreBtn.setAlpha(0f);
            restoreBtn.setVisibility(android.view.View.VISIBLE);
            restoreBtn.animate().alpha(1f).setDuration(200).start();
        }
    }

    private void showSpeedDialog() {
        if (mediaController == null) return;
        
        String[] speeds = {"0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "2.0x", "Custom"};
        float[] values = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f};

        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Playback Speed")
            .setItems(speeds, (dialog, which) -> {
                if (which < values.length) {
                    setSpeed(values[which]);
                } else {
                    showCustomSpeedDialog();
                }
            })
            .show();
    }

    private void showCustomSpeedDialog() {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setHint("Enter speed (e.g. 1.1)");

        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Custom Speed")
            .setView(input)
            .setPositiveButton("Set", (dialog, which) -> {
                try {
                    float speed = Float.parseFloat(input.getText().toString());
                    setSpeed(speed);
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "Invalid number", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void setSpeed(float speed) {
        if (mediaController != null) {
            mediaController.setPlaybackParameters(new androidx.media3.common.PlaybackParameters(speed));
            Toast.makeText(this, "Speed: " + speed + "x", Toast.LENGTH_SHORT).show();
        }
    }

    private com.tuhoang.silentpipe.data.FavoriteItem currentMediaItem;

    private void toggleFavorite() {
        if (currentMediaItem != null) {
             new Thread(() -> {
                com.tuhoang.silentpipe.data.AppDatabase db = androidx.room.Room.databaseBuilder(
                        getApplicationContext(),
                        com.tuhoang.silentpipe.data.AppDatabase.class, "silentpipe-db").build();
                
                com.google.android.material.floatingactionbutton.FloatingActionButton fab = findViewById(R.id.fab_favorite);
                boolean isFav = db.favoriteDao().isFavorite(currentMediaItem.url);
                
                if (isFav) {
                    db.favoriteDao().deleteByUrl(currentMediaItem.url);
                    runOnUiThread(() -> {
                        animateFab(fab);
                        fab.setImageResource(android.R.drawable.btn_star_big_off);
                        Toast.makeText(this, "Removed from Favorites", Toast.LENGTH_SHORT).show();
                    });
                } else {
                    db.favoriteDao().insert(currentMediaItem);
                    runOnUiThread(() -> {
                        animateFab(fab);
                        fab.setImageResource(android.R.drawable.btn_star_big_on);
                        Toast.makeText(this, "Added to Favorites", Toast.LENGTH_SHORT).show();
                    });
                }
            }).start();
        }
    }
    
    private void animateFab(android.view.View view) {
        view.animate().scaleX(1.2f).scaleY(1.2f).setDuration(150).withEndAction(() -> {
            view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
        }).start();
    }

    private void addToFavorites() {
         toggleFavorite();
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
        if ("com.tuhoang.silentpipe.ACTION_PLAY_CLIPBOARD".equals(intent.getAction())) {
            checkClipboardAndPlay();
            return;
        }

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

    private void checkClipboardAndPlay() {
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        String prefillText = "";
        
        if (clipboard != null && clipboard.hasPrimaryClip()) {
            android.content.ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
            if (item != null && item.getText() != null) {
                String text = item.getText().toString();
                String url = normalizeUrl(text);
                if (url != null) {
                    prefillText = url;
                }
            }
        }

        showUrlInputDialog(prefillText);
    }

    private void showUrlInputDialog(String prefill) {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        input.setText(prefill);
        if (!prefill.isEmpty()) {
            input.setSelection(prefill.length());
        }
        
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Enter Media URL")
            .setMessage("Enter a YouTube or TikTok link to play (Background supported)")
            .setView(input)
            .setPositiveButton("Play", (dialog, which) -> {
                String url = input.getText().toString().trim();
                String normalized = normalizeUrl(url);
                if (normalized != null) {
                    loadVideo(normalized);
                } else {
                    Toast.makeText(this, "Invalid URL", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancel", (dialog, which) -> dialog.cancel())
            .setCancelable(true)
            .show();
    }

    private String normalizeUrl(String url) {
        if (url == null) return null;

        // Spotify
        if (url.contains("spotify.com")) {
             return url; // Pass directly to Python
        }

        // Xử lý YouTube (Shorts, mobile, v.v.)
        Pattern ytPattern = Pattern.compile("(?:v=|/v/|/embed/|/shorts/|youtu.be/|/watch\\?v=)([^&\\n\\?#]+)");
        Matcher ytMatcher = ytPattern.matcher(url);
        if (ytMatcher.find()) {
            return "https://www.youtube.com/watch?v=" + ytMatcher.group(1);
        }

        // Regex: (https://(www|vt|vm).tiktok.com/...)
        // Use a simpler regex to capture the full URL until whitespace
        if (url.contains("tiktok.com")) {
            Pattern tiktokPattern = Pattern.compile("https://(?:www\\.|vt\\.|vm\\.)?tiktok\\.com/[^\\s]+");
            Matcher tiktokMatcher = tiktokPattern.matcher(url);
            if (tiktokMatcher.find()) {
                return tiktokMatcher.group(0);
            }
        }

        // Nếu không khớp pattern nào nhưng có vẻ là URL
        if (url.startsWith("http")) {
            return url;
        }

        return null; // Không phải link hợp lệ
    }

    private void downloadMedia() {
        if (currentStreamUrl == null) {
             Toast.makeText(this, "No media to download", Toast.LENGTH_SHORT).show();
             return;
        }

        try {
            android.app.DownloadManager downloadManager = (android.app.DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            android.net.Uri uri = android.net.Uri.parse(currentStreamUrl);
            
            android.app.DownloadManager.Request request = new android.app.DownloadManager.Request(uri);
            request.setTitle("SilentPipe: " + (currentMediaItem != null ? currentMediaItem.title : "Media"));
            request.setDescription("Downloading video/audio...");
            request.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_MUSIC, "SilentPipe/" + (currentMediaItem != null ? currentMediaItem.title.replaceAll("[^a-zA-Z0-9.-]", "_") : "download") + ".mp4");
            
            downloadManager.enqueue(request);
            Toast.makeText(this, "Downloading to Music/SilentPipe...", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Download failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String currentStreamUrl;

    public void loadVideo(String url) {
        runOnUiThread(() -> {
            Toast.makeText(this, "Đang xử lý link...", Toast.LENGTH_SHORT).show();
            playerView.setVisibility(android.view.View.VISIBLE);
        });
        new Thread(() -> {
            try {
                Python py = Python.getInstance();
                PyObject module = py.getModule("media_extractor");
                PyObject result = module.callAttr("extract_info", url);

                if (result == null) {
                    runOnUiThread(() -> Toast.makeText(this, "Lỗi: Không nhận được phản hồi từ Python!", Toast.LENGTH_LONG).show());
                    return;
                }

                // Use Python dict get method directly to avoid Java Map generic issues
                PyObject urlObj = result.callAttr("get", "url");
                PyObject titleObj = result.callAttr("get", "title");
                PyObject errorObj = result.callAttr("get", "error");

                String streamUrl = (urlObj != null) ? urlObj.toString() : null;
                String title = (titleObj != null) ? titleObj.toString() : "Unknown Title";

                if (streamUrl != null && !streamUrl.isEmpty() && !streamUrl.equals("None")) {
                    final String finalUrl = streamUrl;
                    currentStreamUrl = finalUrl;
                    final String finalTitle = title;
                    PyObject uploaderObj = result.callAttr("get", "uploader");
                    final String finalUploader = (uploaderObj != null) ? uploaderObj.toString() : "Unknown Uploader";
                    final long duration = 0; // Simplified for now

                    runOnUiThread(() -> {
                        if (mediaController != null) {
                            try {
                                MediaItem mediaItem = new MediaItem.Builder()
                                        .setUri(finalUrl)
                                        .setMediaMetadata(new androidx.media3.common.MediaMetadata.Builder()
                                                .setTitle(finalTitle)
                                                .setArtist(finalUploader)
                                                .build())
                                        .build();
                                
                                mediaController.setMediaItem(mediaItem);
                                mediaController.prepare();
                                mediaController.play();
                                Toast.makeText(this, "Đang phát: " + finalTitle, Toast.LENGTH_SHORT).show();
                                
                                findViewById(R.id.fab_favorite).setVisibility(android.view.View.VISIBLE);
                                findViewById(R.id.fab_speed).setVisibility(android.view.View.VISIBLE);
                                findViewById(R.id.fab_minimize).setVisibility(android.view.View.VISIBLE);
                                findViewById(R.id.fab_download).setVisibility(android.view.View.VISIBLE);
                                findViewById(R.id.fab_restore).setVisibility(android.view.View.GONE);
                                
                                currentMediaItem = new com.tuhoang.silentpipe.data.FavoriteItem(
                                    finalTitle, url, "", finalUploader, duration, System.currentTimeMillis()
                                );
                                
                                // Check status to update Icon
                                new Thread(() -> {
                                    com.tuhoang.silentpipe.data.AppDatabase db = androidx.room.Room.databaseBuilder(
                                            getApplicationContext(),
                                            com.tuhoang.silentpipe.data.AppDatabase.class, "silentpipe-db").build();
                                    boolean isFav = db.favoriteDao().isFavorite(url);
                                    runOnUiThread(() -> {
                                        com.google.android.material.floatingactionbutton.FloatingActionButton fab = findViewById(R.id.fab_favorite);
                                        fab.setImageResource(isFav ? android.R.drawable.btn_star_big_on : android.R.drawable.btn_star_big_off);
                                    });
                                }).start();
                                
                            } catch (Exception e) {
                                Toast.makeText(this, "Lỗi Player: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            }
                        } else {
                             Toast.makeText(this, "Lỗi: MediaController chưa sẵn sàng!", Toast.LENGTH_LONG).show();
                        }
                    });
                } else if (errorObj != null && !errorObj.toString().equals("None")) {
                    String error = errorObj.toString();
                    runOnUiThread(() -> Toast.makeText(this, "Lỗi Python: " + error, Toast.LENGTH_LONG).show());
                } else {
                    String rawResult = result.toString();
                    runOnUiThread(() -> Toast.makeText(this, "Lỗi KD (Invalid): " + rawResult, Toast.LENGTH_LONG).show());
                    android.util.Log.e("SilentPipe", "Invalid Python Result: " + rawResult);
                }
            } catch (Throwable e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "Lỗi nghiêm trọng: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }
}
