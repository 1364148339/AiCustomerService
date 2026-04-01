package com.wq.mobiletaskagent.router;

import com.wq.mobiletaskagent.client.ModelClient;
import com.wq.mobiletaskagent.client.ModelClientException;
import com.wq.mobiletaskagent.config.ModelProviderProperties;
import com.wq.mobiletaskagent.model.ModelRequest;
import com.wq.mobiletaskagent.model.ModelResponse;

import java.util.EnumMap;
import java.util.Map;

public class ModelRouter {
    private final ModelProviderProperties properties;
    private final Map<ModelProviderProperties.Provider, ModelClient> clients;

    public ModelRouter(ModelProviderProperties properties, Map<ModelProviderProperties.Provider, ModelClient> clients) {
        this.properties = properties;
        this.clients = new EnumMap<>(clients);
    }

    public ModelResponse generate(ModelRequest request) {
        ModelClient primary = clients.get(properties.getProvider());
        if (primary == null) {
            throw new IllegalStateException("未注册主供应方客户端: " + properties.getProvider());
        }
        try {
            return primary.generate(request);
        } catch (ModelClientException ex) {
            ModelClient fallback = clients.get(properties.getFallbackProvider());
            if (fallback == null || properties.getFallbackProvider() == properties.getProvider()) {
                throw ex;
            }
            return fallback.generate(request);
        }
    }
}
