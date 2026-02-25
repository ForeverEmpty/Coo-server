package org.foreverempty.common.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.foreverempty.common.context.UserContext;
import org.slf4j.MDC;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Objects;
import java.util.UUID;

public class UserInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String traceId = request.getHeader("traceId");

        MDC.put("traceId",
                Objects.requireNonNullElseGet(
                        traceId,
                        () -> UUID.randomUUID().toString().replace("-", "")));

        String userId = request.getHeader("user-id");

        if (userId != null) {
            UserContext.setUserId(Long.parseLong(userId));
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
            @Nullable Exception ex) throws Exception {
        MDC.remove("traceId");
        UserContext.remove();
    }
}
