package com.wq.mobiletaskagent.model;

import java.util.List;

public record ModelRequest(List<ChatMessage> messages, Double temperature, Integer maxTokens) {
}
