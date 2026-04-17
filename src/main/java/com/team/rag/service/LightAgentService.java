package com.team.rag.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.rag.bean.AgentAnswerResponse;
import com.team.rag.bean.AgentStep;
import com.team.rag.bean.ChatMessage;
import com.team.rag.bean.RagAnswerResponse;
import com.team.rag.bean.RagQo;
import com.team.rag.bean.RetrievedChunk;
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
    private final ChatModelService chatModelService;
    private final ObjectMapper objectMapper;

    public LightAgentService(RagQAService ragQAService,
                             ObservabilityService observabilityService,
                             ChatModelService chatModelService,
                             ObjectMapper objectMapper) {
        this.ragQAService = ragQAService;
        this.observabilityService = observabilityService;
        this.chatModelService = chatModelService;
        this.objectMapper = objectMapper;
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

        RetrievalEvaluation evaluation = evaluateRetrieval(qo, response.retrievedChunks());

        if (!evaluation.isSufficient() && !"ALL".equalsIgnoreCase(safe(working.getSourceKind()))) {
            RagQo retry = copy(working);
            retry.setSourceKind("ALL");
            retry.setTopK(Math.max(retry.getTopK() == null ? 8 : retry.getTopK(), 8));
            addStep(steps, "reflect", "RETRY", "评估结果不佳（" + evaluation.reason() + "），扩大到全部来源并二次检索", 0);
            
            RagAnswerResponse retryResponse = runRagTool(retry, steps, false, ignored -> {
            });
            RetrievalEvaluation retryEvaluation = evaluateRetrieval(qo, retryResponse.retrievedChunks());
            addStep(steps, "reflect", retryEvaluation.isSufficient() ? "SUCCESS" : "WARN", 
                    "二次检索评估结果：" + retryEvaluation.reason(), 0);
            response = retryResponse;
        } else {
            addStep(steps, "reflect", evaluation.isSufficient() ? "SUCCESS" : "WARN", 
                    "人工评价级召回分析：" + evaluation.reason(), 0);
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
        
        RetrievalEvaluation evaluation = evaluateRetrieval(qo, response.retrievedChunks());
        emitStep(steps, stepConsumer, "reflect", evaluation.isSufficient() ? "SUCCESS" : "WARN",
                "召回结果分析：" + evaluation.reason(), 0);

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

    private record RetrievalEvaluation(boolean isSufficient, String reason) {
    }

    private RetrievalEvaluation evaluateRetrieval(RagQo qo, List<RetrievedChunk> chunks) {
        if (chunks.isEmpty()) {
            return new RetrievalEvaluation(false, "召回结果为空，无法提供任何信息。");
        }

        StringBuilder context = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            context.append("【片段").append(i + 1).append("】\n");
            context.append(chunks.get(i).content()).append("\n\n");
        }

        String prompt = """
                你是一个检索质量评估专家。请评估以下召回片段是否包含足够且相关的信息来回答用户的问题。
                
                [用户问题]
                %s
                
                [召回片段]
                %s
                
                请判断：
                1. 召回片段是否与问题直接相关？
                2. 召回片段是否足以完整回答问题？
                
                请严格以 JSON 格式输出，包含两个字段：
                - "isSufficient": boolean (如果足以回答返回 true，否则返回 false)
                - "reason": string (简要说明评估理由，指出人工评价级别的优缺点、片段的贡献或缺失的信息)
                """.formatted(qo.getQuery(), context.toString());

        long start = System.currentTimeMillis();
        try {
            String response = chatModelService.chat(qo.getAiProvider(), List.of(new ChatMessage("user", prompt)));
            response = response.trim();
            if (response.startsWith("```json")) {
                response = response.substring(7);
            } else if (response.startsWith("```")) {
                response = response.substring(3);
            }
            if (response.endsWith("```")) {
                response = response.substring(0, response.length() - 3);
            }

            JsonNode jsonNode = objectMapper.readTree(response);
            boolean isSufficient = jsonNode.path("isSufficient").asBoolean(false);
            String reason = jsonNode.path("reason").asText("解析失败");
            
            observabilityService.record("agent", "tool.evaluate_retrieval", "SUCCESS",
                    System.currentTimeMillis() - start, Map.of(
                            "isSufficient", isSufficient,
                            "reason", reason
                    ), null);
            return new RetrievalEvaluation(isSufficient, reason);
        } catch (Exception e) {
            observabilityService.record("agent", "tool.evaluate_retrieval", "ERROR",
                    System.currentTimeMillis() - start, Map.of("error", e.getMessage()), e);
            return new RetrievalEvaluation(true, "评估模型调用失败，默认放行：" + e.getMessage());
        }
    }
}
