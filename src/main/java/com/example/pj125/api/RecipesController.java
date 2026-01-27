package com.example.pj125.api;

import com.example.pj125.common.Ack;
import com.example.pj125.recipe.Recipe;
import com.example.pj125.recipe.RecipeService;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/recipes")
public class RecipesController {
    private final RecipeService recipeService;

    public RecipesController(RecipeService recipeService) {
        this.recipeService = recipeService;
    }

    @PostConstruct
    public void init() {
        recipeService.ensureExampleRecipe();
    }

    @GetMapping
    public Ack<List<String>> list() {
        return Ack.ok(recipeService.listRecipeIds());
    }

    @GetMapping("/{recipeId}")
    public Ack<Recipe> get(@PathVariable String recipeId) {
        return Ack.ok(recipeService.get(recipeId));
    }

    @PostMapping
    public Ack<Recipe> upsert(@Valid @RequestBody Recipe recipe) {
        return Ack.ok(recipeService.upsert(recipe));
    }

    @DeleteMapping("/{recipeId}")
    public Ack<?> delete(@PathVariable String recipeId) {
        recipeService.delete(recipeId);
        return Ack.ok(true);
    }
}

