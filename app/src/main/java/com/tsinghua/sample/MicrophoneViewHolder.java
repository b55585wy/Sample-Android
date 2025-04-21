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

    public MicrophoneViewHolder(View itemView) {
        super(itemView);
        deviceName = itemView.findViewById(R.id.deviceName);
        startBtn = itemView.findViewById(R.id.startBtn);
        settingsBtn = itemView.findViewById(R.id.settingsBtn);
    }
}
