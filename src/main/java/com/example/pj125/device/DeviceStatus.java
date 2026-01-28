package com.example.pj125.device;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class DeviceStatus {
    private DeviceId deviceId;

    /**
     * OFFLINE | IDLE | READY | BUSY | ERROR
     */
    private OpState opState;

    /**
     * UNLOCKED | LOCKING | LOCKED | LOST
     */
    private LockState lockState;

    private boolean connected;
    private String version;
    private double temperatureC;
    private List<String> alarms = new ArrayList<>();

    private String lastUpdatedTs = OffsetDateTime.now().toString();
    private String lastErrorCode;
    private String lastErrorMessage;

    /**
     * Optional: round-trip time measured by gateway when probing device status (ms).
     * Null means not measured.
     */
    private Long rttMs;

    public DeviceId getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(DeviceId deviceId) {
        this.deviceId = deviceId;
    }

    public OpState getOpState() {
        return opState;
    }

    public void setOpState(OpState opState) {
        this.opState = opState;
    }

    public LockState getLockState() {
        return lockState;
    }

    public void setLockState(LockState lockState) {
        this.lockState = lockState;
    }

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public double getTemperatureC() {
        return temperatureC;
    }

    public void setTemperatureC(double temperatureC) {
        this.temperatureC = temperatureC;
    }

    public List<String> getAlarms() {
        return alarms;
    }

    public void setAlarms(List<String> alarms) {
        this.alarms = alarms;
    }

    public String getLastUpdatedTs() {
        return lastUpdatedTs;
    }

    public void setLastUpdatedTs(String lastUpdatedTs) {
        this.lastUpdatedTs = lastUpdatedTs;
    }

    public String getLastErrorCode() {
        return lastErrorCode;
    }

    public void setLastErrorCode(String lastErrorCode) {
        this.lastErrorCode = lastErrorCode;
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    public void setLastErrorMessage(String lastErrorMessage) {
        this.lastErrorMessage = lastErrorMessage;
    }

    public Long getRttMs() {
        return rttMs;
    }

    public void setRttMs(Long rttMs) {
        this.rttMs = rttMs;
    }
}
