package com.tsinghua.sample.media;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import com.tsinghua.sample.core.DataLogger;
import com.tsinghua.sample.core.SessionManager;
import com.tsinghua.sample.core.TimeSync;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Locale;

public class IMURecorder implements SensorEventListener {
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor gyroscope;
    private boolean isRecording;
    public File outputDirectory;
    private Context context;
    private final String TAG = "IMURecorder";
    private OnDataUpdateListener dataUpdateListener;
    private static final int UPDATE_INTERVAL = 100;
    private int accelerometerDataCount = 0;
    private int gyroscopeDataCount = 0;

    // 调试用计数器
    private long totalAccelEvents = 0;
    private long totalGyroEvents = 0;
    private long lastHeartbeatTime = 0;
    private static final long HEARTBEAT_INTERVAL_MS = 2000; // 每2秒输出一次心跳日志

    // 流式写入相关
    private DataLogger accelerometerWriter;
    private DataLogger gyroscopeWriter;
    private File accelerometerFile;
    private File gyroscopeFile;

    // 后台线程处理传感器回调，避免阻塞主线程
    private HandlerThread sensorThread;
    private Handler sensorHandler;

    // 主线程Handler，用于UI更新回调
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // 用于UI更新的最新数据
    private String lastAccelData = "";
    private String lastGyroData = "";

    // 缓冲区大小，可以根据需要调整
    private static final int BUFFER_SIZE = 8192;

    public IMURecorder(Context context) {
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        isRecording = false;
        this.context = context;
    }

