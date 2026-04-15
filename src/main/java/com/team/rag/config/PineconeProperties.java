package com.team.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pinecone")
public class PineconeProperties {

    private String controlUrl;
    private String apiKey;
    private String apiVersion;
    private String indexName;
    private String indexHost;
    private String namespace;
    private String metric;
    private String cloud;
    private String region;
    private Integer dimension;
    private Boolean autoCreateIndex;

    public String getControlUrl() {
        return controlUrl;
    }

    public void setControlUrl(String controlUrl) {
        this.controlUrl = controlUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public String getIndexName() {
        return indexName;
    }

    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }

    public String getIndexHost() {
        return indexHost;
    }

    public void setIndexHost(String indexHost) {
        this.indexHost = indexHost;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getMetric() {
        return metric;
    }

    public void setMetric(String metric) {
        this.metric = metric;
    }

    public String getCloud() {
        return cloud;
    }

    public void setCloud(String cloud) {
        this.cloud = cloud;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public Integer getDimension() {
        return dimension;
    }

    public void setDimension(Integer dimension) {
        this.dimension = dimension;
    }

    public Boolean getAutoCreateIndex() {
        return autoCreateIndex;
    }

    public void setAutoCreateIndex(Boolean autoCreateIndex) {
        this.autoCreateIndex = autoCreateIndex;
    }
}
