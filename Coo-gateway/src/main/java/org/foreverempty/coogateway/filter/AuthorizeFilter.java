package org.foreverempty.coogateway.filter;

import org.foreverempty.common.utils.JwtUtils;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@Component
public class AuthorizeFilter implements GlobalFilter, Ordered {

    private static final List<String> WHITE_LIST = List.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/chat/**"
    );

    private final AntPathMatcher antPathMatcher = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = UUID.randomUUID().toString().replace("-", "");

        ServerHttpRequest request = exchange.getRequest()
                .mutate()
                .header("traceId", traceId)
                .build();

        org.slf4j.MDC.put("traceId", traceId);

        String path = request.getURI().getPath();

        for (String whitePath : WHITE_LIST) {
            if (antPathMatcher.match(whitePath, path)) {
                return chain.filter(exchange.mutate().request(request).build());
            }
        }

        String authHeader = request.getHeaders().getFirst("Authorization");

        String token = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        } else {
            token = authHeader;
        }

        if (token == null) {
            token = request.getQueryParams().getFirst("token");
        }

        Long userId = JwtUtils.getUserId(token);
        if (userId == null) {
            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return response.setComplete();
        }

        ServerHttpRequest modifyRequest = request.mutate()
                .header("user-id", userId.toString())
                .build();
        return chain.filter(exchange.mutate().request(modifyRequest).build());
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
