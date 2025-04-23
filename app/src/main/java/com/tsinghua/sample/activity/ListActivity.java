package com.tsinghua.sample.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.lm.sdk.LmAPI;
import com.lm.sdk.inter.IResponseListener;
import com.lm.sdk.mode.SystemControlBean;
import com.tsinghua.sample.DeviceAdapter;
import com.tsinghua.sample.R;
import com.tsinghua.sample.model.Device;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ListActivity extends AppCompatActivity implements IResponseListener {

    private static final String ACTION_USB_PERMISSION = "com.tsinghua.sample.USB_PERMISSION";
    private UsbManager usbManager;
    private static final int PERMISSION_REQUEST_CODE = 100;  // Permission request code
    private static final int REQUEST_CAMERA_PERMISSION = 200;

    private RecyclerView recyclerView;
    private DeviceAdapter adapter;
    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                if (state == BluetoothAdapter.STATE_OFF) {
                    // Bluetooth is off
                    Toast.makeText(context, "Bluetooth is OFF", Toast.LENGTH_SHORT).show();
                } else if (state == BluetoothAdapter.STATE_ON) {
                    // Bluetooth is on
                    Toast.makeText(context, "Bluetooth is ON", Toast.LENGTH_SHORT).show();
                }
            }


        }
};


    @SuppressLint("NewApi")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);
        LmAPI.init(getApplication());
        LmAPI.setDebug(true);
        LmAPI.addWLSCmdListener(this, this);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        intentFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        intentFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        registerReceiver(broadcastReceiver,intentFilter);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!checkPermissions(new String[]{
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_ADVERTISE
            })) {
                showPermissionDialog();
                return;
            }
        } else {
            if (!checkPermissions(new String[]{
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
            })) {
                showPermissionDialog();
                return;
            }
        }
        checkPermissions();
        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        registerReceiver(usbReceiver, new IntentFilter(ACTION_USB_PERMISSION), Context.RECEIVER_NOT_EXPORTED);

        checkOximeterUsbPermission();

        recyclerView = findViewById(R.id.deviceRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        List<Device> devices = new ArrayList<>();
        devices.add(new Device(Device.TYPE_FRONT_CAMERA, "前置录制"));
        devices.add(new Device(Device.TYPE_BACK_CAMERA, "后置录制"));
        devices.add(new Device(Device.TYPE_MICROPHONE, "麦克风"));
        devices.add(new Device(Device.TYPE_IMU, "IMU"));
        devices.add(new Device(Device.TYPE_RING, "指环"));
        devices.add(new Device(Device.TYPE_ECG, "心电"));
        devices.add(new Device(Device.TYPE_OXIMETER, "血氧仪"));

        adapter = new DeviceAdapter(this, devices);
        recyclerView.setAdapter(adapter);
    }

    private void checkOximeterUsbPermission() {
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        for (UsbDevice device : deviceList.values()) {
            Log.d("USB", "Detected device: VID=" + device.getVendorId() + " PID=" + device.getProductId());
            if (!usbManager.hasPermission(device)) {
                PendingIntent permissionIntent = PendingIntent.getBroadcast(this, 0,
                        new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);
                usbManager.requestPermission(device, permissionIntent);
            } else {
                Log.d("USB", "已有权限，无需请求");
            }
        }
    }

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if (ACTION_USB_PERMISSION.equals(intent.getAction())) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            Log.d("USB", "权限已授予");
                        }
                    } else {
                        Log.w("USB", "权限被拒绝" );
                    }
                }
            }
        }
    };
    private void checkPermissions(){

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivity(intent);
            }
        }

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{
                    android.Manifest.permission.CAMERA,
                    android.Manifest.permission.RECORD_AUDIO,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_CAMERA_PERMISSION);
        }
    }
    @Override
    protected void onDestroy() {
        unregisterReceiver(usbReceiver);
        super.onDestroy();
    }
    private void showPermissionDialog() {
        new AlertDialog.Builder(this)
                .setTitle("权限请求")
                .setMessage("该应用需要访问位置信息和蓝牙权限，请授予权限以继续使用蓝牙功能。")
                .setPositiveButton("确认", (dialog, which) -> {
                    requestPermission(new String[]{
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.BLUETOOTH_ADVERTISE
                    }, PERMISSION_REQUEST_CODE);
                })
                .setNegativeButton("取消", null)
                .show();

    }
    private void requestPermission(String[] permissions, int requestCode) {
        ActivityCompat.requestPermissions(this, permissions, requestCode);
    }
    private boolean checkPermissions(String[] permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void lmBleConnecting(int i) {

    }

    @Override
    public void lmBleConnectionSucceeded(int i) {

    }

    @Override
    public void lmBleConnectionFailed(int i) {

    }

    @Override
    public void VERSION(byte b, String s) {

    }

    @Override
    public void syncTime(byte b, byte[] bytes) {

    }

    @Override
    public void stepCount(byte[] bytes) {

    }

    @Override
    public void clearStepCount(byte b) {

    }

    @Override
    public void battery(byte b, byte b1) {

    }

    @Override
    public void timeOut() {

    }

    @Override
    public void saveData(String s) {

    }

    @Override
    public void reset(byte[] bytes) {

    }

    @Override
    public void setCollection(byte b) {

    }

    @Override
    public void getCollection(byte[] bytes) {

    }

    @Override
    public void getSerialNum(byte[] bytes) {

    }

    @Override
    public void setSerialNum(byte b) {

    }

    @Override
    public void cleanHistory(byte b) {

    }

    @Override
    public void setBlueToolName(byte b) {

    }

    @Override
    public void readBlueToolName(byte b, String s) {

    }

    @Override
    public void stopRealTimeBP(byte b) {

    }

    @Override
    public void BPwaveformData(byte b, byte b1, String s) {

    }

    @Override
    public void onSport(int i, byte[] bytes) {

    }

    @Override
    public void breathLight(byte b) {

    }

    @Override
    public void SET_HID(byte b) {

    }

    @Override
    public void GET_HID(byte b, byte b1, byte b2) {

    }

    @Override
    public void GET_HID_CODE(byte[] bytes) {

    }

    @Override
    public void GET_CONTROL_AUDIO_ADPCM(byte b) {

    }

    @Override
    public void SET_AUDIO_ADPCM_AUDIO(byte b) {

    }

    @Override
    public void setAudio(short i, int i1, byte[] bytes) {

    }

    @Override
    public void stopHeart(byte b) {

    }

    @Override
    public void stopQ2(byte b) {

    }

    @Override
    public void GET_ECG(byte[] bytes) {

    }

    @Override
    public void SystemControl(SystemControlBean systemControlBean) {

    }

    @Override
    public void CONTROL_AUDIO(byte[] bytes) {

    }

    @Override
    public void motionCalibration(byte b) {

    }

    @Override
    public void stopBloodPressure(byte b) {

    }

    @Override
    public void appBind(SystemControlBean systemControlBean) {

    }

    @Override
    public void appConnect(SystemControlBean systemControlBean) {

    }

    @Override
    public void appRefresh(SystemControlBean systemControlBean) {

    }
}
