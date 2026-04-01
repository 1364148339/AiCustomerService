package com.wq.mobiletaskagent.client;

import com.wq.mobiletaskagent.config.ModelProviderProperties;
import com.wq.mobiletaskagent.model.ChatMessage;
import com.wq.mobiletaskagent.model.ModelRequest;
import com.wq.mobiletaskagent.model.ModelResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

class OpenAutoGlmClientTest {
    @Test
    void shouldGenerateResponseSuccessfully() {
        ModelProviderProperties properties = baseProperties();
        OpenAutoGlmClient client = new OpenAutoGlmClient(properties, (p, r) ->
                new ModelResponse("执行步骤已生成", p.getProvider().name(), p.getModel())
        );

        ModelResponse response = client.generate(sampleRequest());

        Assertions.assertEquals("执行步骤已生成", response.content());
        Assertions.assertEquals("OPEN_AUTO_GLM", response.provider());
    }

    @Test
    void shouldThrowAuthFailedWhenUnauthorized() {
        ModelProviderProperties properties = baseProperties();
        OpenAutoGlmClient client = new OpenAutoGlmClient(properties, (p, r) -> {
            throw new ModelClientException("AUTH_FAILED", "鉴权失败");
        });

        ModelClientException exception = Assertions.assertThrows(ModelClientException.class, () -> client.generate(sampleRequest()));

        Assertions.assertEquals("AUTH_FAILED", exception.getErrorCode());
    }

    @Test
    void shouldFailValidationWhenApiKeyMissing() {
        ModelProviderProperties properties = baseProperties();
        properties.setApiKey(" ");
        OpenAutoGlmClient client = new OpenAutoGlmClient(properties, (p, r) ->
                new ModelResponse("不会执行", p.getProvider().name(), p.getModel())
        );

        IllegalStateException exception = Assertions.assertThrows(IllegalStateException.class, () -> client.generate(sampleRequest()));

        Assertions.assertTrue(exception.getMessage().contains("apiKey"));
    }

    @Test
    void shouldRetryAndSucceedWhenFirstCallFailed() {
        ModelProviderProperties properties = baseProperties();
        properties.setMaxRetries(1);
        int[] invokeCount = {0};
        OpenAutoGlmClient client = new OpenAutoGlmClient(properties, (p, r) -> {
            if (invokeCount[0]++ == 0) {
                throw new ModelClientException("TIMEOUT", "超时");
            }
            return new ModelResponse("重试成功", p.getProvider().name(), p.getModel());
        });

        ModelResponse response = client.generate(sampleRequest());

        Assertions.assertEquals("重试成功", response.content());
        Assertions.assertEquals(2, invokeCount[0]);
    }

    private ModelProviderProperties baseProperties() {
        ModelProviderProperties properties = new ModelProviderProperties();
        properties.setApiKey("token");
        properties.setModel("glm-4.5");
        properties.setTimeout(Duration.ofSeconds(2));
        properties.setMaxRetries(0);
        return properties;
    }

    private ModelRequest sampleRequest() {
        return new ModelRequest(List.of(new ChatMessage("user", "打开微信并发送早会纪要")), 0.2, 300);
    }
}
