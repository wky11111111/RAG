package com.team.rag.service;

import com.team.rag.bean.AgentAnswerResponse;
import com.team.rag.bean.AgentStep;
import com.team.rag.bean.RagAnswerResponse;
import com.team.rag.bean.RagQo;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Service
public class LightAgentService {

    private final RagQAService ragQAService;
    private final ObservabilityService observabilityService;

    public LightAgentService(RagQAService ragQAService, ObservabilityService observabilityService) {
        this.ragQAService = ragQAService;
        this.observabilityService = observabilityService;
    }

    public AgentAnswerResponse qa(RagQo qo) {
        List<AgentStep> steps = new ArrayList<>();
        long start = System.currentTimeMillis();
        RagQo working = copy(qo);

        addStep(steps, "plan", "SUCCESS", planDetail(qo), 0);
        observabilityService.record("agent", "plan", "SUCCESS", 0, Map.of(
                "query", safe(qo.getQuery()),
                "sourceKind", safe(qo.getSourceKind()),
                "strategy", planDetail(qo)
        ), null);

        if (shouldSearchAll(qo)) {
            working.setSourceKind("ALL");
            addStep(steps, "route", "SUCCESS", "问题依赖上下文或记忆，自动路由到全部来源", 0);
        }

        RagAnswerResponse response = runRagTool(working, steps, false, ignored -> {
        });

        if (response.retrievedChunks().isEmpty() && !"ALL".equalsIgnoreCase(safe(working.getSourceKind()))) {
            RagQo retry = copy(working);
            retry.setSourceKind("ALL");
            retry.setTopK(Math.max(retry.getTopK() == null ? 8 : retry.getTopK(), 8));
            addStep(steps, "reflect", "RETRY", "首次召回为空，扩大到全部来源并二次检索", 0);
            response = runRagTool(retry, steps, false, ignored -> {
            });
        } else {
            addStep(steps, "reflect", "SUCCESS", "召回结果可用于回答，无需二次检索", 0);
        }

        return new AgentAnswerResponse(
                response.answer(),
                response.citations(),
                response.retrievedChunks(),
                response.memoryId(),
                System.currentTimeMillis() - start,
                steps
        );
    }

    public AgentAnswerResponse streamQa(RagQo qo,
                                        Consumer<String> tokenConsumer,
                                        Consumer<AgentStep> stepConsumer) {
        List<AgentStep> steps = new ArrayList<>();
        long start = System.currentTimeMillis();
        RagQo working = copy(qo);

        emitStep(steps, stepConsumer, "plan", "SUCCESS", planDetail(qo), 0);
        observabilityService.record("agent", "plan", "SUCCESS", 0, Map.of(
                "query", safe(qo.getQuery()),
                "sourceKind", safe(qo.getSourceKind()),
                "strategy", planDetail(qo)
        ), null);

        if (shouldSearchAll(qo)) {
            working.setSourceKind("ALL");
            emitStep(steps, stepConsumer, "route", "SUCCESS", "问题依赖上下文或记忆，自动路由到全部来源", 0);
        }

        RagAnswerResponse response = runRagTool(working, steps, true, tokenConsumer, stepConsumer);
        emitStep(steps, stepConsumer, "reflect", "SUCCESS",
                response.retrievedChunks().isEmpty() ? "本次召回为空，建议补充资料或放宽分类过滤" : "召回结果可用于回答",
                0);

        return new AgentAnswerResponse(
                response.answer(),
                response.citations(),
                response.retrievedChunks(),
                response.memoryId(),
                System.currentTimeMillis() - start,
                steps
        );
    }

    private RagAnswerResponse runRagTool(RagQo qo,
                                         List<AgentStep> steps,
                                         boolean streaming,
                                         Consumer<String> tokenConsumer) {
        return runRagTool(qo, steps, streaming, tokenConsumer, ignored -> {
        });
    }

    private RagAnswerResponse runRagTool(RagQo qo,
                                         List<AgentStep> steps,
                                         boolean streaming,
                                         Consumer<String> tokenConsumer,
                                         Consumer<AgentStep> stepConsumer) {
        long start = System.currentTimeMillis();
        try {
            RagAnswerResponse response = streaming
                    ? ragQAService.streamQa(qo, tokenConsumer)
                    : ragQAService.qa(qo);
            emitStep(steps, stepConsumer, "tool.retrieve_and_answer", "SUCCESS",
                    "调用 RAG 工具完成检索、重排、Prompt 拼接和回答，召回 "
                            + response.retrievedChunks().size() + " 个片段",
                    System.currentTimeMillis() - start);
            observabilityService.record("agent", "tool.retrieve_and_answer", "SUCCESS",
                    System.currentTimeMillis() - start, Map.of(
                            "hitCount", response.retrievedChunks().size(),
                            "memoryId", response.memoryId(),
                            "sourceKind", safe(qo.getSourceKind())
                    ), null);
            return response;
        } catch (RuntimeException exception) {
            emitStep(steps, stepConsumer, "tool.retrieve_and_answer", "ERROR",
                    exception.getMessage(), System.currentTimeMillis() - start);
            observabilityService.record("agent", "tool.retrieve_and_answer", "ERROR",
                    System.currentTimeMillis() - start, Map.of("sourceKind", safe(qo.getSourceKind())), exception);
            throw exception;
        }
    }

    private boolean shouldSearchAll(RagQo qo) {
        if (StringUtils.hasText(qo.getSourceKind())) {
            return false;
        }
        String query = safe(qo.getQuery()).toLowerCase();
        return query.contains("记忆")
                || query.contains("之前")
                || query.contains("刚才")
                || query.contains("上次")
                || query.contains("我")
                || query.contains("我的");
    }

    private String planDetail(RagQo qo) {
        List<String> tasks = new ArrayList<>();
        tasks.add("分析用户问题");
        tasks.add("结合短期记忆进行查询重写");
        tasks.add("调用 RAG 检索工具");
        tasks.add("基于召回结果生成答案和引用");
        if (shouldSearchAll(qo)) {
            tasks.add("问题可能依赖长期记忆，优先搜索全部来源");
        }
        return String.join(" -> ", tasks);
    }

    private RagQo copy(RagQo source) {
        RagQo target = new RagQo();
        target.setQuery(source.getQuery());
        target.setMemoryId(source.getMemoryId());
        target.setTopK(source.getTopK());
        target.setSourceKind(source.getSourceKind());
        target.setCategoryPath(source.getCategoryPath());
        target.setBusinessScene(source.getBusinessScene());
        target.setFileType(source.getFileType());
        target.setProjectName(source.getProjectName());
        target.setDepartment(source.getDepartment());
        target.setUserId(source.getUserId());
        target.setConversationId(source.getConversationId());
        target.setAiProvider(source.getAiProvider());
        return target;
    }

    private void addStep(List<AgentStep> steps, String name, String status, String detail, long costTimeMs) {
        steps.add(new AgentStep(name, status, detail, costTimeMs));
    }

    private void emitStep(List<AgentStep> steps,
                          Consumer<AgentStep> stepConsumer,
                          String name,
                          String status,
                          String detail,
                          long costTimeMs) {
        AgentStep step = new AgentStep(name, status, detail, costTimeMs);
        steps.add(step);
        stepConsumer.accept(step);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
