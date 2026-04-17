package com.team.rag.bean;

public class TaskStateEntry {
    private String taskId;
    private String stateCategory;
    private String stateKey;
    private String content;
    private long timestamp;

    public TaskStateEntry() {
    }

    public TaskStateEntry(String taskId, String stateCategory, String stateKey, String content, long timestamp) {
        this.taskId = taskId;
        this.stateCategory = stateCategory;
        this.stateKey = stateKey;
        this.content = content;
        this.timestamp = timestamp;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getStateCategory() {
        return stateCategory;
    }

    public void setStateCategory(String stateCategory) {
        this.stateCategory = stateCategory;
    }

    public String getStateKey() {
        return stateKey;
    }

    public void setStateKey(String stateKey) {
        this.stateKey = stateKey;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
