package org.foreverempty.coogateway.filter;

import com.alibaba.nacos.shaded.io.grpc.Server;
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

@Component
public class AuthorizeFilter implements GlobalFilter, Ordered {

    private static final List<String> WHITE_LIST = List.of(
            "/auth/api/auth/login",
            "/auth/api/auth/register"
    );

    private final AntPathMatcher antPathMatcher = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        for (String whitePath : WHITE_LIST) {
            if (antPathMatcher.match(path, whitePath)) {
                return chain.filter(exchange);
            }
        }

        String token = request.getHeaders().getFirst("Authorization");

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
