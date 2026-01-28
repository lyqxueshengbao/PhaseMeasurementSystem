package com.example.pj125.device;

import com.example.pj125.common.ErrorCode;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Placeholder for future remote (HTTP/agent based) device implementation.
 * <p>
 * This is intentionally minimal and only used when endpoint != local-sim.
 */
public class RemoteDevicePlaceholder implements Device {
    private final DeviceId id;
    private final String endpoint;
    private final DeviceInfo info;
    private final Object lock = new Object();
    private final DeviceStatus status;

    public RemoteDevicePlaceholder(DeviceId id, String endpoint) {
        this.id = id;
        this.endpoint = endpoint;
        this.info = new DeviceInfo();
        this.info.setDeviceId(id);
        this.info.setModel("RemoteDevicePlaceholder");
        this.info.setSerialNumber("REMOTE-" + id.name());
        this.info.setFirmwareVersion("N/A");
        this.info.setProtocolVersion("REMOTE-TODO");

        this.status = new DeviceStatus();
        this.status.setDeviceId(id);
        this.status.setConnected(false);
        this.status.setVersion("remote-todo");
        this.status.setOpState(OpState.OFFLINE);
        this.status.setLockState(LockState.UNLOCKED);
        this.status.setTemperatureC(0.0);
        this.status.setAlarms(new ArrayList<>(List.of("远程设备未实现: " + endpoint)));
        this.status.setLastUpdatedTs(OffsetDateTime.now().toString());
        this.status.setLastErrorCode(ErrorCode.DEVICE_ERROR.name());
        this.status.setLastErrorMessage("远程设备未实现: " + endpoint);
    }

    @Override
    public DeviceId id() {
        return id;
    }

    @Override
    public DeviceInfo info() {
        return info;
    }

    @Override
    public DeviceStatus status() {
        synchronized (lock) {
            status.setLastUpdatedTs(OffsetDateTime.now().toString());
            return copy(status);
        }
    }

    @Override
    public ErrorCode connect() {
        return ErrorCode.DEVICE_OFFLINE;
    }

    @Override
    public ErrorCode disconnect() {
        return ErrorCode.OK;
    }

    @Override
    public ErrorCode configure(DeviceConfig config) {
        return ErrorCode.DEVICE_OFFLINE;
    }

    @Override
    public ErrorCode apply() {
        return ErrorCode.DEVICE_OFFLINE;
    }

    @Override
    public ErrorCode startLock() {
        return ErrorCode.DEVICE_OFFLINE;
    }

    @Override
    public ErrorCode startMeasurement() {
        return ErrorCode.DEVICE_OFFLINE;
    }

    @Override
    public ErrorCode stop() {
        return ErrorCode.OK;
    }

    private static DeviceStatus copy(DeviceStatus s) {
        DeviceStatus c = new DeviceStatus();
        c.setDeviceId(s.getDeviceId());
        c.setOpState(s.getOpState());
        c.setLockState(s.getLockState());
        c.setConnected(s.isConnected());
        c.setVersion(s.getVersion());
        c.setTemperatureC(s.getTemperatureC());
        c.setAlarms(s.getAlarms() == null ? new ArrayList<>() : new ArrayList<>(s.getAlarms()));
        c.setLastUpdatedTs(s.getLastUpdatedTs());
        c.setLastErrorCode(s.getLastErrorCode());
        c.setLastErrorMessage(s.getLastErrorMessage());
        c.setRttMs(s.getRttMs());
        return c;
    }
}
