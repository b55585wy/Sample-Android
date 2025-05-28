package com.tsinghua.sample.utils;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class PlotView extends View {

    private final List<Float> dataBuffer = new ArrayList<>();
    private final int bufferSize = 512;
    private final Paint axisPaint = new Paint();
    private final Paint plotPaint = new Paint();
    private final float axisPadding = 0f;

    public PlotView(Context context) {
        super(context);
        init();
    }

    public PlotView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        axisPaint.setColor(Color.parseColor("#ABD0B1"));
        axisPaint.setStrokeWidth(2);
        axisPaint.setStyle(Paint.Style.STROKE);
        axisPaint.setTextSize(30);

        plotPaint.setColor(Color.parseColor("#A1DD9B"));
        plotPaint.setStrokeWidth(5);
        plotPaint.setStyle(Paint.Style.STROKE);
    }

    public void addValue(float value) {
        if (dataBuffer.size() >= bufferSize) {
            dataBuffer.remove(0);
        }
        dataBuffer.add(value);
        postInvalidate(); // Redraw
    }

    public void clearPlot() {
        dataBuffer.clear();
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float width = getWidth();
        float height = getHeight();
        float axisWidth = width - 2 * axisPadding;
        float axisHeight = height - 2 * axisPadding;

        // Draw bounding box
        canvas.drawRect(axisPadding, axisPadding, width - axisPadding, height - axisPadding, axisPaint);

        // Draw plot
        if (dataBuffer.isEmpty()) return;

        float xStep = axisWidth / (bufferSize - 1);
        float yScale = axisHeight / 4f; // same as JS yScale

        Path path = new Path();
        for (int i = 0; i < dataBuffer.size(); i++) {
            float value = dataBuffer.get(i);
            float x = axisPadding + i * xStep;
            float y = height - axisPadding - (value + 2f) * yScale; // match JS: (value + 2) * scale
            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }

        canvas.drawPath(path, plotPaint);
    }
}
