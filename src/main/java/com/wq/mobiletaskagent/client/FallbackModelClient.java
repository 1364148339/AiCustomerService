package com.wq.mobiletaskagent.client;

import com.wq.mobiletaskagent.config.ModelProviderProperties;
import com.wq.mobiletaskagent.model.ModelRequest;
import com.wq.mobiletaskagent.model.ModelResponse;

public class FallbackModelClient implements ModelClient {
    @Override
    public String providerName() {
        return ModelProviderProperties.Provider.FALLBACK.name();
    }

    @Override
    public ModelResponse generate(ModelRequest request) {
        String content = "任务已接收，当前使用降级策略，请稍后重试自动化执行。";
        return new ModelResponse(content, providerName(), "fallback-local");
    }
}
