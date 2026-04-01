package com.wq.mobiletaskagent.client;

import com.wq.mobiletaskagent.model.ModelRequest;
import com.wq.mobiletaskagent.model.ModelResponse;

public interface ModelClient {
    String providerName();

    ModelResponse generate(ModelRequest request);
}
