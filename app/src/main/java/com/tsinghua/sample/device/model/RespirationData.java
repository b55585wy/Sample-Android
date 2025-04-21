package com.tsinghua.sample.device.model;

public class RespirationData {
    public long timestamp;
    public int resp;

    public RespirationData(long timestamp, int resp) {
        this.timestamp = timestamp;
        this.resp = resp;
    }
}
