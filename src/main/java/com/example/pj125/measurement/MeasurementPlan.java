package com.example.pj125.measurement;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.List;

public class MeasurementPlan {
    @NotNull
    private List<MeasurementMode> modes = new ArrayList<>();

    @Min(1)
    private int repeat = 1;

    public List<MeasurementMode> getModes() {
        return modes;
    }

    public void setModes(List<MeasurementMode> modes) {
        this.modes = modes;
    }

    public int getRepeat() {
        return repeat;
    }

    public void setRepeat(int repeat) {
        this.repeat = repeat;
    }
}

