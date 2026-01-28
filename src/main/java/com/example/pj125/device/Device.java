package com.example.pj125.device;

import com.example.pj125.common.ErrorCode;

public interface Device {
    DeviceId id();

    DeviceInfo info();

    DeviceStatus status();

    /**
     * Lightweight liveness probe. Default is to reuse {@link #status()}.
     * Remote implementations may override to provide an actual ping/heartbeat call.
     */
    default DeviceStatus ping() {
        return status();
    }

    /**
     * Read back the current effective config. For devices that don't support it, returns null.
     */
    default DeviceConfig readbackConfig() {
        return null;
    }

    /**
     * Connect device (simulation or real).
     */
    ErrorCode connect();

    ErrorCode disconnect();

    /**
     * Just set config buffer, not take effect.
     */
    ErrorCode configure(DeviceConfig config);

    /**
     * Apply buffered config to device; may take applyDelayMs.
     */
    ErrorCode apply();

    /**
     * Start locking process; device enters LOCKING.
     */
    ErrorCode startLock();

    /**
     * Enter BUSY for measurement; may take measurementTimeMs.
     */
    ErrorCode startMeasurement();

    /**
     * Stop and enter SAFE state (stop capture/tx etc).
     */
    ErrorCode stop();
}
