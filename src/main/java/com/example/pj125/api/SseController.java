package com.example.pj125.api;

import com.example.pj125.sse.SseHub;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/sse")
public class SseController {
    private final SseHub hub;

    public SseController(SseHub hub) {
        this.hub = hub;
    }

    @GetMapping("/runs/{runId}")
    public SseEmitter subscribe(@PathVariable String runId) {
        return hub.subscribe(runId);
    }
}

