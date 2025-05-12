package com.example.handwritingtotext;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import com.google.mlkit.vision.digitalink.Ink;

import java.util.ArrayList;
import java.util.List;

public class DrawingView extends View {
    private static final float STROKE_WIDTH = 10f;

    private Paint paint = new Paint();
    private Path path = new Path();

    private float lastX, lastY;
    private List<Path> paths = new ArrayList<>();

    // For ML Kit Ink Recognition
    private Ink.Stroke.Builder strokeBuilder;
    private Ink.Builder inkBuilder;

    public DrawingView(Context context) {
        super(context);
        init();
    }

    public DrawingView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DrawingView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint.setAntiAlias(true);
        paint.setColor(Color.BLACK);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(STROKE_WIDTH);
    }

    public void setInkBuilder(Ink.Builder inkBuilder) {
        this.inkBuilder = inkBuilder;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Draw all saved paths
        for (Path p : paths) {
            canvas.drawPath(p, paint);
        }

        // Draw current path
        canvas.drawPath(path, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        long t = System.currentTimeMillis();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // Start a new stroke
                path = new Path();
                paths.add(path);
                path.moveTo(x, y);
                lastX = x;
                lastY = y;

                // Create a new stroke builder for the ML Kit ink
                strokeBuilder = Ink.Stroke.builder();
                strokeBuilder.addPoint(Ink.Point.create(x, y, t));
                break;

            case MotionEvent.ACTION_MOVE:
                // Add points to the path
                path.quadTo(lastX, lastY, (x + lastX) / 2, (y + lastY) / 2);
                lastX = x;
                lastY = y;

                // Add points to the ML Kit ink
                strokeBuilder.addPoint(Ink.Point.create(x, y, t));
                break;

            case MotionEvent.ACTION_UP:
                // Finish the path
                path.lineTo(x, y);

                // Add the final point and build the stroke
                strokeBuilder.addPoint(Ink.Point.create(x, y, t));
                if (inkBuilder != null) {
                    inkBuilder.addStroke(strokeBuilder.build());
                }
                break;

            default:
                return false;
        }

        invalidate();
        return true;
    }

    public void clear() {
        path = new Path();
        paths.clear();
        invalidate();
    }
}