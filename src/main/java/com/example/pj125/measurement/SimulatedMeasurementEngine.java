package com.example.pj125.measurement;

import com.example.pj125.common.HashUtils;
import com.example.pj125.device.DeviceConfig;
import com.example.pj125.link.LinkModelConfig;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

@Service
public class SimulatedMeasurementEngine implements MeasurementEngine {
    /**
     * Equivalent phase slope: deg per ns.
     * 1 GHz -> 360 deg per 1 ns.
     */
    private static final double PHASE_SLOPE_DEG_PER_NS = 360.0;

    @Override
    public MeasurementRepeatResult simulate(String runId,
                                            String recipeId,
                                            MeasurementMode mode,
                                            int repeatIndex,
                                            DeviceConfig mainConfigApplied,
                                            DeviceConfig relayConfigApplied,
                                            LinkModelConfig linkModelConfig) {
        String seedKey = runId + "|" + recipeId + "|" + mode.name() + "|" + repeatIndex;
        long seed = HashUtils.seedFromKey(seedKey);
        Random r = new Random(seed);

        double delayNs;
        double noiseStdNs = Math.max(0.0, linkModelConfig.getNoiseStdNs());
        double basePhaseDeg;
        String modelExplain;

        if (mode == MeasurementMode.LINK) {
            delayNs = simulateLinkDelayNs(linkModelConfig, repeatIndex, r);
            basePhaseDeg = linkModelConfig.getBasePhaseDeg();
            modelExplain = "LINK: fixed+drift+noise";
        } else if (mode == MeasurementMode.MAIN_INTERNAL) {
            delayNs = mainConfigApplied.getMeasurePathDelayNs() - mainConfigApplied.getReferencePathDelayNs();
            delayNs = delayNs + r.nextGaussian() * (noiseStdNs * 0.5);
            basePhaseDeg = 0.0;
            modelExplain = "MAIN_INTERNAL: (measure-ref)+noise";
        } else {
            delayNs = relayConfigApplied.getMeasurePathDelayNs() - relayConfigApplied.getReferencePathDelayNs();
            delayNs = delayNs + r.nextGaussian() * (noiseStdNs * 0.5);
            basePhaseDeg = 0.0;
            modelExplain = "RELAY_INTERNAL: (measure-ref)+noise";
        }

        double phaseNoiseDeg = r.nextGaussian() * Math.max(0.1, noiseStdNs) * 2.0;
        double phaseDeg = wrapPhaseDeg(basePhaseDeg + PHASE_SLOPE_DEG_PER_NS * (delayNs / 1.0) + phaseNoiseDeg);

        double snrDb = 30.0 - Math.min(25.0, noiseStdNs * 10.0) + r.nextGaussian() * 0.5;
        double confidence = clamp01(0.98 - Math.min(0.9, noiseStdNs / 5.0) - Math.abs(phaseNoiseDeg) / 180.0);

        QualityFlag qf;
        if (confidence >= 0.85) qf = QualityFlag.OK;
        else if (confidence >= 0.65) qf = QualityFlag.WARN;
        else if (confidence >= 0.35) qf = QualityFlag.BAD;
        else qf = QualityFlag.INVALID;

        MeasurementRepeatResult out = new MeasurementRepeatResult();
        out.setMode(mode);
        out.setRepeatIndex(repeatIndex);
        out.setDelayNs(round(delayNs, 6));
        out.setPhaseDeg(round(phaseDeg, 6));
        out.setConfidence(round(confidence, 6));
        out.setQualityFlag(qf);
        out.setSnrDb(round(snrDb, 3));

        Map<String, Object> explain = new LinkedHashMap<>();
        explain.put("seedKey", seedKey);
        explain.put("seed", seed);
        explain.put("model", modelExplain);
        explain.put("units", Map.of("delayNs", "ns", "phaseDeg", "deg"));
        out.setExplain(explain);
        return out;
    }

    private static double simulateLinkDelayNs(LinkModelConfig cfg, int repeatIndex, Random r) {
        double fixed = cfg.getFixedLinkDelayNs();
        double drift = fixed * (cfg.getDriftPpm() / 1_000_000.0) * (repeatIndex - 0.5);
        double noise = r.nextGaussian() * cfg.getNoiseStdNs();
        return fixed + drift + noise;
    }

    private static double wrapPhaseDeg(double deg) {
        double x = deg % 360.0;
        if (x > 180.0) x -= 360.0;
        if (x < -180.0) x += 360.0;
        return x;
    }

    private static double clamp01(double v) {
        if (v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
    }

    private static double round(double v, int digits) {
        double s = Math.pow(10.0, digits);
        return Math.round(v * s) / s;
    }
}

