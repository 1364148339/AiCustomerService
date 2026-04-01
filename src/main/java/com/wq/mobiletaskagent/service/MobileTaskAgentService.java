package com.wq.mobiletaskagent.service;

import com.wq.mobiletaskagent.model.ChatMessage;
import com.wq.mobiletaskagent.model.ModelRequest;
import com.wq.mobiletaskagent.model.ModelResponse;
import com.wq.mobiletaskagent.router.ModelRouter;

import java.util.List;

public class MobileTaskAgentService {
    private final ModelRouter modelRouter;

    public MobileTaskAgentService(ModelRouter modelRouter) {
        this.modelRouter = modelRouter;
    }

    public ModelResponse planPhoneTask(String userTask) {
        List<ChatMessage> messages = List.of(
                new ChatMessage("system", "你是手机任务控制 agent，负责将用户意图转换为可执行步骤。"),
                new ChatMessage("user", userTask)
        );
        ModelRequest request = new ModelRequest(messages, 0.2, 800);
        return modelRouter.generate(request);
    }
}
