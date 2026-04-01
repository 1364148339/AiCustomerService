package com.wq.mobiletaskagent.client;

import com.wq.mobiletaskagent.config.ModelProviderProperties;
import com.wq.mobiletaskagent.model.ModelRequest;
import com.wq.mobiletaskagent.model.ModelResponse;
import com.zhipu.oapi.ClientV4;
import com.zhipu.oapi.Constants;
import com.zhipu.oapi.service.v4.model.ChatCompletionRequest;
import com.zhipu.oapi.service.v4.model.ModelApiResponse;

import java.util.List;
import java.util.Objects;

public class OpenAutoGlmClient implements ModelClient {
    private final ModelProviderProperties properties;
    private final GlmInvoker invoker;

    public interface GlmInvoker {
        ModelResponse invoke(ModelProviderProperties properties, ModelRequest request);
    }

    public OpenAutoGlmClient(ModelProviderProperties properties) {
        this(properties, new SdkGlmInvoker());
    }

    public OpenAutoGlmClient(ModelProviderProperties properties, GlmInvoker invoker) {
        this.properties = properties;
        this.invoker = invoker;
    }

    @Override
    public String providerName() {
        return ModelProviderProperties.Provider.OPEN_AUTO_GLM.name();
    }

    @Override
    public ModelResponse generate(ModelRequest request) {
        properties.validate();
        int retries = Math.max(0, properties.getMaxRetries());
        RuntimeException lastError = null;
        for (int attempt = 0; attempt <= retries; attempt++) {
            try {
                return invoker.invoke(properties, request);
            } catch (RuntimeException ex) {
                lastError = ex;
                if (attempt == retries) {
                    throw ex;
                }
            }
        }
        throw new ModelClientException("MODEL_CALL_FAILED", "Open-AutoGLM 调用失败", lastError);
    }

    static class SdkGlmInvoker implements GlmInvoker {
        @Override
        public ModelResponse invoke(ModelProviderProperties properties, ModelRequest request) {
            try {
                ClientV4 client = buildClient(properties.getApiKey());
                List<com.zhipu.oapi.service.v4.model.ChatMessage> messages = request.messages()
                        .stream()
                        .map(it -> new com.zhipu.oapi.service.v4.model.ChatMessage(it.role(), it.content()))
                        .toList();
                ChatCompletionRequest sdkRequest = ChatCompletionRequest.builder()
                        .model(properties.getModel())
                        .stream(Boolean.FALSE)
                        .invokeMethod(Constants.invokeMethod)
                        .messages(messages)
                        .build();
                ModelApiResponse sdkResponse = client.invokeModelApi(sdkRequest);
                String content = extractContent(sdkResponse);
                return new ModelResponse(content, ModelProviderProperties.Provider.OPEN_AUTO_GLM.name(), properties.getModel());
            } catch (ModelClientException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new ModelClientException("UPSTREAM_ERROR", "Open-AutoGLM SDK 调用失败", ex);
            }
        }

        private ClientV4 buildClient(String apiKey) {
            String normalizedKey = Objects.requireNonNull(apiKey, "apiKey 不能为空").trim();
            String[] keyPair = normalizedKey.split("\\.", 2);
            if (keyPair.length == 2 && !keyPair[0].isBlank() && !keyPair[1].isBlank()) {
                return new ClientV4.Builder(keyPair[0], keyPair[1]).build();
            }
            return new ClientV4.Builder(normalizedKey).build();
        }

        private String extractContent(ModelApiResponse sdkResponse) {
            if (sdkResponse == null || sdkResponse.getData() == null || sdkResponse.getData().getChoices() == null
                    || sdkResponse.getData().getChoices().isEmpty()
                    || sdkResponse.getData().getChoices().get(0).getMessage() == null) {
                throw new ModelClientException("INVALID_RESPONSE", "Open-AutoGLM SDK 返回结构异常");
            }
            String content = String.valueOf(sdkResponse.getData().getChoices().get(0).getMessage().getContent());
            if (content.isBlank() || "null".equalsIgnoreCase(content)) {
                throw new ModelClientException("INVALID_RESPONSE", "Open-AutoGLM SDK 返回内容为空");
            }
            return content;
        }
    }
}
