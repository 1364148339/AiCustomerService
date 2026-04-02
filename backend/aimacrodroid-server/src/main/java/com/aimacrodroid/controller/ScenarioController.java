package com.aimacrodroid.controller;

import com.aimacrodroid.common.api.Result;
import com.aimacrodroid.domain.dto.ScenarioCreateReqDTO;
import com.aimacrodroid.domain.dto.ScenarioStepsUpdateReqDTO;
import com.aimacrodroid.domain.entity.ScenarioDefinition;
import com.aimacrodroid.domain.vo.ScenarioDetailVO;
import com.aimacrodroid.service.ScenarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "场景管理", description = "场景与步骤管理接口")
@RestController
@RequestMapping("/api/scenarios")
@RequiredArgsConstructor
public class ScenarioController {

    private final ScenarioService scenarioService;

    @Operation(summary = "查询场景列表")
    @GetMapping
    public Result<List<ScenarioDefinition>> list() {
        return Result.success(scenarioService.listScenarios());
    }

    @Operation(summary = "创建场景")
    @PostMapping
    public Result<ScenarioDefinition> create(@Validated @RequestBody ScenarioCreateReqDTO req) {
        return Result.success(scenarioService.createScenario(req));
    }

    @Operation(summary = "查询场景详情")
    @GetMapping("/{scenarioKey}")
    public Result<ScenarioDetailVO> detail(@PathVariable("scenarioKey") String scenarioKey) {
        return Result.success(scenarioService.getDetail(scenarioKey));
    }

    @Operation(summary = "保存场景步骤")
    @PutMapping("/{scenarioKey}/steps")
    public Result<Void> saveSteps(@PathVariable("scenarioKey") String scenarioKey,
                                  @Validated @RequestBody ScenarioStepsUpdateReqDTO req) {
        scenarioService.saveSteps(scenarioKey, req);
        return Result.success();
    }
}
