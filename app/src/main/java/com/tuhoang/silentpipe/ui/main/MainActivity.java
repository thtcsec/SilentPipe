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
import androidx.media3.common.MediaItem;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.media3.ui.PlayerView;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import com.tuhoang.silentpipe.R;
import com.tuhoang.silentpipe.core.service.PlaybackService;
import com.tuhoang.silentpipe.data.FavoriteItem;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private PlayerView playerView;
    private MediaController mediaController;
    private String currentStreamUrl;
    private FavoriteItem currentMediaItem;
    private String lastClipboardText = ""; // Track last checked clipboard content


    private boolean ignoreNextClipboardCheck = false; // Prevent Snackbar after Share intent

    @Override
    protected void onResume() {
        super.onResume();
        if (ignoreNextClipboardCheck) {
            ignoreNextClipboardCheck = false;
        } else {
            checkClipboard(true); // Unified method
        }
        
        // Fix for "10s" hardcoded issue: Update UI on resume
        if (playerView != null) {
            android.content.SharedPreferences prefsUI = getSharedPreferences("silentpipe_prefs", android.content.Context.MODE_PRIVATE);
            int skipTime = prefsUI.getInt("pref_skip_time", 10);
            String skipText = skipTime + "s";
            
            android.widget.TextView tvRew = playerView.findViewById(R.id.tv_rew_time);
            if (tvRew != null) tvRew.setText(skipText);
            
            android.widget.TextView tvFwd = playerView.findViewById(R.id.tv_ffwd_time);
            if (tvFwd != null) tvFwd.setText(skipText);
        }

        // Hide/Show FABs based on player state...
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Ensure Python is started (backup for Application preload)
        if (!com.chaquo.python.Python.isStarted()) {
            com.chaquo.python.Python.start(new com.chaquo.python.android.AndroidPlatform(this));
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // Stub logic for Python init

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
        androidx.navigation.NavController navController = androidx.navigation.fragment.NavHostFragment.findNavController(navHostFragment);
        com.google.android.material.bottomnavigation.BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        androidx.navigation.ui.NavigationUI.setupWithNavController(bottomNav, navController);
        
        // Hide Toolbar on Advanced EQ
        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            boolean isAdvancedEQ = destination.getId() == R.id.navigation_advanced_eq;
            findViewById(R.id.appbar_layout).setVisibility(isAdvancedEQ ? android.view.View.GONE : android.view.View.VISIBLE);
            bottomNav.setVisibility(isAdvancedEQ ? android.view.View.GONE : android.view.View.VISIBLE); 
        });

        // Request Notification Permission for Android 13+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        // Setup Toolbar
        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        androidx.navigation.ui.AppBarConfiguration appBarConfiguration = 
                new androidx.navigation.ui.AppBarConfiguration.Builder(R.id.nav_home, R.id.nav_favorites, R.id.nav_settings).build();
        androidx.navigation.ui.NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);

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

        // Make fab_stack draggable
        android.view.View fabStack = findViewById(R.id.fab_stack);
        makeDraggable(fabStack);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
             if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                 requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
             }
        }
    }

    private float dX, dY;
    private float initialTouchX, initialTouchY;
    private static final int MOVE_THRESHOLD = 20;

    private void makeDraggable(android.view.View container) {
        android.view.ViewGroup group = (android.view.ViewGroup) container;
        android.view.View.OnTouchListener dragListener = new android.view.View.OnTouchListener() {
            private boolean isDragging = false;

            @Override
            public boolean onTouch(android.view.View v, android.view.MotionEvent event) {
                switch (event.getAction()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        dX = container.getX() - event.getRawX();
                        dY = container.getY() - event.getRawY();
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        isDragging = false;
                        v.setPressed(true); 
                        return true; // Consume to get MOVE/UP
                    case android.view.MotionEvent.ACTION_MOVE:
                        float deltaX = event.getRawX() - initialTouchX;
                        float deltaY = event.getRawY() - initialTouchY;
                        
                        if (isDragging || Math.abs(deltaX) > MOVE_THRESHOLD || Math.abs(deltaY) > MOVE_THRESHOLD) {
                            if (!isDragging) {
                                isDragging = true;
                                v.setPressed(false); // Cancel ripple if dragging
                            }
                            float newX = event.getRawX() + dX;
                            float newY = event.getRawY() + dY;
                            
                            // Bounds check with safety margin for BottomNav
                            int bottomNavHeight = findViewById(R.id.bottom_nav).getHeight();
                            if (bottomNavHeight == 0) bottomNavHeight = 200; // Fallback
                            
                            newX = Math.max(0, Math.min(newX, ((android.view.View)container.getParent()).getWidth() - container.getWidth()));
                            // Subtract BottomNav height + margin from max Y
                            newY = Math.max(0, Math.min(newY, ((android.view.View)container.getParent()).getHeight() - container.getHeight() - bottomNavHeight - 50));
                            
                            container.setX(newX);
                            container.setY(newY);
                        }
                        return true;
                    case android.view.MotionEvent.ACTION_UP:
                        v.setPressed(false);
                        if (!isDragging) {
                            v.performClick();
                        }
                        return true;
                    case android.view.MotionEvent.ACTION_CANCEL:
                        v.setPressed(false);
                        return true;
                }
                return false;
            }
        };

        // Apply to all children of the stack
        for (int i = 0; i < group.getChildCount(); i++) {
            group.getChildAt(i).setOnTouchListener(dragListener);
        }
    }

    private void togglePlayer(boolean show) {
        android.view.View fabStack = findViewById(R.id.fab_stack);
        android.view.View[] fabChildren = {
            findViewById(R.id.fab_favorite),
            findViewById(R.id.fab_speed),
            findViewById(R.id.fab_download),
            findViewById(R.id.fab_minimize)
        };
        
        android.view.View restoreBtn = findViewById(R.id.fab_restore);

        if (show) {
            restoreBtn.setVisibility(android.view.View.GONE);
            playerView.setVisibility(android.view.View.VISIBLE);
            playerView.animate().alpha(1f).setDuration(200).start();
            for (android.view.View view : fabChildren) {
                view.setVisibility(android.view.View.VISIBLE);
            }
        } else {
            playerView.animate().alpha(0f).setDuration(200).withEndAction(() -> playerView.setVisibility(android.view.View.GONE)).start();
            for (android.view.View view : fabChildren) {
                view.setVisibility(android.view.View.GONE);
            }
            restoreBtn.setVisibility(android.view.View.VISIBLE);
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
            checkClipboard(false);
            return;
        }
        
        ignoreNextClipboardCheck = true; // Intent handling should suppress auto-check

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

    private void checkClipboard(boolean fromResume) {
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
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
        android.content.SharedPreferences prefs = getSharedPreferences("silentpipe_prefs", android.content.Context.MODE_PRIVATE);
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
                 // Non-intrusive Snackbar, anchored above BottomNav
                 com.google.android.material.snackbar.Snackbar.make(
                     findViewById(R.id.main), 
                     "Link detected! Play?", 
                     com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                     .setAnchorView(R.id.bottom_nav) // Fix covering BottomNav
                     .setAction("PLAY", v -> loadVideo(urlToPlay))
                     .show();
             } else {
                 // Explicit intent -> Show confirmation
                 showUrlInputDialog(urlToPlay);
             }
        } else if (!fromResume) {
            // Explicit intent but no URL
             showUrlInputDialog("");
        }
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

    public void showEqualizer() {
        PlaybackService service = PlaybackService.instance;
        if (service != null) {
            com.tuhoang.silentpipe.ui.main.EqualizerFragment eqFragment = new com.tuhoang.silentpipe.ui.main.EqualizerFragment();
            eqFragment.setPlaybackService(service);
            eqFragment.show(getSupportFragmentManager(), "Equalizer");
        } else {
            Toast.makeText(this, "Service not ready. Play something first!", Toast.LENGTH_SHORT).show();
        }
    }

    public void loadVideo(String url) {
        ignoreNextClipboardCheck = true; // Fix Share/Clipboard conflict: Don't prompt again in onResume
        runOnUiThread(() -> {
            Toast.makeText(this, "Đang xử lý link...", Toast.LENGTH_SHORT).show();
            playerView.setVisibility(android.view.View.VISIBLE);
            togglePlayer(true); // Ensure FABs and player are restored if minimized
        });
        new Thread(() -> {
            try {
                Python py = Python.getInstance();
                PyObject module = py.getModule("media_extractor");
                
                android.content.SharedPreferences prefs = getSharedPreferences("silentpipe_prefs", android.content.Context.MODE_PRIVATE);
                boolean preferHq = prefs.getBoolean("pref_hq_audio", false);
                
                PyObject result = module.callAttr("extract_info", url, preferHq);

                if (result == null) {
                    runOnUiThread(() -> Toast.makeText(this, "Lỗi: Không nhận được phản hồi từ Python!", Toast.LENGTH_LONG).show());
                    return;
                }

                // Use Python dict get method directly to avoid Java Map generic issues
                PyObject urlObj = result.callAttr("get", "url");
                PyObject titleObj = result.callAttr("get", "title");
                PyObject errorObj = result.callAttr("get", "error");
                PyObject sourceObj = result.callAttr("get", "source_info");

                String streamUrl = (urlObj != null) ? urlObj.toString() : null;
                String title = (titleObj != null) ? titleObj.toString() : "Unknown Title";
                String sourceInfo = (sourceObj != null) ? sourceObj.toString() : "Source: YouTube";

                if (streamUrl != null && !streamUrl.isEmpty() && !streamUrl.equals("None")) {
                    final String finalUrl = streamUrl;
                    currentStreamUrl = finalUrl;
                    final String finalTitle = title;
                    final String finalSourceInfo = sourceInfo;
                    
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
                                Toast.makeText(this, "Playing: " + finalTitle, Toast.LENGTH_SHORT).show();
                                
                                // Update Title and Source in Custom Controller
                                android.widget.TextView tvTitle = playerView.findViewById(R.id.tv_player_title);
                                if (tvTitle != null) tvTitle.setText(finalTitle);
                                
                                android.widget.TextView tvSource = playerView.findViewById(R.id.tv_source_info);
                                if (tvSource != null) tvSource.setText(finalSourceInfo);
                                
                                // Update Skip Time Text
                                android.content.SharedPreferences prefsUI = getSharedPreferences("silentpipe_prefs", android.content.Context.MODE_PRIVATE);
                                int skipTime = prefsUI.getInt("pref_skip_time", 10);
                                String skipText = skipTime + "s";
                                
                                android.widget.TextView tvRew = playerView.findViewById(R.id.tv_rew_time);
                                if (tvRew != null) tvRew.setText(skipText);
                                
                                android.widget.TextView tvFwd = playerView.findViewById(R.id.tv_ffwd_time);
                                if (tvFwd != null) tvFwd.setText(skipText);
                                
                                // Manual Button Binding (Fix for unresponsive buttons)
                                android.view.View btnPlay = playerView.findViewById(androidx.media3.ui.R.id.exo_play);
                                android.view.View btnPause = playerView.findViewById(androidx.media3.ui.R.id.exo_pause);
                                
                                if (btnPlay != null) {
                                    btnPlay.setOnClickListener(v -> {
                                        if (mediaController != null) {
                                            android.util.Log.d("SilentPipe", "Manual Play Clicked");
                                            if (mediaController.getPlaybackState() == androidx.media3.common.Player.STATE_ENDED) {
                                                mediaController.seekTo(0);
                                                mediaController.play();
                                            } else {
                                                mediaController.play();
                                            }
                                        } else {
                                             Toast.makeText(this, "Controller null", Toast.LENGTH_SHORT).show();
                                        }
                                    });
                                }
                                
                                if (btnPause != null) {
                                    btnPause.setOnClickListener(v -> {
                                        android.util.Log.d("SilentPipe", "Manual Pause Clicked");
                                        if (mediaController != null) mediaController.pause();
                                    });
                                }
                                
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
