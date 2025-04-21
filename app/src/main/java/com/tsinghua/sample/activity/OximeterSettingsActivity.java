package com.tsinghua.sample.activity;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import com.tsinghua.sample.R;

public class OximeterSettingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_oximeter_settings);

        String name = getIntent().getStringExtra("deviceName");
        setTitle(name + " 设置");

        TextView info = findViewById(R.id.settingsInfo);
        info.setText("这里是血氧仪设备的设置页");
    }
}
