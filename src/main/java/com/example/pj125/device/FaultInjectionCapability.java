package com.example.pj125.device;

import com.example.pj125.measurement.MeasurementMode;

/**
 * Optional capability for deterministic fault injection used by simulator-only recipes.
 * <p>
 * Real-device gateways can simply not expose this capability.
 */
public interface FaultInjectionCapability {
    /**
     * Deterministically inject RANDOM_LOST_LOCK for the current repeat, if enabled.
     */
    void maybeInjectLostLock(String runId, String recipeId, MeasurementMode mode, int repeatIndex);
}

