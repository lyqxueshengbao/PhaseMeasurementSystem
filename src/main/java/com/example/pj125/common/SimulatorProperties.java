package com.example.pj125.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "simulator")
public class SimulatorProperties {
    private long applyDelayMs = 300;
    private long lockTimeMs = 1200;
    private long measurementTimeMs = 800;
    private long waitLockedTimeoutMs = 5000;
    private Fault fault = new Fault();

    public long getApplyDelayMs() {
        return applyDelayMs;
    }

    public void setApplyDelayMs(long applyDelayMs) {
        this.applyDelayMs = applyDelayMs;
    }

    public long getLockTimeMs() {
        return lockTimeMs;
    }

    public void setLockTimeMs(long lockTimeMs) {
        this.lockTimeMs = lockTimeMs;
    }

    public long getMeasurementTimeMs() {
        return measurementTimeMs;
    }

    public void setMeasurementTimeMs(long measurementTimeMs) {
        this.measurementTimeMs = measurementTimeMs;
    }

    public Fault getFault() {
        return fault;
    }

    public void setFault(Fault fault) {
        this.fault = fault;
    }

    public long getWaitLockedTimeoutMs() {
        return waitLockedTimeoutMs;
    }

    public void setWaitLockedTimeoutMs(long waitLockedTimeoutMs) {
        this.waitLockedTimeoutMs = waitLockedTimeoutMs;
    }

    public static class Fault {
        private FaultType type = FaultType.NONE;
        private double randomLostLockProbability = 0.5;

        public FaultType getType() {
            return type;
        }

        public void setType(FaultType type) {
            this.type = type;
        }

        public double getRandomLostLockProbability() {
            return randomLostLockProbability;
        }

        public void setRandomLostLockProbability(double randomLostLockProbability) {
            this.randomLostLockProbability = randomLostLockProbability;
        }
    }

    public enum FaultType {
        NONE,
        LOCK_TIMEOUT,
        DEVICE_BUSY,
        RANDOM_LOST_LOCK
    }
}

