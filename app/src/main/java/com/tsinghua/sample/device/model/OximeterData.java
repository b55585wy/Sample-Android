package com.tsinghua.sample.device.model;

public class OximeterData {
    public long timestamp;
    public int bvp = -1;
    public int hr = -1;
    public int spo2 = -1;

    public OximeterData(long timestamp) {
        this.timestamp = timestamp;
    }
}
