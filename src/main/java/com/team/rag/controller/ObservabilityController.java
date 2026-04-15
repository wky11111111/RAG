package com.team.rag.controller;

import com.team.rag.bean.OperationLogView;
import com.team.rag.service.ObservabilityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/observability")
public class ObservabilityController {

    private final ObservabilityService observabilityService;

    public ObservabilityController(ObservabilityService observabilityService) {
        this.observabilityService = observabilityService;
    }

    @GetMapping("/logs")
    public List<OperationLogView> logs(@RequestParam(required = false) String traceId,
                                       @RequestParam(required = false) String userId) {
        return observabilityService.list(traceId, userId);
    }
}
