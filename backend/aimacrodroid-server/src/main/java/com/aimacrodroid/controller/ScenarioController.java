package com.aimacrodroid.controller;

import com.aimacrodroid.common.api.Result;
import com.aimacrodroid.domain.dto.ScenarioCreateReqDTO;
import com.aimacrodroid.domain.dto.ScenarioStepsUpdateReqDTO;
import com.aimacrodroid.domain.entity.ScenarioDefinition;
import com.aimacrodroid.domain.vo.ScenarioDetailVO;
import com.aimacrodroid.security.OperatorRole;
import com.aimacrodroid.security.RequireRoles;
import com.aimacrodroid.service.ScenarioService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "场景管理", description = "场景与步骤管理接口")
@RestController
@RequestMapping("/api/scenarios")
@RequiredArgsConstructor
public class ScenarioController {

    private final ScenarioService scenarioService;

    @Operation(summary = "查询场景列表")
    @GetMapping
    @RequireRoles({OperatorRole.ADMIN, OperatorRole.OPS, OperatorRole.READONLY})
    public Result<IPage<ScenarioDefinition>> list(@RequestParam(value = "pageNo", defaultValue = "1") Long pageNo,
                                                  @RequestParam(value = "pageSize", defaultValue = "20") Long pageSize) {
        long safePageNo = pageNo == null || pageNo < 1 ? 1 : pageNo;
        long safePageSize = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 200);
        return Result.success(scenarioService.listScenarios(safePageNo, safePageSize));
    }

    @Operation(summary = "创建场景")
    @PostMapping
    @RequireRoles({OperatorRole.ADMIN, OperatorRole.OPS})
    public Result<ScenarioDefinition> create(@Validated @RequestBody ScenarioCreateReqDTO req) {
        return Result.success(scenarioService.createScenario(req));
    }

    @Operation(summary = "查询场景详情")
    @GetMapping("/{scenarioKey}")
    @RequireRoles({OperatorRole.ADMIN, OperatorRole.OPS, OperatorRole.READONLY})
    public Result<ScenarioDetailVO> detail(@PathVariable("scenarioKey") String scenarioKey) {
        return Result.success(scenarioService.getDetail(scenarioKey));
    }

    @Operation(summary = "保存场景步骤")
    @PutMapping("/{scenarioKey}/steps")
    @RequireRoles({OperatorRole.ADMIN, OperatorRole.OPS})
    public Result<Void> saveSteps(@PathVariable("scenarioKey") String scenarioKey,
                                  @Validated @RequestBody ScenarioStepsUpdateReqDTO req) {
        scenarioService.saveSteps(scenarioKey, req);
        return Result.success();
    }

    @Operation(summary = "发布场景")
    @PostMapping("/{scenarioKey}/publish")
    @RequireRoles({OperatorRole.ADMIN, OperatorRole.OPS})
    public Result<ScenarioDefinition> publish(@PathVariable("scenarioKey") String scenarioKey) {
        return Result.success(scenarioService.publishScenario(scenarioKey));
    }
}
