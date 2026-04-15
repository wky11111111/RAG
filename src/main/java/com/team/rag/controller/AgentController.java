package com.team.rag.controller;

import com.team.rag.bean.AgentAnswerResponse;
import com.team.rag.bean.AgentStep;
import com.team.rag.bean.Citation;
import com.team.rag.bean.OperationLogView;
import com.team.rag.bean.RagQo;
import com.team.rag.service.LightAgentService;
import com.team.rag.service.ObservabilityService;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final LightAgentService lightAgentService;
    private final ExecutorService ragExecutorService;
    private final ObservabilityService observabilityService;

    public AgentController(LightAgentService lightAgentService,
                           ExecutorService ragExecutorService,
                           ObservabilityService observabilityService) {
        this.lightAgentService = lightAgentService;
        this.ragExecutorService = ragExecutorService;
        this.observabilityService = observabilityService;
    }

    @PostMapping("/qa")
    public AgentAnswerResponse qa(@Valid @RequestBody RagQo qo) {
        return lightAgentService.qa(qo);
    }

    @org.springframework.web.bind.annotation.GetMapping("/trace/{traceId}")
    public List<OperationLogView> trace(@org.springframework.web.bind.annotation.PathVariable String traceId) {
        return observabilityService.list(traceId, null);
    }

    @PostMapping(value = "/qa/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody RagQo qo) {
        SseEmitter emitter = new SseEmitter(0L);
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        ragExecutorService.execute(() -> {
            if (contextMap != null) {
                MDC.setContextMap(contextMap);
            }
            try {
                streamAnswer(emitter, qo);
            } finally {
                MDC.clear();
            }
        });
        return emitter;
    }

    private void streamAnswer(SseEmitter emitter, RagQo qo) {
        try {
            AgentAnswerResponse response = lightAgentService.streamQa(
                    qo,
                    delta -> send(emitter, "delta", delta),
                    step -> send(emitter, "step", step)
            );
            List<Citation> citations = response.citations();
            send(emitter, "references", Map.of("citations", citations, "steps", response.steps()));
            send(emitter, "done", "[DONE]");
            emitter.complete();
        } catch (UncheckedIOException exception) {
            emitter.completeWithError(exception.getCause());
        } catch (Exception exception) {
            try {
                emitter.send(SseEmitter.event().name("error").data(Map.of("message", exception.getMessage())));
            } catch (IOException ignored) {
            }
            emitter.completeWithError(exception);
        }
    }

    private void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
