package com.example.pj125.device;

import com.example.pj125.common.SimulatorProperties;

public class SimulatedRelayStation extends SimulatedStation {
    public SimulatedRelayStation(SimulatorProperties props) {
        super(DeviceId.RELAY, "SimulatedRelayStation", "SIM-RELAY-001", "sim-1.0.0", props);
    }
}

