package com.tsinghua.sample.device;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import com.tsinghua.sample.device.model.OximeterData;

public class OximeterService extends Service {

    private final IBinder binder = new LocalBinder();
    private OximeterManager manager;

    public class LocalBinder extends Binder {
        public OximeterService getService() {
            return OximeterService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d("OximeterService", "onCreate - 初始化 OximeterManager");
        // 只初始化 Manager，不自动连接
        // 连接由 ListActivity 在获得 USB 权限后通过 checkOximeterUsbPermission() 控制
        manager = OximeterManager.getInstance(this);
    }

    public void setListener(OximeterDataListener listener) {
        manager.setDataListener(listener);

    }

    public void startRecording(String path) {
        manager.startRecording(path);
    }

    public void stopRecording() {
        manager.stopRecording();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        manager.disconnect();
        super.onDestroy();
    }
}
