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

    private android.graphics.Path mPath = new android.graphics.Path();
    private android.graphics.LinearGradient mGradient;
    private int mColorTheme = 0;

    private void init() {
        mBytes = null;
        mForePaint.setStrokeWidth(12f);
        mForePaint.setAntiAlias(true);
        mForePaint.setStyle(Paint.Style.STROKE);
        mForePaint.setStrokeCap(Paint.Cap.ROUND);
        mForePaint.setStrokeJoin(Paint.Join.ROUND);
    }

    public void setColorTheme(int theme) {
        if (this.mColorTheme != theme) {
            this.mColorTheme = theme;
            updateGradient();
            invalidate();
        }
    }

    private void updateGradient() {
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;
        
        int[] colors;
        switch (mColorTheme) {
            case 1: // Ocean (Cyan - Blue)
                colors = new int[]{Color.parseColor("#00C9FF"), Color.parseColor("#92FE9D"), Color.parseColor("#1CB5E0"), Color.parseColor("#000046")};
                break;
            case 2: // Sunset (Red - Yellow)
                colors = new int[]{Color.parseColor("#FF512F"), Color.parseColor("#F09819"), Color.parseColor("#FFD200"), Color.parseColor("#F7971E")};
                break;
            case 3: // Forest (Green - Lime)
                colors = new int[]{Color.parseColor("#11998e"), Color.parseColor("#38ef7d"), Color.parseColor("#DCE35B"), Color.parseColor("#45B649")};
                break;
            case 0: // Cyberpunk
            default:
                colors = new int[]{Color.parseColor("#8E2DE2"), Color.parseColor("#4A00E0"), Color.parseColor("#ff00cc"), Color.parseColor("#333399")};
                break;
        }
        
        mGradient = new android.graphics.LinearGradient(
                0, 0, w, h,
                colors,
                null,
                android.graphics.Shader.TileMode.CLAMP
        );
        mForePaint.setShader(mGradient);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateGradient();
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
        // Adjust paint style based on visualizer mode
        if (style == VisualizerStyle.WAVEFORM) {
            mForePaint.setStyle(Paint.Style.STROKE);
            mForePaint.setStrokeWidth(6f);
        } else {
            mForePaint.setStyle(Paint.Style.FILL);
        }
        invalidate();
    }

    public void setColor(int color) {
        // Gradient overrides color, but keep method for compatibility if needed later
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
        mPath.reset();
        int width = getWidth();
        int height = getHeight();
        float centerY = height / 2f;
        
        mPath.moveTo(0, centerY);
        
        // Use a smoothing factor to create a bezier-like wave
        int step = Math.max(1, mBytes.length / 64);
        
        for (int i = 0; i < mBytes.length - step; i += step) {
            float x1 = width * i / (float) (mBytes.length - 1);
            float y1 = centerY + ((byte) (mBytes[i] + 128)) * (centerY) / 128f;
            
            float x2 = width * (i + step) / (float) (mBytes.length - 1);
            float y2 = centerY + ((byte) (mBytes[i + step] + 128)) * (centerY) / 128f;
            
            float cx = (x1 + x2) / 2f;
            float cy = (y1 + y2) / 2f;
            
            mPath.quadTo(x1, y1, cx, cy);
        }
        
        mPath.lineTo(width, centerY);
        canvas.drawPath(mPath, mForePaint);
    }

    private void drawBars(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        
        // Dynamically adjust bar width and spacing
        float totalBarWidth = (float) width / mSpectrumNum;
        float barWidth = totalBarWidth * 0.6f; 
        float spacing = totalBarWidth * 0.4f;
        
        for (int i = 0; i < mSpectrumNum; i++) {
            // Read amplitude data
            int byteIndex = (int) ((float) i / mSpectrumNum * mBytes.length);
            float amplitude = ((byte) (mBytes[byteIndex] + 128)) / 128f; 
            
            // Exaggerate amplitude for better visual effect and smooth out minimum height
            float rawHeight = Math.abs(amplitude - 1.0f) * height * 1.5f;
            float barHeight = Math.max(rawHeight, 10f); 
            
            float x = i * totalBarWidth + spacing / 2f;
            
            float top = height / 2f - barHeight / 2f;
            float bottom = height / 2f + barHeight / 2f;
            
            // Draw a rounded rectangle for a premium DJ-deck look
            canvas.drawRoundRect(x, top, x + barWidth, bottom, barWidth / 2f, barWidth / 2f, mForePaint);
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
