
import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.tuhoang.silentpipe.R;

public class FindSoundFragment extends Fragment {
    private static final String TAG = "FindSoundFragment";
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);

    private AudioRecord audioRecord;
    private boolean isListening = false;
    private Thread recordingThread;
    
    private View btnListen;
    private TextView tvStatus;
    
    private ActivityResultLauncher<String> requestPermissionLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    startListening();
                } else {
                    Toast.makeText(getContext(), getString(R.string.find_sound_permission_rational), Toast.LENGTH_SHORT).show();
                    tvStatus.setText(getString(R.string.find_sound_permission_denied));
                }
            }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_find_sound, container, false);
    }


    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        btnListen = view.findViewById(R.id.btn_listen); 
        tvStatus = view.findViewById(R.id.tv_status);
        
        if (btnListen != null) {
            btnListen.setOnClickListener(v -> toggleListening());
        }
    }
    
    private void toggleListening() {
        if (isListening) {
            stopListening();
        } else {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                startListening();
            } else {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
            }
        }
    }

    private void startListening() {
        if (isListening) return;
        
        try {
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, BUFFER_SIZE);
            
            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                Toast.makeText(getContext(), getString(R.string.find_sound_init_failed), Toast.LENGTH_SHORT).show();
                return;
            }
            
            audioRecord.startRecording();
            isListening = true;
            if (tvStatus != null) tvStatus.setText(getString(R.string.find_sound_listening));
            
            recordingThread = new Thread(() -> {
                short[] buffer = new short[BUFFER_SIZE];
                while (isListening) {
                    int read = audioRecord.read(buffer, 0, BUFFER_SIZE);
                    if (read > 0) {
                        // Analyze audio logic here (Calculate amplitude for visualizer)
                        double amplitude = 0;
                        for (int i = 0; i < read; i++) {
                            amplitude += Math.abs(buffer[i]);
                        }
                        amplitude /= read;
                        
                        final double finalAmp = amplitude;
                        // Optional: Update UI with amplitude (Visualizer placeholder)
                         getActivity().runOnUiThread(() -> {
                             if (tvStatus != null) tvStatus.setText(getString(R.string.find_sound_listening) + " Amp: " + (int)finalAmp);
                         });
                    }
                }
            });
            recordingThread.start();
            
        } catch (SecurityException e) {
            Toast.makeText(getContext(), getString(R.string.find_sound_security_error, e.getMessage()), Toast.LENGTH_SHORT).show();
            Log.e(TAG, "SecurityException: " + e.getMessage());
        } catch (Exception e) {
             Log.e(TAG, "Error starting recording: " + e.getMessage());
        }
    }

    private void stopListening() {
        if (!isListening) return;
        
        isListening = false;
        try {
            if (recordingThread != null) {
                recordingThread.join();
                recordingThread = null;
            }
            if (audioRecord != null) {
                audioRecord.stop();
                audioRecord.release();
                audioRecord = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping recording: " + e.getMessage());
        }
        
        if (tvStatus != null) tvStatus.setText(getString(R.string.find_sound_hint));
    }

    @Override
    public void onDestroyView() {
        stopListening();
        super.onDestroyView();
    }
}
