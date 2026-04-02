package com.aimacrodroid.service;

import com.aimacrodroid.domain.dto.ScenarioCreateReqDTO;
import com.aimacrodroid.domain.dto.ScenarioStepsUpdateReqDTO;
import com.aimacrodroid.domain.entity.ScenarioDefinition;
import com.aimacrodroid.domain.vo.ScenarioDetailVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

public interface ScenarioService extends IService<ScenarioDefinition> {

    ScenarioDefinition createScenario(ScenarioCreateReqDTO req);

    ScenarioDetailVO getDetail(String scenarioKey);

    void saveSteps(String scenarioKey, ScenarioStepsUpdateReqDTO req);

    ScenarioDefinition publishScenario(String scenarioKey);

    IPage<ScenarioDefinition> listScenarios(long pageNo, long pageSize);
}
