package com.example.xalute;
import java.io.Serializable;


public class EcgData implements Serializable {
    private int ecgValue;
    private long timestamp;


    public EcgData(int ecgValue, long timestamp) {
        this.ecgValue = ecgValue;
        this.timestamp = timestamp;
    }

    public int getEcgValue() {
        return ecgValue;
    }

    public void setEcgValue(int ecgValue) {
        this.ecgValue = ecgValue;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

}