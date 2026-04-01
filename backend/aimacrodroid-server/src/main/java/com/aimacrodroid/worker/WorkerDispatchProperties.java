package com.aimacrodroid.worker;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "task.dispatch")
public class WorkerDispatchProperties {
    private Integer deviceConcurrency = 1;
    private Integer batchSize = 100;
    private Integer pollIntervalMs = 1000;
}
