package com.tsinghua.sample.device;

import android.content.Context;
import android.hardware.usb.*;
import android.util.Log;

import com.tsinghua.sample.device.model.OximeterData;
import com.tsinghua.sample.core.SessionManager;
import com.tsinghua.sample.core.TimeSync;
import com.tsinghua.sample.core.DataLogger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.Semaphore;

public class OximeterManager {

    private static final String TAG = "OximeterManager";
    private static final int VENDOR_ID = 0x1234; // 替换为真实 VID
    private static final int PRODUCT_ID = 0x5678; // 替换为真实 PID
    private final List<OximeterData> preview = new ArrayList<>();
    private static final int USB_RECIP_INTERFACE = 0x01; // 接口接收方

    // 单例实例
    private static OximeterManager instance;
    private static Context appContext;

    private UsbManager usbManager;
    private final Context context;
    private UsbDeviceConnection connection;
    private UsbEndpoint endpointIn, endpointOut;

    private final List<OximeterData> buf = new ArrayList<>();
    private final Semaphore lock = new Semaphore(0);

    private boolean alive = false;
    private volatile boolean recording = false;

    private DataLogger spo2Logger;
    private Thread recordingThread;  // 录制线程引用，用于等待线程结束
    private OximeterDataListener listener;
    private DebugLogListener debugListener;

    /**
     * 调试日志监听器
     */
    public interface DebugLogListener {
        void onDebugLog(String message);
    }

    /**
     * 获取单例实例
     */
    public static synchronized OximeterManager getInstance(Context context) {
        if (instance == null) {
            appContext = context.getApplicationContext();
            instance = new OximeterManager(appContext);
        }
        return instance;
    }

    /**
     * 获取单例实例（需要先调用getInstance(Context)初始化）
     */
    public static OximeterManager getInstance() {
        return instance;
    }

    private OximeterManager(Context context) {
        this.context = context;
        usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
    }

    public boolean isConnected() {
        return alive;
    }

    public void setDataListener(OximeterDataListener l) {
        this.listener = l;
    }

    public void setDebugListener(DebugLogListener l) {
        this.debugListener = l;
    }

    private void debugLog(String msg) {
        Log.d(TAG, msg);
        if (debugListener != null) {
            debugListener.onDebugLog(msg);
        }
    }

    public void connectAndStart() {
        // 如果已经连接，先断开
        if (alive) {
            debugLog("已连接，先断开旧连接");
            disconnect();
        }

        debugLog("扫描USB设备，数量: " + usbManager.getDeviceList().size());

        if (usbManager.getDeviceList().isEmpty()) {
            debugLog("未发现USB设备");
            return;
        }

        for (UsbDevice device : usbManager.getDeviceList().values()) {
            debugLog("发现: VID=" + String.format("0x%04X", device.getVendorId()) +
                    " PID=" + String.format("0x%04X", device.getProductId()) +
                    " " + device.getProductName());

            UsbInterface usbInterface = device.getInterface(0);

            for (int i = 0; i < usbInterface.getEndpointCount(); i++) {
                UsbEndpoint ep = usbInterface.getEndpoint(i);
                Log.e("USB", "Endpoint " + i + ": type=" + ep.getType() + ", dir=" + ep.getDirection());

                if (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_INT &&
                        ep.getDirection() == UsbConstants.USB_DIR_IN) {
                    endpointIn = ep;
                }
                if (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_INT &&
                        ep.getDirection() == UsbConstants.USB_DIR_OUT) {
                    endpointOut = ep;
                }
            }

            connection = usbManager.openDevice(device);
            if (connection == null) {
                debugLog("打开设备失败(需USB权限)");
                return;
            }

            boolean claimed = connection.claimInterface(usbInterface, true);
            debugLog("接口claim: " + claimed);
            if (!claimed) {
                debugLog("claim接口失败");
                return;
            }

            byte[] initCmd1 = new byte[]{(byte)0x8e, 0x03, 0x11, 0x00};
            byte[] initCmd2 = new byte[]{0x00, (byte)0x8e, 0x03, 0x11};
            byte[] init1= new byte[]{(byte) 0x81, 0x01, 0x00, 0x00};
            byte[] init2 = new byte[]{0x00, (byte) 0x81, 0x01, 0x00};

            int requestTypeSetReport = UsbConstants.USB_DIR_OUT | UsbConstants.USB_TYPE_CLASS | USB_RECIP_INTERFACE;
            int requestTypeGetReport = UsbConstants.USB_DIR_IN | UsbConstants.USB_TYPE_CLASS | USB_RECIP_INTERFACE;

            int tag = connection.controlTransfer(
                    requestTypeSetReport,
                    0x09, // SET_REPORT
                    0x200, // Report ID=0 (Output Report)
                    usbInterface.getId(), // 接口ID
                    initCmd1,
                    initCmd1.length,
                    300
            );
            debugLog("初始化结果: " + tag);
            connection.controlTransfer(
                    requestTypeSetReport,
                    0x09, // SET_REPORT
                    0x200, // Report ID=0 (Output Report)
                    usbInterface.getId(), // 接口ID
                    initCmd2,
                    initCmd2.length,
                    300
            );
            connection.controlTransfer(
                    requestTypeSetReport,
                    0x09, // SET_REPORT
                    0x200, // Report ID=0 (Output Report)
                    usbInterface.getId(), // 接口ID
                    init1,
                    init1.length,
                    300
            );
            connection.controlTransfer(
                    requestTypeSetReport,
                    0x09, // SET_REPORT
                    0x200, // Report ID=0 (Output Report)
                    usbInterface.getId(), // 接口ID
                    init2,
                    init2.length,
                    300
            );

            byte[] infoBuffer = new byte[32];
            int len = connection.controlTransfer(
                    requestTypeGetReport,
                    0x01, // GET_REPORT
                    0x101, // Report ID=0 (Input Report)
                    usbInterface.getId(), // 接口ID
                    infoBuffer,
                    infoBuffer.length,
                    300
            );

            Log.e(TAG,String.valueOf(len));
            if (len > 0) {
                String devId = new String(infoBuffer, 0, len).trim();
                debugLog("设备ID: " + devId);
            }

            for (int i = 0; i < 2; i++) {
                connection.bulkTransfer(endpointOut, new byte[]{0x00, (byte) 0x9b, 0x01, 0x1c}, 4, 100);
                connection.bulkTransfer(endpointOut, new byte[]{0x00, (byte) 0x9b, 0x00, 0x1b}, 4, 100);
            }

            alive = true;
            debugLog("连接成功，开始接收数据");
            new Thread(this::ping).start();
            new Thread(this::connect).start();
            break;
        }
    }


