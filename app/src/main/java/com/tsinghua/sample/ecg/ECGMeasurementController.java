package com.tsinghua.sample.ecg;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.tsinghua.sample.core.DataLogger;
import com.tsinghua.sample.core.SessionManager;
import com.tsinghua.sample.core.TimeSync;
import com.vivalnk.sdk.DataReceiveListener;
import com.vivalnk.sdk.VitalClient;
import com.vivalnk.sdk.ble.BluetoothConnectListener;
import com.vivalnk.sdk.ble.BluetoothScanListener;
import com.vivalnk.sdk.common.ble.connect.BleConnectOptions;
import com.vivalnk.sdk.common.ble.scan.ScanOptions;
import com.vivalnk.sdk.Callback;
import com.vivalnk.sdk.model.BatteryInfo;
import com.vivalnk.sdk.model.Device;
import com.vivalnk.sdk.model.Motion;
import com.vivalnk.sdk.model.SampleData;
import com.vivalnk.sdk.SampleDataReceiveListener;
import com.vivalnk.sdk.device.vv330.VV330Manager;
import com.vivalnk.sdk.device.vv330.DataStreamMode;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 统一的心电测量控制器
 * - 负责扫描/连接 VivaLNK ECG 设备
 * - 提供实时数据回调给 UI（波形、HR、RR、导联状态、电量）
 * - 在会话开始/结束时负责 CSV 落盘（与 SessionManager/TimeSync 对齐）
 *
 * 设计目标：完全重写，不依赖旧代码或 git 历史，实现「一键录制」中同步启动/停止心电测量。
 */
public class ECGMeasurementController {
    private static final String TAG = "ECGController";

    public enum ConnectionState {DISCONNECTED, CONNECTING, CONNECTED, READY}

    public static class ECGRealtimeData {
        public long wallMs;
        public Integer hr;
        public Integer rr;
        public boolean leadOn;
        public float[] ecgMv;
        public Motion[] acc;
        public boolean flash;
        public Integer batteryPercent;
    }

    public interface Listener {
        void onConnectionStateChanged(ConnectionState state, Device device);
        void onRealtimeData(ECGRealtimeData data);
        void onLog(String msg);
    }

    private static final ECGMeasurementController INSTANCE = new ECGMeasurementController();

