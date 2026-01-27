package com.example.pj125.recipe;

import com.example.pj125.common.SimulatorProperties;

public class SimulatorProfile {
    private Long applyDelayMs;
    private Long lockTimeMs;
    private Long measurementTimeMs;
    private SimulatorProperties.FaultType faultType;
    private Double randomLostLockProbability;

    public Long getApplyDelayMs() {
        return applyDelayMs;
    }

    public void setApplyDelayMs(Long applyDelayMs) {
        this.applyDelayMs = applyDelayMs;
    }

    public Long getLockTimeMs() {
        return lockTimeMs;
    }

    public void setLockTimeMs(Long lockTimeMs) {
        this.lockTimeMs = lockTimeMs;
    }

    public Long getMeasurementTimeMs() {
        return measurementTimeMs;
    }

    public void setMeasurementTimeMs(Long measurementTimeMs) {
        this.measurementTimeMs = measurementTimeMs;
    }

    public SimulatorProperties.FaultType getFaultType() {
        return faultType;
    }

    public void setFaultType(SimulatorProperties.FaultType faultType) {
        this.faultType = faultType;
    }

    public Double getRandomLostLockProbability() {
        return randomLostLockProbability;
    }

    public void setRandomLostLockProbability(Double randomLostLockProbability) {
        this.randomLostLockProbability = randomLostLockProbability;
    }
}