    private void ping() {
        try {
            while (alive) {
                connection.bulkTransfer(endpointOut, new byte[]{0x00, (byte) 0x9b, 0x01, 0x1c}, 4, 300);
                connection.bulkTransfer(endpointOut, new byte[]{0x00, (byte) 0x9b, 0x00, 0x1b}, 4, 300);
                Thread.sleep(20000);
            }
        } catch (Exception e) {
            Log.e(TAG, "Ping error: " + e);
            alive = false;
        }
    }

    private void connect() {
        debugLog("数据接收线程启动");
        try {
            ByteBuffer buffer = ByteBuffer.allocate(64);
            int packetCount = 0;
            while (alive) {
                int len = connection.bulkTransfer(endpointIn, buffer.array(), buffer.capacity(), 300);
                if (len > 0) {
                    packetCount++;
                    long t = TimeSync.nowWallMillis();  // 使用统一时间基准
                    byte[] recv = Arrays.copyOf(buffer.array(), len);

                    // 调试：打印原始数据（前5个包每个都打印，之后每100个打印一次）
                    if (packetCount <= 5 || packetCount % 100 == 0) {
                        StringBuilder hex = new StringBuilder();
                        for (int j = 0; j < Math.min(len, 16); j++) {
                            hex.append(String.format("%02X ", recv[j] & 0xFF));
                        }
                        debugLog("#" + packetCount + " len=" + len + ": " + hex);
                    }

                    Integer hr = null, spo2 = null;
                    List<Integer> bvpList = new ArrayList<>();

                    for (int i = 0; i < len - 4; i++) {
                        if ((recv[i] & 0xFF) == 0xEB && recv[i + 1] == 0x00) {
                            bvpList.add(recv[i + 3] & 0xFF);
                        }
                        if ((recv[i] & 0xFF) == 0xEB && recv[i + 1] == 0x01 && recv[i + 2] == 0x05) {
                            hr = recv[i + 3] & 0xFF;
                            spo2 = recv[i + 4] & 0xFF;
                            debugLog("解析: HR=" + hr + " SpO2=" + spo2);
                        }
                    }
                    OximeterData data = new OximeterData(t);

                    if (!bvpList.isEmpty()) {
                        data.bvp = bvpList.get(bvpList.size() - 1);
                        synchronized (buf) {
                            buf.add(data);
                        }
                        preview.add(data);
                        lock.release();
                    }

                    if (spo2 != null) {
                        data.spo2 = spo2;
                        synchronized (buf) {
                            buf.add(data);
                        }
                        preview.add(data);
                        lock.release();
                    }

                    if (hr != null) {
                        data.hr = hr;
                        synchronized (buf) {
                            buf.add(data);
                        }
                        preview.add(data);
                        lock.release();
                    }
                    if (listener != null && (data.bvp != -1 || data.hr != -1 || data.spo2 != -1)) {
                        listener.onOximeterData(data);
                    }
                    while (preview.size() > 10000) preview.remove(0);
                    // 限制buf大小，防止内存溢出
                    synchronized (buf) {
                        while (buf.size() > 10000) {
                            buf.remove(0);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "接收异常", e);
        } finally {
            buf.clear();
            lock.release();
            if (connection != null) connection.close();
        }
    }

    public void startRecording(String path) {
        if (recording) return;

        // 验证时间同步状态
        TimeSync.logTimestampStatus("SpO2");

        recording = true;

        // 在后台线程初始化日志，避免阻塞主线程
        recordingThread = new Thread(() -> {
            try {
                SessionManager sm = SessionManager.getInstance();
                sm.ensureSession(context, context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
                        .getString("experiment_id", "default"));
                File dir = sm.subDir("spo2");
                if (dir != null && !dir.exists()) dir.mkdirs();
                File logFile = new File(dir != null ? dir : new File(path),
                        "spo2_" + System.currentTimeMillis() + ".csv");
                spo2Logger = new DataLogger(logFile, "wall_ms,hr,spo2,bvp");
                Log.d(TAG, "SpO2 logger initialized: " + logFile.getAbsolutePath());
            } catch (Exception e) {
                Log.e(TAG, "init spo2 logger failed", e);
            }

            // 数据写入循环
            while (recording) {
                try {
                    // 使用带超时的 tryAcquire 避免无限阻塞
                    boolean acquired = lock.tryAcquire(100, java.util.concurrent.TimeUnit.MILLISECONDS);
                    if (!acquired) continue;

                    synchronized (buf) {
                        if (buf.isEmpty()) continue;
                        OximeterData d = buf.remove(0);
                        if (spo2Logger != null) {
                            long wall = TimeSync.nowWallMillis();
                            spo2Logger.writeLine(wall + "," + d.hr + "," + d.spo2 + "," + d.bvp);
                        }
                    }
                } catch (InterruptedException e) {
                    Log.d(TAG, "Recording thread interrupted");
                    break;
                } catch (Exception e) {
                    Log.e(TAG, "Error in recording loop", e);
                }
            }
            Log.d(TAG, "SpO2 recording thread ended");
        }, "SpO2-Recording");
        recordingThread.start();
    }

    public void stopRecording() {
        recording = false;

        // 在后台线程等待录制线程退出并清理资源，避免阻塞主线程
        final Thread threadToJoin = recordingThread;
        final DataLogger loggerToClose = spo2Logger;
        recordingThread = null;
        spo2Logger = null;

        if (threadToJoin != null || loggerToClose != null) {
            new Thread(() -> {
                if (threadToJoin != null) {
                    try {
                        threadToJoin.join(2000);  // 最多等待2秒
                    } catch (InterruptedException e) {
                        Log.w(TAG, "Interrupted while waiting for recording thread");
                        Thread.currentThread().interrupt();
                    }
                }
                if (loggerToClose != null) {
                    loggerToClose.close();
                }
                Log.d(TAG, "SpO2 recording cleanup completed");
            }, "SpO2-Cleanup").start();
        }
    }

    public void disconnect() {
        debugLog("断开连接");
        alive = false;
        recording = false;

        // 捕获需要清理的资源引用
        final Thread threadToJoin = recordingThread;
        final DataLogger loggerToClose = spo2Logger;
        final UsbDeviceConnection connToClose = connection;
        recordingThread = null;
        spo2Logger = null;
        connection = null;

        // 清理endpoints以便重新连接
        endpointIn = null;
        endpointOut = null;

        // 清理数据缓冲（主线程安全）
        buf.clear();
        preview.clear();

        // 在后台线程执行阻塞操作，避免ANR
        if (threadToJoin != null || loggerToClose != null || connToClose != null) {
            new Thread(() -> {
                // 等待录制线程退出
                if (threadToJoin != null) {
                    try {
                        threadToJoin.join(2000);  // 最多等待2秒
                    } catch (InterruptedException e) {
                        Log.w(TAG, "Interrupted while waiting for recording thread");
                        Thread.currentThread().interrupt();
                    }
                }

                // 关闭USB连接
                if (connToClose != null) {
                    try {
                        connToClose.close();
                    } catch (Exception e) {
                        Log.e(TAG, "关闭连接时出错", e);
                    }
                }

                // 关闭logger
                if (loggerToClose != null) {
                    loggerToClose.close();
                }
                Log.d(TAG, "SpO2 disconnect cleanup completed");
            }, "SpO2-Disconnect").start();
        }
    }
}
