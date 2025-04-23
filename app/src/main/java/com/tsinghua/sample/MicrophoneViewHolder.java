package com.tsinghua.sample;

import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

public class MicrophoneViewHolder extends RecyclerView.ViewHolder {
    public TextView deviceName;
    public Button startBtn;
    public ImageButton settingsBtn;
    public View infoLayout;     // 用于显示音频数据的预览区域


    public MicrophoneViewHolder(View itemView) {
        super(itemView);
        deviceName = itemView.findViewById(R.id.deviceName);
        startBtn = itemView.findViewById(R.id.startBtn);
        settingsBtn = itemView.findViewById(R.id.settingsBtn);
    }

//    // 切换展开与收起的音频数据显示
//    public void toggleInfo() {
//        if (infoVisible) {
//            infoLayout.animate()
//                    .translationY(100)
//                    .alpha(0f)
//                    .setDuration(200)
//                    .withEndAction(() -> infoLayout.setVisibility(View.GONE))
//                    .start();
//        } else {
//            infoLayout.setAlpha(0f);
//            infoLayout.setTranslationY(100);
//            infoLayout.setVisibility(View.VISIBLE);
//            infoLayout.animate()
//                    .translationY(0)
//                    .alpha(1f)
//                    .setDuration(200)
//                    .start();
//        }
//        infoVisible = !infoVisible;
//    }

    // 更新音频数据

}
