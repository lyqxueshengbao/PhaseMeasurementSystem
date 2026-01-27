package com.example.pj125.run;

import com.example.pj125.device.DeviceConfig;

public class RunInfo {
    private String runId;
    private String recipeId;
    private String startedAt;
    private String endedAt;
    private RunState state;
    private RunStep step;
    private String lastErrorCode;
    private String lastErrorMessage;

    /**
     * Readback (effective) device config snapshot after APPLY_RECIPE.
     * Used for acceptance of simulator-only fields like MAIN DDS control word.
     */
    private DeviceConfig mainAppliedConfig;
    private DeviceConfig relayAppliedConfig;

    /**
     * Spec field: RUNNING|SUCCEEDED|FAILED|CANCELLED|SAFE.
     * Current implementation maps state: RUNNING->RUNNING, DONE->SUCCEEDED, FAILED->FAILED.
     */
    public String getStatus() {
        if (state == null) return null;
        return switch (state) {
            case RUNNING -> "RUNNING";
            case DONE -> "SUCCEEDED";
            case FAILED -> "FAILED";
        };
    }

    /**
     * Spec field: error summary object; null if not failed.
     */
    public ErrorSummary getError() {
        if (state != RunState.FAILED) return null;
        if (lastErrorCode == null && lastErrorMessage == null) return null;
        return new ErrorSummary(lastErrorCode, lastErrorMessage);
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public String getRecipeId() {
        return recipeId;
    }

    public void setRecipeId(String recipeId) {
        this.recipeId = recipeId;
    }

    public String getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(String startedAt) {
        this.startedAt = startedAt;
    }

    public String getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(String endedAt) {
        this.endedAt = endedAt;
    }

    public RunState getState() {
        return state;
    }

    public void setState(RunState state) {
        this.state = state;
    }

    public RunStep getStep() {
        return step;
    }

    public void setStep(RunStep step) {
        this.step = step;
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

    public DeviceConfig getMainAppliedConfig() {
        return mainAppliedConfig;
    }

    public void setMainAppliedConfig(DeviceConfig mainAppliedConfig) {
        this.mainAppliedConfig = mainAppliedConfig;
    }

    public DeviceConfig getRelayAppliedConfig() {
        return relayAppliedConfig;
    }

    public void setRelayAppliedConfig(DeviceConfig relayAppliedConfig) {
        this.relayAppliedConfig = relayAppliedConfig;
    }

    public static class ErrorSummary {
        private String errorCode;
        private String message;

        public ErrorSummary() {
        }

        public ErrorSummary(String errorCode, String message) {
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
