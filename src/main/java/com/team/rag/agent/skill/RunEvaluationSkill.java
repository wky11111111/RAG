package com.team.rag.agent.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.rag.bean.EvaluationRunRequest;
import com.team.rag.service.EvaluationService;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class RunEvaluationSkill extends BaseAgentSkill {

    private final EvaluationService evaluationService;
    private final ObjectMapper objectMapper;

    public RunEvaluationSkill(EvaluationService evaluationService, ObjectMapper objectMapper) {
        this.evaluationService = evaluationService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "rag.run_evaluation";
    }

    @Override
    public String description() {
        return "Run RAG evaluation cases and return recall/precision/hit-rate metrics.";
    }

    @Override
    public boolean destructive() {
        return false;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return schema(Map.of(
                "cases", "array|required",
                "topK", "integer|optional"
        ));
    }

    @Override
    public Object execute(Map<String, Object> arguments) {
        EvaluationRunRequest request = objectMapper.convertValue(arguments, EvaluationRunRequest.class);
        return evaluationService.run(request);
    }
}
