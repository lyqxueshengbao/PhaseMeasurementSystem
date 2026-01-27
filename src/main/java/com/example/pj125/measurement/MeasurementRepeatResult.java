package com.example.pj125.measurement;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MeasurementRepeatResult {
    /**
     * ISO-8601 timestamp for this measurement result.
     */
    private String ts;
    private MeasurementMode mode;
    private int repeatIndex;

    /**
     * ns
     */
    private double delayNs;

    /**
     * deg, recommend range [-180, 180]
     */
    private double phaseDeg;

    /**
     * 0~1
     */
    private double confidence;

    private QualityFlag qualityFlag;

    private Double snrDb;
    private List<String> flags = new ArrayList<>();
    private Map<String, Object> explain;

    public String getTs() {
        return ts;
    }

    public void setTs(String ts) {
        this.ts = ts;
    }

    public MeasurementMode getMode() {
        return mode;
    }

    public void setMode(MeasurementMode mode) {
        this.mode = mode;
    }

    public int getRepeatIndex() {
        return repeatIndex;
    }

    public void setRepeatIndex(int repeatIndex) {
        this.repeatIndex = repeatIndex;
    }

    public double getDelayNs() {
        return delayNs;
    }

    public void setDelayNs(double delayNs) {
        this.delayNs = delayNs;
    }

    public double getPhaseDeg() {
        return phaseDeg;
    }

    public void setPhaseDeg(double phaseDeg) {
        this.phaseDeg = phaseDeg;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public QualityFlag getQualityFlag() {
        return qualityFlag;
    }

    public void setQualityFlag(QualityFlag qualityFlag) {
        this.qualityFlag = qualityFlag;
    }

    public Double getSnrDb() {
        return snrDb;
    }

    public void setSnrDb(Double snrDb) {
        this.snrDb = snrDb;
    }

    public List<String> getFlags() {
        return flags;
    }

    public void setFlags(List<String> flags) {
        this.flags = flags;
    }

    public Map<String, Object> getExplain() {
        return explain;
    }

    public void setExplain(Map<String, Object> explain) {
        this.explain = explain;
    }
}
