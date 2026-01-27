package com.example.pj125.api;

import com.example.pj125.common.Ack;
import com.example.pj125.common.ApiException;
import com.example.pj125.common.ErrorCode;
import com.example.pj125.measurement.MeasurementResultFile;
import com.example.pj125.run.AtmosphericDelayResult;
import com.example.pj125.run.RunError;
import com.example.pj125.run.RunInfo;
import com.example.pj125.run.RunPersistenceService;
import com.example.pj125.run.RunService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.util.Map;

@RestController
@RequestMapping("/api/runs")
public class RunsController {
    private final RunService runService;
    private final RunPersistenceService persistence;

    public RunsController(RunService runService, RunPersistenceService persistence) {
        this.runService = runService;
        this.persistence = persistence;
    }

    public static class StartRunRequest {
        @NotBlank
        private String recipeId;

        public String getRecipeId() {
            return recipeId;
        }

        public void setRecipeId(String recipeId) {
            this.recipeId = recipeId;
        }
    }

    @PostMapping
    public Ack<Map<String, Object>> create(@Valid @RequestBody StartRunRequest req) {
        String runId = runService.startRun(req.getRecipeId());
        return Ack.ok(Map.of(
                "runId", runId,
                "sseUrl", "/api/sse/runs/" + runId
        ));
    }

    @GetMapping("/{runId}")
    public Ack<RunInfo> get(@PathVariable String runId) {
        return Ack.ok(runService.getRunInfo(runId));
    }

    @GetMapping
    public Ack<?> list() {
        return Ack.ok(persistence.listRuns());
    }

    @GetMapping("/{runId}/files")
    public Ack<Map<String, Long>> files(@PathVariable String runId) {
        return Ack.ok(persistence.listFiles(runId));
    }

    @GetMapping("/{runId}/archive")
    public Object archive(@PathVariable String runId) {
        return persistence.archiveZip(runId);
    }

    @GetMapping("/{runId}/measurement_result")
    public Ack<MeasurementResultFile> measurement(@PathVariable String runId) {
        return Ack.ok(persistence.readJson(runId, "measurement_result.json", MeasurementResultFile.class));
    }

    @GetMapping("/{runId}/atmospheric_delay")
    public Ack<?> atmospheric(@PathVariable String runId) {
        // Scheme B in SPEC.md: this endpoint must always be available for existing runId.
        if (!Files.exists(persistence.runDir(runId))) {
            throw new ApiException(ErrorCode.NOT_FOUND, "run不存在: " + runId);
        }

        if (Files.exists(persistence.runDir(runId).resolve("atmospheric_delay.json"))) {
            AtmosphericDelayResult r = persistence.readJson(runId, "atmospheric_delay.json", AtmosphericDelayResult.class);
            return Ack.ok(r);
        }

        if (Files.exists(persistence.runDir(runId).resolve("error.json"))) {
            RunError err = persistence.readJson(runId, "error.json", RunError.class);
            ErrorCode code;
            try {
                code = ErrorCode.valueOf(err.getErrorCode());
            } catch (Exception ignored) {
                code = ErrorCode.INTERNAL_ERROR;
            }
            Ack<RunError> ack = Ack.fail(code, err.getMessage());
            ack.setData(err);
            return ack;
        }

        // RUNNING: not yet available
        Ack<Map<String, Object>> ack = Ack.fail(ErrorCode.NOT_FOUND, "大气时延尚未生成");
        ack.setData(Map.of("errorCode", "NOT_READY", "message", "大气时延尚未生成"));
        return ack;
    }
}
