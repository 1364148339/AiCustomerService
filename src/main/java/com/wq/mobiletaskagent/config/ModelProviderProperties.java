package com.wq.mobiletaskagent.config;

import java.time.Duration;

public class ModelProviderProperties {
    public enum Provider {
        OPEN_AUTO_GLM,
        FALLBACK
    }

    private Provider provider = Provider.OPEN_AUTO_GLM;
    private String baseUrl = "https://open.bigmodel.cn/api/paas/v4";
    private String apiKey;
    private String model = "glm-4.5";
    private Duration timeout = Duration.ofSeconds(20);
    private int maxRetries = 1;
    private Provider fallbackProvider = Provider.FALLBACK;

    public void validate() {
        if (provider == Provider.OPEN_AUTO_GLM) {
            if (apiKey == null || apiKey.isBlank()) {
                throw new IllegalStateException("OPEN_AUTO_GLM 启用时必须配置 apiKey");
            }
            if (model == null || model.isBlank()) {
                throw new IllegalStateException("OPEN_AUTO_GLM 启用时必须配置 model");
            }
            if (baseUrl == null || baseUrl.isBlank()) {
                throw new IllegalStateException("OPEN_AUTO_GLM 启用时必须配置 baseUrl");
            }
        }
    }

    public Provider getProvider() {
        return provider;
    }

    public void setProvider(Provider provider) {
        this.provider = provider;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public Provider getFallbackProvider() {
        return fallbackProvider;
    }

    public void setFallbackProvider(Provider fallbackProvider) {
        this.fallbackProvider = fallbackProvider;
    }
}
