package com.example.pj125.device;

public class DeviceConfig {
    private boolean txEnable = true;

    /**
     * Hz: DDS output frequency control word (only MAIN is effective; RELAY will ignore).
     * Null means "not set / default".
     */
    private Double ddsFreqHz;

    /**
     * ns: internal reference path delay
     */
    private double referencePathDelayNs;

    /**
     * ns: internal measurement path delay
     */
    private double measurePathDelayNs;

    public boolean isTxEnable() {
        return txEnable;
    }

    public void setTxEnable(boolean txEnable) {
        this.txEnable = txEnable;
    }

    public Double getDdsFreqHz() {
        return ddsFreqHz;
    }

    public void setDdsFreqHz(Double ddsFreqHz) {
        this.ddsFreqHz = ddsFreqHz;
    }

    public double getReferencePathDelayNs() {
        return referencePathDelayNs;
    }

    public void setReferencePathDelayNs(double referencePathDelayNs) {
        this.referencePathDelayNs = referencePathDelayNs;
    }

    public double getMeasurePathDelayNs() {
        return measurePathDelayNs;
    }

    public void setMeasurePathDelayNs(double measurePathDelayNs) {
        this.measurePathDelayNs = measurePathDelayNs;
    }
}
