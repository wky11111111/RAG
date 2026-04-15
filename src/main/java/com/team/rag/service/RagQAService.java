package com.team.rag.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.rag.bean.ChatMessage;
import com.team.rag.bean.Citation;
import com.team.rag.bean.MemoryDetailResponse;
import com.team.rag.bean.MemoryRoundView;
import com.team.rag.bean.MemorySessionView;
import com.team.rag.bean.PineconeMatch;
import com.team.rag.bean.RagAnswerResponse;
import com.team.rag.bean.RagQo;
import com.team.rag.bean.RetrievedChunk;
import com.team.rag.bean.SearchFilter;
import com.team.rag.client.DashScopeClient;
import com.team.rag.client.PineconeClient;
import com.team.rag.config.RagProperties;
import com.team.rag.entity.KnowledgeChunkEntity;
import com.team.rag.util.BM25Retriever;
import com.team.rag.util.TextTokenizer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Service
public class RagQAService {

    private final BM25Retriever bm25Retriever;
    private final PineconeClient pineconeClient;
    private final DashScopeClient dashScopeClient;
    private final ChatModelService chatModelService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final ConversationMemoryService conversationMemoryService;
    private final RagProperties ragProperties;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final TextTokenizer textTokenizer;
    private final SystemPromptService systemPromptService;
    private final ObservabilityService observabilityService;

    public RagQAService(BM25Retriever bm25Retriever,
                        PineconeClient pineconeClient,
                        DashScopeClient dashScopeClient,
                        ChatModelService chatModelService,
                        KnowledgeBaseService knowledgeBaseService,
                        ConversationMemoryService conversationMemoryService,
                        RagProperties ragProperties,
                        StringRedisTemplate stringRedisTemplate,
                        ObjectMapper objectMapper,
                        TextTokenizer textTokenizer,
                        SystemPromptService systemPromptService,
                        ObservabilityService observabilityService) {
        this.bm25Retriever = bm25Retriever;
        this.pineconeClient = pineconeClient;
        this.dashScopeClient = dashScopeClient;
        this.chatModelService = chatModelService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.conversationMemoryService = conversationMemoryService;
        this.ragProperties = ragProperties;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.textTokenizer = textTokenizer;
        this.systemPromptService = systemPromptService;
        this.observabilityService = observabilityService;
    }

    public RagAnswerResponse qa(String query, Long memoryId, Integer topK) {
        RagQo qo = new RagQo();
        qo.setQuery(query);
        qo.setMemoryId(memoryId);
        qo.setTopK(topK);
        return qa(qo);
    }

    public RagAnswerResponse qa(RagQo qo) {
        long effectiveMemoryId = qo.getMemoryId() == null ? 1L : qo.getMemoryId();
        observabilityService.bind(qo.getUserId(), StringUtils.hasText(qo.getConversationId())
                ? qo.getConversationId()
                : String.valueOf(effectiveMemoryId));
        SearchFilter filter = searchFilter(qo);
        String aiProvider = chatModelService.resolveProvider(qo.getAiProvider());
        List<ChatMessage> recentMessages = recentMessages(effectiveMemoryId);
        String cacheKey = answerCacheKey(effectiveMemoryId, qo.getQuery(), recentMessages, filter, aiProvider);
        try {
            String cached = stringRedisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                try {
                    return objectMapper.readValue(cached, RagAnswerResponse.class);
                } catch (JsonProcessingException exception) {
                    stringRedisTemplate.delete(cacheKey);
                }
            }
        } catch (RuntimeException ignored) {
        }

