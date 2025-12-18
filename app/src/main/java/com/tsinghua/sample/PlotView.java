package com.tsinghua.sample;

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

    private final List<Integer> dataBuffer = new ArrayList<>();
    private final int bufferSize = 512;  // 默认最大数据点数
    private final Paint axisPaint = new Paint();
    private final Paint plotPaint = new Paint();
    private float axisPadding = 10f;  // 调整坐标轴的填充量

    // 自定义颜色
    private int axisColor = Color.parseColor("#ABD0B1"); // 坐标轴颜色
    private int plotColor = Color.parseColor("#00FF00"); // 曲线颜色

    // Y轴的最大值和最小值，基于当前可见的最新数据来计算
    private int maxY = Integer.MIN_VALUE;
    private int minY = Integer.MAX_VALUE;

    // 增加一个阈值参数来检测数据变化的大小
    private final int MIN_Y_RANGE = 10;

    // 性能优化：批量更新控制
    private int pendingUpdates = 0;
    private static final int BATCH_UPDATE_THRESHOLD = 5; // 累积5个数据点后才重绘
    private long lastInvalidateTime = 0;
    private static final long MIN_INVALIDATE_INTERVAL_MS = 33; // 最快30fps刷新

    public PlotView(Context context) {
        super(context);
        init();
    }

    public PlotView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        axisPaint.setColor(axisColor);
        axisPaint.setStrokeWidth(2);
        axisPaint.setStyle(Paint.Style.STROKE);
        axisPaint.setTextSize(30);

        plotPaint.setColor(plotColor);
        plotPaint.setStrokeWidth(3);
        plotPaint.setStyle(Paint.Style.STROKE);
    }

    // 自定义坐标轴和绘制曲线的颜色
    public void setAxisColor(int color) {
        axisColor = color;
        axisPaint.setColor(axisColor);
        invalidate();  // 重新绘制
    }

    public void setPlotColor(int color) {
        plotColor = color;
        plotPaint.setColor(plotColor);
        invalidate();  // 重新绘制
    }

    // 添加数据点并更新 Y 轴的最大最小值
    public void addValue(int value) {
        // 如果当前系列的缓存超过最大大小，则移除最旧的点
        if (dataBuffer.size() >= bufferSize) {
            dataBuffer.remove(0);
        }

        // 添加新的数据点
        dataBuffer.add(value);

        // 增量更新Y轴范围（避免每次遍历整个数组）
        if (value > maxY) maxY = value;
        if (value < minY) minY = value;

        // 动态调整Y轴范围，当数据变化较小的时候，扩大Y轴范围
        adjustYScale();

        // 性能优化：批量重绘，避免每个数据点都触发UI刷新
        pendingUpdates++;
        long now = System.currentTimeMillis();
        if (pendingUpdates >= BATCH_UPDATE_THRESHOLD ||
            (now - lastInvalidateTime) >= MIN_INVALIDATE_INTERVAL_MS) {
            pendingUpdates = 0;
            lastInvalidateTime = now;
            postInvalidate(); // 请求重新绘制
        }
    }

    /**
     * 强制刷新图表（用于录制结束时确保最后的数据显示）
     */
    public void forceRefresh() {
        if (pendingUpdates > 0) {
            pendingUpdates = 0;
            lastInvalidateTime = System.currentTimeMillis();
            postInvalidate();
        }
    }

    // 清除所有数据并重置 Y 轴范围
    public void clearPlot() {
        dataBuffer.clear();
        maxY = Integer.MIN_VALUE;
        minY = Integer.MAX_VALUE;
        pendingUpdates = 0;
        postInvalidate(); // 请求重新绘制
    }

    // 动态更新 Y 轴的最大最小值（仅在clearPlot后或数据移除时需要完整重算）
    private void recalculateYRange() {
        if (dataBuffer.isEmpty()) {
            maxY = Integer.MIN_VALUE;
            minY = Integer.MAX_VALUE;
            return;
        }

        int localMaxY = Integer.MIN_VALUE;
        int localMinY = Integer.MAX_VALUE;

        // 只计算当前缓冲区内的数据
        for (int value : dataBuffer) {
            localMaxY = Math.max(localMaxY, value);
            localMinY = Math.min(localMinY, value);
        }

        maxY = localMaxY;
        minY = localMinY;
    }

    // 动态调整 Y 轴的范围，若数据变化较小，适当扩大Y轴的范围
    private void adjustYScale() {
        if ((maxY - minY) < MIN_Y_RANGE) {
            int range = MIN_Y_RANGE - (maxY - minY);
            maxY += range;
            minY -= range;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float width = getWidth();
        float height = getHeight();
        float axisWidth = width - 2 * axisPadding;
        float axisHeight = height - 2 * axisPadding;

        // 绘制坐标轴
        canvas.drawRect(axisPadding, axisPadding, width - axisPadding, height - axisPadding, axisPaint);

        // 如果没有数据，直接返回
        if (dataBuffer.isEmpty()) return;

        // 动态调整 Y 轴比例
        float yScale = axisHeight / (maxY - minY);

        // 创建绘制路径
        Path path = new Path();

        // 绘制曲线
        for (int i = 0; i < dataBuffer.size(); i++) {
            int value = dataBuffer.get(i);
            float x = axisPadding + i * (axisWidth / (bufferSize - 1));
            float y = height - axisPadding - (value - minY) * yScale;  // 根据最大最小值调整 Y 轴
            if (i == 0) {
                path.moveTo(x, y);  // 起始点
            } else {
                path.lineTo(x, y);  // 连接其他点
            }
        }

        // 绘制曲线
        canvas.drawPath(path, plotPaint);
    }
}
