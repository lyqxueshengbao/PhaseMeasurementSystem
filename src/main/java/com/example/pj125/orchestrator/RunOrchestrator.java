package com.example.pj125.orchestrator;

import com.example.pj125.common.ErrorCode;
import com.example.pj125.common.SimulatorProperties;
import com.example.pj125.device.Device;
import com.example.pj125.device.DeviceConfig;
import com.example.pj125.device.DeviceGateway;
import com.example.pj125.device.DeviceId;
import com.example.pj125.device.DeviceStatus;
import com.example.pj125.device.LockState;
import com.example.pj125.device.OpState;
import com.example.pj125.measurement.MeasurementEngine;
import com.example.pj125.measurement.MeasurementMode;
import com.example.pj125.measurement.MeasurementRepeatResult;
import com.example.pj125.measurement.MeasurementResultFile;
import com.example.pj125.measurement.QualityFlag;
import com.example.pj125.recipe.Recipe;
import com.example.pj125.run.AtmosphericDelayResult;
import com.example.pj125.run.RunError;
import com.example.pj125.run.RunInfo;
import com.example.pj125.run.RunLogLine;
import com.example.pj125.run.RunPersistenceService;
import com.example.pj125.run.RunState;
import com.example.pj125.run.RunStep;
import com.example.pj125.sse.SseEventType;
import com.example.pj125.sse.SseHub;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class RunOrchestrator {
    private final DeviceGateway deviceManager;
    private final MeasurementEngine measurementEngine;
    private final RunPersistenceService persistence;
    private final SseHub sseHub;
    private final SimulatorProperties simulatorProperties;

    public RunOrchestrator(DeviceGateway deviceManager,
                           MeasurementEngine measurementEngine,
                           RunPersistenceService persistence,
                           SseHub sseHub,
                           SimulatorProperties simulatorProperties) {
        this.deviceManager = deviceManager;
        this.measurementEngine = measurementEngine;
        this.persistence = persistence;
        this.sseHub = sseHub;
        this.simulatorProperties = simulatorProperties;
    }

    public void run(String runId, Recipe recipe, RunInfo runInfo) {
        MeasurementResultFile measurementFile = new MeasurementResultFile();
        measurementFile.setRunId(runId);
        measurementFile.setRecipeId(recipe.getRecipeId());

        try {
            step(runId, runInfo, RunStep.INIT, "运行初始化");
            logInfo(runId, runInfo.getStep(), "runId=" + runId + ", recipeId=" + recipe.getRecipeId());

            step(runId, runInfo, RunStep.CHECK_DEVICES, "检查/连接设备");
            ensureDevicesConnected(runId, runInfo);

            step(runId, runInfo, RunStep.APPLY_RECIPE, "下发配方并Apply");
            applyRecipeToDevices(runId, runInfo, recipe);

            step(runId, runInfo, RunStep.LOCK_START, "启动锁定");
            startLock(runId, runInfo);

            step(runId, runInfo, RunStep.WAIT_LOCKED, "等待LOCKED");
            waitLocked(runId, runInfo);

            step(runId, runInfo, RunStep.MEASURE, "执行测量");
            doMeasurements(runId, runInfo, recipe, measurementFile);
            persistence.writeMeasurementResult(runId, measurementFile);

            step(runId, runInfo, RunStep.SUMMARY, "计算大气时延");
            AtmosphericDelayResult adr = computeAtmospheric(runId, recipe, measurementFile);
            persistence.writeAtmosphericResult(runId, adr);
            sseHub.publish(runId, SseEventType.ATMOSPHERIC_RESULT, adr);

            runInfo.setState(RunState.DONE);
            runInfo.setStep(RunStep.DONE);
            runInfo.setEndedAt(OffsetDateTime.now().toString());
            persistence.writeRunInfo(runId, runInfo);
            sseHub.publish(runId, SseEventType.DONE, Map.of("message", "运行完成"));
        } catch (Exception e) {
            ErrorCode code = (e instanceof OrchestratorFailure f) ? f.code : ErrorCode.INTERNAL_ERROR;
            String msg = e.getMessage() == null ? "未知错误" : e.getMessage();
            fail(runId, runInfo, code, msg, measurementFile);
        } finally {
            sseHub.closeRun(runId);
        }
    }

    private void ensureDevicesConnected(String runId, RunInfo runInfo) {
        Device main = deviceManager.get(DeviceId.MAIN);
        Device relay = deviceManager.get(DeviceId.RELAY);

        if (!main.status().isConnected()) {
            ErrorCode rc = main.connect();
            if (rc != ErrorCode.OK) throw new OrchestratorFailure(rc, "主站连接失败: " + rc.name());
        }
        if (!relay.status().isConnected()) {
            ErrorCode rc = relay.connect();
            if (rc != ErrorCode.OK) throw new OrchestratorFailure(rc, "转发站连接失败: " + rc.name());
        }
        publishDeviceStatus(runId, main.status());
        publishDeviceStatus(runId, relay.status());
    }

    private void applyRecipeToDevices(String runId, RunInfo runInfo, Recipe recipe) {
        deviceManager.applySimulatorProfile(recipe.getSimulatorProfile());

        Device main = deviceManager.get(DeviceId.MAIN);
        Device relay = deviceManager.get(DeviceId.RELAY);

        ErrorCode rc;
        rc = main.configure(recipe.getMainConfig());
        if (rc != ErrorCode.OK) throw new OrchestratorFailure(rc, "主站配置失败: " + rc.name());
        rc = relay.configure(recipe.getRelayConfig());
        if (rc != ErrorCode.OK) throw new OrchestratorFailure(rc, "转发站配置失败: " + rc.name());

        rc = main.apply();
        if (rc != ErrorCode.OK) throw new OrchestratorFailure(rc, "主站Apply失败: " + rc.name());
        rc = relay.apply();
        if (rc != ErrorCode.OK) throw new OrchestratorFailure(rc, "转发站Apply失败: " + rc.name());

        // readback config snapshot for acceptance (e.g. MAIN DDS control word)
        runInfo.setMainAppliedConfig(main.readbackConfig());
        runInfo.setRelayAppliedConfig(relay.readbackConfig());
        persistence.writeRunInfo(runId, runInfo);

        publishDeviceStatus(runId, main.status());
        publishDeviceStatus(runId, relay.status());
        logInfo(runId, runInfo.getStep(), "配方已下发并生效");
    }

    private void startLock(String runId, RunInfo runInfo) {
        Device main = deviceManager.get(DeviceId.MAIN);
        Device relay = deviceManager.get(DeviceId.RELAY);

        ErrorCode rc;
        rc = main.startLock();
        if (rc != ErrorCode.OK) throw new OrchestratorFailure(rc, "主站启动锁定失败: " + rc.name());
        rc = relay.startLock();
        if (rc != ErrorCode.OK) throw new OrchestratorFailure(rc, "转发站启动锁定失败: " + rc.name());

        publishDeviceStatus(runId, main.status());
        publishDeviceStatus(runId, relay.status());
        logInfo(runId, runInfo.getStep(), "两台设备开始锁定");
    }

    private void waitLocked(String runId, RunInfo runInfo) {
        long start = System.currentTimeMillis();
        long timeoutMs = simulatorProperties.getWaitLockedTimeoutMs();
        Device main = deviceManager.get(DeviceId.MAIN);
        Device relay = deviceManager.get(DeviceId.RELAY);

        while (true) {
            DeviceStatus ms = main.status();
            DeviceStatus rs = relay.status();
            publishDeviceStatus(runId, ms);
            publishDeviceStatus(runId, rs);

            if (ms.getLockState() == LockState.LOCKED && rs.getLockState() == LockState.LOCKED) {
                logInfo(runId, runInfo.getStep(), "两台设备已LOCKED");
                return;
            }
            if (System.currentTimeMillis() - start > timeoutMs) {
                throw new OrchestratorFailure(ErrorCode.LOCK_TIMEOUT, "等待LOCKED超时");
            }
            sleep(200);
        }
    }

    private void doMeasurements(String runId, RunInfo runInfo, Recipe recipe, MeasurementResultFile measurementFile) {
        int repeat = recipe.getMeasurementPlan().getRepeat();
        List<MeasurementMode> modes = recipe.getMeasurementPlan().getModes();
        if (modes == null || modes.isEmpty()) throw new OrchestratorFailure(ErrorCode.VALIDATION_ERROR, "measurementPlan.modes不能为空");
        if (repeat <= 0) throw new OrchestratorFailure(ErrorCode.VALIDATION_ERROR, "measurementPlan.repeat必须>=1");

        Device main = deviceManager.get(DeviceId.MAIN);
        Device relay = deviceManager.get(DeviceId.RELAY);
        var fault = deviceManager.faultInjection().orElse(null);

        for (MeasurementMode mode : modes) {
            for (int i = 0; i < repeat; i++) {
                // deterministic lost lock injection
                if (fault != null) {
                    fault.maybeInjectLostLock(runId, recipe.getRecipeId(), mode, i);
                }

                DeviceStatus ms = main.status();
                DeviceStatus rs = relay.status();
                publishDeviceStatus(runId, ms);
                publishDeviceStatus(runId, rs);
                if (ms.getLockState() != LockState.LOCKED || rs.getLockState() != LockState.LOCKED) {
                    throw new OrchestratorFailure(ErrorCode.LOCK_LOST, "测量前检测到失锁");
                }

                ErrorCode rc;
                rc = main.startMeasurement();
                if (rc != ErrorCode.OK) throw new OrchestratorFailure(rc, "主站启动测量失败: " + rc.name());
                rc = relay.startMeasurement();
                if (rc != ErrorCode.OK) throw new OrchestratorFailure(rc, "转发站启动测量失败: " + rc.name());

                publishDeviceStatus(runId, main.status());
                publishDeviceStatus(runId, relay.status());

                waitNotBusy(runId, main, relay, 20_000);

                DeviceConfig mainCfg = main.readbackConfig();
                if (mainCfg == null) mainCfg = recipe.getMainConfig();
                DeviceConfig relayCfg = relay.readbackConfig();
                if (relayCfg == null) relayCfg = recipe.getRelayConfig();

                MeasurementRepeatResult rr = measurementEngine.simulate(runId, recipe.getRecipeId(), mode, i, mainCfg, relayCfg, recipe.getLinkModel());
                rr.setTs(OffsetDateTime.now().toString());
                measurementFile.getResults().add(rr);

                sseHub.publish(runId, SseEventType.MEASUREMENT_RESULT, rr);
                persistence.writeMeasurementResult(runId, measurementFile);
                logInfo(runId, runInfo.getStep(), "测量完成: " + mode.name() + " repeat=" + i + " delayNs=" + rr.getDelayNs() + " phaseDeg=" + rr.getPhaseDeg());
            }
        }
    }

    private AtmosphericDelayResult computeAtmospheric(String runId, Recipe recipe, MeasurementResultFile measurementFile) {
        int repeat = recipe.getMeasurementPlan().getRepeat();
        Map<MeasurementMode, List<MeasurementRepeatResult>> byMode = new EnumMap<>(MeasurementMode.class);
        for (MeasurementRepeatResult r : measurementFile.getResults()) {
            byMode.computeIfAbsent(r.getMode(), k -> new ArrayList<>()).add(r);
        }

        Stats link = statsOrFail(byMode, MeasurementMode.LINK, repeat);
        Stats main = statsOrFail(byMode, MeasurementMode.MAIN_INTERNAL, repeat);
        Stats relay = statsOrFail(byMode, MeasurementMode.RELAY_INTERNAL, repeat);

        AtmosphericDelayResult out = new AtmosphericDelayResult();
        out.setTs(OffsetDateTime.now().toString());
        out.setFormulaVersion("atm-v1");
        out.setStatus("SUCCEEDED");

        double atm = link.avg - main.avg - relay.avg;
        double unc = Math.sqrt(link.std * link.std + main.std * main.std + relay.std * relay.std);
        out.setAtmosphericDelayNs(round(atm, 6));
        out.setUncertaintyNs(round(unc, 6));

        int minValidRequired = (int) Math.ceil(repeat * 0.7);
        AtmosphericDelayResult.InputsSnapshot snapshot = new AtmosphericDelayResult.InputsSnapshot();
        snapshot.setMinValidRequired(minValidRequired);
        snapshot.setLink(modeStats(link));
        snapshot.setMainInternal(modeStats(main));
        snapshot.setRelayInternal(modeStats(relay));
        out.setInputsSnapshot(snapshot);
        out.setError(null);
        return out;
    }

    private static AtmosphericDelayResult.ModeStats modeStats(Stats s) {
        AtmosphericDelayResult.ModeStats ms = new AtmosphericDelayResult.ModeStats();
        ms.setAvgNs(s.avg);
        ms.setStdNs(s.std);
        ms.setValidCount(s.validCount);
        return ms;
    }

    private Stats statsOrFail(Map<MeasurementMode, List<MeasurementRepeatResult>> byMode, MeasurementMode mode, int repeat) {
        List<MeasurementRepeatResult> list = byMode.get(mode);
        if (list == null || list.isEmpty()) throw new OrchestratorFailure(ErrorCode.ATMOSPHERIC_FAILED, "缺少测量项: " + mode.name());

        int invalid = 0;
        List<Double> values = new ArrayList<>();
        for (MeasurementRepeatResult r : list) {
            if (r.getQualityFlag() == QualityFlag.INVALID) invalid++;
            else values.add(r.getDelayNs());
        }
        int valid = values.size();
        int need = (int) Math.ceil(repeat * 0.7);
        if (valid < need) throw new OrchestratorFailure(ErrorCode.ATMOSPHERIC_FAILED, mode.name() + "有效条数不足: " + valid + "/" + repeat);
        if (invalid > repeat - need) throw new OrchestratorFailure(ErrorCode.ATMOSPHERIC_FAILED, mode.name() + "无效数据占比过高: invalid=" + invalid + "/" + repeat);

        double avg = values.stream().mapToDouble(v -> v).average().orElse(0.0);
        double var = 0.0;
        for (double v : values) var += (v - avg) * (v - avg);
        double std = values.size() <= 1 ? 0.0 : Math.sqrt(var / values.size());
        return new Stats(round(avg, 6), round(std, 6), valid);
    }

    private void waitNotBusy(String runId, Device main, Device relay, long timeoutMs) {
        long start = System.currentTimeMillis();
        while (true) {
            DeviceStatus ms = main.status();
            DeviceStatus rs = relay.status();
            publishDeviceStatus(runId, ms);
            publishDeviceStatus(runId, rs);
            if (ms.getOpState() != OpState.BUSY && rs.getOpState() != OpState.BUSY) return;
            if (System.currentTimeMillis() - start > timeoutMs) {
                throw new OrchestratorFailure(ErrorCode.MEASUREMENT_FAILED, "等待测量完成超时");
            }
            sleep(100);
        }
    }

    private void step(String runId, RunInfo runInfo, RunStep step, String tip) {
        runInfo.setStep(step);
        persistence.writeRunInfo(runId, runInfo);
        sseHub.publish(runId, SseEventType.STEP, Map.of("step", step.name(), "message", tip));
        logInfo(runId, step, tip);
    }

    private void publishDeviceStatus(String runId, DeviceStatus status) {
        sseHub.publish(runId, SseEventType.DEVICE_STATUS, status);
    }

    private void logInfo(String runId, RunStep step, String msg) {
        RunLogLine line = new RunLogLine();
        line.setTs(OffsetDateTime.now().toString());
        line.setRunId(runId);
        line.setLevel("INFO");
        line.setStep(step == null ? null : step.name());
        line.setMessage(msg);
        persistence.appendLog(runId, line);
        sseHub.publish(runId, SseEventType.LOG, Map.of("level", "INFO", "step", step == null ? null : step.name(), "message", msg));
    }

    private void fail(String runId, RunInfo runInfo, ErrorCode code, String msg, MeasurementResultFile measurementFile) {
        String failedStep = runInfo.getStep() == null ? null : runInfo.getStep().name();
        try {
            Device main = deviceManager.get(DeviceId.MAIN);
            Device relay = deviceManager.get(DeviceId.RELAY);
            main.stop();
            relay.stop();
        } catch (Exception ignored) {
        }

        try {
            persistence.writeMeasurementResult(runId, measurementFile);
        } catch (Exception ignored) {
        }

        RunError err = new RunError();
        err.setRunId(runId);
        err.setTs(OffsetDateTime.now().toString());
        err.setStep(failedStep);
        err.setErrorCode(code.name());
        err.setMessage(msg);
        persistence.writeError(runId, err);

        runInfo.setState(RunState.FAILED);
        runInfo.setStep(RunStep.FAILED);
        runInfo.setLastErrorCode(code.name());
        runInfo.setLastErrorMessage(msg);
        runInfo.setEndedAt(OffsetDateTime.now().toString());
        persistence.writeRunInfo(runId, runInfo);

        sseHub.publish(runId, SseEventType.STEP, Map.of("step", RunStep.FAILED.name(), "message", "运行失败"));
        sseHub.publish(runId, SseEventType.FAILED, Map.of("errorCode", code.name(), "message", msg));
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static double round(double v, int digits) {
        double s = Math.pow(10.0, digits);
        return Math.round(v * s) / s;
    }

    private static class Stats {
        final double avg;
        final double std;
        final int validCount;

        Stats(double avg, double std, int validCount) {
            this.avg = avg;
            this.std = std;
            this.validCount = validCount;
        }
    }

    private static class OrchestratorFailure extends RuntimeException {
        final ErrorCode code;

        OrchestratorFailure(ErrorCode code, String message) {
            super(message);
            this.code = code;
        }
    }
}