        long start = System.currentTimeMillis();
        try {
        String rewrittenQuery = observabilityService.time("rag", "query_rewrite", Map.of(
                "memoryId", effectiveMemoryId,
                "historySize", recentMessages.size(),
                "aiProvider", aiProvider
        ), () -> rewriteQuery(qo.getQuery(), recentMessages, aiProvider));
        List<RetrievedChunk> hits = observabilityService.time("rag", "retrieval_rerank", Map.of(
                "query", shrink(rewrittenQuery, 120),
                "topK", effectiveTopK(qo.getTopK()),
                "filter", filter.toString()
        ), () -> mergeHits(rewrittenQuery, effectiveTopK(qo.getTopK()), filter));
        recordRetrievalResults(hits, filter);
        String answer = observabilityService.time("rag", "llm_response", Map.of(
                "hitCount", hits.size(),
                "streaming", false,
                "aiProvider", aiProvider
        ), () -> generateAnswer(qo.getQuery(), rewrittenQuery, hits, recentMessages, filter, aiProvider));
        List<Citation> citations = buildFileLevelCitations(hits);

        RagAnswerResponse response = new RagAnswerResponse(
                answer,
                citations,
                hits,
                effectiveMemoryId,
                System.currentTimeMillis() - start
        );

        appendAndMaybeSummarize(effectiveMemoryId, qo.getQuery(), answer, aiProvider);

        try {
            stringRedisTemplate.opsForValue().set(
                    cacheKey,
                    objectMapper.writeValueAsString(response),
                    Duration.ofMinutes(ragProperties.getAnswer().getCacheTtlMinutes())
            );
        } catch (JsonProcessingException | RuntimeException ignored) {
        }
        observabilityService.record("rag", "qa_total", "SUCCESS", System.currentTimeMillis() - start, Map.of(
                "hitCount", hits.size(),
                "citationCount", citations.size(),
                "memoryId", effectiveMemoryId,
                "aiProvider", aiProvider
        ), null);
        return response;
        } catch (RuntimeException exception) {
            observabilityService.record("rag", "qa_total", "ERROR", System.currentTimeMillis() - start, Map.of(
                    "memoryId", effectiveMemoryId,
                    "query", shrink(qo.getQuery(), 200)
            ), exception);
            throw exception;
        }
    }

    public RagAnswerResponse streamQa(String query, Long memoryId, Integer topK, Consumer<String> tokenConsumer) {
        RagQo qo = new RagQo();
        qo.setQuery(query);
        qo.setMemoryId(memoryId);
        qo.setTopK(topK);
        return streamQa(qo, tokenConsumer);
    }

    public RagAnswerResponse streamQa(RagQo qo, Consumer<String> tokenConsumer) {
        long effectiveMemoryId = qo.getMemoryId() == null ? 1L : qo.getMemoryId();
        observabilityService.bind(qo.getUserId(), StringUtils.hasText(qo.getConversationId())
                ? qo.getConversationId()
                : String.valueOf(effectiveMemoryId));
        long start = System.currentTimeMillis();
        SearchFilter filter = searchFilter(qo);
        String aiProvider = chatModelService.resolveProvider(qo.getAiProvider());
        List<ChatMessage> recentMessages = recentMessages(effectiveMemoryId);

        try {
        String rewrittenQuery = observabilityService.time("rag", "query_rewrite", Map.of(
                "memoryId", effectiveMemoryId,
                "historySize", recentMessages.size(),
                "aiProvider", aiProvider
        ), () -> rewriteQuery(qo.getQuery(), recentMessages, aiProvider));
        List<RetrievedChunk> hits = observabilityService.time("rag", "retrieval_rerank", Map.of(
                "query", shrink(rewrittenQuery, 120),
                "topK", effectiveTopK(qo.getTopK()),
                "filter", filter.toString()
        ), () -> mergeHits(rewrittenQuery, effectiveTopK(qo.getTopK()), filter));
        recordRetrievalResults(hits, filter);
        String answer = observabilityService.time("rag", "llm_response", Map.of(
                "hitCount", hits.size(),
                "streaming", true,
                "aiProvider", aiProvider
        ), () -> generateAnswerStream(qo.getQuery(), rewrittenQuery, hits, recentMessages, filter, aiProvider, tokenConsumer));
        List<Citation> citations = buildFileLevelCitations(hits);

        RagAnswerResponse response = new RagAnswerResponse(
                answer,
                citations,
                hits,
                effectiveMemoryId,
                System.currentTimeMillis() - start
        );

        appendAndMaybeSummarize(effectiveMemoryId, qo.getQuery(), answer, aiProvider);
        observabilityService.record("rag", "qa_total", "SUCCESS", System.currentTimeMillis() - start, Map.of(
                "hitCount", hits.size(),
                "citationCount", citations.size(),
                "memoryId", effectiveMemoryId,
                "streaming", true,
                "aiProvider", aiProvider
        ), null);
        return response;
        } catch (RuntimeException exception) {
            observabilityService.record("rag", "qa_total", "ERROR", System.currentTimeMillis() - start, Map.of(
                    "memoryId", effectiveMemoryId,
                    "query", shrink(qo.getQuery(), 200),
                    "streaming", true
            ), exception);
            throw exception;
        }
    }

    public List<MemorySessionView> listMemorySessions(int limit) {
        return conversationMemoryService.listMemoryIds(limit).stream()
                .map(this::toSessionView)
                .toList();
    }

    public MemoryDetailResponse getMemoryDetail(long memoryId) {
        List<ChatMessage> messages = safeAllMessages(memoryId);
        List<MemoryRoundView> rounds = toRounds(messages);
        String summary = safeSummary(memoryId);
        int tokenCount = memoryTokenCount(summary, messages);
        int threshold = summaryThreshold();
        return new MemoryDetailResponse(
                memoryId,
                rounds.size(),
                rounds,
                messages,
                summary,
                tokenCount,
                threshold,
                summary != null && !summary.isBlank()
        );
    }

    private SearchFilter searchFilter(RagQo qo) {
        String sourceKind = StringUtils.hasText(qo.getSourceKind()) ? qo.getSourceKind() : KnowledgeBaseService.SOURCE_RAG;
        return new SearchFilter(
                sourceKind,
                qo.getCategoryPath(),
                qo.getBusinessScene(),
                qo.getFileType(),
                qo.getProjectName(),
                qo.getDepartment(),
                qo.getUserId(),
                qo.getConversationId()
        );
    }

    private List<ChatMessage> recentMessages(long memoryId) {
        List<ChatMessage> messages = conversationMemoryService.getRecentMessages(
                memoryId,
                ragProperties.getAnswer().getHistorySize()
        );
        return messages == null ? List.of() : messages;
    }

    private int effectiveTopK(Integer topK) {
        return topK == null ? ragProperties.getRetrieval().getFinalTopK() : topK;
    }

    private String answerCacheKey(long memoryId,
                                  String query,
                                  List<ChatMessage> recentMessages,
                                  SearchFilter filter,
                                  String aiProvider) {
        return "rag:answer:" + aiProvider + ":" + memoryId + ":" + query.hashCode() + ":" + recentMessages.hashCode() + ":" + filter.hashCode();
    }

    private void appendAndMaybeSummarize(long memoryId, String query, String answer, String aiProvider) {
        conversationMemoryService.append(memoryId, "user", query);
        conversationMemoryService.append(memoryId, "assistant", answer);
        summarizeIfNeeded(memoryId, aiProvider);
    }

    private void summarizeIfNeeded(long memoryId, String aiProvider) {
        List<ChatMessage> messages = safeAllMessages(memoryId);
        String existingSummary = safeSummary(memoryId);
        int tokenCount = memoryTokenCount(existingSummary, messages);
        if (tokenCount < summaryThreshold() || messages.size() <= summaryKeepMessages()) {
            return;
        }

        List<ChatMessage> summaryMessages = List.of(
                new ChatMessage("system", """
                        你是对话记忆摘要器。请把历史对话压缩成稳定、可继续对话的短期记忆摘要。
                        保留用户偏好、已确认事实、关键约束、未完成任务和重要上下文。
                        不要编造，不要加入知识库以外的新事实。
                        """),
                new ChatMessage("user", """
                        已有摘要：
                        %s

                        需要压缩的历史对话：
                        %s

                        请输出更新后的摘要。
                        """.formatted(existingSummary == null || existingSummary.isBlank() ? "暂无" : existingSummary, buildHistoryText(messages)))
        );

        try {
            String summary = chatModelService.chat(aiProvider, summaryMessages).trim();
            if (!summary.isBlank()) {
                conversationMemoryService.replaceWithSummary(memoryId, summary, tail(messages, summaryKeepMessages()));
            }
        } catch (RuntimeException ignored) {
        }
    }

    private List<ChatMessage> tail(List<ChatMessage> messages, int keep) {
        if (messages.size() <= keep) {
            return messages;
        }
        return new ArrayList<>(messages.subList(messages.size() - keep, messages.size()));
    }

    private int memoryTokenCount(String summary, List<ChatMessage> messages) {
        int total = estimateTokens(summary);
        for (ChatMessage message : messages) {
            total += estimateTokens(message.content());
        }
        return total;
    }

    private int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return Math.max(textTokenizer.tokenize(text).size(), (int) Math.ceil(text.length() / 4.0));
    }

    private int summaryThreshold() {
        Integer threshold = ragProperties.getAnswer().getMemorySummaryTokenThreshold();
        return threshold == null || threshold < 1000 ? 32000 : threshold;
    }

    private int summaryKeepMessages() {
        Integer keep = ragProperties.getAnswer().getMemorySummaryKeepMessages();
        return keep == null || keep < 2 ? 8 : keep;
    }

    private String safeSummary(long memoryId) {
        String summary = conversationMemoryService.getSummary(memoryId);
        return summary == null ? "" : summary;
    }

    private List<ChatMessage> safeAllMessages(long memoryId) {
        List<ChatMessage> messages = conversationMemoryService.getAllMessages(memoryId);
        return messages == null ? List.of() : messages;
    }

    private String rewriteQuery(String query, List<ChatMessage> recentMessages, String aiProvider) {
        if (recentMessages.isEmpty()) {
            return query;
        }

        List<ChatMessage> rewriteMessages = List.of(
                new ChatMessage("system", """
                        你是 RAG 知识库检索前的查询改写器。
                        请结合短期记忆和当前用户问题，把它改写成一条完整、清晰、适合检索知识库的中文问题。
                        只输出改写后的问题，不要解释，不要编号，不要添加与用户意图无关的新信息。
                        """),
                new ChatMessage("user", """
                        短期记忆：
                        %s

                        当前用户问题：
                        %s
                        """.formatted(buildHistoryText(recentMessages), query))
        );

        try {
            String rewritten = chatModelService.chat(aiProvider, rewriteMessages);
            return sanitizeRewrittenQuery(rewritten, query);
        } catch (RuntimeException ignored) {
            return query;
        }
    }

    private String sanitizeRewrittenQuery(String rewritten, String fallback) {
        if (rewritten == null || rewritten.isBlank()) {
            return fallback;
        }
        String normalized = rewritten
                .replaceAll("(?i)^rewritten query[:：]\\s*", "")
                .replaceAll("^改写后的问题[:：]\\s*", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (normalized.isBlank()) {
            return fallback;
        }
        return normalized.length() > 500 ? normalized.substring(0, 500) : normalized;
    }

    private String buildHistoryText(List<ChatMessage> recentMessages) {
        StringBuilder builder = new StringBuilder();
        for (ChatMessage message : recentMessages) {
            String role = switch (message.role().toLowerCase()) {
                case "user" -> "用户";
                case "system" -> "记忆摘要";
                default -> "助手";
            };
            builder.append(role).append(": ").append(shrink(message.content(), 300)).append('\n');
        }
        return builder.toString().trim();
    }

    private List<RetrievedChunk> mergeHits(String query, int limit, SearchFilter filter) {
        List<RetrievedChunk> bm25Hits = bm25Retriever.retrieve(query, filter);
        List<RetrievedChunk> vectorHits = vectorRetrieve(query, filter);

        Map<String, RetrievedChunk> allHits = new LinkedHashMap<>();
        Map<String, Double> fusedScores = new HashMap<>();

        applyRrfScores(bm25Hits, allHits, fusedScores);
        applyRrfScores(vectorHits, allHits, fusedScores);

        List<RetrievedChunk> fused = fusedScores.entrySet().stream()
                .filter(entry -> entry.getValue() > 0.0)
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(Math.max(limit * 3, limit))
                .map(entry -> {
                    RetrievedChunk hit = allHits.get(entry.getKey());
                    return new RetrievedChunk(
                            hit.chunkId(),
                            hit.documentId(),
                            hit.documentName(),
                            hit.docType(),
                            entry.getValue(),
                            hit.content()
                    );
                })
                .toList();
        return rerank(query, fused).stream().limit(limit).toList();
    }

    private void applyRrfScores(List<RetrievedChunk> hits,
                                Map<String, RetrievedChunk> allHits,
                                Map<String, Double> fusedScores) {
        int k = 60;
        for (int index = 0; index < hits.size(); index++) {
            RetrievedChunk hit = hits.get(index);
            allHits.putIfAbsent(hit.chunkId(), hit);
            fusedScores.merge(hit.chunkId(), 1.0 / (k + index + 1), Double::sum);
        }
    }

    private List<RetrievedChunk> rerank(String query, List<RetrievedChunk> candidates) {
        List<String> queryTerms = textTokenizer.tokenize(query).stream()
                .map(String::toLowerCase)
                .distinct()
                .toList();
        return candidates.stream()
                .map(hit -> {
                    double lexical = lexicalOverlap(queryTerms, hit.content());
                    double score = hit.score() * 0.7 + lexical * 0.3;
                    return new RetrievedChunk(hit.chunkId(), hit.documentId(), hit.documentName(), hit.docType(), score, hit.content());
                })
                .sorted((left, right) -> Double.compare(right.score(), left.score()))
                .toList();
    }

    private double lexicalOverlap(List<String> queryTerms, String content) {
        if (queryTerms.isEmpty() || content == null || content.isBlank()) {
            return 0.0;
        }
        String normalized = content.toLowerCase();
        long matched = queryTerms.stream().filter(normalized::contains).count();
        return matched / (double) queryTerms.size();
    }

    private List<RetrievedChunk> vectorRetrieve(String query, SearchFilter filter) {
        List<List<Float>> embeddings = observabilityService.time("vector", "query_embedding", Map.of(
                "query", shrink(query, 120)
        ), () -> dashScopeClient.embedAll(List.of(query)));
        int topK = filter.active()
                ? Math.max(ragProperties.getRetrieval().getVectorTopK() * 4, ragProperties.getRetrieval().getVectorTopK())
                : ragProperties.getRetrieval().getVectorTopK();
        List<PineconeMatch> matches = observabilityService.time("vector", "pinecone_query", Map.of(
                "topK", topK,
                "filter", filter.toString()
        ), () -> queryVectorNamespaces(embeddings.get(0), topK, filter));
        List<String> chunkIds = matches.stream().map(PineconeMatch::id).toList();
        Map<String, KnowledgeChunkEntity> chunkMap = knowledgeBaseService.findChunksByUuid(chunkIds);

        List<RetrievedChunk> hits = new ArrayList<>();
        for (PineconeMatch match : matches) {
            KnowledgeChunkEntity chunk = chunkMap.get(match.id());
            if (chunk == null || !filter.matches(chunk.getDocument())) {
                continue;
            }
            hits.add(new RetrievedChunk(
                    chunk.getChunkUuid(),
                    chunk.getDocument().getDocumentUuid(),
                    chunk.getDocument().getName(),
                    chunk.getDocument().getDocType(),
                    match.score(),
                    chunk.getContent()
            ));
        }
        return hits;
    }

    private List<PineconeMatch> queryVectorNamespaces(List<Float> vector, int topK, SearchFilter filter) {
        if (filter.sourceKind() != null) {
            return pineconeClient.query(vector, topK, filter);
        }
        List<PineconeMatch> matches = new ArrayList<>();
        matches.addAll(pineconeClient.query(vector, topK, withSourceKind(filter, KnowledgeBaseService.SOURCE_RAG)));
        matches.addAll(pineconeClient.query(vector, topK, withSourceKind(filter, KnowledgeBaseService.SOURCE_MEMORY)));
        return matches.stream()
                .sorted((left, right) -> Double.compare(right.score(), left.score()))
                .limit(topK)
                .toList();
    }

    private SearchFilter withSourceKind(SearchFilter filter, String sourceKind) {
        return new SearchFilter(
                sourceKind,
                filter.categoryPath(),
                filter.businessScene(),
                filter.fileType(),
                filter.projectName(),
                filter.department(),
                filter.userId(),
                filter.conversationId()
        );
    }

    private String generateAnswer(String query,
                                  String rewrittenQuery,
                                  List<RetrievedChunk> hits,
                                  List<ChatMessage> recentMessages,
                                  SearchFilter filter,
                                  String aiProvider) {
        if (hits.isEmpty()) {
            return "知识库中暂时没有检索到与当前分类条件直接相关的内容，请先补充资料，或者调整分类筛选条件。";
        }

        return chatModelService.chat(aiProvider, buildAnswerMessages(query, rewrittenQuery, hits, recentMessages, filter)).trim();
    }

    private String generateAnswerStream(String query,
                                        String rewrittenQuery,
                                        List<RetrievedChunk> hits,
                                        List<ChatMessage> recentMessages,
                                        SearchFilter filter,
                                        String aiProvider,
                                        Consumer<String> tokenConsumer) {
        if (hits.isEmpty()) {
            String fallback = "知识库中暂时没有检索到与当前分类条件直接相关的内容，请先补充资料，或者调整分类筛选条件。";
            tokenConsumer.accept(fallback);
            return fallback;
        }

        return chatModelService.streamChat(aiProvider, buildAnswerMessages(query, rewrittenQuery, hits, recentMessages, filter), tokenConsumer).trim();
    }

    private List<ChatMessage> buildAnswerMessages(String query,
                                                  String rewrittenQuery,
                                                  List<RetrievedChunk> hits,
                                                  List<ChatMessage> recentMessages,
                                                  SearchFilter filter) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("system", systemPromptService.getPrompt()));
        messages.addAll(recentMessages);

        StringBuilder context = new StringBuilder();
        for (int i = 0; i < hits.size(); i++) {
            RetrievedChunk hit = hits.get(i);
            context.append("片段").append(i + 1)
                    .append(" [").append(hit.documentName()).append(" / ").append(hit.docType()).append("]\n")
                    .append(hit.content()).append("\n\n");
        }

        messages.add(new ChatMessage("user", """
                请根据以下知识库片段回答用户问题。

                当前检索过滤：
                %s

                知识库片段：
                %s

                用户原始问题：
                %s

                检索改写问题：
                %s
                """.formatted(filter, context, query, rewrittenQuery)));
        return messages;
    }

    private List<Citation> buildFileLevelCitations(List<RetrievedChunk> hits) {
        Map<String, CitationAccumulator> grouped = new LinkedHashMap<>();
        for (RetrievedChunk hit : hits) {
            grouped.computeIfAbsent(
                    hit.documentId(),
                    ignored -> new CitationAccumulator(hit.documentId(), hit.documentName(), hit.docType())
            ).accept(hit.score());
        }

        return grouped.values().stream()
                .limit(ragProperties.getAnswer().getMaxCitations())
                .map(item -> new Citation(
                        item.documentId,
                        item.documentName,
                        item.bestScore,
                        item.docType,
                        item.hitCount
                ))
                .toList();
    }

    private void recordRetrievalResults(List<RetrievedChunk> hits, SearchFilter filter) {
        List<Map<String, Object>> resultDetails = hits.stream()
                .limit(10)
                .map(hit -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("documentId", hit.documentId());
                    item.put("documentName", hit.documentName());
                    item.put("chunkId", hit.chunkId());
                    item.put("score", hit.score());
                    item.put("docType", hit.docType());
                    return item;
                })
                .toList();
        double topScore = hits.stream().mapToDouble(RetrievedChunk::score).max().orElse(0.0);
        observabilityService.record("rag", "retrieval_result", "SUCCESS", 0, Map.of(
                "recallCount", hits.size(),
                "topScore", topScore,
                "filter", filter.toString(),
                "results", resultDetails
        ), null);
    }

    private MemorySessionView toSessionView(long memoryId) {
        List<ChatMessage> messages = safeAllMessages(memoryId);
        List<MemoryRoundView> rounds = toRounds(messages);
        String title = rounds.isEmpty() ? "新会话" : fallback(rounds.get(0).userPreview(), "新会话");
        String lastPreview;
        if (rounds.isEmpty()) {
            lastPreview = "暂无对话内容";
        } else {
            MemoryRoundView lastRound = rounds.get(rounds.size() - 1);
            lastPreview = fallback(lastRound.assistantPreview(), lastRound.userPreview());
        }
        return new MemorySessionView(memoryId, title, rounds.size(), lastPreview);
    }

    private List<MemoryRoundView> toRounds(List<ChatMessage> messages) {
        List<MemoryRoundView> rounds = new ArrayList<>();
        String pendingUser = null;
        int round = 0;

        for (ChatMessage message : messages) {
            if ("user".equalsIgnoreCase(message.role())) {
                if (pendingUser != null) {
                    round++;
                    rounds.add(new MemoryRoundView(round, shrink(pendingUser, 32), ""));
                }
                pendingUser = message.content();
            } else if ("assistant".equalsIgnoreCase(message.role())) {
                round++;
                rounds.add(new MemoryRoundView(
                        round,
                        shrink(pendingUser, 32),
                        shrink(message.content(), 40)
                ));
                pendingUser = null;
            }
        }

        if (pendingUser != null) {
            round++;
            rounds.add(new MemoryRoundView(round, shrink(pendingUser, 32), ""));
        }
        return rounds;
    }

    private String shrink(String text, int maxLength) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    private String fallback(String preferred, String alternative) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        if (alternative != null && !alternative.isBlank()) {
            return alternative;
        }
        return "新会话";
    }

    private static final class CitationAccumulator {

        private final String documentId;
        private final String documentName;
        private final String docType;
        private double bestScore;
        private int hitCount;

        private CitationAccumulator(String documentId, String documentName, String docType) {
            this.documentId = documentId;
            this.documentName = documentName;
            this.docType = docType;
        }

        private void accept(double score) {
            this.bestScore = Math.max(this.bestScore, score);
            this.hitCount++;
        }
    }
}
