package com.tsinghua.sample;

import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.tsinghua.sample.utils.PlotView;

public class CameraViewHolder extends RecyclerView.ViewHolder {
    public TextView deviceName;
    public ImageView expandArrow;
    public LinearLayout headerLayout;
    public LinearLayout contentLayout;
    public ImageButton settingsBtn;

    // 摄像头选择按钮
    public MaterialButton btnFrontCamera;
    public MaterialButton btnBackCamera;
    public MaterialButton btnDualCamera;

    // 预览容器
    public FrameLayout previewContainer;
    public LinearLayout previewPlaceholder;
    public SurfaceView cameraSurfaceView;      // 主预览（前置或后置单摄）
    public SurfaceView pipSurfaceView;         // 画中画预览（双摄时的后置）
    public MaterialCardView pipContainer;      // 画中画容器
    private TextView placeholderText;

    // 面部推理相关
    public PlotView plotViewHR;                // 心率波形图
    public TextView textHeartRate;             // 心率显示

    private boolean isExpanded = true;
    private boolean isRecording = false;

    // 当前选择的摄像头模式: 0=前置, 1=后置, 2=双摄
    private int currentMode = 0;

    public CameraViewHolder(View itemView) {
        super(itemView);

        deviceName = itemView.findViewById(R.id.deviceName);
        expandArrow = itemView.findViewById(R.id.expandArrow);
        headerLayout = itemView.findViewById(R.id.headerLayout);
        contentLayout = itemView.findViewById(R.id.contentLayout);
        settingsBtn = itemView.findViewById(R.id.settingsBtn);

        btnFrontCamera = itemView.findViewById(R.id.btnFrontCamera);
        btnBackCamera = itemView.findViewById(R.id.btnBackCamera);
        btnDualCamera = itemView.findViewById(R.id.btnDualCamera);

        previewContainer = itemView.findViewById(R.id.previewContainer);
        previewPlaceholder = itemView.findViewById(R.id.previewPlaceholder);
        cameraSurfaceView = itemView.findViewById(R.id.cameraSurfaceView);
        pipSurfaceView = itemView.findViewById(R.id.pipSurfaceView);
        pipContainer = itemView.findViewById(R.id.pipContainer);
        placeholderText = itemView.findViewById(R.id.placeholderText);

        // 面部推理相关UI
        plotViewHR = itemView.findViewById(R.id.plotViewHR);
        textHeartRate = itemView.findViewById(R.id.textHeartRate);

        // 点击头部折叠/展开
        headerLayout.setOnClickListener(v -> toggleExpand());

        // 默认选中前置录制
        updateCameraSelection(0);
    }

    private void toggleExpand() {
        isExpanded = !isExpanded;
        contentLayout.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
        expandArrow.setRotation(isExpanded ? 180 : 0);
    }

    public void updateCameraSelection(int mode) {
        currentMode = mode;

        // 更新按钮样式
        btnFrontCamera.setBackgroundTintList(
            itemView.getContext().getColorStateList(mode == 0 ? R.color.my_primary : android.R.color.transparent));
        btnBackCamera.setBackgroundTintList(
            itemView.getContext().getColorStateList(mode == 1 ? R.color.my_primary : android.R.color.transparent));
        btnDualCamera.setBackgroundTintList(
            itemView.getContext().getColorStateList(mode == 2 ? R.color.my_primary : android.R.color.transparent));

        // 更新文字颜色
        btnFrontCamera.setTextColor(itemView.getContext().getColor(mode == 0 ? android.R.color.white : R.color.my_primary));
        btnBackCamera.setTextColor(itemView.getContext().getColor(mode == 1 ? android.R.color.white : R.color.my_primary));
        btnDualCamera.setTextColor(itemView.getContext().getColor(mode == 2 ? android.R.color.white : R.color.my_primary));
    }

    public int getCurrentMode() {
        return currentMode;
    }

    /**
     * 显示摄像头预览（录制开始时调用）
     * 隐藏占位符，露出底层的SurfaceView
     */
    public void showPreview() {
        isRecording = true;
        previewPlaceholder.setVisibility(View.GONE);
    }

    /**
     * 隐藏摄像头预览，显示占位符（录制停止时调用）
     * 显示占位符，覆盖底层的SurfaceView
     */
    public void hidePreview() {
        isRecording = false;
        previewPlaceholder.setVisibility(View.VISIBLE);
    }

    /**
     * 更新占位符提示文字
     */
    public void setPlaceholderText(String text) {
        if (placeholderText != null) {
            placeholderText.setText(text);
        }
    }

    /**
     * 获取用于预览的 SurfaceView（主预览）
     */
    public SurfaceView getSurfaceView() {
        return cameraSurfaceView;
    }

    /**
     * 获取画中画 SurfaceView（双摄时的后置预览）
     */
    public SurfaceView getPipSurfaceView() {
        return pipSurfaceView;
    }

    /**
     * 显示画中画容器（双摄模式）
     */
    public void showPipPreview() {
        if (pipContainer != null) {
            pipContainer.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 隐藏画中画容器
     */
    public void hidePipPreview() {
        if (pipContainer != null) {
            pipContainer.setVisibility(View.GONE);
        }
    }

    public boolean isRecording() {
        return isRecording;
    }

    public void setExpanded(boolean expanded) {
        this.isExpanded = expanded;
        contentLayout.setVisibility(expanded ? View.VISIBLE : View.GONE);
        expandArrow.setRotation(expanded ? 180 : 0);
    }

    /**
     * 显示心率波形图（面部推理模式）
     */
    public void showHeartRatePlot() {
        if (plotViewHR != null) {
            plotViewHR.setVisibility(View.VISIBLE);
        }
        if (textHeartRate != null) {
            textHeartRate.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 隐藏心率波形图
     */
    public void hideHeartRatePlot() {
        if (plotViewHR != null) {
            plotViewHR.setVisibility(View.GONE);
        }
        if (textHeartRate != null) {
            textHeartRate.setVisibility(View.GONE);
        }
    }

    /**
     * 获取心率波形PlotView
     */
    public PlotView getPlotViewHR() {
        return plotViewHR;
    }

    /**
     * 更新心率显示
     */
    public void updateHeartRate(int heartRate) {
        if (textHeartRate != null) {
            textHeartRate.setText("心率: " + heartRate + " BPM");
        }
    }
}
