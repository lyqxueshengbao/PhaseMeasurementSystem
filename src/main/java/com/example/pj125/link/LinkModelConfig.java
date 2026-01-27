package com.example.pj125.link;

public class LinkModelConfig {
    /**
     * ns: fixed link delay component.
     */
    private double fixedLinkDelayNs;

    /**
     * ppm: drift coefficient, used as deterministic slow variation in simulation.
     */
    private double driftPpm;

    /**
     * ns: noise standard deviation (Gaussian).
     */
    private double noiseStdNs;

    /**
     * deg: base phase offset.
     */
    private double basePhaseDeg;

    public double getFixedLinkDelayNs() {
        return fixedLinkDelayNs;
    }

    public void setFixedLinkDelayNs(double fixedLinkDelayNs) {
        this.fixedLinkDelayNs = fixedLinkDelayNs;
    }

    public double getDriftPpm() {
        return driftPpm;
    }

    public void setDriftPpm(double driftPpm) {
        this.driftPpm = driftPpm;
    }

    public double getNoiseStdNs() {
        return noiseStdNs;
    }

    public void setNoiseStdNs(double noiseStdNs) {
        this.noiseStdNs = noiseStdNs;
    }

    public double getBasePhaseDeg() {
        return basePhaseDeg;
    }

    public void setBasePhaseDeg(double basePhaseDeg) {
        this.basePhaseDeg = basePhaseDeg;
    }
}

