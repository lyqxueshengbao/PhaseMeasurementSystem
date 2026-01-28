package com.example.pj125.sse;

import com.example.pj125.common.JsonUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class SseHub {
    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> seqs = new ConcurrentHashMap<>();
    private final Map<String, CopyOnWriteArrayList<SseEnvelope>> history = new ConcurrentHashMap<>();
    private final Map<String, Object> locks = new ConcurrentHashMap<>();

    private Object lockFor(String runId) {
        return locks.computeIfAbsent(runId, k -> new Object());
    }

    public SseEmitter subscribe(String runId) {
        SseEmitter emitter = new SseEmitter(0L);

        emitter.onCompletion(() -> remove(runId, emitter));
        emitter.onTimeout(() -> remove(runId, emitter));
        emitter.onError(e -> remove(runId, emitter));

        Object lock = lockFor(runId);
        synchronized (lock) {
            // replay before registering emitter to avoid duplicates/out-of-order
            List<SseEnvelope> h = history.get(runId);
            if (h != null) {
                for (SseEnvelope env : h) {
                    safeSend(emitter, env);
                }
                if (!h.isEmpty()) {
                    String last = h.get(h.size() - 1).getType();
                    if (SseEventType.DONE.name().equals(last) || SseEventType.FAILED.name().equals(last)) {
                        try {
                            emitter.complete();
                        } catch (Exception ignored) {
                        }
                        return emitter;
                    }
                }
            }
            emitters.computeIfAbsent(runId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        }
        return emitter;
    }

    public void publish(String runId, SseEventType type, Object payload) {
        Object lock = lockFor(runId);
        synchronized (lock) {
            SseEnvelope env = new SseEnvelope();
            env.setType(type.name());
            env.setRunId(runId);
            env.setTs(OffsetDateTime.now().toString());
            env.setSeq(seqs.computeIfAbsent(runId, k -> new AtomicLong(0)).incrementAndGet());
            env.setPayload(payload);

            CopyOnWriteArrayList<SseEnvelope> h = history.computeIfAbsent(runId, k -> new CopyOnWriteArrayList<>());
            h.add(env);
            // prevent unbounded memory (best-effort)
            if (h.size() > 5000) {
                for (int i = 0; i < 1000 && !h.isEmpty(); i++) {
                    h.remove(0);
                }
            }

            CopyOnWriteArrayList<SseEmitter> list = emitters.get(runId);
            if (list == null) return;
            for (SseEmitter e : list) {
                safeSend(e, env);
            }
        }
    }

    public void closeRun(String runId) {
        CopyOnWriteArrayList<SseEmitter> list;
        Object lock = lockFor(runId);
        synchronized (lock) {
            list = emitters.remove(runId);
        }
        if (list == null) return;
        for (SseEmitter e : list) {
            try {
                e.complete();
            } catch (Exception ignored) {
            }
        }
        locks.remove(runId);
    }

    private void remove(String runId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.get(runId);
        if (list != null) list.remove(emitter);
    }

    private void safeSend(SseEmitter emitter, SseEnvelope env) {
        try {
            String json = JsonUtils.mapper().writeValueAsString(env);
            emitter.send(SseEmitter.event().name(env.getType()).data(json));
        } catch (JsonProcessingException e) {
            // ignore
        } catch (IOException e) {
            try {
                emitter.completeWithError(e);
            } catch (Exception ignored) {
            }
        }
    }
}
