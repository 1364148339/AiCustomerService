package com.aimacrodroid.domain.vo;

import com.aimacrodroid.domain.entity.ScenarioDefinition;
import com.aimacrodroid.domain.entity.ScenarioStep;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@Schema(description = "场景详情")
public class ScenarioDetailVO {

    @Schema(description = "场景信息")
    private ScenarioDefinition scenario;

    @Schema(description = "步骤列表")
    private List<ScenarioStep> steps;
}
