package com.example.pj125.common;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

public final class RunIdGenerator {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    private RunIdGenerator() {
    }

    public static String newRunId() {
        String ts = OffsetDateTime.now().format(FMT);
        int seq = COUNTER.updateAndGet(v -> (v % 999) + 1);
        return "RUN-" + ts + "-" + String.format("%03d", seq);
    }
}

