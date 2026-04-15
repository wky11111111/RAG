package com.team.rag.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.rag.bean.OperationLogView;
import com.team.rag.entity.OperationLogEntity;
import com.team.rag.repository.OperationLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class ObservabilityService {

    public static final String TRACE_ID = "traceId";
    public static final String USER_ID = "userId";
    public static final String SESSION_ID = "sessionId";

    private static final Logger log = LoggerFactory.getLogger("rag.observability");

    private final OperationLogRepository repository;
    private final ObjectMapper objectMapper;

    public ObservabilityService(OperationLogRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public String traceId() {
        String traceId = MDC.get(TRACE_ID);
        if (StringUtils.hasText(traceId)) {
            return traceId;
        }
        traceId = UUID.randomUUID().toString().replace("-", "");
        MDC.put(TRACE_ID, traceId);
        return traceId;
    }

    public void bind(String userId, String sessionId) {
        if (StringUtils.hasText(userId)) {
            MDC.put(USER_ID, userId);
        }
        if (StringUtils.hasText(sessionId)) {
            MDC.put(SESSION_ID, sessionId);
        }
    }

    public <T> T time(String module, String action, Map<String, Object> detail, Supplier<T> supplier) {
        long start = System.currentTimeMillis();
        try {
            T result = supplier.get();
            record(module, action, "SUCCESS", System.currentTimeMillis() - start, detail, null);
            return result;
        } catch (RuntimeException exception) {
            record(module, action, "ERROR", System.currentTimeMillis() - start, detail, exception);
            throw exception;
        }
    }

    public void record(String module, String action, String status, long costTime, Map<String, Object> detail, Throwable throwable) {
        OperationLogEntity entity = new OperationLogEntity();
        entity.setTraceId(traceId());
        entity.setUserId(MDC.get(USER_ID));
        entity.setSessionId(MDC.get(SESSION_ID));
        entity.setModule(module);
        entity.setAction(action);
        entity.setStatus(status);
        entity.setCostTime(costTime);
        entity.setTimestamp(Instant.now());
        entity.setDetailJson(toJson(detail));
        entity.setErrorStack(throwable == null ? null : stackTrace(throwable));
        try {
            repository.save(entity);
        } catch (RuntimeException ignored) {
            // 日志落库失败不能影响主链路，控制台结构化日志仍可被 ELK/Loki 采集。
        }
        log.info("traceId={} userId={} sessionId={} module={} action={} status={} costTime={} detail={}",
                entity.getTraceId(), entity.getUserId(), entity.getSessionId(), module, action, status, costTime, entity.getDetailJson());
    }

    public List<OperationLogView> list(String traceId, String userId) {
        List<OperationLogEntity> logs;
        if (StringUtils.hasText(traceId)) {
            logs = repository.findTop200ByTraceIdOrderByTimestampAsc(traceId);
        } else if (StringUtils.hasText(userId)) {
            logs = repository.findTop200ByUserIdOrderByTimestampDesc(userId);
        } else {
            logs = repository.findTop200ByOrderByTimestampDesc();
        }
        return logs.stream().map(this::toView).toList();
    }

    private OperationLogView toView(OperationLogEntity entity) {
        return new OperationLogView(
                entity.getId(),
                entity.getTraceId(),
                entity.getUserId(),
                entity.getSessionId(),
                entity.getModule(),
                entity.getAction(),
                entity.getStatus(),
                entity.getCostTime(),
                entity.getTimestamp(),
                entity.getDetailJson(),
                entity.getErrorStack()
        );
    }

    private String toJson(Map<String, Object> detail) {
        if (detail == null || detail.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException exception) {
            return "{\"jsonError\":\"" + exception.getMessage() + "\"}";
        }
    }

    private String stackTrace(Throwable throwable) {
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }
}
