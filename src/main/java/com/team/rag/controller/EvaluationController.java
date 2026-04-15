package com.team.rag.controller;

import com.team.rag.bean.EvaluationRunRequest;
import com.team.rag.bean.EvaluationRunResponse;
import com.team.rag.service.EvaluationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/evaluation")
public class EvaluationController {

    private final EvaluationService evaluationService;

    public EvaluationController(EvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @PostMapping("/run")
    public EvaluationRunResponse run(@RequestBody EvaluationRunRequest request) {
        return evaluationService.run(request);
    }
}
