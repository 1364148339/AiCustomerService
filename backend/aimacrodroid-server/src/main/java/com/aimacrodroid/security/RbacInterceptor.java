package com.aimacrodroid.security;

import com.aimacrodroid.common.exception.BizException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Component
public class RbacInterceptor implements HandlerInterceptor {
    private static final String HEADER_OPERATOR_ID = "X-Operator-Id";
    private static final String HEADER_OPERATOR_ROLE = "X-Operator-Role";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RequireRoles requireRoles = AnnotationUtils.findAnnotation(handlerMethod.getMethod(), RequireRoles.class);
        if (requireRoles == null) {
            requireRoles = AnnotationUtils.findAnnotation(handlerMethod.getBeanType(), RequireRoles.class);
        }
        if (requireRoles == null) {
            return true;
        }
        String operatorId = request.getHeader(HEADER_OPERATOR_ID);
        OperatorRole role = OperatorRole.from(request.getHeader(HEADER_OPERATOR_ROLE));
        if (operatorId == null || operatorId.isBlank() || role == null) {
            throw new BizException("FORBIDDEN", "缺少有效的RBAC身份信息", 403);
        }
        Set<OperatorRole> allowSet = new HashSet<>(Arrays.asList(requireRoles.value()));
        if (!allowSet.contains(role)) {
            throw new BizException("FORBIDDEN", "当前角色无权访问该资源", 403);
        }
        OperatorContext.set(operatorId, role);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        OperatorContext.clear();
    }
}
