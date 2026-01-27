package com.example.pj125.measurement;

import com.example.pj125.device.DeviceConfig;
import com.example.pj125.link.LinkModelConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ReproducibilityTest {
    @Test
    void sameSeedKeyMustProduceSameResult() {
        SimulatedMeasurementEngine eng = new SimulatedMeasurementEngine();

        DeviceConfig main = new DeviceConfig();
        main.setReferencePathDelayNs(100);
        main.setMeasurePathDelayNs(150);

        DeviceConfig relay = new DeviceConfig();
        relay.setReferencePathDelayNs(80);
        relay.setMeasurePathDelayNs(120);

        LinkModelConfig link = new LinkModelConfig();
        link.setFixedLinkDelayNs(800);
        link.setDriftPpm(0.2);
        link.setNoiseStdNs(0.5);
        link.setBasePhaseDeg(15);

        MeasurementRepeatResult a = eng.simulate("RUN-TEST", "RCP-1", MeasurementMode.LINK, 0, main, relay, link);
        MeasurementRepeatResult b = eng.simulate("RUN-TEST", "RCP-1", MeasurementMode.LINK, 0, main, relay, link);
        assertEquals(a.getDelayNs(), b.getDelayNs());
        assertEquals(a.getPhaseDeg(), b.getPhaseDeg());
        assertEquals(a.getConfidence(), b.getConfidence());
        assertEquals(a.getQualityFlag(), b.getQualityFlag());
    }

    @Test
    void differentRepeatIndexShouldDifferInMostCases() {
        SimulatedMeasurementEngine eng = new SimulatedMeasurementEngine();
        DeviceConfig main = new DeviceConfig();
        DeviceConfig relay = new DeviceConfig();
        LinkModelConfig link = new LinkModelConfig();
        link.setFixedLinkDelayNs(800);
        link.setNoiseStdNs(0.5);

        MeasurementRepeatResult a = eng.simulate("RUN-TEST", "RCP-1", MeasurementMode.LINK, 0, main, relay, link);
        MeasurementRepeatResult b = eng.simulate("RUN-TEST", "RCP-1", MeasurementMode.LINK, 1, main, relay, link);
        assertNotEquals(a.getDelayNs(), b.getDelayNs());
    }
}

