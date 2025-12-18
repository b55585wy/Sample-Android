package com.tsinghua.sample.activity;

import com.tsinghua.sample.DeviceAdapter;
import com.tsinghua.sample.EcgViewHolder;
import com.vivalnk.sdk.VitalClient;
import com.vivalnk.sdk.common.ble.scan.ScanOptions;
import com.vivalnk.sdk.model.Device;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.tsinghua.sample.R;
import com.tsinghua.sample.EcgAdapter;
import com.vivalnk.sdk.ble.BluetoothScanListener;

import java.util.ArrayList;
import java.util.List;

public class EcgSettingsActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private Button scanButton;
    private TextView selectedDeviceInfo;
    private EcgAdapter deviceAdapter;
    private ArrayList<String> deviceInfoList;
    private ArrayList<Device> scannedDevices;
    private com.tsinghua.sample.model.Device mainDevice; // 注意，是你的 Device 类型，不是viva的
    private EcgViewHolder ecgViewHolder;

    private List<Device> selectedDevices; // 👈 新增
    private boolean isScanning = false;
    private static final String TAG = "EcgSettings";

    private BluetoothScanListener scanListener = new BluetoothScanListener() {
        @Override
        public void onDeviceFound(Device device) {
            Log.e(TAG, "找到设备: " + device.getName() + " " + device.getId());

            if (!scannedDevices.contains(device)) {
                scannedDevices.add(device);
                deviceInfoList.add(device.getName() + " - " + device.getId());
            }

            runOnUiThread(() -> deviceAdapter.notifyDataSetChanged());
        }

        @Override
        public void onStop() {
            Log.e(TAG, "扫描停止，找到 " + scannedDevices.size() + " 个设备");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ecg_settings);
        mainDevice = (com.tsinghua.sample.model.Device) getIntent().getSerializableExtra("device_object");
        ecgViewHolder = DeviceAdapter.currentEcgViewHolder;

        recyclerView = findViewById(R.id.deviceListView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        scanButton = findViewById(R.id.scanButton);
        selectedDeviceInfo = findViewById(R.id.selectedDeviceInfo);
        TextView info = findViewById(R.id.settingsInfo);
        info.setText("心电设备设置");

        deviceInfoList = new ArrayList<>();
        scannedDevices = new ArrayList<>();
        selectedDevices = new ArrayList<>(); // 👈 初始化
        deviceAdapter = new EcgAdapter(this, scannedDevices, selectedDevices);
        recyclerView.setAdapter(deviceAdapter);

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
        isScanning = true;
        scanButton.setText("停止扫描");
        scanButton.setBackgroundResource(R.drawable.button_stop_scan);

        scannedDevices.clear();
        deviceInfoList.clear();
        VitalClient.getInstance().startScan(new ScanOptions.Builder().build(), scanListener);

        Log.d(TAG, "开始扫描心电设备...");
    }

    private void stopBluetoothScan() {
        if (isScanning) {
            VitalClient.getInstance().stopScan();
            isScanning = false;
            scanButton.setText("开始扫描");
            scanButton.setBackgroundResource(R.drawable.button_start_scan);

            Log.d(TAG, "停止扫描心电设备...");
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopBluetoothScan();
    }

    // 更新显示
    public void updateSelectedDeviceInfo() {
        if (selectedDevices.isEmpty()) {
            selectedDeviceInfo.setText("选中设备: 无");
        } else {
            List<String> macList = new ArrayList<>();
            for (Device d : selectedDevices) {
                macList.add(d.getId());
            }
            selectedDeviceInfo.setText("选中设备: " + TextUtils.join(", ", macList));
        }
    }

    // 用户点击某个设备后调用
    public void onDeviceSelected(Device device) {
        if (!selectedDevices.contains(device)) {
            selectedDevices.add(device);
            updateSelectedDeviceInfo();
            deviceAdapter.notifyDataSetChanged();
            Toast.makeText(this, "已选择: " + device.getName(), Toast.LENGTH_SHORT).show();

            // 🔥 每次选中后更新到 mainDevice
            if (mainDevice != null) {
                mainDevice.setEcgSubDevices(selectedDevices); // 选中子设备，保存进主Device
            }
            // ⚠️ 不要在这里调用 bindData！
            // 原因：bindData 会使用当前 Activity (EcgSettingsActivity) 作为 context，
            // 但连接回调需要在 ListActivity 的上下文中运行。
            // 如果在这里调用，用户返回 ListActivity 后，EcgSettingsActivity 被销毁，
            // 但 SDK 回调还在使用已销毁的 Activity，会导致崩溃或状态丢失。
            //
            // 设备列表已保存到 mainDevice.ecgSubDevices，
            // ListActivity.onResume() 会自动刷新 RecyclerView 来更新 UI。
        }
    }

}
