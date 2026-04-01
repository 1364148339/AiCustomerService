package com.aimacrodroid.service.impl;

import com.aimacrodroid.common.exception.BizException;
import com.aimacrodroid.domain.dto.ScenarioCreateReqDTO;
import com.aimacrodroid.domain.dto.ScenarioStepItemDTO;
import com.aimacrodroid.domain.dto.ScenarioStepsUpdateReqDTO;
import com.aimacrodroid.domain.entity.ScenarioDefinition;
import com.aimacrodroid.domain.entity.ScenarioStep;
import com.aimacrodroid.domain.vo.ScenarioDetailVO;
import com.aimacrodroid.mapper.ScenarioDefinitionMapper;
import com.aimacrodroid.mapper.ScenarioStepMapper;
import com.aimacrodroid.service.AuditLogService;
import com.aimacrodroid.service.ScenarioService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScenarioServiceImpl extends ServiceImpl<ScenarioDefinitionMapper, ScenarioDefinition> implements ScenarioService {

    private final ScenarioStepMapper scenarioStepMapper;
    private final AuditLogService auditLogService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScenarioDefinition createScenario(ScenarioCreateReqDTO req) {
        LambdaQueryWrapper<ScenarioDefinition> keyQuery = new LambdaQueryWrapper<>();
        keyQuery.eq(ScenarioDefinition::getScenarioKey, req.getScenarioKey());
        if (this.getOne(keyQuery) != null) {
            throw new BizException("INVALID_PARAM", "场景Key已存在");
        }
        ScenarioDefinition scenario = new ScenarioDefinition();
        scenario.setScenarioKey(req.getScenarioKey());
        scenario.setScenarioName(req.getScenarioName());
        scenario.setScenarioDesc(req.getDescription());
        scenario.setStatus("DRAFT");
        scenario.setVersionNo(1L);
        this.save(scenario);
        auditLogService.record("system", "SCENARIO_CREATE", "SCENARIO", String.valueOf(scenario.getId()), "SUCCESS", new HashMap<>());
        return scenario;
    }

    @Override
    public ScenarioDetailVO getDetail(String scenarioKey) {
        ScenarioDefinition scenario = getByKey(scenarioKey);
        LambdaQueryWrapper<ScenarioStep> stepQuery = new LambdaQueryWrapper<>();
        stepQuery.eq(ScenarioStep::getScenarioId, scenario.getId()).orderByAsc(ScenarioStep::getStepNo);
        List<ScenarioStep> steps = scenarioStepMapper.selectList(stepQuery);
        return ScenarioDetailVO.builder().scenario(scenario).steps(steps).build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveSteps(String scenarioKey, ScenarioStepsUpdateReqDTO req) {
        ScenarioDefinition scenario = getByKey(scenarioKey);
        List<ScenarioStepItemDTO> normalized = req.getSteps().stream()
                .sorted(Comparator.comparingInt(ScenarioStepItemDTO::getStepNo))
                .collect(Collectors.toList());
        validateStepSequence(normalized);
        long enabledCount = normalized.stream().filter(item -> item.getEnabled() == null || item.getEnabled()).count();
        if (enabledCount < 1) {
            throw new BizException("SCENARIO_STEPS_EMPTY", "至少需要一个启用步骤");
        }

        LambdaQueryWrapper<ScenarioStep> removeQuery = new LambdaQueryWrapper<>();
        removeQuery.eq(ScenarioStep::getScenarioId, scenario.getId());
        scenarioStepMapper.delete(removeQuery);

        for (ScenarioStepItemDTO item : normalized) {
            ScenarioStep step = new ScenarioStep();
            step.setScenarioId(scenario.getId());
            step.setStepNo(item.getStepNo());
            step.setStepName(item.getStepName());
            step.setActionCode(item.getActionCode());
            step.setActionParams(item.getActionParams());
            step.setTimeoutMs(defaultTimeout(item.getTimeoutMs()));
            step.setRetryMax(defaultRetry(item.getRetryMax()));
            step.setRetryBackoffMs(defaultBackoff(item.getRetryBackoffMs()));
            step.setIsEnabled((item.getEnabled() == null || item.getEnabled()) ? 1 : 0);
            scenarioStepMapper.insert(step);
        }
        auditLogService.record("system", "SCENARIO_SAVE_STEPS", "SCENARIO", String.valueOf(scenario.getId()), "SUCCESS", new HashMap<>());
    }

    @Override
    public List<ScenarioDefinition> listScenarios() {
        LambdaQueryWrapper<ScenarioDefinition> query = new LambdaQueryWrapper<>();
        query.orderByDesc(ScenarioDefinition::getGmtCreate);
        return this.list(query);
    }

    private ScenarioDefinition getByKey(String scenarioKey) {
        LambdaQueryWrapper<ScenarioDefinition> query = new LambdaQueryWrapper<>();
        query.eq(ScenarioDefinition::getScenarioKey, scenarioKey);
        ScenarioDefinition scenario = this.getOne(query);
        if (scenario == null) {
            throw new BizException("SCENARIO_NOT_FOUND", "场景不存在");
        }
        return scenario;
    }

    private void validateStepSequence(List<ScenarioStepItemDTO> steps) {
        for (int i = 0; i < steps.size(); i++) {
            Integer expected = i + 1;
            if (!expected.equals(steps.get(i).getStepNo())) {
                throw new BizException("INVALID_PARAM", "步骤序号必须连续且从1开始");
            }
        }
    }

    private Integer defaultTimeout(Integer timeoutMs) {
        if (timeoutMs == null || timeoutMs <= 0) {
            return 5000;
        }
        return timeoutMs;
    }

    private Integer defaultRetry(Integer retryMax) {
        if (retryMax == null || retryMax < 0) {
            return 0;
        }
        return retryMax;
    }

    private Integer defaultBackoff(Integer retryBackoffMs) {
        if (retryBackoffMs == null || retryBackoffMs < 0) {
            return 1000;
        }
        return retryBackoffMs;
    }
}
