package com.example.pj125.run;

import java.util.List;

/**
 * SPEC.md 3.8 AtmosphericDelayResult
 */
public class AtmosphericDelayResult {
    private String ts;
    private String formulaVersion;
    /**
     * SUCCEEDED | FAILED
     */
    private String status;
    /**
     * ns; null on failure.
     */
    private Double atmosphericDelayNs;
    /**
     * ns; null on failure.
     */
    private Double uncertaintyNs;
    private InputsSnapshot inputsSnapshot;
    private ErrorInfo error;

    public String getTs() {
        return ts;
    }

    public void setTs(String ts) {
        this.ts = ts;
    }

    public String getFormulaVersion() {
        return formulaVersion;
    }

    public void setFormulaVersion(String formulaVersion) {
        this.formulaVersion = formulaVersion;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Double getAtmosphericDelayNs() {
        return atmosphericDelayNs;
    }

    public void setAtmosphericDelayNs(Double atmosphericDelayNs) {
        this.atmosphericDelayNs = atmosphericDelayNs;
    }

    public Double getUncertaintyNs() {
        return uncertaintyNs;
    }

    public void setUncertaintyNs(Double uncertaintyNs) {
        this.uncertaintyNs = uncertaintyNs;
    }

    public InputsSnapshot getInputsSnapshot() {
        return inputsSnapshot;
    }

    public void setInputsSnapshot(InputsSnapshot inputsSnapshot) {
        this.inputsSnapshot = inputsSnapshot;
    }

    public ErrorInfo getError() {
        return error;
    }

    public void setError(ErrorInfo error) {
        this.error = error;
    }

    public static class InputsSnapshot {
        private ModeStats link;
        private ModeStats mainInternal;
        private ModeStats relayInternal;
        private Integer minValidRequired;
        private List<String> missingModes;

        public ModeStats getLink() {
            return link;
        }

        public void setLink(ModeStats link) {
            this.link = link;
        }

        public ModeStats getMainInternal() {
            return mainInternal;
        }

        public void setMainInternal(ModeStats mainInternal) {
            this.mainInternal = mainInternal;
        }

        public ModeStats getRelayInternal() {
            return relayInternal;
        }

        public void setRelayInternal(ModeStats relayInternal) {
            this.relayInternal = relayInternal;
        }

        public Integer getMinValidRequired() {
            return minValidRequired;
        }

        public void setMinValidRequired(Integer minValidRequired) {
            this.minValidRequired = minValidRequired;
        }

        public List<String> getMissingModes() {
            return missingModes;
        }

        public void setMissingModes(List<String> missingModes) {
            this.missingModes = missingModes;
        }
    }

    public static class ModeStats {
        private Double avgNs;
        private Double stdNs;
        private Integer validCount;

        public Double getAvgNs() {
            return avgNs;
        }

        public void setAvgNs(Double avgNs) {
            this.avgNs = avgNs;
        }

        public Double getStdNs() {
            return stdNs;
        }

        public void setStdNs(Double stdNs) {
            this.stdNs = stdNs;
        }

        public Integer getValidCount() {
            return validCount;
        }

        public void setValidCount(Integer validCount) {
            this.validCount = validCount;
        }
    }

    public static class ErrorInfo {
        private String errorCode;
        private String message;

        public ErrorInfo() {
        }

        public ErrorInfo(String errorCode, String message) {
            this.errorCode = errorCode;
            this.message = message;
        }

        public String getErrorCode() {
            return errorCode;
        }

        public void setErrorCode(String errorCode) {
            this.errorCode = errorCode;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}

