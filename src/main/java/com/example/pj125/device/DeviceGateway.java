package com.example.pj125.device;

import com.example.pj125.recipe.SimulatorProfile;

import java.util.Map;
import java.util.Optional;

/**
 * Abstraction for accessing main/relay devices.
 * <p>
 * Current implementation is in-process simulator, but this interface is kept so
 * future deployments can swap to remote HTTP/agent based devices without
 * rewriting orchestrator logic.
 */
public interface DeviceGateway {
    Device get(DeviceId id);

    Map<DeviceId, DeviceStatus> statuses();

    Map<DeviceId, DeviceInfo> infos();

    void applySimulatorProfile(SimulatorProfile profile);

    /**
     * Optional capability: simulator-only fault injection (e.g. RANDOM_LOST_LOCK).
     */
    default Optional<FaultInjectionCapability> faultInjection() {
        return Optional.empty();
    }
}
