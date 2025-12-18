package com.tsinghua.sample.core;

import android.os.SystemClock;
import android.util.Log;

/**
 * 统一时间基准（与iOS FacialCollection对齐）
 *
 * 时间戳说明：
 * - wallMillis: System.currentTimeMillis()（UTC墙钟时间）
 * - elapsedNanos: SystemClock.elapsedRealtimeNanos()（单调时钟，不受系统时间调整影响）
 *
 * 用于将传感器/相机的 sensorTimestamp(ns, monotonic) 对齐到统一毫秒时间戳。
 *
 * 使用方式：
 * 1. App启动时调用 initializeGlobal() 建立全局时间基准
 * 2. 会话开始时调用 startSessionClock() 重新同步（可选）
 * 3. 各模块使用 nowWallMillis() 获取统一时间戳
 * 4. 传感器数据使用 sensorNsToWallMillis() 转换
 *
 * 重要：所有模块必须使用此类的方法获取时间戳，禁止直接使用 System.currentTimeMillis()
 */
public class TimeSync {
    private static final String TAG = "TimeSync";

    private static volatile long startWallMillis;
    private static volatile long startElapsedNanos;
    private static volatile boolean initialized = false;
    private static volatile long sessionStartWallMillis;  // 当前会话开始时间
    private static volatile int sessionCount = 0;  // 会话计数，用于调试

    /**
     * 全局初始化（在Application或主Activity的onCreate中调用）
     * 建立全局时间基准，确保所有模块使用统一时间戳
     */
    public static synchronized void initializeGlobal() {
        if (!initialized) {
            startElapsedNanos = SystemClock.elapsedRealtimeNanos();
            startWallMillis = System.currentTimeMillis();
            sessionStartWallMillis = startWallMillis;
            initialized = true;
            Log.i(TAG, "Global time sync initialized: wallMs=" + startWallMillis
                    + ", elapsedNs=" + startElapsedNanos);
        } else {
            Log.d(TAG, "Global time sync already initialized, skipping");
        }
    }

    /**
     * 在录制/会话开始时调用，重新同步时间基准
     * 这会更新时间基准，但保持已初始化状态
     */
    public static synchronized void startSessionClock() {
        // 同时采样两个时钟，尽量减少误差
        startElapsedNanos = SystemClock.elapsedRealtimeNanos();
        startWallMillis = System.currentTimeMillis();
        sessionStartWallMillis = startWallMillis;
        sessionCount++;
        initialized = true;
        Log.i(TAG, "Session clock started (session #" + sessionCount + "): wallMs="
                + startWallMillis + ", elapsedNs=" + startElapsedNanos);
    }

    /** 重置时钟（会话结束时可选调用） */
    public static synchronized void reset() {
        // 注意：reset不会将initialized设为false，保持全局时间基准
        // 只重置会话相关的状态
        sessionStartWallMillis = 0;
        Log.i(TAG, "Session clock reset (global time sync remains active)");
    }

    /** 完全重置（仅用于测试或特殊情况） */
    public static synchronized void resetAll() {
        initialized = false;
        startWallMillis = 0;
        startElapsedNanos = 0;
        sessionStartWallMillis = 0;
        sessionCount = 0;
        Log.i(TAG, "TimeSync fully reset");
    }

    public static boolean isInitialized() {
        return initialized;
    }

    /** 获取当前会话编号 */
    public static int getSessionCount() {
        return sessionCount;
    }

    /** 获取会话开始的墙钟时间（毫秒） */
    public static long getSessionStartWallMillis() {
        ensure();
        return startWallMillis;
    }

    /** 将 sensorTimestamp（ns，基于 elapsedRealtime）转换为 wall 毫秒。 */
    public static long sensorNsToWallMillis(long sensorTimestampNs) {
        ensure();
        long deltaNs = sensorTimestampNs - startElapsedNanos;
        return startWallMillis + deltaNs / 1_000_000L;
    }

    /** 将 sensorTimestamp（ns）转换为相对秒（浮点）。 */
    public static double sensorNsToRelativeSeconds(long sensorTimestampNs) {
        ensure();
        long deltaNs = sensorTimestampNs - startElapsedNanos;
        return deltaNs / 1_000_000_000.0;
    }

    /** 获取当前 wall 毫秒（统一时间戳，所有模块应使用此方法）。 */
    public static long nowWallMillis() {
        ensure();
        long deltaNs = SystemClock.elapsedRealtimeNanos() - startElapsedNanos;
        return startWallMillis + deltaNs / 1_000_000L;
    }

    /** 获取当前相对于会话开始的秒数。 */
    public static double nowRelativeSeconds() {
        ensure();
        long deltaNs = SystemClock.elapsedRealtimeNanos() - startElapsedNanos;
        return deltaNs / 1_000_000_000.0;
    }

    /** 获取当前 elapsedRealtimeNanos（用于传感器时间戳比较）。 */
    public static long nowElapsedNanos() {
        return SystemClock.elapsedRealtimeNanos();
    }

    private static void ensure() {
        if (!initialized) {
            Log.w(TAG, "TimeSync not initialized, auto-starting session clock");
            startSessionClock();
        }
    }

    /**
     * 打印当前时间状态（用于调试时间戳对齐）
     * 各模块在启动录制时可调用此方法验证时间基准一致
     */
    public static void logTimestampStatus(String moduleName) {
        if (!initialized) {
            Log.w(TAG, "[" + moduleName + "] TimeSync not yet initialized!");
            return;
        }
        long currentWall = nowWallMillis();
        long elapsedSinceStart = currentWall - sessionStartWallMillis;
        double relativeSec = nowRelativeSeconds();

        Log.i(TAG, "[" + moduleName + "] Timestamp status: " +
                "wallMs=" + currentWall +
                ", sessionStart=" + sessionStartWallMillis +
                ", elapsedMs=" + elapsedSinceStart +
                ", relativeSec=" + String.format("%.3f", relativeSec) +
                ", session#" + sessionCount);
    }

    /**
     * 验证传感器时间戳转换（用于调试）
     */
    public static void verifySensorTimestamp(String moduleName, long sensorNs) {
        if (!initialized) {
            Log.w(TAG, "[" + moduleName + "] Cannot verify - TimeSync not initialized");
            return;
        }
        long wallMs = sensorNsToWallMillis(sensorNs);
        double relativeSec = sensorNsToRelativeSeconds(sensorNs);
        long drift = Math.abs(wallMs - nowWallMillis());

        Log.d(TAG, "[" + moduleName + "] Sensor timestamp: " +
                "sensorNs=" + sensorNs +
                " -> wallMs=" + wallMs +
                ", relativeSec=" + String.format("%.3f", relativeSec) +
                ", driftMs=" + drift);

        if (drift > 1000) {
            Log.w(TAG, "[" + moduleName + "] WARNING: Large drift detected (" + drift + "ms)");
        }
    }
}
