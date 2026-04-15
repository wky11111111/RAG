package com.team.rag.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.rag.bean.ChunkView;
import com.team.rag.bean.EvaluationRunRequest;
import com.team.rag.bean.RagQo;
import com.team.rag.bean.SearchFilter;
import com.team.rag.service.DocumentService;
import com.team.rag.service.EvaluationService;
import com.team.rag.service.KnowledgeBaseService;
import com.team.rag.service.ObservabilityService;
import com.team.rag.service.RagQAService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Service
public class RagMcpToolService {

    private final Map<String, ToolHandler> handlers = new LinkedHashMap<>();
    private final List<McpToolDefinition> definitions;
    private final RagQAService ragQAService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final DocumentService documentService;
    private final ObservabilityService observabilityService;
    private final EvaluationService evaluationService;
    private final ObjectMapper objectMapper;

    public RagMcpToolService(RagQAService ragQAService,
                             KnowledgeBaseService knowledgeBaseService,
                             DocumentService documentService,
                             ObservabilityService observabilityService,
                             EvaluationService evaluationService,
                             ObjectMapper objectMapper) {
        this.ragQAService = ragQAService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.documentService = documentService;
        this.observabilityService = observabilityService;
        this.evaluationService = evaluationService;
        this.objectMapper = objectMapper;
        this.definitions = registerTools();
    }

    public List<McpToolDefinition> listTools() {
        return definitions;
    }

    public McpToolCallResponse call(McpToolCallRequest request) {
        long start = System.currentTimeMillis();
        String toolName = request == null ? "" : request.name();
        Map<String, Object> args = request == null || request.arguments() == null
                ? Map.of()
                : request.arguments();
        try {
            ToolHandler handler = handlers.get(toolName);
            if (handler == null) {
                throw new IllegalArgumentException("Unknown MCP tool: " + toolName);
            }
            Object result = observabilityService.time("mcp", toolName, Map.of("arguments", args),
                    () -> handler.call(args));
            return McpToolCallResponse.success(toolName, result, System.currentTimeMillis() - start);
        } catch (RuntimeException exception) {
            observabilityService.record("mcp", toolName, "ERROR", System.currentTimeMillis() - start,
                    Map.of("arguments", args), exception);
            return McpToolCallResponse.failure(toolName, exception.getMessage(), System.currentTimeMillis() - start);
        }
    }

    private List<McpToolDefinition> registerTools() {
        add("rag.search_knowledge",
                "Use the RAG knowledge base to answer a question. Supports category filters and model provider selection.",
                false,
                schema(Map.of(
                        "query", "string|required",
                        "topK", "integer|optional",
                        "categoryPath", "string|optional",
                        "businessScene", "string|optional",
                        "fileType", "string|optional",
                        "projectName", "string|optional",
                        "department", "string|optional",
                        "aiProvider", "dashscope|deepseek|optional"
                )),
                args -> ragQAService.qa(ragQo(args, KnowledgeBaseService.SOURCE_RAG)));

        add("rag.search_memory",
                "Use long-term memory to answer a question. Memory retrieval is isolated by userId and optional conversationId.",
                false,
                schema(Map.of(
                        "query", "string|required",
                        "userId", "string|required",
                        "conversationId", "string|optional",
                        "topK", "integer|optional",
                        "aiProvider", "dashscope|deepseek|optional"
                )),
                args -> ragQAService.qa(ragQo(args, KnowledgeBaseService.SOURCE_MEMORY)));

        add("rag.list_documents",
                "List RAG documents or long-term memories with metadata filters.",
                false,
                schema(Map.of(
                        "sourceKind", "RAG|MEMORY|ALL|optional",
                        "categoryPath", "string|optional",
                        "businessScene", "string|optional",
                        "fileType", "string|optional",
                        "projectName", "string|optional",
                        "department", "string|optional",
                        "userId", "string|optional",
                        "conversationId", "string|optional"
                )),
                args -> knowledgeBaseService.listDocuments(searchFilter(args, null)));

        add("rag.get_document_chunks",
                "Get chunk metadata and text for one document.",
                false,
                schema(Map.of("documentId", "string|required")),
                args -> knowledgeBaseService.getChunksByDocumentUuid(required(args, "documentId")).stream()
                        .map(chunk -> new ChunkView(
                                chunk.getChunkUuid(),
                                chunk.getChunkIndex(),
                                chunk.getTokenCount(),
                                chunk.getContent()
                        ))
                        .toList());

        add("rag.delete_document",
                "Delete one RAG document or one long-term memory. Requires confirm=true.",
                true,
                schema(Map.of(
                        "documentId", "string|required",
                        "sourceKind", "RAG|MEMORY|required",
                        "confirm", "boolean|required"
                )),
                this::deleteDocument);

        add("rag.reindex_document",
                "Rebuild chunks and vectors for one existing document.",
                true,
                schema(Map.of("documentId", "string|required")),
                args -> documentService.reindexDocument(required(args, "documentId")));

        add("rag.get_trace_logs",
                "Query persisted observability logs by traceId or userId.",
                false,
                schema(Map.of(
                        "traceId", "string|optional",
                        "userId", "string|optional"
                )),
                args -> observabilityService.list(str(args, "traceId"), str(args, "userId")));

        add("rag.run_evaluation",
                "Run RAG evaluation cases and return recall/precision/hit-rate metrics.",
                false,
                schema(Map.of(
                        "cases", "array|required",
                        "topK", "integer|optional"
                )),
                args -> evaluationService.run(objectMapper.convertValue(args, EvaluationRunRequest.class)));

        return List.copyOf(definitionsFromHandlers());
    }

