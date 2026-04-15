package com.team.rag.agent.skill;

import com.team.rag.service.ObservabilityService;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class GetTraceLogsSkill extends BaseAgentSkill {

    private final ObservabilityService observabilityService;

    public GetTraceLogsSkill(ObservabilityService observabilityService) {
        this.observabilityService = observabilityService;
    }

    @Override
    public String name() {
        return "rag.get_trace_logs";
    }

    @Override
    public String description() {
        return "Query persisted observability logs by traceId or userId.";
    }

    @Override
    public boolean destructive() {
        return false;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return schema(Map.of(
                "traceId", "string|optional",
                "userId", "string|optional"
        ));
    }

    @Override
    public Object execute(Map<String, Object> arguments) {
        return observabilityService.list(str(arguments, "traceId"), str(arguments, "userId"));
    }
}
