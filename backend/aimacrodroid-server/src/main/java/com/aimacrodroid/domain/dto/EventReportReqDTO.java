package com.aimacrodroid.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Schema(description = "事件回传请求参数")
public class EventReportReqDTO {

    @NotNull(message = "任务ID不能为空")
    @Schema(description = "任务ID")
    private Long taskId;

    @Schema(description = "指令业务ID(仅原子轨道必填)")
    private String commandId;

    @NotBlank(message = "状态不能为空")
    @Schema(description = "事件状态(RUNNING/SUCCESS/FAIL)")
    private String status;

    @NotNull(message = "时间戳不能为空")
    @Schema(description = "设备端实际上报时间戳(毫秒)")
    private Long timestamp;

    @Schema(description = "本步骤耗时(毫秒)")
    private Long durationMs;

    @Schema(description = "截图URL")
    private String screenshotUrl;

    @Schema(description = "截图时刻前台包名")
    private String foregroundPkg;

    @Schema(description = "OCR/结构化视图数据")
    private List<Map<String, Object>> elements;

    @Schema(description = "本节点发生的错误码")
    private String errorCode;

    @Schema(description = "错误详细信息")
    private String errorMessage;

    @Schema(description = "轨迹数据摘要")
    private List<Map<String, Object>> trace;

    @Schema(description = "大模型意图思考过程摘要")
    private String thinking;

    @Schema(description = "是否触发敏感页面保护(0:否 1:是)")
    private Integer sensitiveScreenDetected = 0;

    @Schema(description = "本节点的进度数据快照")
    private Map<String, Object> progress;

    @NotBlank(message = "签名不能为空")
    @Schema(description = "防篡改签名(HMAC)")
    private String hmac;
}
