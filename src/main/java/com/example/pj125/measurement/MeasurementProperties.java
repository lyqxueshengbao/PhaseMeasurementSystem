package com.example.pj125.measurement;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pj125.measurement")
public class MeasurementProperties {
    /**
     * Measurement backend selector.
     * <p>
     * Supported values:
     * <ul>
     *   <li>simulated (default): in-process deterministic simulator</li>
     *   <li>matlab-engine: use MATLAB Engine for Java (local machine integration)</li>
     * </ul>
     */
    private String engine = "simulated";

    /**
     * Local directory containing MATLAB function(s) for integration.
     * Used when engine=matlab-engine.
     * <p>
     * Default assumes you run the server from the repo root and scripts are under ./matlab.
     */
    private String matlabScriptDir = "matlab";

    /**
     * MATLAB measurement model selector (used by matlab/pj125_measure_json.m).
     * <p>
     * Supported values:
     * <ul>
     *   <li>placeholder (default): lightweight statistical model</li>
     *   <li>signal4_6G_mc: run a heavier chain-like Monte Carlo model (LINK mode)</li>
     * </ul>
     */
    private String matlabModel = "placeholder";

    /**
     * Monte Carlo iterations for MATLAB model (only used when matlabModel uses MC).
     */
    private int matlabMonteCarloT = 2000;

    public String getEngine() {
        return engine;
    }

    public void setEngine(String engine) {
        this.engine = engine;
    }

    public String getMatlabScriptDir() {
        return matlabScriptDir;
    }

    public void setMatlabScriptDir(String matlabScriptDir) {
        this.matlabScriptDir = matlabScriptDir;
    }

    public String getMatlabModel() {
        return matlabModel;
    }

    public void setMatlabModel(String matlabModel) {
        this.matlabModel = matlabModel;
    }

    public int getMatlabMonteCarloT() {
        return matlabMonteCarloT;
    }

    public void setMatlabMonteCarloT(int matlabMonteCarloT) {
        this.matlabMonteCarloT = matlabMonteCarloT;
    }
}
