package com.tuhoang.silentpipe.ui.main;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
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

import com.tuhoang.silentpipe.ui.manager.ClipboardHelper;
import com.tuhoang.silentpipe.ui.manager.DownloadHelper;
import com.tuhoang.silentpipe.ui.manager.NavigationHelper;
import com.tuhoang.silentpipe.ui.view.VisualizerView;


import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity implements ClipboardHelper.Callback {
    private static final String TAG = "MainActivity";
    private PlayerView playerView;
    private MediaController mediaController;
    private String currentStreamUrl;
    private FavoriteItem currentMediaItem;

    private VisualizerView visualizerView;
    
    // Helpers
    private ClipboardHelper clipboardHelper;
    private DownloadHelper downloadHelper;
    private NavigationHelper navigationHelper;

    private boolean ignoreNextClipboardCheck = false;
    private final java.util.concurrent.ExecutorService executorService = java.util.concurrent.Executors.newFixedThreadPool(4);

    @Override
    protected void onResume() {
        super.onResume();
        if (ignoreNextClipboardCheck) {
            ignoreNextClipboardCheck = false;
        } else {
            if (clipboardHelper != null) clipboardHelper.checkClipboard(true);
        }
        
        updateSkipTimeUI();
    }
    
    private void updateSkipTimeUI() {
        if (playerView != null) {
            android.content.SharedPreferences prefsUI = getSharedPreferences("silentpipe_prefs", Context.MODE_PRIVATE);
            int skipTime = prefsUI.getInt("pref_skip_time", 10);
            String skipText = skipTime + "s";
            
            android.widget.TextView tvRew = playerView.findViewById(R.id.tv_rew_time);
            if (tvRew != null) tvRew.setText(skipText);
            
            android.widget.TextView tvFwd = playerView.findViewById(R.id.tv_ffwd_time);
            if (tvFwd != null) tvFwd.setText(skipText);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (currentMediaItem != null) {
            // Apply preferences automatically when returning to the app
            boolean isMaximized = playerView != null && playerView.getVisibility() == View.VISIBLE;
            togglePlayer(isMaximized);
            
            if (visualizerView != null) {
                SharedPreferences vizPrefs = getSharedPreferences("silentpipe_prefs", Context.MODE_PRIVATE);
                int vizStyleIndex = vizPrefs.getInt("pref_visualizer_style", 0);
                visualizerView.setStyle(vizStyleIndex == 1 ? 
                    com.tuhoang.silentpipe.ui.view.VisualizerView.VisualizerStyle.BARS : 
                    com.tuhoang.silentpipe.ui.view.VisualizerView.VisualizerStyle.WAVEFORM);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(this));
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        playerView = findViewById(R.id.player_view);

        // Initialize Helpers
        clipboardHelper = new ClipboardHelper(this, this);
        downloadHelper = new DownloadHelper(this);
        navigationHelper = new NavigationHelper(this);

        visualizerView = findViewById(R.id.visualizer_view);
        
        // Permissions for Visualizer
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 102);
        }

        // Setup Buttons
        setupButtons();

        // Permissions
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        // Handle Back Press
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                androidx.drawerlayout.widget.DrawerLayout drawer = findViewById(R.id.drawer_layout);
                if (drawer != null && drawer.isDrawerOpen(androidx.core.view.GravityCompat.END)) {
                    drawer.closeDrawer(androidx.core.view.GravityCompat.END);
                } else if (playerView.getVisibility() == View.VISIBLE && findViewById(R.id.fab_minimize).getVisibility() == View.VISIBLE) {
                    togglePlayer(false); // Minimize player on back
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);
                }
            }
        });
    }

    private void setupButtons() {
        findViewById(R.id.fab_favorite).setOnClickListener(v -> addToFavorites());
        findViewById(R.id.fab_speed).setOnClickListener(v -> showSpeedDialog());
        findViewById(R.id.fab_download).setOnClickListener(v -> downloadMedia());

        findViewById(R.id.fab_minimize).setOnClickListener(v -> togglePlayer(false));
        findViewById(R.id.fab_restore).setOnClickListener(v -> togglePlayer(true));

        // Bottom Player Buttons
        findViewById(R.id.layout_bottom_player).setOnClickListener(v -> togglePlayer(true));
        findViewById(R.id.btn_bottom_player_close).setOnClickListener(v -> {
            togglePlayer(true); // First restore to stop? Or just stop?
            // User likely wants to stop playback and close player.
            if (mediaController != null) mediaController.pause(); // Or stop()
             findViewById(R.id.layout_bottom_player).setVisibility(View.GONE);
             findViewById(R.id.fab_restore).setVisibility(View.VISIBLE); // Reset to default state? 
             // Actually, if we close, we probably want to hide everything.
             togglePlayer(false); // Ensure minimized
             findViewById(R.id.layout_bottom_player).setVisibility(View.GONE);
             findViewById(R.id.fab_restore).setVisibility(View.VISIBLE); // Show floating button as fallback?
             // Better: just hide bottom player and show floating button, or stop completely?
             // Let's just pause and hide bottom player, reverting to floating button state effectively "minimized but paused".
             // Or maybe "Close" means "Exit Player". 
             // Let's go with: Pause and Switch to Floating Button (Paused state).
             if (mediaController != null) mediaController.pause();
             togglePlayer(false); // This will re-evaluate prefs. 
             // If pref is Bottom Player, it will show Bottom Player again.
             // We need a way to say "Hidden". 
             // Let's just Pause.
        });
        
        findViewById(R.id.btn_bottom_player_play).setOnClickListener(v -> {
            if (mediaController != null) {
                if (mediaController.isPlaying()) mediaController.pause();
                else mediaController.play();
                updateBottomPlayerUI();
            }
        });

        if (visualizerView != null) {
            // Apply Visualizer Style from Prefs
            SharedPreferences vizPrefs = getSharedPreferences("silentpipe_prefs", Context.MODE_PRIVATE);
            int vizStyleIndex = vizPrefs.getInt("pref_visualizer_style", 0);
            visualizerView.setStyle(vizStyleIndex == 1 ? 
                com.tuhoang.silentpipe.ui.view.VisualizerView.VisualizerStyle.BARS : 
                com.tuhoang.silentpipe.ui.view.VisualizerView.VisualizerStyle.WAVEFORM);
            
            // Set Color (Fallback to white while diagnosing theme attr)
            visualizerView.setColor(android.graphics.Color.WHITE);
        }

        makeDraggable(findViewById(R.id.fab_stack));

        TextView tvFooter = findViewById(R.id.tv_version_footer);
        if (tvFooter != null) {
            try {
                String version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
                tvFooter.setText(getString(R.string.footer_version, version));
            } catch (Exception e) {
                tvFooter.setText(getString(R.string.footer_version, "1.0"));
            }
        }
    }

    // Draggable Logic (Keep here or move later if complex)
    private float dX, dY;
    private float initialTouchX, initialTouchY;
    private static final int MOVE_THRESHOLD = 20;

    private void makeDraggable(View container) {
        ViewGroup group = (ViewGroup) container;
        View.OnTouchListener dragListener = new View.OnTouchListener() {
            private boolean isDragging = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        dX = container.getTranslationX() - event.getRawX();
                        dY = container.getTranslationY() - event.getRawY();
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        isDragging = false;
                        v.setPressed(true); 
                        return true;
                    case android.view.MotionEvent.ACTION_MOVE:
                        float deltaX = event.getRawX() - initialTouchX;
                        float deltaY = event.getRawY() - initialTouchY;
                        
                        if (isDragging || Math.abs(deltaX) > MOVE_THRESHOLD || Math.abs(deltaY) > MOVE_THRESHOLD) {
                            if (!isDragging) {
                                isDragging = true;
                                v.setPressed(false);
                            }
                            float newTranslationX = event.getRawX() + dX;
                            float newTranslationY = event.getRawY() + dY;
                            
                            container.setTranslationX(newTranslationX);
                            container.setTranslationY(newTranslationY);
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
        container.setOnTouchListener(dragListener);
        for (int i = 0; i < group.getChildCount(); i++) {
            group.getChildAt(i).setOnTouchListener(dragListener);
        }
    }

    private void togglePlayer(boolean show) {
        View fabStack = findViewById(R.id.fab_stack);
        View[] fabChildren = {
            findViewById(R.id.fab_favorite),
            findViewById(R.id.fab_speed),
            findViewById(R.id.fab_download),
            findViewById(R.id.fab_minimize)
        };
        
        View restoreBtn = findViewById(R.id.fab_restore);
        View nowPlayingCard = findViewById(R.id.card_now_playing);
        TextView tvNowPlaying = findViewById(R.id.tv_now_playing);
        
        View bottomPlayer = findViewById(R.id.layout_bottom_player);
        SharedPreferences prefs = getSharedPreferences("silentpipe_prefs", Context.MODE_PRIVATE);
        boolean useBottomPlayer = prefs.getBoolean("pref_use_bottom_player", false);
        boolean showVisualizer = prefs.getBoolean("pref_show_visualizer", false);

        if (show) {
            restoreBtn.setVisibility(View.GONE);
            if (nowPlayingCard != null) nowPlayingCard.setVisibility(View.GONE);
            if (bottomPlayer != null) bottomPlayer.setVisibility(View.GONE);
            
            playerView.setVisibility(View.VISIBLE);
            playerView.animate().alpha(1f).setDuration(200).start();
            if (visualizerView != null && showVisualizer) {
                 visualizerView.setVisibility(View.VISIBLE);
                 visualizerView.animate().alpha(1f).setDuration(200).start();
                 com.tuhoang.silentpipe.core.service.PlaybackService service = com.tuhoang.silentpipe.core.service.PlaybackService.instance;
                 if (service != null && service.getAudioEffectManager() != null) {
                     int sessionId = service.getAudioEffectManager().getAudioSessionId();
                     if (sessionId != 0) {
                         try {
                             visualizerView.link(sessionId);
                         } catch (Exception e) {}
                     }
                 }
            }
            
            for (View view : fabChildren) view.setVisibility(View.VISIBLE);
        } else {
            playerView.animate().alpha(0f).setDuration(200).withEndAction(() -> playerView.setVisibility(View.GONE)).start();
            if (visualizerView != null) {
                 visualizerView.animate().alpha(0f).setDuration(200).withEndAction(() -> visualizerView.setVisibility(View.GONE)).start();
            }
            
            for (View view : fabChildren) view.setVisibility(View.GONE);
            
            if (useBottomPlayer) {
                restoreBtn.setVisibility(View.GONE);
                if (nowPlayingCard != null) nowPlayingCard.setVisibility(View.GONE);
                
                if (bottomPlayer != null) {
                    bottomPlayer.setVisibility(View.VISIBLE);
                    bottomPlayer.setTranslationY(100f);
                    bottomPlayer.setAlpha(0f);
                    bottomPlayer.animate()
                            .translationY(0f)
                            .alpha(1f)
                            .setDuration(250)
                            .setInterpolator(new android.view.animation.DecelerateInterpolator())
                            .start();
                    updateBottomPlayerUI();
                }
            } else {
                if (bottomPlayer != null) bottomPlayer.setVisibility(View.GONE);
                restoreBtn.setVisibility(View.VISIBLE);
                
                if (nowPlayingCard != null && currentMediaItem != null) {
                    nowPlayingCard.setVisibility(View.VISIBLE);
                    nowPlayingCard.setAlpha(0f);
                    nowPlayingCard.animate().alpha(1f).setDuration(250).start();
                    if (tvNowPlaying != null) {
                        tvNowPlaying.setSelected(true); // Trigger marquee
                    }
                }
            }
        }
    }

    private void updateBottomPlayerUI() {
        if (currentMediaItem == null) return;
        
        TextView tvTitle = findViewById(R.id.tv_bottom_player_title);
        if (tvTitle != null) {
            tvTitle.setText(currentMediaItem.title);
            tvTitle.setSelected(true);
        }
        
        android.widget.ImageButton btnPlay = findViewById(R.id.btn_bottom_player_play);
        if (btnPlay != null && mediaController != null) {
            btnPlay.setImageResource(mediaController.isPlaying() ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
        }
    }

    private void showSpeedDialog() {
        new SpeedPickerFragment().show(getSupportFragmentManager(), "speed_picker");
    }

    public float getCurrentSpeed() {
        if (mediaController != null) {
            return mediaController.getPlaybackParameters().speed;
        }
        return 1.0f;
    }

    public void setSpeed(float speed) {
        if (mediaController != null) {
            mediaController.setPlaybackParameters(new androidx.media3.common.PlaybackParameters(speed));
            Toast.makeText(this, getString(R.string.speed_picker_title) + ": " + speed + "x", Toast.LENGTH_SHORT).show();
        }
    }

    private void showCustomSpeedDialog() {
        EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setHint(getString(R.string.dialog_custom_speed_hint));

        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_custom_speed_title))
            .setView(input)
            .setPositiveButton(getString(R.string.dialog_set), (dialog, which) -> {
                try {
                    float speed = Float.parseFloat(input.getText().toString());
                    setSpeed(speed);
                } catch (NumberFormatException e) {
                    Toast.makeText(this, getString(R.string.toast_invalid_url), Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .show();
    }

    private void toggleFavorite() {
        if (currentMediaItem != null) {
             executorService.execute(() -> {
                com.tuhoang.silentpipe.data.AppDatabase db = com.tuhoang.silentpipe.data.AppDatabase.getDatabase(getApplicationContext());
                
                com.google.android.material.floatingactionbutton.FloatingActionButton fab = findViewById(R.id.fab_favorite);
                boolean isFav = db.favoriteDao().isFavorite(currentMediaItem.url);
                
                if (isFav) {
                    db.favoriteDao().deleteByUrl(currentMediaItem.url);
                    runOnUiThread(() -> {
                        animateFab(fab);
                        fab.setImageResource(android.R.drawable.btn_star_big_off);
                        Toast.makeText(this, getString(R.string.toast_fav_removed), Toast.LENGTH_SHORT).show();
                    });
                } else {
                    db.favoriteDao().insert(currentMediaItem);
                    runOnUiThread(() -> {
                        animateFab(fab);
                        fab.setImageResource(android.R.drawable.btn_star_big_on);
                        Toast.makeText(this, getString(R.string.toast_fav_added), Toast.LENGTH_SHORT).show();
                    });
                }
            });
        }
    }
    
    private void animateFab(View view) {
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
                handleIntent(getIntent());
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Error initializing MediaController", e);
            }
            
            // Sync UI state
            if (mediaController != null && 
               (mediaController.getPlaybackState() == androidx.media3.common.Player.STATE_READY || 
                mediaController.getPlaybackState() == androidx.media3.common.Player.STATE_BUFFERING ||
                mediaController.getPlaybackState() == androidx.media3.common.Player.STATE_ENDED ||
                mediaController.isPlaying())) {
                    
                 // Restore metadata if possible (optional, but UI sync is key)
                mediaController.getMediaMetadata();
                CharSequence title = mediaController.getMediaMetadata().title;
                if (title != null) {
                    TextView tvTitle = playerView.findViewById(R.id.tv_player_title);
                    if (tvTitle != null) tvTitle.setText(title);

                    TextView tvNowPlaying = findViewById(R.id.tv_now_playing);
                    if (tvNowPlaying != null) {
                        tvNowPlaying.setText(title);
                        tvNowPlaying.setSelected(true);
                    }
                    updateBottomPlayerUI(); // Sync bottom player
                }

                // Fix: Only auto-show (Mini Player) if playing and nothing is visible.
                     // Do NOT force maximized player (togglePlayer(true)) on resume.
                 boolean isFullPlayerVisible = playerView.getVisibility() == View.VISIBLE;
                 View bottomPlayer = findViewById(R.id.layout_bottom_player);
                 boolean isBottomPlayerVisible = bottomPlayer != null && bottomPlayer.getVisibility() == View.VISIBLE;
                 View fabRestore = findViewById(R.id.fab_restore);
                 boolean isFabVisible = fabRestore != null && fabRestore.getVisibility() == View.VISIBLE;
                 
                 if (!isFullPlayerVisible && !isBottomPlayerVisible && !isFabVisible) {
                     if (mediaController.isPlaying()) {
                         togglePlayer(false); // Default to Mini Player if playing and hidden
                     }
                 }

                 // Setup Visualizer
                 SharedPreferences prefs = getSharedPreferences("silentpipe_prefs", Context.MODE_PRIVATE);
                 boolean showVisualizer = prefs.getBoolean("pref_show_visualizer", false);
                 boolean showVideo = prefs.getBoolean("pref_show_video", false);
                 
                 if (showVisualizer && !showVideo) {
                     if (visualizerView != null) {
                          visualizerView.setVisibility(View.VISIBLE);
                          PlaybackService service = PlaybackService.instance;
                          if (service != null && service.getAudioEffectManager() != null) {
                              int sessionId = service.getAudioEffectManager().getAudioSessionId();
                              if (sessionId != 0) {
                                  try {
                                      visualizerView.link(sessionId);
                                  } catch (Exception e) {
                                      Log.e(TAG, "Failed to link visualizer", e);
                                  }
                              }
                          }
                     }
                 } else {
                     if (visualizerView != null) visualizerView.setVisibility(View.GONE);
                 }
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
    protected void onDestroy() {
        super.onDestroy();
        if (visualizerView != null) {
            visualizerView.release();
        }
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (mediaController != null) {
            handleIntent(intent);
        }
    }

    private void handleIntent(Intent intent) {
        if ("com.tuhoang.silentpipe.ACTION_PLAY_CLIPBOARD".equals(intent.getAction())) {
            clipboardHelper.checkClipboard(false);
            setIntent(new Intent()); // Consume intent
            return;
        }
        
        ignoreNextClipboardCheck = true; // Intent handling should suppress auto-check

        if (Intent.ACTION_SEND.equals(intent.getAction()) && "text/plain".equals(intent.getType())) {
            String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (sharedText != null && clipboardHelper != null) {
                String normalizedUrl = clipboardHelper.normalizeUrl(sharedText);
                if (normalizedUrl != null) {
                    loadVideo(normalizedUrl);
                    intent.removeExtra(Intent.EXTRA_TEXT); // Consume extra to prevent re-play on resume
                    intent.setAction(""); 
                }
            }
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction()) && "text/plain".equals(intent.getType())) {
            ArrayList<String> sharedTexts = intent.getStringArrayListExtra(Intent.EXTRA_TEXT);
            if (sharedTexts != null && !sharedTexts.isEmpty() && clipboardHelper != null) {
                String normalizedUrl = clipboardHelper.normalizeUrl(sharedTexts.get(0));
                if (normalizedUrl != null) {
                    loadVideo(normalizedUrl);
                    intent.removeExtra(Intent.EXTRA_TEXT); // Consume extra
                     intent.setAction("");
                }
            }
        }
    }
    
    // ClipboardHelper Callback
    @Override
    public void onUrlFound(String url) {
        if (url != null && !url.isEmpty()) {
            loadVideo(url);
        } else {
            showUrlInputDialog("");
        }
    }

    @Override
    public void onCheckComplete() {
        // Optional: Do something after check
    }

    private void showUrlInputDialog(String prefill) {
        EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        input.setText(prefill);
        if (!prefill.isEmpty()) input.setSelection(prefill.length());
        
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_url_title))
            .setMessage(getString(R.string.dialog_url_message))
            .setView(input)
            .setPositiveButton(getString(R.string.dialog_play), (dialog, which) -> {
                String url = input.getText().toString().trim();
                String normalized = (clipboardHelper != null) ? clipboardHelper.normalizeUrl(url) : url;
                if (normalized != null) {
                    loadVideo(normalized);
                } else {
                    Toast.makeText(this, getString(R.string.toast_invalid_url), Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton(getString(R.string.dialog_cancel), (dialog, which) -> dialog.cancel())
            .setCancelable(true)
            .show();
    }

    private void downloadMedia() {
        if (downloadHelper != null) {
            downloadHelper.downloadMedia(currentStreamUrl, currentMediaItem);
        }
    }

    public void showEqualizer() {
        new com.tuhoang.silentpipe.ui.main.EqualizerFragment().show(getSupportFragmentManager(), "equalizer");
    }

    public void loadVideo(String url) {
        loadVideo(url, null);
    }

    public void loadVideo(String url, com.tuhoang.silentpipe.data.FavoriteItem cachedItem) {
        ignoreNextClipboardCheck = true; 
        runOnUiThread(() -> {
            if (cachedItem != null) {
                playerView.setVisibility(View.VISIBLE);
                togglePlayer(true);
                
                android.widget.TextView tvTitle = playerView.findViewById(R.id.tv_player_title);
                if (tvTitle != null) tvTitle.setText(cachedItem.title);

                View cardNowPlaying = findViewById(R.id.card_now_playing);
                android.widget.TextView tvNowPlaying = findViewById(R.id.tv_now_playing);
                if (tvNowPlaying != null) {
                    tvNowPlaying.setText(cachedItem.title);
                    tvNowPlaying.setSelected(true);
                    if (cardNowPlaying != null) cardNowPlaying.setVisibility(View.VISIBLE);
                }
                
                Toast.makeText(this, getString(R.string.toast_processing_link), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, getString(R.string.toast_processing_link), Toast.LENGTH_SHORT).show();
                playerView.setVisibility(View.VISIBLE);
                togglePlayer(true); 
            }
        });
        executorService.execute(() -> {
            try {
                Python py = Python.getInstance();
                PyObject module = py.getModule("media_extractor");
                
                SharedPreferences prefs = getSharedPreferences("silentpipe_prefs", Context.MODE_PRIVATE);
                boolean preferHq = prefs.getBoolean("pref_hq_audio", false);
                boolean showVideo = prefs.getBoolean("pref_show_video", false);
                String cookies = prefs.getString("pref_youtube_cookies", "");
                
                PyObject result = module.callAttr("extract_info", url, preferHq, cookies, showVideo);

                if (result == null) {
                    runOnUiThread(() -> Toast.makeText(this, getString(R.string.toast_error_player, getString(R.string.unknown_error)), Toast.LENGTH_LONG).show());
                    return;
                }

                PyObject urlObj = result.callAttr("get", "url");
                PyObject titleObj = result.callAttr("get", "title");
                PyObject errorObj = result.callAttr("get", "error");
                PyObject sourceObj = result.callAttr("get", "source_info");

                String streamUrl = (urlObj != null) ? urlObj.toString() : null;
                String title = (titleObj != null) ? titleObj.toString() : getString(R.string.unknown_title);
                String sourceInfo = (sourceObj != null) ? sourceObj.toString() : getString(R.string.source_youtube);

                if (streamUrl != null && !streamUrl.isEmpty() && !streamUrl.equals("None")) {
                    final String finalUrl = streamUrl;
                    currentStreamUrl = finalUrl;
                    final String finalTitle = title;
                    final String finalSourceInfo = sourceInfo;
                    
                    PyObject uploaderObj = result.callAttr("get", "uploader");
                    final String finalUploader = (uploaderObj != null) ? uploaderObj.toString() : getString(R.string.unknown_uploader);
                    final long duration = 0; 

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
                                Toast.makeText(this, getString(R.string.toast_playing, finalTitle), Toast.LENGTH_SHORT).show();
                                // Update Title and Source in Custom Controller
                                android.widget.TextView tvTitle = playerView.findViewById(R.id.tv_player_title);
                                if (tvTitle != null) tvTitle.setText(finalTitle);

                                // Update Now Playing Floating Text
                                // Update Now Playing Floating Text
                                View cardNowPlaying = findViewById(R.id.card_now_playing);
                                android.widget.TextView tvNowPlaying = findViewById(R.id.tv_now_playing);
                                if (tvNowPlaying != null) {
                                    tvNowPlaying.setText(finalTitle);
                                    tvNowPlaying.setSelected(true);
                                    if (cardNowPlaying != null && findViewById(R.id.fab_restore).getVisibility() == View.VISIBLE) {
                                        cardNowPlaying.setVisibility(View.VISIBLE);
                                    }
                                }
                                updateBottomPlayerUI(); // Sync bottom player
                                
                                android.widget.TextView tvSource = playerView.findViewById(R.id.tv_source_info);
                                if (tvSource != null) tvSource.setText(finalSourceInfo);
                                
                                updateSkipTimeUI(); // Re-sync UI text
                                
                                // Manual Button Binding
                                View btnPlay = playerView.findViewById(androidx.media3.ui.R.id.exo_play);
                                View btnPause = playerView.findViewById(androidx.media3.ui.R.id.exo_pause);
                                
                                if (btnPlay != null) {
                                    btnPlay.setOnClickListener(v -> {
                                        if (mediaController != null) {
                                            if (mediaController.getPlaybackState() == androidx.media3.common.Player.STATE_ENDED) {
                                                mediaController.seekTo(0);
                                                mediaController.play();
                                            } else {
                                                mediaController.play();
                                            }
                                        }
                                    });
                                }
                                
                                if (btnPause != null) {
                                    btnPause.setOnClickListener(v -> {
                                        if (mediaController != null) mediaController.pause();
                                    });
                                }
                                
                                findViewById(R.id.fab_favorite).setVisibility(View.VISIBLE);
                                findViewById(R.id.fab_speed).setVisibility(View.VISIBLE);
                                findViewById(R.id.fab_minimize).setVisibility(View.VISIBLE);
                                findViewById(R.id.fab_download).setVisibility(View.VISIBLE);
                                findViewById(R.id.fab_restore).setVisibility(View.GONE);
                                
                                currentMediaItem = new com.tuhoang.silentpipe.data.FavoriteItem(
                                    finalTitle, url, "", finalUploader, duration, System.currentTimeMillis()
                                );
                                
                                // Check status to update Icon
                                executorService.execute(() -> {
                                    com.tuhoang.silentpipe.data.AppDatabase db = com.tuhoang.silentpipe.data.AppDatabase.getDatabase(getApplicationContext());
                                    boolean isFav = db.favoriteDao().isFavorite(url);
                                    runOnUiThread(() -> {
                                        com.google.android.material.floatingactionbutton.FloatingActionButton fab = findViewById(R.id.fab_favorite);
                                        fab.setImageResource(isFav ? android.R.drawable.btn_star_big_on : android.R.drawable.btn_star_big_off);
                                    });
                                });
                                
                            } catch (Exception e) {
                                com.tuhoang.silentpipe.core.manager.ErrorLogManager.getInstance(this).logError("Player", e.getMessage());
                                Toast.makeText(this, getString(R.string.toast_error_player, e.getMessage()), Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                } else if (errorObj != null && !errorObj.toString().equals("None")) {
                    String error = errorObj.toString();
                    com.tuhoang.silentpipe.core.manager.ErrorLogManager.getInstance(this).logError("Python", error);
                    runOnUiThread(() -> Toast.makeText(this, getString(R.string.toast_error_python, error), Toast.LENGTH_LONG).show());
                } else {
                    String rawResult = result.toString();
                    runOnUiThread(() -> Toast.makeText(this, getString(R.string.toast_critical_error, rawResult), Toast.LENGTH_LONG).show());
                }
            } catch (Throwable e) {
                Log.e(TAG, "Critical Error in loadVideo", e);
                com.tuhoang.silentpipe.core.manager.ErrorLogManager.getInstance(this).logError("MainActivity", "Critical: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(this, getString(R.string.toast_critical_error, e.getMessage()), Toast.LENGTH_LONG).show());
            }
        });
    }
}
