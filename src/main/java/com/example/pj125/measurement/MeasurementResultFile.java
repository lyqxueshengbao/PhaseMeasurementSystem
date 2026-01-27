package com.example.pj125.measurement;

import java.util.ArrayList;
import java.util.List;

public class MeasurementResultFile {
    private String runId;
    private String recipeId;
    private List<MeasurementRepeatResult> results = new ArrayList<>();

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public String getRecipeId() {
        return recipeId;
    }

    public void setRecipeId(String recipeId) {
        this.recipeId = recipeId;
    }

    public List<MeasurementRepeatResult> getResults() {
        return results;
    }

    public void setResults(List<MeasurementRepeatResult> results) {
        this.results = results;
    }
}

