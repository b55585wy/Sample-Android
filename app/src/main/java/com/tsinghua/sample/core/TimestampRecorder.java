package com.tsinghua.sample.core;

import java.io.File;

/**
 * 手动标记记录器，对齐 iOS 版：timestamp,marker_id,label,description
 * timestamp 使用 wall_ms，便于跨设备对齐。
 */
public class TimestampRecorder {
    private final DataLogger logger;
    private int markerCount = 0;

    public TimestampRecorder(File file) throws Exception {
        this.logger = new DataLogger(file, "timestamp_ms,marker_id,label,description");
    }

    public synchronized void addMarker(String label, String description) {
        markerCount++;
        long wallMs = TimeSync.nowWallMillis();
        String safeLabel = label == null ? "" : label;
        String safeDesc = description == null ? "" : description;
        String line = wallMs + "," + markerCount + "," + safeLabel + "," + safeDesc;
        logger.writeLine(line);
    }

    public void close() {
        logger.close();
    }
}
