package com.example.pj125.device;

import com.example.pj125.common.ErrorCode;
import com.example.pj125.common.SimulatorProperties;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public abstract class SimulatedStation implements Device {
    private final DeviceId id;
    private final DeviceInfo info;

    private final Object lock = new Object();
    private final DeviceStatus status;

    private DeviceConfig bufferedConfig = new DeviceConfig();
    private DeviceConfig appliedConfig = new DeviceConfig();

    private long applyDelayMs;
    private long lockTimeMs;
    private long measurementTimeMs;
    private SimulatorProperties.FaultType faultType;
    private double randomLostLockProbability;

    private long lockCompleteAtEpochMs = -1;
    private long measurementCompleteAtEpochMs = -1;

    protected SimulatedStation(DeviceId id, String model, String serial, String version, SimulatorProperties props) {
        this.id = id;
        this.info = new DeviceInfo();
        this.info.setDeviceId(id);
        this.info.setModel(model);
        this.info.setSerialNumber(serial);
        this.info.setFirmwareVersion(version);
        this.info.setProtocolVersion("SIM-PROTO-1.0");

        this.status = new DeviceStatus();
        this.status.setDeviceId(id);
        this.status.setConnected(false);
        this.status.setVersion(version);
        this.status.setOpState(OpState.OFFLINE);
        this.status.setLockState(LockState.UNLOCKED);
        this.status.setTemperatureC(40.0);
        this.status.setAlarms(new ArrayList<>());

        applyProfile(props);
    }

    public void applyProfile(SimulatorProperties props) {
        synchronized (lock) {
            this.applyDelayMs = props.getApplyDelayMs();
            this.lockTimeMs = props.getLockTimeMs();
            this.measurementTimeMs = props.getMeasurementTimeMs();
            this.faultType = props.getFault().getType();
            this.randomLostLockProbability = props.getFault().getRandomLostLockProbability();
        }
    }

    public void applyProfileOverride(Long applyDelayMs, Long lockTimeMs, Long measurementTimeMs,
                                     SimulatorProperties.FaultType faultType, Double randomLostLockProbability) {
        synchronized (lock) {
            if (applyDelayMs != null) this.applyDelayMs = applyDelayMs;
            if (lockTimeMs != null) this.lockTimeMs = lockTimeMs;
            if (measurementTimeMs != null) this.measurementTimeMs = measurementTimeMs;
            if (faultType != null) this.faultType = faultType;
            if (randomLostLockProbability != null) this.randomLostLockProbability = randomLostLockProbability;
        }
    }

    public DeviceConfig getAppliedConfig() {
        synchronized (lock) {
            return appliedConfig;
        }
    }

    @Override
    public DeviceConfig readbackConfig() {
        return getAppliedConfig();
    }

    public long getMeasurementTimeMs() {
        synchronized (lock) {
            return measurementTimeMs;
        }
    }

    public SimulatorProperties.FaultType getFaultType() {
        synchronized (lock) {
            return faultType;
        }
    }

    public double getRandomLostLockProbability() {
        synchronized (lock) {
            return randomLostLockProbability;
        }
    }

    public void forceLockLost(String reason) {
        synchronized (lock) {
            status.setLockState(LockState.LOST);
            status.setOpState(OpState.ERROR);
            setAlarm("失锁: " + reason);
            touch();
        }
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
            tickLockedIfNeeded();
            tickMeasurementIfNeeded();
            status.setLastUpdatedTs(OffsetDateTime.now().toString());
            return copy(status);
        }
    }

    @Override
    public ErrorCode connect() {
        synchronized (lock) {
            if (status.isConnected()) return ErrorCode.OK;
            status.setConnected(true);
            status.setOpState(OpState.IDLE);
            status.setLockState(LockState.UNLOCKED);
            clearError();
            touch();
            return ErrorCode.OK;
        }
    }

    @Override
    public ErrorCode disconnect() {
        synchronized (lock) {
            status.setConnected(false);
            status.setOpState(OpState.OFFLINE);
            status.setLockState(LockState.UNLOCKED);
            lockCompleteAtEpochMs = -1;
            measurementCompleteAtEpochMs = -1;
            touch();
            return ErrorCode.OK;
        }
    }

    @Override
    public ErrorCode configure(DeviceConfig config) {
        synchronized (lock) {
            if (!status.isConnected()) return ErrorCode.DEVICE_OFFLINE;
            if (status.getOpState() == OpState.BUSY) return ErrorCode.DEVICE_BUSY;
            bufferedConfig = copyAndNormalizeConfig(id, config);
            touch();
            return ErrorCode.OK;
        }
    }

    private static DeviceConfig copyAndNormalizeConfig(DeviceId id, DeviceConfig in) {
        DeviceConfig src = Objects.requireNonNullElseGet(in, DeviceConfig::new);
        DeviceConfig c = new DeviceConfig();
        c.setTxEnable(src.isTxEnable());
        c.setReferencePathDelayNs(src.getReferencePathDelayNs());
        c.setMeasurePathDelayNs(src.getMeasurePathDelayNs());
        if (id == DeviceId.MAIN) {
            c.setDdsFreqHz(src.getDdsFreqHz());
        } else {
            // RELAY ignores DDS control word, but should not fail.
            c.setDdsFreqHz(null);
        }
        return c;
    }

    @Override
    public ErrorCode apply() {
        synchronized (lock) {
            if (!status.isConnected()) return ErrorCode.DEVICE_OFFLINE;
            if (status.getOpState() == OpState.BUSY) return ErrorCode.DEVICE_BUSY;
            if (faultType == SimulatorProperties.FaultType.DEVICE_BUSY) return ErrorCode.DEVICE_BUSY;
            status.setOpState(OpState.BUSY);
            touch();
        }
        sleepQuietly(applyDelayMs);
        synchronized (lock) {
            appliedConfig = bufferedConfig;
            if (status.getLockState() == LockState.LOST) {
                status.setOpState(OpState.ERROR);
            } else {
                status.setOpState(OpState.IDLE);
            }
            touch();
            return ErrorCode.OK;
        }
    }

    @Override
    public ErrorCode startLock() {
        synchronized (lock) {
            if (!status.isConnected()) return ErrorCode.DEVICE_OFFLINE;
            if (status.getOpState() == OpState.BUSY) return ErrorCode.DEVICE_BUSY;
            if (faultType == SimulatorProperties.FaultType.DEVICE_BUSY) return ErrorCode.DEVICE_BUSY;

            status.setLockState(LockState.LOCKING);
            status.setOpState(OpState.IDLE);
            long now = System.currentTimeMillis();
            if (faultType == SimulatorProperties.FaultType.LOCK_TIMEOUT) {
                lockCompleteAtEpochMs = now + lockTimeMs + 60_000;
            } else {
                lockCompleteAtEpochMs = now + lockTimeMs;
            }
            touch();
            return ErrorCode.OK;
        }
    }

    @Override
    public ErrorCode startMeasurement() {
        synchronized (lock) {
            tickLockedIfNeeded();
            if (!status.isConnected()) return ErrorCode.DEVICE_OFFLINE;
            if (status.getOpState() == OpState.BUSY) return ErrorCode.DEVICE_BUSY;
            if (status.getLockState() != LockState.LOCKED) return ErrorCode.LOCK_LOST;
            if (faultType == SimulatorProperties.FaultType.DEVICE_BUSY) return ErrorCode.DEVICE_BUSY;

            status.setOpState(OpState.BUSY);
            measurementCompleteAtEpochMs = System.currentTimeMillis() + measurementTimeMs;
            touch();
            return ErrorCode.OK;
        }
    }

    @Override
    public ErrorCode stop() {
        synchronized (lock) {
            status.setOpState(status.isConnected() ? OpState.IDLE : OpState.OFFLINE);
            if (status.isConnected() && status.getLockState() == LockState.LOST) {
                status.setLockState(LockState.UNLOCKED);
            }
            measurementCompleteAtEpochMs = -1;
            clearAlarm("设备忙");
            touch();
            return ErrorCode.OK;
        }
    }

    private void tickLockedIfNeeded() {
        if (status.getLockState() != LockState.LOCKING) return;
        if (lockCompleteAtEpochMs <= 0) return;
        if (System.currentTimeMillis() < lockCompleteAtEpochMs) return;
        status.setLockState(LockState.LOCKED);
        status.setOpState(OpState.READY);
    }

    private void tickMeasurementIfNeeded() {
        if (status.getOpState() != OpState.BUSY) return;
        if (measurementCompleteAtEpochMs <= 0) return;
        if (System.currentTimeMillis() < measurementCompleteAtEpochMs) return;
        measurementCompleteAtEpochMs = -1;
        if (status.getLockState() == LockState.LOCKED) {
            status.setOpState(OpState.READY);
        } else if (status.getLockState() == LockState.LOST) {
            status.setOpState(OpState.ERROR);
        } else {
            status.setOpState(OpState.IDLE);
        }
    }

    private void setAlarm(String alarm) {
        List<String> alarms = status.getAlarms();
        if (alarms == null) {
            alarms = new ArrayList<>();
            status.setAlarms(alarms);
        }
        if (!alarms.contains(alarm)) alarms.add(alarm);
    }

    private void clearAlarm(String alarm) {
        if (status.getAlarms() == null) return;
        status.getAlarms().remove(alarm);
    }

    private void clearError() {
        status.setLastErrorCode(null);
        status.setLastErrorMessage(null);
        if (status.getAlarms() != null) status.getAlarms().clear();
    }

    private void touch() {
        status.setLastUpdatedTs(OffsetDateTime.now().toString());
    }

    private static void sleepQuietly(long ms) {
        if (ms <= 0) return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
