package com.aimacrodroid.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_scenario")
public class ScenarioDefinition extends BaseEntity {

    private String scenarioKey;

    private String scenarioName;

    private String scenarioDesc;

    private String status;

    private Long versionNo;
}
