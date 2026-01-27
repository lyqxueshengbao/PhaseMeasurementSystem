package com.example.pj125.recipe;

import com.example.pj125.common.ApiException;
import com.example.pj125.common.AppProperties;
import com.example.pj125.common.ErrorCode;
import com.example.pj125.common.JsonUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RecipeService {
    private final ObjectMapper mapper = JsonUtils.mapper();
    private final Path recipesDir;

    public RecipeService(AppProperties props) {
        this.recipesDir = Path.of(props.getDataDir(), "recipes");
    }

    public List<String> listRecipeIds() {
        ensureDir();
        try (var stream = Files.list(recipesDir)) {
            return stream
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .map(p -> p.getFileName().toString().replace(".json", ""))
                    .sorted(Comparator.naturalOrder())
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new ApiException(ErrorCode.PERSIST_FAILED, "读取配方列表失败: " + e.getMessage());
        }
    }

    public Recipe get(String recipeId) {
        ensureDir();
        Path p = recipesDir.resolve(recipeId + ".json");
        if (!Files.exists(p)) throw new ApiException(ErrorCode.NOT_FOUND, "配方不存在: " + recipeId);
        try {
            return mapper.readValue(Files.readString(p), Recipe.class);
        } catch (IOException e) {
            throw new ApiException(ErrorCode.PERSIST_FAILED, "读取配方失败: " + e.getMessage());
        }
    }

    public Recipe upsert(Recipe recipe) {
        ensureDir();
        if (recipe == null || recipe.getRecipeId() == null || recipe.getRecipeId().isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "recipeId不能为空");
        }
        Path p = recipesDir.resolve(recipe.getRecipeId() + ".json");
        try {
            Files.writeString(p, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(recipe));
            return recipe;
        } catch (IOException e) {
            throw new ApiException(ErrorCode.PERSIST_FAILED, "保存配方失败: " + e.getMessage());
        }
    }

    public void delete(String recipeId) {
        ensureDir();
        Path p = recipesDir.resolve(recipeId + ".json");
        try {
            Files.deleteIfExists(p);
        } catch (IOException e) {
            throw new ApiException(ErrorCode.PERSIST_FAILED, "删除配方失败: " + e.getMessage());
        }
    }

    private void ensureDir() {
        try {
            Files.createDirectories(recipesDir);
        } catch (IOException e) {
            throw new ApiException(ErrorCode.PERSIST_FAILED, "创建recipes目录失败: " + e.getMessage());
        }
    }

    public Recipe ensureExampleRecipe() {
        ensureDir();
        String id = "RCP-DEFAULT";
        Path p = recipesDir.resolve(id + ".json");
        if (Files.exists(p)) return get(id);

        Recipe r = new Recipe();
        r.setRecipeId(id);
        r.setName("默认联调配方");
        r.getMainConfig().setReferencePathDelayNs(120.0);
        r.getMainConfig().setMeasurePathDelayNs(180.0);
        r.getRelayConfig().setReferencePathDelayNs(95.0);
        r.getRelayConfig().setMeasurePathDelayNs(130.0);
        r.getLinkModel().setFixedLinkDelayNs(800.0);
        r.getLinkModel().setDriftPpm(0.2);
        r.getLinkModel().setNoiseStdNs(0.5);
        r.getLinkModel().setBasePhaseDeg(15.0);
        r.getMeasurementPlan().setModes(List.of(
                com.example.pj125.measurement.MeasurementMode.LINK,
                com.example.pj125.measurement.MeasurementMode.MAIN_INTERNAL,
                com.example.pj125.measurement.MeasurementMode.RELAY_INTERNAL
        ));
        r.getMeasurementPlan().setRepeat(8);
        upsert(r);
        return r;
    }
}
