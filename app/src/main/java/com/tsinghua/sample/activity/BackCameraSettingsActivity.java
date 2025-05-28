package com.tsinghua.sample.activity;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Switch;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import com.tsinghua.sample.R;

public class BackCameraSettingsActivity extends AppCompatActivity {
    private static final String PREFS_NAME = "SettingsPrefs";
    private static final String KEY_BACK_FLASH = "flashlight";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_back_camera_settings);

        String name = getIntent().getStringExtra("deviceName");
        setTitle(name + " 设置");

        TextView info = findViewById(R.id.settingsInfo);
        info.setText("这里是后置录制设备的设置页");

        Switch flashToggle = findViewById(R.id.flashToggle);
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        // 初始化开关状态
        boolean isOn = prefs.getBoolean(KEY_BACK_FLASH, false);
        flashToggle.setChecked(isOn);

        // 监听开关，保存偏好
        flashToggle.setOnCheckedChangeListener((buttonView, checked) -> {
            prefs.edit()
                    .putBoolean(KEY_BACK_FLASH, checked)
                    .apply();
        });
    }
}
