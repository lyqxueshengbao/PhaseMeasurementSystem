package com.example.pj125.measurement;

import com.example.pj125.device.DeviceConfig;
import com.example.pj125.link.LinkModelConfig;

public interface MeasurementEngine {
    MeasurementRepeatResult simulate(String runId,
                                    String recipeId,
                                    MeasurementMode mode,
                                    int repeatIndex,
                                    DeviceConfig mainConfigApplied,
                                    DeviceConfig relayConfigApplied,
                                    LinkModelConfig linkModelConfig);
}

