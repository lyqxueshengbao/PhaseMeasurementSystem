package com.example.pj125.recipe;

import com.example.pj125.device.DeviceConfig;
import com.example.pj125.link.LinkModelConfig;
import com.example.pj125.measurement.MeasurementPlan;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class Recipe {
    @NotBlank
    private String recipeId;

    @NotBlank
    private String name;

    @NotNull
    @Valid
    private DeviceConfig mainConfig = new DeviceConfig();

    @NotNull
    @Valid
    private DeviceConfig relayConfig = new DeviceConfig();

    @NotNull
    @Valid
    private LinkModelConfig linkModel = new LinkModelConfig();

    @NotNull
    @Valid
    private MeasurementPlan measurementPlan = new MeasurementPlan();

    @Valid
    private SimulatorProfile simulatorProfile;

    public String getRecipeId() {
        return recipeId;
    }

    public void setRecipeId(String recipeId) {
        this.recipeId = recipeId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public DeviceConfig getMainConfig() {
        return mainConfig;
    }

    public void setMainConfig(DeviceConfig mainConfig) {
        this.mainConfig = mainConfig;
    }

    public DeviceConfig getRelayConfig() {
        return relayConfig;
    }

    public void setRelayConfig(DeviceConfig relayConfig) {
        this.relayConfig = relayConfig;
    }

    public LinkModelConfig getLinkModel() {
        return linkModel;
    }

    public void setLinkModel(LinkModelConfig linkModel) {
        this.linkModel = linkModel;
    }

    public MeasurementPlan getMeasurementPlan() {
        return measurementPlan;
    }

    public void setMeasurementPlan(MeasurementPlan measurementPlan) {
        this.measurementPlan = measurementPlan;
    }

    public SimulatorProfile getSimulatorProfile() {
        return simulatorProfile;
    }

    public void setSimulatorProfile(SimulatorProfile simulatorProfile) {
        this.simulatorProfile = simulatorProfile;
    }
}