    public static ECGMeasurementController getInstance() {
        return INSTANCE;
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Set<Listener> listeners = new CopyOnWriteArraySet<>();
    private final List<Device> scannedDevices = new ArrayList<>();
    private final Callback noopCallback = new Callback() {};

    private Context appContext;
    private Device connectedDevice;
    private VV330Manager vv330Manager;  // SDK管理器，用于切换数据模式
    private boolean scanning = false;
    private boolean measuring = false;
    private DataLogger ecgLogger;
    private File ecgFile;
    private Integer lastBatteryPercent;

    private ECGMeasurementController() {
    }

    // region Public API

    public void init(Context context) {
        if (appContext == null && context != null) {
            appContext = context.getApplicationContext();
        }
    }

    public void addListener(Listener l) {
        if (l != null) listeners.add(l);
    }

    public void removeListener(Listener l) {
        if (l != null) listeners.remove(l);
    }

    public boolean isConnected() {
        return connectedDevice != null && VitalClient.getInstance().isConnected(connectedDevice);
    }

    public boolean isMeasuring() {
        return measuring;
    }

    public Device getConnectedDevice() {
        return connectedDevice;
    }

    public List<Device> getScannedDevices() {
        return new ArrayList<>(scannedDevices);
    }

    /**
     * 开始扫描心电设备
     */
    public void startScan(BluetoothScanListener externalListener) {
        scanning = true;
        scannedDevices.clear();
        VitalClient.getInstance().startScan(new ScanOptions.Builder().build(), new BluetoothScanListener() {
            @Override
            public void onDeviceFound(Device device) {
                if (device == null) return;
                if (!containsDevice(scannedDevices, device)) {
                    scannedDevices.add(device);
                }
                log("发现心电设备: " + device.getName() + " (" + device.getId() + ")");
                if (externalListener != null) externalListener.onDeviceFound(device);
            }

            @Override
            public void onStop() {
                scanning = false;
                log("心电扫描结束，共找到 " + scannedDevices.size() + " 台");
                if (externalListener != null) externalListener.onStop();
            }
        });
    }

    public void stopScan() {
        scanning = false;
        VitalClient.getInstance().stopScan();
    }

    /**
     * 连接指定设备
     */
    public void connect(Device device, BluetoothConnectListener externalListener) {
        if (device == null) return;
        connectedDevice = null;
        updateState(ConnectionState.CONNECTING, device);

        BleConnectOptions options = new BleConnectOptions.Builder()
                .setAutoConnect(false)
                .setConnectRetry(2)
                .setConnectTimeout(10_000)
                .build();

        VitalClient.getInstance().connect(device, options, new BluetoothConnectListener() {
            @Override
            public void onConnected(Device d) {
                connectedDevice = d;
                log("心电设备已连接: " + d.getName());
                updateState(ConnectionState.CONNECTED, d);
                // 注册数据接收（只用DataReceiveListener，它已处理SampleData，避免重复）
                VitalClient.getInstance().registerDataReceiver(d, dataListener);
                // 注：不再注册sampleDataListener，因为dataListener已处理SampleData，避免数据重复
                if (externalListener != null) externalListener.onConnected(d);
            }

            @Override
            public void onDeviceReady(Device d) {
                log("心电设备服务就绪: " + d.getName());
                VitalClient.getInstance().enableNotification(d, noopCallback);
                updateState(ConnectionState.READY, d);

                // 关键：创建VV330Manager并切换到FullDualMode以启用数据流
                if (isVivaLNKEcgDevice(d.getName())) {
                    vv330Manager = new VV330Manager(d);
                    vv330Manager.switchToFullDualMode(d, new Callback() {
                        @Override
                        public void onComplete(java.util.Map<String, Object> data) {
                            log("成功切换到FullDualMode，数据流已启用");
                        }
                        @Override
                        public void onError(int code, String msg) {
                            log("切换数据模式失败[" + code + "]: " + msg);
                        }
                    });
                }

                if (externalListener != null) externalListener.onDeviceReady(d);
            }

            @Override
            public void onDisconnected(Device d, boolean isForce) {
                log("心电设备断开: " + d.getName());
                VitalClient.getInstance().unregisterDataReceiver(d);
                VitalClient.getInstance().unregisterSampleDataReceiver(d);
                connectedDevice = null;
                vv330Manager = null;  // 清理VV330Manager
                measuring = false;
                closeLogger();
                updateState(ConnectionState.DISCONNECTED, d);
                if (externalListener != null) externalListener.onDisconnected(d, isForce);
            }

            @Override
            public void onError(Device d, int code, String msg) {
                log("心电连接错误[" + code + "]: " + msg);
                if (externalListener != null) externalListener.onError(d, code, msg);
            }
        });
    }

    public void disconnect() {
        if (connectedDevice != null) {
            VitalClient.getInstance().disconnect(connectedDevice);
            VitalClient.getInstance().unregisterDataReceiver(connectedDevice);
            connectedDevice = null;
        }
        vv330Manager = null;  // 清理VV330Manager
        measuring = false;
        closeLogger();
        updateState(ConnectionState.DISCONNECTED, null);
    }

    /**
     * 启动同步测量（录制时调用）
     */
    public synchronized void beginSynchronizedMeasurement(int durationSeconds, File sessionDir) {
        if (!isConnected()) {
            log("未连接心电设备，无法开始测量");
            return;
        }
        if (measuring) {
            log("测量已在进行中，跳过重复启动");
            return;
        }

        File dir = null;
        if (sessionDir != null) {
            dir = new File(sessionDir, "ecg");
        } else if (SessionManager.getInstance().getSessionDir() != null) {
            dir = SessionManager.getInstance().subDir("ecg");
        }
        if (dir == null) {
            log("未找到会话目录，测量未开始");
            return;
        }
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        ecgFile = new File(dir, "ecg_" + System.currentTimeMillis() + ".csv");
        try {
            // header 与 iOS 尽量对齐，保留导联状态
            String header = "wall_ms,ecg_mv,heart_rate,respiratory_rate,lead_on";
            ecgLogger = new DataLogger(ecgFile, header);
            measuring = true;
            log("心电测量开始，文件: " + ecgFile.getName());
        } catch (Exception e) {
            log("创建心电日志失败: " + e.getMessage());
            measuring = false;
        }
    }

    /**
     * 停止同步测量
     */
    public synchronized void stopSynchronizedMeasurement() {
        if (!measuring) return;
        measuring = false;
        closeLogger();
        log("心电测量停止");
    }

    // endregion

    // region Internal plumbing

    private boolean containsDevice(List<Device> list, Device target) {
        for (Device d : list) {
            if (d != null && target != null && target.getId().equals(d.getId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断是否为VivaLNK ECG设备
     */
    private boolean isVivaLNKEcgDevice(String name) {
        return name != null && (name.startsWith("ECGRec_") || name.startsWith("VitalScout_"));
    }

    private final DataReceiveListener dataListener = new DataReceiveListener() {
        @Override
        public void onReceiveData(Device device, java.util.Map<String, Object> data) {
            if (connectedDevice == null || device == null || !device.getId().equals(connectedDevice.getId())) {
                return;
            }
            Log.d("ECG_DEBUG", ">>> DataReceiveListener.onReceiveData 收到数据");
            // 优先尝试 SampleData
            Object obj = data.get("data");
            if (obj instanceof SampleData) {
                Log.d("ECG_DEBUG", "数据类型: SampleData");
                SampleData sample = (SampleData) obj;
                boolean flash = sample.isFlash() != null && sample.isFlash();
                handleSampleData(sample, flash);
                return;
            }
            // 兼容可能返回的直观 map（hr/rr/ecg 等键）
            Log.d("ECG_DEBUG", "数据类型: Map, keys=" + data.keySet());
            handleMapData(data);
        }

        @Override
        public void onBatteryChange(Device device, java.util.Map<String, Object> data) {
            Object obj = data.get("data");
            if (obj instanceof BatteryInfo) {
                BatteryInfo info = (BatteryInfo) obj;
                int percent = info.getPercent() > 0 ? info.getPercent() : info.getLevel();
                lastBatteryPercent = percent;
                ECGRealtimeData packet = new ECGRealtimeData();
                packet.batteryPercent = percent;
                notifyRealtime(packet);
            }
        }

        @Override
        public void onLeadStatusChange(Device device, boolean isLeadOn) {
            ECGRealtimeData packet = new ECGRealtimeData();
            packet.leadOn = isLeadOn;
            notifyRealtime(packet);
        }
    };

    private final com.vivalnk.sdk.SampleDataReceiveListener sampleDataListener = new SampleDataReceiveListener() {
        @Override
        public void onReceiveSampleData(Device device, boolean flash, SampleData sampleData) {
            if (connectedDevice == null || device == null || !device.getId().equals(connectedDevice.getId())) return;
            Log.d("ECG_DEBUG", ">>> SampleDataReceiveListener.onReceiveSampleData 收到数据, flash=" + flash);
            handleSampleData(sampleData, flash);
        }
    };

    // SDK哨兵值常量：表示"未计算出有效值"
    private static final int ECG_INVALID_MARKER_NEG = -8678;  // ECG无信号/饱和标记（负）
    private static final int ECG_INVALID_MARKER_POS = 8678;   // ECG无信号/饱和标记（正）
    private static final int HR_INVALID_VALUE = -201;     // HR未计算哨兵值
    private static final int RR_INVALID_VALUE = -101;     // RR未计算哨兵值

    private void handleSampleData(SampleData sample, boolean flash) {
        int[] ecgRaw = sample.getECG();
        Integer hrRaw = sample.getHR();
        Integer rrRaw = sample.getRR();
        boolean leadOn = sample.isLeadOn() == null || sample.isLeadOn();
        int magnification = sample.getMagnification();

        // 过滤无效的HR/RR哨兵值（SDK用负数表示尚未计算出有效值）
        Integer hr = (hrRaw != null && hrRaw > 0) ? hrRaw : null;
        Integer rr = (rrRaw != null && rrRaw > 0) ? rrRaw : null;

        // DEBUG: 打印原始数据
        Log.d("ECG_DEBUG", "========== 收到SampleData ==========");
        Log.d("ECG_DEBUG", "flash=" + flash + ", leadOn=" + leadOn + ", magnification=" + magnification);
        Log.d("ECG_DEBUG", "HR_raw=" + hrRaw + " -> HR_filtered=" + hr);
        Log.d("ECG_DEBUG", "RR_raw=" + rrRaw + " -> RR_filtered=" + rr);
        Log.d("ECG_DEBUG", "ecgRaw长度=" + (ecgRaw != null ? ecgRaw.length : "null"));
        if (ecgRaw != null && ecgRaw.length > 0) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(10, ecgRaw.length); i++) {
                sb.append(ecgRaw[i]).append(",");
            }
            Log.d("ECG_DEBUG", "ecgRaw前10个值: " + sb.toString());
        }

        float[] ecgMv = null;
        boolean ecgSignalValid = false;  // 标记ECG信号是否有效
        if (ecgRaw != null && ecgRaw.length > 0) {
            // 统计无效标记数量（+8678 和 -8678 都是无效标记）
            int invalidCount = 0;
            for (int v : ecgRaw) {
                if (v == ECG_INVALID_MARKER_NEG || v == ECG_INVALID_MARKER_POS) invalidCount++;
            }
            float invalidRatio = (float) invalidCount / ecgRaw.length;
            ecgSignalValid = invalidRatio < 0.5f;  // 超过50%无效则认为整体无效

            Log.d("ECG_DEBUG", "ECG无效标记(±8678)数量=" + invalidCount + "/" + ecgRaw.length
                + " (" + String.format(Locale.US, "%.1f%%", invalidRatio * 100) + ")"
                + ", 信号" + (ecgSignalValid ? "有效" : "无效"));

            ecgMv = new float[ecgRaw.length];
            float divider = magnification == 0 ? 1f : magnification;
            Log.d("ECG_DEBUG", "divider=" + divider);
            for (int i = 0; i < ecgRaw.length; i++) {
                // 对无效标记值，替换为0（或可选择保留原值供参考）
                if (ecgRaw[i] == ECG_INVALID_MARKER_NEG || ecgRaw[i] == ECG_INVALID_MARKER_POS) {
                    ecgMv[i] = 0f;  // 无效值替换为0，避免波形图大幅跳动
                } else {
                    ecgMv[i] = ecgRaw[i] / divider;
                }
            }
            // DEBUG: 打印转换后的mV值
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(10, ecgMv.length); i++) {
                sb.append(String.format(Locale.US, "%.4f", ecgMv[i])).append(",");
            }
            Log.d("ECG_DEBUG", "ecgMv前10个值: " + sb.toString());
        }

        long wall = TimeSync.nowWallMillis();

        // 综合判断导联状态：SDK报告leadOn且ECG信号有效才算真正导联良好
        boolean effectiveLeadOn = leadOn && ecgSignalValid;

        if (measuring && ecgLogger != null && ecgMv != null && ecgMv.length > 0) {
            String ecgStr = formatEcg(ecgMv);
            String line = wall + ",\"" + ecgStr + "\"," +
                    (hr != null ? hr : "") + "," +
                    (rr != null ? rr : "") + "," +
                    (effectiveLeadOn ? 1 : 0);
            ecgLogger.writeLine(line);
        }

        ECGRealtimeData packet = new ECGRealtimeData();
        packet.wallMs = wall;
        packet.hr = hr;
        packet.rr = rr;
        packet.leadOn = effectiveLeadOn;  // 使用综合判断的导联状态
        packet.ecgMv = ecgSignalValid ? ecgMv : null;  // 信号无效时不传波形，避免UI显示噪声
        packet.flash = flash;
        packet.batteryPercent = lastBatteryPercent;

        notifyRealtime(packet);
    }

    /**
     * 兼容 map 结构的数据（某些固件或 SDK 回调直接给键值对）
     */
    private void handleMapData(java.util.Map<String, Object> data) {
        int[] ecgRaw = null;
        Integer hr = null;
        Integer rr = null;
        Boolean leadOn = null;
        Integer magnification = null;

        if (data.get("ecg") instanceof int[]) {
            ecgRaw = (int[]) data.get("ecg");
        } else if (data.get("ecg") instanceof java.util.List) {
            java.util.List<?> list = (java.util.List<?>) data.get("ecg");
            ecgRaw = new int[list.size()];
            for (int i = 0; i < list.size(); i++) {
                Object v = list.get(i);
                ecgRaw[i] = v instanceof Number ? ((Number) v).intValue() : 0;
            }
        }
        if (data.get("hr") instanceof Number) hr = ((Number) data.get("hr")).intValue();
        if (data.get("HR") instanceof Number) hr = ((Number) data.get("HR")).intValue();
        if (data.get("rr") instanceof Number) rr = ((Number) data.get("rr")).intValue();
        if (data.get("RR") instanceof Number) rr = ((Number) data.get("RR")).intValue();
        if (data.get("leadOn") instanceof Boolean) leadOn = (Boolean) data.get("leadOn");
        if (data.get("lead_on") instanceof Boolean) leadOn = (Boolean) data.get("lead_on");
        if (data.get("magnification") instanceof Number)
            magnification = ((Number) data.get("magnification")).intValue();

        if (ecgRaw == null) return; // 没有波形仍不写文件

        int mag = magnification != null && magnification > 0 ? magnification : 1000; // 常见放大系数
        float[] ecgMv = new float[ecgRaw.length];
        for (int i = 0; i < ecgRaw.length; i++) {
            ecgMv[i] = ecgRaw[i] / (float) mag;
        }

        boolean lead = leadOn == null || leadOn;
        long wall = TimeSync.nowWallMillis();

        if (measuring && ecgLogger != null) {
            String ecgStr = formatEcg(ecgMv);
            String line = wall + ",\"" + ecgStr + "\"," +
                    (hr != null ? hr : "") + "," +
                    (rr != null ? rr : "") + "," +
                    (lead ? 1 : 0);
            ecgLogger.writeLine(line);
        }

        ECGRealtimeData packet = new ECGRealtimeData();
        packet.wallMs = wall;
        packet.hr = hr;
        packet.rr = rr;
        packet.leadOn = lead;
        packet.ecgMv = ecgMv;
        packet.flash = false;
        packet.batteryPercent = lastBatteryPercent;
        notifyRealtime(packet);
    }

    private String formatEcg(float[] ecg) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ecg.length; i++) {
            if (i > 0) sb.append(';');
            sb.append(String.format(Locale.US, "%.5f", ecg[i]));
        }
        return sb.toString();
    }

    private void notifyRealtime(ECGRealtimeData data) {
        Log.d("ECG_DEBUG", "<<< notifyRealtime: HR=" + data.hr + ", RR=" + data.rr
            + ", leadOn=" + data.leadOn + ", ecgMv长度=" + (data.ecgMv != null ? data.ecgMv.length : "null")
            + ", battery=" + data.batteryPercent);
        for (Listener l : listeners) {
            mainHandler.post(() -> l.onRealtimeData(data));
        }
    }

    private void updateState(ConnectionState state, Device device) {
        for (Listener l : listeners) {
            mainHandler.post(() -> l.onConnectionStateChanged(state, device));
        }
    }

    private void log(String msg) {
        Log.d(TAG, msg);
        for (Listener l : listeners) {
            mainHandler.post(() -> l.onLog(msg));
        }
    }

    private void closeLogger() {
        if (ecgLogger != null) {
            ecgLogger.close();
            ecgLogger = null;
        }
        ecgFile = null;
    }
    // endregion
}
