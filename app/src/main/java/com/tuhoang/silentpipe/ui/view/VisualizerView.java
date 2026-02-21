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

    public enum VisualizerStyle { WAVEFORM, BARS }
    private VisualizerStyle mStyle = VisualizerStyle.WAVEFORM;

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
        mForePaint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void updateVisualizer(byte[] bytes) {
        mBytes = bytes;
        invalidate();
    }
    
    public void setSpectrumNum(int num) {
        mSpectrumNum = num;
    }

    public void setStyle(VisualizerStyle style) {
        this.mStyle = style;
        invalidate();
    }

    public void setColor(int color) {
        mForePaint.setColor(color);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mBytes == null) return;

        if (mStyle == VisualizerStyle.BARS) {
            drawBars(canvas);
        } else {
            drawWaveform(canvas);
        }
    }

    private void drawWaveform(Canvas canvas) {
        if (mPoints == null || mPoints.length < mBytes.length * 4) {
            mPoints = new float[mBytes.length * 4];
        }
        for (int i = 0; i < mBytes.length - 1; i++) {
            mPoints[i * 4] = getWidth() * i / (float) (mBytes.length - 1);
            mPoints[i * 4 + 1] = getHeight() / 2f + ((byte) (mBytes[i] + 128)) * (getHeight() / 2f) / 128f;
            mPoints[i * 4 + 2] = getWidth() * (i + 1) / (float) (mBytes.length - 1);
            mPoints[i * 4 + 3] = getHeight() / 2f + ((byte) (mBytes[i + 1] + 128)) * (getHeight() / 2f) / 128f;
        }
        canvas.drawLines(mPoints, mForePaint);
    }

    private void drawBars(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        float barWidth = (float) width / mSpectrumNum;
        
        for (int i = 0; i < mSpectrumNum; i++) {
            int byteIndex = (int) ((float) i / mSpectrumNum * mBytes.length);
            float amplitude = ((byte) (mBytes[byteIndex] + 128)) / 128f; // 0.0 to 2.0
            float barHeight = Math.abs(amplitude - 1.0f) * height; 
            
            float x = i * barWidth + barWidth / 2f;
            canvas.drawLine(x, height / 2f - barHeight / 2f, x, height / 2f + barHeight / 2f, mForePaint);
        }
    }
    
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
