package com.wq.mobiletaskagent.router;

import com.wq.mobiletaskagent.client.FallbackModelClient;
import com.wq.mobiletaskagent.client.ModelClient;
import com.wq.mobiletaskagent.client.ModelClientException;
import com.wq.mobiletaskagent.config.ModelProviderProperties;
import com.wq.mobiletaskagent.model.ChatMessage;
import com.wq.mobiletaskagent.model.ModelRequest;
import com.wq.mobiletaskagent.model.ModelResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class ModelRouterTest {
    @Test
    void shouldFallbackWhenPrimaryFailed() {
        ModelProviderProperties properties = new ModelProviderProperties();
        properties.setProvider(ModelProviderProperties.Provider.OPEN_AUTO_GLM);
        properties.setFallbackProvider(ModelProviderProperties.Provider.FALLBACK);
        ModelClient failedPrimary = new ModelClient() {
            @Override
            public String providerName() {
                return "OPEN_AUTO_GLM";
            }

            @Override
            public ModelResponse generate(ModelRequest request) {
                throw new ModelClientException("TIMEOUT", "timeout");
            }
        };
        ModelRouter router = new ModelRouter(properties, Map.of(
                ModelProviderProperties.Provider.OPEN_AUTO_GLM, failedPrimary,
                ModelProviderProperties.Provider.FALLBACK, new FallbackModelClient()
        ));

        ModelResponse response = router.generate(new ModelRequest(List.of(new ChatMessage("user", "打开飞书并提醒开会")), 0.3, 200));

        Assertions.assertEquals("FALLBACK", response.provider());
    }

    @Test
    void shouldThrowWhenNoFallbackClient() {
        ModelProviderProperties properties = new ModelProviderProperties();
        properties.setProvider(ModelProviderProperties.Provider.OPEN_AUTO_GLM);
        properties.setFallbackProvider(ModelProviderProperties.Provider.FALLBACK);
        ModelClient failedPrimary = new ModelClient() {
            @Override
            public String providerName() {
                return "OPEN_AUTO_GLM";
            }

            @Override
            public ModelResponse generate(ModelRequest request) {
                throw new ModelClientException("UPSTREAM_ERROR", "error");
            }
        };
        ModelRouter router = new ModelRouter(properties, Map.of(
                ModelProviderProperties.Provider.OPEN_AUTO_GLM, failedPrimary
        ));

        Assertions.assertThrows(ModelClientException.class, () ->
                router.generate(new ModelRequest(List.of(new ChatMessage("user", "测试")), 0.2, 100))
        );
    }
}
