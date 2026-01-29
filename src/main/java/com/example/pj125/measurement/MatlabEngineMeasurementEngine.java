package com.example.pj125.measurement;

import com.example.pj125.common.JsonUtils;
import com.example.pj125.device.DeviceConfig;
import com.example.pj125.link.LinkModelConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.io.File;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Optional integration: compute measurement repeat results by calling a local MATLAB Engine.
 * <p>
 * This is OFF by default and only enabled when:
 * <pre>
 * pj125.measurement.engine=matlab-engine
 * </pre>
 * <p>
 * To keep this project buildable without MATLAB installed, this class uses reflection and does not
 * add compile-time dependencies on MATLAB Engine for Java.
 */
@Service
@Primary
@ConditionalOnProperty(prefix = "pj125.measurement", name = "engine", havingValue = "matlab-engine")
public class MatlabEngineMeasurementEngine implements MeasurementEngine {
    private static final String MATLAB_FUNC = "pj125_measure_json";

    private final MeasurementProperties properties;
    private final Class<?> matlabEngineClass;
    private final Method startMatlab;
    private final Method putVariable;
    private final Method eval;
    private final Method getVariable;

    private volatile Object engine;

    public MatlabEngineMeasurementEngine(MeasurementProperties properties) {
        this.properties = properties;
        try {
            matlabEngineClass = Class.forName("com.mathworks.engine.MatlabEngine");
            startMatlab = matlabEngineClass.getMethod("startMatlab");
            putVariable = matlabEngineClass.getMethod("putVariable", String.class, Object.class);
            eval = matlabEngineClass.getMethod("eval", String.class);
            getVariable = matlabEngineClass.getMethod("getVariable", String.class);
        } catch (Exception e) {
            throw new IllegalStateException("MATLAB Engine for Java not found/loaded. " +
                    "Install MATLAB and add engine.jar to the runtime classpath, " +
                    "or set pj125.measurement.engine=simulated.", e);
        }
    }

    @Override
    public MeasurementRepeatResult simulate(String runId,
                                            String recipeId,
                                            MeasurementMode mode,
                                            int repeatIndex,
                                            DeviceConfig mainConfigApplied,
                                            DeviceConfig relayConfigApplied,
                                            LinkModelConfig linkModelConfig) {
        Object eng = ensureEngine();

        String seedKey = runId + "|" + recipeId + "|" + mode.name() + "|" + repeatIndex;
        ObjectNode req = JsonUtils.mapper().createObjectNode();
        req.put("runId", runId);
        req.put("recipeId", recipeId);
        req.put("mode", mode.name());
        req.put("repeatIndex", repeatIndex);
        req.put("seedKey", seedKey);
        req.put("model", properties.getMatlabModel());
        req.put("monteCarloT", properties.getMatlabMonteCarloT());
        req.set("mainConfig", JsonUtils.mapper().valueToTree(mainConfigApplied));
        req.set("relayConfig", JsonUtils.mapper().valueToTree(relayConfigApplied));
        req.set("linkModel", JsonUtils.mapper().valueToTree(linkModelConfig));

        String reqJson = req.toString();
        String outJson = callMatlab(eng, reqJson);

        JsonNode out;
        try {
            out = JsonUtils.mapper().readTree(outJson);
        } catch (Exception e) {
            throw new IllegalStateException("MATLAB returned non-JSON result: " + outJson, e);
        }

        MeasurementRepeatResult r = new MeasurementRepeatResult();
        r.setMode(mode);
        r.setRepeatIndex(repeatIndex);
        r.setDelayNs(out.path("delayNs").asDouble());
        r.setPhaseDeg(out.path("phaseDeg").asDouble());
        r.setConfidence(out.path("confidence").asDouble(1.0));
        r.setSnrDb(out.path("snrDb").asDouble(30.0));
        String qf = out.path("qualityFlag").asText(null);
        if (qf != null) {
            try {
                r.setQualityFlag(QualityFlag.valueOf(qf));
            } catch (Exception ignored) {
            }
        }

        Map<String, Object> explain = new LinkedHashMap<>();
        explain.put("seedKey", seedKey);
        explain.put("backend", "matlab-engine");
        explain.put("matlabFunction", MATLAB_FUNC);
        if (out.hasNonNull("mcT")) explain.put("mcT", out.get("mcT").asInt());
        if (out.hasNonNull("f0Hz")) explain.put("f0Hz", out.get("f0Hz").asDouble());
        if (out.hasNonNull("mcMeanDelayNs")) explain.put("mcMeanDelayNs", out.get("mcMeanDelayNs").asDouble());
        if (out.hasNonNull("mcStdDelayNs")) explain.put("mcStdDelayNs", out.get("mcStdDelayNs").asDouble());
        r.setExplain(explain);
        return r;
    }

    private Object ensureEngine() {
        Object local = engine;
        if (local != null) return local;
        synchronized (this) {
            if (engine != null) return engine;
            try {
                engine = startMatlab.invoke(null);
                bootstrapPath(engine);
                return engine;
            } catch (Exception e) {
                throw new IllegalStateException("Failed to start MATLAB Engine for Java.", e);
            }
        }
    }

    private void bootstrapPath(Object eng) {
        String dir = properties == null ? null : properties.getMatlabScriptDir();
        if (dir == null || dir.trim().isEmpty()) return;

        File f = new File(dir.trim());
        if (!f.isAbsolute()) {
            f = new File(System.getProperty("user.dir"), dir.trim());
        }
        String abs = f.getAbsolutePath().replace('\\', '/');
        String matlabStr = abs.replace("'", "''");

        try {
            eval.invoke(eng, "addpath('" + matlabStr + "');");
            eval.invoke(eng, "whichOut = which('" + MATLAB_FUNC + "');");
            Object whichOut = getVariable.invoke(eng, "whichOut");
            String resolved = whichOut == null ? "" : whichOut.toString();
            if (resolved.trim().isEmpty()) {
                throw new IllegalStateException("MATLAB function not found on path: " + MATLAB_FUNC +
                        " (matlabScriptDir=" + abs + ")");
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to add MATLAB script dir to path: " + abs, e);
        }
    }

    private String callMatlab(Object eng, String reqJson) {
        try {
            putVariable.invoke(eng, "reqJson", reqJson);
            eval.invoke(eng, "outJson = " + MATLAB_FUNC + "(reqJson);");
            Object out = getVariable.invoke(eng, "outJson");
            if (out == null) return "";
            if (out instanceof String s) return s;
            if (out instanceof char[] chars) return new String(chars);
            return out.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to call MATLAB function " + MATLAB_FUNC + "(reqJson).", e);
        }
    }
}
