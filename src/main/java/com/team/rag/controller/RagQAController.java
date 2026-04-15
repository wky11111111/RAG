package com.team.rag.controller;

import com.team.rag.bean.Citation;
import com.team.rag.bean.MemoryDetailResponse;
import com.team.rag.bean.MemorySessionView;
import com.team.rag.bean.RagAnswerResponse;
import com.team.rag.bean.RagQo;
import com.team.rag.service.RagQAService;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
@RequestMapping("/api/rag")
public class RagQAController {

    private final RagQAService ragQAService;
    private final ExecutorService ragExecutorService;

    public RagQAController(RagQAService ragQAService, ExecutorService ragExecutorService) {
        this.ragQAService = ragQAService;
        this.ragExecutorService = ragExecutorService;
    }

    @PostMapping("/qa")
    public RagAnswerResponse qa(@Valid @RequestBody RagQo qo) {
        return ragQAService.qa(qo);
    }

    @GetMapping("/memory/sessions")
    public List<MemorySessionView> sessions() {
        return ragQAService.listMemorySessions(12);
    }

    @GetMapping("/memory/{memoryId}")
    public MemoryDetailResponse memoryDetail(@PathVariable long memoryId) {
        return ragQAService.getMemoryDetail(memoryId);
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
            RagAnswerResponse response = ragQAService.streamQa(qo, delta -> {
                try {
                    emitter.send(SseEmitter.event().name("delta").data(delta));
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            });
            List<Citation> citations = response.citations();
            emitter.send(SseEmitter.event().name("references").data(Map.of("citations", citations)));
            emitter.send(SseEmitter.event().name("done").data("[DONE]"));
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
}
