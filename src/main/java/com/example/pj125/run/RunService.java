package com.example.pj125.run;

import com.example.pj125.common.ApiException;
import com.example.pj125.common.ErrorCode;
import com.example.pj125.common.RunIdGenerator;
import com.example.pj125.device.DeviceGateway;
import com.example.pj125.orchestrator.RunOrchestrator;
import com.example.pj125.recipe.Recipe;
import com.example.pj125.recipe.RecipeService;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class RunService {
    private final RecipeService recipeService;
    private final DeviceGateway deviceManager;
    private final RunPersistenceService persistence;
    private final RunOrchestrator orchestrator;
    private final TaskExecutor taskExecutor;

    private final AtomicReference<String> runningRunId = new AtomicReference<>(null);
    private final Map<String, RunInfo> cache = new ConcurrentHashMap<>();

    public RunService(RecipeService recipeService,
                      DeviceGateway deviceManager,
                      RunPersistenceService persistence,
                      RunOrchestrator orchestrator,
                      TaskExecutor taskExecutor) {
        this.recipeService = recipeService;
        this.deviceManager = deviceManager;
        this.persistence = persistence;
        this.orchestrator = orchestrator;
        this.taskExecutor = taskExecutor;
    }

    public String startRun(String recipeId) {
        if (runningRunId.get() != null) {
            throw new ApiException(ErrorCode.ORCHESTRATOR_RUNNING, "当前已有运行中的任务: " + runningRunId.get());
        }
        Recipe recipe = recipeService.get(recipeId);

        String runId = RunIdGenerator.newRunId();
        if (!runningRunId.compareAndSet(null, runId)) {
            throw new ApiException(ErrorCode.ORCHESTRATOR_RUNNING, "当前已有运行中的任务: " + runningRunId.get());
        }

        RunInfo runInfo = new RunInfo();
        runInfo.setRunId(runId);
        runInfo.setRecipeId(recipeId);
        runInfo.setStartedAt(OffsetDateTime.now().toString());
        runInfo.setState(RunState.RUNNING);
        runInfo.setStep(RunStep.INIT);
        cache.put(runId, runInfo);

        persistence.initRun(runId, recipe, RunPersistenceService.buildDeviceInfoPayload(deviceManager.infos()), runInfo);

        taskExecutor.execute(() -> {
            try {
                orchestrator.run(runId, recipe, runInfo);
            } finally {
                runningRunId.compareAndSet(runId, null);
            }
        });
        return runId;
    }

    public RunInfo getRunInfo(String runId) {
        RunInfo cached = cache.get(runId);
        if (cached != null) return cached;
        return persistence.readJson(runId, "run_info.json", RunInfo.class);
    }
}
