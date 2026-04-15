package com.team.rag.agent.skill;

import com.team.rag.bean.RagQo;
import com.team.rag.bean.SearchFilter;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

public abstract class BaseAgentSkill implements AgentSkill {

    protected Map<String, Object> schema(Map<String, String> properties) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        return schema;
    }

    protected String required(Map<String, Object> args, String key) {
        String value = str(args, key);
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value;
    }

    protected String str(Map<String, Object> args, String key) {
        return str(args, key, null);
    }

    protected String str(Map<String, Object> args, String key, String fallback) {
        Object value = args.get(key);
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : fallback;
    }

    protected Integer integer(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    protected boolean bool(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    protected RagQo ragQo(Map<String, Object> args, String defaultSourceKind) {
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

    protected SearchFilter searchFilter(Map<String, Object> args, String defaultSourceKind) {
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
}
