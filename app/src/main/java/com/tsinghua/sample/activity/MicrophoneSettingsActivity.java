package com.tsinghua.sample.activity;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import com.tsinghua.sample.R;

public class MicrophoneSettingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_microphone_settings);

        String name = getIntent().getStringExtra("deviceName");
        setTitle(name + " 设置");

        TextView info = findViewById(R.id.settingsInfo);
        info.setText("这里是麦克风设备的设置页");
    }
}
