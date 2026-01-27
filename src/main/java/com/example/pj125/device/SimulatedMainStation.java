package com.example.pj125.device;

import com.example.pj125.common.SimulatorProperties;

public class SimulatedMainStation extends SimulatedStation {
    public SimulatedMainStation(SimulatorProperties props) {
        super(DeviceId.MAIN, "SimulatedMainStation", "SIM-MAIN-001", "sim-1.0.0", props);
    }
}

