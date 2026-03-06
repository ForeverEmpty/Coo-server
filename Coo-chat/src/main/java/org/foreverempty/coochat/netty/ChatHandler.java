package org.foreverempty.coochat.netty;

import com.fasterxml.jackson.databind.ObjectMapper;
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
                log.info("User {} authenticated, websocket connected. Online users: {}",
                        userId, sessionManager.getOnlineUserCount());
            } else {
                log.warn("Illegal websocket connection or expired token, close connection. URI: {}", uri);
                ctx.close();
            }
        }

        super.userEventTriggered(ctx, evt);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, TextWebSocketFrame textWebSocketFrame)
            throws Exception {
        String text = textWebSocketFrame.text();

        try {
            MessageModel<ChatData> model = objectMapper.readValue(
                    text,
                    objectMapper.getTypeFactory().constructParametricType(MessageModel.class, ChatData.class)
            );

            if ("CHAT".equals(model.getType())) {
                Long currentUserId = channelHandlerContext.channel().attr(USER_ID_KEY).get();
                if (currentUserId == null) {
                    log.warn("unauthorized websocket message, channel={}", channelHandlerContext.channel().id().asShortText());
                    channelHandlerContext.close();
                    return;
                }
                messageService.processChatMessage(model, currentUserId);
            } else if ("PING".equals(model.getType())) {
                channelHandlerContext.writeAndFlush(
                        new TextWebSocketFrame("{\"type\":\"PONG\"}")
                );
            }
        } catch (Exception e) {
            log.error("Message parsing failed: {}", text, e);
        }
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        log.info("Connection added: {}", ctx.channel().id().asShortText());
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        Long userId = ctx.channel().attr(USER_ID_KEY).get();
        if (userId != null) {
            sessionManager.removeSession(userId, ctx.channel());
            log.info("User {} offline, online users left: {}", userId, sessionManager.getOnlineUserCount());
        }
        log.info("Connection removed: {}", ctx.channel().id().asShortText());
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
            log.error("Failed to parse token from websocket query params: {}", e.getMessage());
        }
        return null;
    }
}
