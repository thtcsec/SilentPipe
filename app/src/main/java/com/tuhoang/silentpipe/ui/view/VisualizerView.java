package com.tuhoang.silentpipe.ui.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.audiofx.Visualizer;
import android.util.AttributeSet;
import android.view.View;
import android.util.Log;

public class VisualizerView extends View {
    private static final String TAG = "VisualizerView";
    private byte[] mBytes;
    private float[] mPoints;
    private Rect mRect = new Rect();
    private Paint mForePaint = new Paint();
    private int mSpectrumNum = 48;
    private Visualizer mVisualizer;

    public VisualizerView(Context context) {
        super(context);
        init();
    }

    public VisualizerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public VisualizerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mBytes = null;
        mForePaint.setStrokeWidth(8f);
        mForePaint.setAntiAlias(true);
        mForePaint.setColor(Color.WHITE); 
    }

    public void updateVisualizer(byte[] bytes) {
        mBytes = bytes;
        invalidate();
    }
    
    public void setSpectrumNum(int num) {
        mSpectrumNum = num;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mBytes == null) {
            return;
        }

        if (mPoints == null || mPoints.length < mBytes.length * 4) {
            mPoints = new float[mBytes.length * 4];
        }

        mRect.set(0, 0, getWidth(), getHeight());

        // Simple Waveform or FFT? 
        // If Waveform: bytes are signed 8-bit (-128 to 127)
        // If FFT: bytes are magnitude.
        
        // Assuming Waveform for simplicity first, or FFT if we configure Visualizer that way.
        // Let's assume FFT for bars.
        
        // Draw logic for simple bars (using slight hack for waveform data to look like bars if needed, but FFT is better)
        // For now, let's just draw lines connecting points (Waveform) which is safer default
        // Or specific Bar logic.
        
        // Let's attempt simple Waveform first as it's robust. 
        // If user wants "Sóng nhạc" (Music Wave), bars are usually expected.
        
        // Logic for Bars from Waveform data (approximate):
        // Divide width into mSpectrumNum chunks.
        // Average amplitude in each chunk.
        
        int width = getWidth();
        int height = getHeight();
        int baseLine = height / 2;
        
        // Draw standard waveform
        for (int i = 0; i < mBytes.length - 1; i++) {
            mPoints[i * 4] = mRect.width() * i / (float) (mBytes.length - 1);
            mPoints[i * 4 + 1] = mRect.height() / 2 + ((byte) (mBytes[i] + 128)) * (mRect.height() / 2) / 128;
            mPoints[i * 4 + 2] = mRect.width() * (i + 1) / (float) (mBytes.length - 1);
            mPoints[i * 4 + 3] = mRect.height() / 2 + ((byte) (mBytes[i + 1] + 128)) * (mRect.height() / 2) / 128;
        }
        canvas.drawLines(mPoints, mForePaint);
    }
    
    // Helper to link with ExoPlayer/MediaPlayer audio session
    public void link(int audioSessionId) {
        try {
            if (mVisualizer != null) {
                mVisualizer.release();
            }
            mVisualizer = new Visualizer(audioSessionId);
            mVisualizer.setCaptureSize(Visualizer.getCaptureSizeRange()[1]);
            
            Visualizer.OnDataCaptureListener captureListener = new Visualizer.OnDataCaptureListener() {
                @Override
                public void onWaveFormDataCapture(Visualizer visualizer, byte[] bytes, int samplingRate) {
                    updateVisualizer(bytes);
                }

                @Override
                public void onFftDataCapture(Visualizer visualizer, byte[] bytes, int samplingRate) {
                    // updateVisualizer(bytes); 
                }
            };

            mVisualizer.setDataCaptureListener(captureListener, Visualizer.getMaxCaptureRate() / 2, true, false);
            mVisualizer.setEnabled(true);
        } catch (Exception e) {
            Log.e(TAG, "Error linking visualizer", e);
        }
    }
    
    public void release() {
        if (mVisualizer != null) {
            mVisualizer.release();
            mVisualizer = null;
        }
    }
}
