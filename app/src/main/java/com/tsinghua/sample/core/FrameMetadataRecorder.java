package com.tsinghua.sample.core;

import java.io.File;

/**
 * 帧元数据记录：wall_ms, sensor_ts_sec, relative_sec, frameNumber
 */
public class FrameMetadataRecorder {
    private final DataLogger logger;

    public FrameMetadataRecorder(File file) throws Exception {
        this.logger = new DataLogger(file, "wall_ms,sensor_ts_sec,relative_sec,frame_number");
    }

    public void record(long sensorTimestampNs, long frameNumber) {
        long wallMs = TimeSync.sensorNsToWallMillis(sensorTimestampNs);
        double sensorSec = sensorTimestampNs / 1_000_000_000.0;
        double relativeSec = TimeSync.sensorNsToRelativeSeconds(sensorTimestampNs);
        String line = wallMs + "," + sensorSec + "," + relativeSec + "," + frameNumber;
        logger.writeLine(line);
    }

    public void close() {
        logger.close();
    }

    public void flush() {
        logger.flush();
    }
}
