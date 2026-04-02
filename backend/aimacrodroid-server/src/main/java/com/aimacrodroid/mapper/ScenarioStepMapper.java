package com.aimacrodroid.mapper;

import com.aimacrodroid.domain.entity.ScenarioStep;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ScenarioStepMapper extends BaseMapper<ScenarioStep> {
    @Delete("DELETE FROM t_scenario_step WHERE scenario_id = #{scenarioId}")
    int purgeByScenarioId(@Param("scenarioId") Long scenarioId);
}