    private Object deleteDocument(Map<String, Object> args) {
        if (!bool(args, "confirm")) {
            throw new IllegalArgumentException("delete requires confirm=true");
        }
        String documentId = required(args, "documentId");
        String sourceKind = required(args, "sourceKind");
        if (KnowledgeBaseService.SOURCE_MEMORY.equalsIgnoreCase(sourceKind)) {
            documentService.deleteManualKnowledge(documentId);
            return Map.of("success", true, "message", "MEMORY document deleted", "documentId", documentId);
        }
        if (KnowledgeBaseService.SOURCE_RAG.equalsIgnoreCase(sourceKind)) {
            documentService.deleteRagKnowledge(documentId);
            return Map.of("success", true, "message", "RAG document deleted", "documentId", documentId);
        }
        throw new IllegalArgumentException("sourceKind must be RAG or MEMORY");
    }

    private void add(String name,
                     String description,
                     boolean destructive,
                     Map<String, Object> inputSchema,
                     Function<Map<String, Object>, Object> handler) {
        handlers.put(name, new ToolHandler(new McpToolDefinition(name, description, destructive, inputSchema), handler));
    }

    private List<McpToolDefinition> definitionsFromHandlers() {
        return handlers.values().stream()
                .map(ToolHandler::definition)
                .toList();
    }

    private RagQo ragQo(Map<String, Object> args, String defaultSourceKind) {
        RagQo qo = new RagQo();
        qo.setQuery(required(args, "query"));
        qo.setTopK(integer(args, "topK"));
        qo.setSourceKind(str(args, "sourceKind", defaultSourceKind));
        qo.setCategoryPath(str(args, "categoryPath"));
        qo.setBusinessScene(str(args, "businessScene"));
        qo.setFileType(str(args, "fileType"));
        qo.setProjectName(str(args, "projectName"));
        qo.setDepartment(str(args, "department"));
        qo.setUserId(str(args, "userId"));
        qo.setConversationId(str(args, "conversationId"));
        qo.setAiProvider(str(args, "aiProvider"));
        return qo;
    }

    private SearchFilter searchFilter(Map<String, Object> args, String defaultSourceKind) {
        return new SearchFilter(
                str(args, "sourceKind", defaultSourceKind),
                str(args, "categoryPath"),
                str(args, "businessScene"),
                str(args, "fileType"),
                str(args, "projectName"),
                str(args, "department"),
                str(args, "userId"),
                str(args, "conversationId")
        );
    }

    private Map<String, Object> schema(Map<String, String> properties) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        return schema;
    }

    private String required(Map<String, Object> args, String key) {
        String value = str(args, key);
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value;
    }

    private String str(Map<String, Object> args, String key) {
        return str(args, key, null);
    }

    private String str(Map<String, Object> args, String key, String fallback) {
        Object value = args.get(key);
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : fallback;
    }

    private Integer integer(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private boolean bool(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private record ToolHandler(McpToolDefinition definition, Function<Map<String, Object>, Object> call) {
        Object call(Map<String, Object> arguments) {
            return call.apply(arguments);
        }
    }
}
