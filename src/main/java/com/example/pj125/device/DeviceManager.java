package com.example.pj125.device;

import com.example.pj125.common.HashUtils;
import com.example.pj125.common.SimulatorProperties;
import com.example.pj125.measurement.MeasurementMode;
import com.example.pj125.recipe.SimulatorProfile;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

@Service
public class DeviceManager implements DeviceGateway, FaultInjectionCapability {
    private final Map<DeviceId, Device> devices = new LinkedHashMap<>();
    private final SimulatorProperties simulatorProperties;
    private final DevicesProperties devicesProperties;

    public DeviceManager(SimulatorProperties simulatorProperties, DevicesProperties devicesProperties) {
        this.simulatorProperties = simulatorProperties;
        this.devicesProperties = devicesProperties;

        devices.put(DeviceId.MAIN, createDevice(DeviceId.MAIN, devicesProperties.getMain().getEndpoint()));
        devices.put(DeviceId.RELAY, createDevice(DeviceId.RELAY, devicesProperties.getRelay().getEndpoint()));
    }

    private Device createDevice(DeviceId id, String endpoint) {
        String ep = endpoint == null ? "" : endpoint.trim();
        if (ep.isEmpty() || ep.equalsIgnoreCase("local-sim")) {
            return switch (id) {
                case MAIN -> new SimulatedMainStation(simulatorProperties);
                case RELAY -> new SimulatedRelayStation(simulatorProperties);
            };
        }
        return new RemoteDevicePlaceholder(id, ep);
    }

    @Override
    public Device get(DeviceId id) {
        return devices.get(id);
    }

    @Override
    public Map<DeviceId, DeviceStatus> statuses() {
        Map<DeviceId, DeviceStatus> m = new LinkedHashMap<>();
        for (Map.Entry<DeviceId, Device> e : devices.entrySet()) {
            m.put(e.getKey(), e.getValue().status());
        }
        return m;
    }

    @Override
    public Map<DeviceId, DeviceInfo> infos() {
        Map<DeviceId, DeviceInfo> m = new LinkedHashMap<>();
        for (Map.Entry<DeviceId, Device> e : devices.entrySet()) {
            m.put(e.getKey(), e.getValue().info());
        }
        return m;
    }

    /**
     * Apply simulator profile for the next run (only works for simulated devices).
     * If profile is null, reset to application.yml simulator defaults.
     */
    @Override
    public void applySimulatorProfile(SimulatorProfile profile) {
        for (Device d : devices.values()) {
            if (d instanceof SimulatedStation s) {
                s.applyProfile(simulatorProperties);
                if (profile != null) {
                    s.applyProfileOverride(profile.getApplyDelayMs(), profile.getLockTimeMs(), profile.getMeasurementTimeMs(),
                            profile.getFaultType(), profile.getRandomLostLockProbability());
                }
            }
        }
    }

    @Override
    public Optional<FaultInjectionCapability> faultInjection() {
        return Optional.of(this);
    }

    @Override
    public void maybeInjectLostLock(String runId, String recipeId, MeasurementMode mode, int repeatIndex) {
        Device main = get(DeviceId.MAIN);
        if (!(main instanceof SimulatedStation s)) return;
        if (s.getFaultType() != SimulatorProperties.FaultType.RANDOM_LOST_LOCK) return;

        String seedKey = runId + "|" + recipeId + "|FAULT|" + mode.name() + "|" + repeatIndex;
        long seed = HashUtils.seedFromKey(seedKey);
        Random r = new Random(seed);
        if (r.nextDouble() < s.getRandomLostLockProbability()) {
            s.forceLockLost("RANDOM_LOST_LOCK触发");
        }
    }
}
