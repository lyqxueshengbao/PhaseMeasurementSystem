package com.example.pj125.run;

import com.example.pj125.recipe.RecipeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "app.data-dir=./target/test-data-fail",
        "simulator.applyDelayMs=1",
        "simulator.lockTimeMs=5000",
        "simulator.measurementTimeMs=1",
        "simulator.waitLockedTimeoutMs=50",
        "simulator.fault.type=LOCK_TIMEOUT"
})
public class RunFailureLockTimeoutTest {
    @Autowired
    RecipeService recipeService;
    @Autowired
    RunService runService;
    @Autowired
    RunPersistenceService persistence;

    @Test
    void runShouldFailAndWriteErrorJson() throws Exception {
        recipeService.ensureExampleRecipe();
        String runId = runService.startRun("RCP-DEFAULT");

        Instant dl = Instant.now().plus(Duration.ofSeconds(5));
        while (Instant.now().isBefore(dl)) {
            RunInfo info = runService.getRunInfo(runId);
            if (info.getState() == RunState.FAILED) break;
            Thread.sleep(20);
        }

        RunInfo info = runService.getRunInfo(runId);
        assertEquals(RunState.FAILED, info.getState());

        Path dir = persistence.runDir(runId);
        assertTrue(Files.exists(dir.resolve("recipe.json")));
        assertTrue(Files.exists(dir.resolve("logs.ndjson")));
        assertTrue(Files.exists(dir.resolve("measurement_result.json")));
        assertTrue(Files.exists(dir.resolve("error.json")));
        assertFalse(Files.exists(dir.resolve("atmospheric_delay.json")));
    }
}