    public void startRecording() {
        if (isRecording) {
            Log.w(TAG, "Recording is already in progress at relativeSec=" + TimeSync.nowRelativeSeconds());
            // 打印调用栈帮助调试
            Log.w(TAG, "startRecording called while already recording, stack trace:", new Throwable());
            return;
        }

        // 打印调用栈帮助调试
        Log.d(TAG, "startRecording called at relativeSec=" + TimeSync.nowRelativeSeconds() + ", stack trace:", new Throwable());

        // 验证时间同步状态
        TimeSync.logTimestampStatus("IMU");

        // 初始化文件和写入器
        if (!initializeFiles()) {
            Log.e(TAG, "Failed to initialize files for recording");
            return;
        }

        // 重置计数器
        accelerometerDataCount = 0;
        gyroscopeDataCount = 0;
        totalAccelEvents = 0;
        totalGyroEvents = 0;
        lastHeartbeatTime = System.currentTimeMillis();

        // 创建后台线程处理传感器回调
        sensorThread = new HandlerThread("IMUSensorThread");
        // 添加未捕获异常处理器
        sensorThread.setUncaughtExceptionHandler((thread, ex) -> {
            Log.e(TAG, "IMUSensorThread crashed! Thread: " + thread.getName(), ex);
            Log.e(TAG, "Total accel events before crash: " + totalAccelEvents);
            Log.e(TAG, "Total gyro events before crash: " + totalGyroEvents);
        });
        sensorThread.start();
        sensorHandler = new Handler(sensorThread.getLooper());

        // 在后台线程注册传感器监听，避免阻塞主线程
        boolean accelRegistered = sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST, sensorHandler);
        boolean gyroRegistered = sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_FASTEST, sensorHandler);
        isRecording = true;

        Log.d(TAG, "IMU recording started (on background thread)");
        Log.d(TAG, "Accelerometer registered: " + accelRegistered + ", sensor: " + (accelerometer != null ? accelerometer.getName() : "null"));
        Log.d(TAG, "Gyroscope registered: " + gyroRegistered + ", sensor: " + (gyroscope != null ? gyroscope.getName() : "null"));
    }

    public void stopRecording() {
        // 打印调用栈帮助调试
        Log.d(TAG, "stopRecording called at relativeSec=" + TimeSync.nowRelativeSeconds() + ", stack trace:", new Throwable());

        if (!isRecording) {
            Log.w(TAG, "No recording in progress");
            // 打印调用栈帮助调试
            Log.w(TAG, "stopRecording called while not recording, stack trace:", new Throwable());
            return;
        }

        Log.d(TAG, "Stopping IMU recording at relativeSec=" + TimeSync.nowRelativeSeconds());
        Log.d(TAG, "FINAL STATS: totalAccelEvents=" + totalAccelEvents + ", totalGyroEvents=" + totalGyroEvents);
        Log.d(TAG, "sensorThread state: " + (sensorThread != null ? sensorThread.getState().toString() : "null") +
                ", isAlive=" + (sensorThread != null && sensorThread.isAlive()));

        // 停止传感器监听
        sensorManager.unregisterListener(this);
        isRecording = false;

        // 关闭文件写入器
        closeFiles();

        // 停止后台线程
        if (sensorThread != null) {
            sensorThread.quitSafely();
            sensorThread = null;
            sensorHandler = null;
        }

        Log.d(TAG, "IMU recording stopped. Files saved to: " + outputDirectory.getAbsolutePath());
    }

    private boolean initializeFiles() {
        try {
            // 获取实验 ID 和文件存储路径
            SharedPreferences prefs = context.getSharedPreferences("AppSettings", MODE_PRIVATE);
            String experimentId = prefs.getString("experiment_id", "default");

            // 确保会话与统一时基
            SessionManager sessionManager = SessionManager.getInstance();
            outputDirectory = sessionManager.ensureSession(context, experimentId);
            if (outputDirectory == null) return false;
            File imuDir = sessionManager.subDir("imu");

            // 创建文件
            accelerometerFile = new File(imuDir, "imu_accelerometer_data.csv");
            gyroscopeFile = new File(imuDir, "imu_gyroscope_data.csv");

            // 创建写入器（含表头）
            accelerometerWriter = new DataLogger(accelerometerFile,
                    "sensor_ns,wall_ms,relative_s,accel_x,accel_y,accel_z");
            gyroscopeWriter = new DataLogger(gyroscopeFile,
                    "sensor_ns,wall_ms,relative_s,gyro_x,gyro_y,gyro_z");

            return true;
        } catch (IOException e) {
            Log.e(TAG, "Error initializing files: " + e.getMessage());
            e.printStackTrace();
            closeFiles(); // 清理已创建的资源
            return false;
        }
    }

    private void closeFiles() {
        if (accelerometerWriter != null) {
            accelerometerWriter.close();
            accelerometerWriter = null;
        }
        if (gyroscopeWriter != null) {
            gyroscopeWriter.close();
            gyroscopeWriter = null;
        }
    }

    public void setOnDataUpdateListener(OnDataUpdateListener listener) {
        this.dataUpdateListener = listener;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!isRecording || accelerometerWriter == null || gyroscopeWriter == null) {
            // 添加日志帮助调试为什么事件被忽略
            if (!isRecording) {
                Log.w(TAG, "onSensorChanged: isRecording=false, ignoring event");
            } else if (accelerometerWriter == null) {
                Log.w(TAG, "onSensorChanged: accelerometerWriter is null");
            } else if (gyroscopeWriter == null) {
                Log.w(TAG, "onSensorChanged: gyroscopeWriter is null");
            }
            return;
        }

        // 心跳日志：每2秒输出一次当前状态
        long now = System.currentTimeMillis();
        if (now - lastHeartbeatTime >= HEARTBEAT_INTERVAL_MS) {
            double relSec = TimeSync.nowRelativeSeconds();
            Log.d(TAG, String.format("IMU HEARTBEAT at relSec=%.2f: accelEvents=%d, gyroEvents=%d, thread=%s, threadAlive=%b",
                    relSec, totalAccelEvents, totalGyroEvents,
                    sensorThread != null ? sensorThread.getName() : "null",
                    sensorThread != null && sensorThread.isAlive()));
            lastHeartbeatTime = now;
        }

        long sensorTimestamp = event.timestamp; // ns, monotonic (但可能与系统时钟不同步)
        // 使用实际接收时间计算 wallMs 和 relativeSec，而不是传感器时间戳
        // 因为某些设备上 event.timestamp 与 SystemClock.elapsedRealtimeNanos() 不同步
        long wallMs = TimeSync.nowWallMillis();
        double relativeSec = TimeSync.nowRelativeSeconds();
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];

        try {
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                totalAccelEvents++;
                String data = String.format(Locale.getDefault(), "%d,%d,%.6f,%f,%f,%f",
                        sensorTimestamp, wallMs, relativeSec, x, y, z);
                accelerometerWriter.writeLine(data);

                // 保存最新数据用于UI更新
                lastAccelData = String.format(Locale.getDefault(), "x: %f, y: %f, z: %f", x, y, z);

                accelerometerDataCount++;
                if (accelerometerDataCount >= UPDATE_INTERVAL) {
                    accelerometerWriter.flush(); // 定期刷新到磁盘
                    notifyDataUpdate();
                    accelerometerDataCount = 0;
                }

            } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                totalGyroEvents++;
                // 直接写入到文件
                String data = String.format(Locale.getDefault(), "%d,%d,%.6f,%f,%f,%f",
                        sensorTimestamp, wallMs, relativeSec, x, y, z);
                gyroscopeWriter.writeLine(data);

                // 保存最新数据用于UI更新
                lastGyroData = String.format(Locale.getDefault(), "x: %f, y: %f, z: %f", x, y, z);

                gyroscopeDataCount++;
                if (gyroscopeDataCount >= UPDATE_INTERVAL) {
                    gyroscopeWriter.flush(); // 定期刷新到磁盘
                    notifyDataUpdate();
                    gyroscopeDataCount = 0;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error writing sensor data: " + e.getMessage());
            // 不要因为单个数据点的写入错误停止整个录制
            // stopRecording(); // 移除：避免一个错误终止所有数据采集
        }
    }

    private void notifyDataUpdate() {
        if (dataUpdateListener != null && !lastAccelData.isEmpty() && !lastGyroData.isEmpty()) {
            // 捕获当前数据以在主线程使用
            final String accel = lastAccelData;
            final String gyro = lastGyroData;
            // 切换到主线程更新UI
            mainHandler.post(() -> {
                if (dataUpdateListener != null) {
                    dataUpdateListener.onDataUpdate(accel, gyro);
                }
            });
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Handle changes in sensor accuracy if needed
        Log.d(TAG, "Sensor accuracy changed: " + sensor.getName() + ", accuracy: " + accuracy);
    }

    private String generateTimestamp() {
        return String.valueOf(System.currentTimeMillis());
    }

    // 工具方法保留
    public static String extractLastDigits(String input, int numberOfDigits) {
        if (input == null || input.length() < numberOfDigits) {
            return null;
        }
        return input.substring(input.length() - numberOfDigits);
    }

    public static String extractFirstDigits(String input, int numberOfDigits) {
        if (input == null || input.length() < numberOfDigits || numberOfDigits < 0) {
            return null;
        }
        return input.substring(0, numberOfDigits);
    }

    // 获取当前录制状态
    public boolean isRecording() {
        return isRecording;
    }

    // 强制刷新缓冲区到磁盘（可在需要时调用）
    public void flushToDisk() {
        if (isRecording) {
            if (accelerometerWriter != null) {
                accelerometerWriter.flush();
            }
            if (gyroscopeWriter != null) {
                gyroscopeWriter.flush();
            }
        }
    }

    public interface OnDataUpdateListener {
        void onDataUpdate(String accelData, String gyroData);
    }
}
