package com.aimacrodroid.worker;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "task.step")
public class WorkerStepProperties {
    private Integer defaultTimeoutMs = 5000;
    private Integer maxRetryDefault = 1;
}
