package org.foreverempty.coochat.netty;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;
import org.foreverempty.common.model.ChatData;
import org.foreverempty.common.model.MessageModel;
import org.foreverempty.common.utils.JwtUtils;
import org.foreverempty.coochat.service.MessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@ChannelHandler.Sharable
public class ChatHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    @Autowired
    private SessionManager sessionManager;

    @Autowired
    private ObjectMapper objectMapper;

    public static final AttributeKey<Long> USER_ID_KEY = AttributeKey.valueOf("userId");
    @Autowired
    private MessageService messageService;

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            WebSocketServerProtocolHandler.HandshakeComplete handshake =
                    (WebSocketServerProtocolHandler.HandshakeComplete) evt;

            String uri = handshake.requestUri();
            String token = extractToken(uri);

            Long userId = JwtUtils.getUserId(token);

            if (userId != null) {
                ctx.channel().attr(USER_ID_KEY).set(userId);
                sessionManager.addSession(userId, ctx.channel());
                log.info("用户 {} 验证通过，建立长连接. 当前在线人数: {}", userId, sessionManager.getOnlineUserCount());
            } else {
                log.warn("非法连接或 Token 过期，强制关闭. URI: {}", uri);
                ctx.close();
            }
        }

        super.userEventTriggered(ctx, evt);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, TextWebSocketFrame textWebSocketFrame) throws Exception {
        String text = textWebSocketFrame.text();

        try {
            MessageModel<ChatData> model = objectMapper.readValue(
                    text,
                    objectMapper.getTypeFactory()
                            .constructParametricType(MessageModel.class, ChatData.class)
            );

            if ("CHAT".equals(model.getType())) {
                messageService.processChatMessage(model);
            } else if ("PING".equals(model.getType())) {
                channelHandlerContext.writeAndFlush(
                        new TextWebSocketFrame(
                                "{" +
                                    "\"type\":\"PONG\"" +
                                "}"
                        )
                );
            }
        } catch (Exception e) {
            log.error("Message Parsing Failed: {}", text, e);
        }
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        log.info("连接添加: {}", ctx.channel().id().asShortText());
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        Long userId = ctx.channel().attr(USER_ID_KEY).get();
        if (userId != null) {
            sessionManager.removeSession(userId, ctx.channel());
            log.info("用户 {} 下线，剩余在线人数: {}", userId, sessionManager.getOnlineUserCount());
        }
        log.info("连接移除: {}", ctx.channel().id().asShortText());
    }

    private String extractToken(String uri) {
        try {
            QueryStringDecoder decoder = new QueryStringDecoder(uri);
            Map<String, List<String>> parameters = decoder.parameters();
            List<String> tokens = parameters.get("token");
            if (tokens != null && !tokens.isEmpty()) {
                return tokens.getFirst();
            }
        } catch (Exception e) {
            log.error("解析 Token 参数失败: {}", e.getMessage());
        }
        return null;
    }
}
