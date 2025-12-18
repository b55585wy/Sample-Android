package com.tsinghua.sample.activity;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.lm.sdk.utils.BLEUtils;
import com.tsinghua.sample.R;
import com.tsinghua.sample.RingAdapter;

import java.util.ArrayList;

public class RingSettingsActivity extends AppCompatActivity {
    private BluetoothAdapter bluetoothAdapter;
    private boolean isScanning = false;
    private RecyclerView recyclerView;
    private Button scanButton;
    private TextView selectedDeviceInfo;
    private RingAdapter deviceAdapter;
    private ArrayList<String> deviceInfoList;
    private ArrayList<BluetoothDevice> scannedDevices;

    private BluetoothAdapter.LeScanCallback leScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] bytes) {
            if (device == null || TextUtils.isEmpty(device.getName())) {
                return;
            }

            String deviceInfo = device.getName() + " - MAC: " + device.getAddress();

            // 直接使用设备名过滤，兼容新版指环
            // 新版指环设备名包含"BCL"
            if (device.getName().contains("BCL")) {
                // 防止重复添加设备信息
                if (!deviceInfoList.contains(deviceInfo)) {
                    deviceInfoList.add(deviceInfo);
                    scannedDevices.add(device);
                    Log.d("RingLog", "Found BCL device: " + device.getName() + " - " + device.getAddress());

                    // 确保 UI 更新在主线程
                    runOnUiThread(() -> {
                        if (deviceAdapter != null) {
                            deviceAdapter.notifyDataSetChanged();
                        }
                    });
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ring_settings);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 初始化 UI 元素
        recyclerView = findViewById(R.id.deviceListView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this)); // 添加 LayoutManager
        scanButton = findViewById(R.id.scanButton);
        selectedDeviceInfo = findViewById(R.id.selectedDeviceInfo);
        TextView info = findViewById(R.id.settingsInfo);
        info.setText("指环设备设置");

        deviceInfoList = new ArrayList<>();
        scannedDevices = new ArrayList<>();
        deviceAdapter = new RingAdapter(this, scannedDevices, deviceInfoList);
        recyclerView.setAdapter(deviceAdapter);

        // 读取 SharedPreferences 中保存的设备 MAC 地址和名称
        SharedPreferences prefs = getSharedPreferences("AppSettings", MODE_PRIVATE);
        String savedMac = prefs.getString("mac_address", "");
        String savedName = prefs.getString("device_name", "");

        // 如果有已选中的设备 MAC 地址，显示该设备信息
        if (!TextUtils.isEmpty(savedMac)) {
            String displayText = !TextUtils.isEmpty(savedName) ?
                    "选中设备: " + savedName + " (" + savedMac + ")" :
                    "选中设备: " + savedMac;
            selectedDeviceInfo.setText(displayText);
        } else {
            selectedDeviceInfo.setText("选中设备: 无");
        }

        // 启动蓝牙扫描
        scanButton.setOnClickListener(v -> {
            if (isScanning) {
                stopBluetoothScan();
            } else {
                startBluetoothScan();
            }
        });

        String name = getIntent().getStringExtra("deviceName");
        setTitle(name + " 设置");
    }

    private void startBluetoothScan() {
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "蓝牙适配器不可用", Toast.LENGTH_SHORT).show();
            Log.e("RingSettings", "Bluetooth adapter is null");
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "请先在系统设置中启用蓝牙", Toast.LENGTH_LONG).show();

            // 尝试提示用户启用蓝牙
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            try {
                startActivity(enableBtIntent);
            } catch (Exception ignored) {
            }
            return;
        }

        // 清除之前的扫描结果
        deviceInfoList.clear();
        scannedDevices.clear();
        if (deviceAdapter != null) {
            deviceAdapter.notifyDataSetChanged();
        }

        // 执行蓝牙扫描
        isScanning = true;
        scanButton.setText("停止扫描");
        scanButton.setBackgroundResource(R.drawable.button_stop_scan);
        BLEUtils.startLeScan(this, leScanCallback);
        Toast.makeText(this, "开始扫描设备...", Toast.LENGTH_SHORT).show();
        Log.d("RingLog", "Bluetooth scanning started...");
    }

    private void stopBluetoothScan() {
        if (isScanning) {
            BLEUtils.stopLeScan(this, leScanCallback);  // 停止扫描
            isScanning = false;
            scanButton.setText("开始扫描");
            scanButton.setBackgroundResource(R.drawable.button_start_scan);  // 修改为开始扫描按钮的样式
            Log.d("RingLog", "Bluetooth scanning stopped.");
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isScanning) {
            stopBluetoothScan();  // 停止扫描
        }
    }
    public void updateSelectedDeviceInfo(String macAddress) {
        // 从扫描结果中查找设备名称
        String deviceName = "";
        for (int i = 0; i < scannedDevices.size(); i++) {
            if (scannedDevices.get(i).getAddress().equals(macAddress)) {
                deviceName = scannedDevices.get(i).getName();
                break;
            }
        }

        // 保存设备名称到SharedPreferences
        SharedPreferences prefs = getSharedPreferences("AppSettings", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("device_name", deviceName);
        editor.apply();

        // 更新显示选中设备的信息
        String displayText = !TextUtils.isEmpty(deviceName) ?
                "选中设备: " + deviceName + " (" + macAddress + ")" :
                "选中设备: " + macAddress;
        selectedDeviceInfo.setText(displayText);

        // 设置result通知调用者
        setResult(RESULT_OK);

        Toast.makeText(this, "设备选择已保存", Toast.LENGTH_SHORT).show();
    }

}
