package org.foreverempty.coochat.netty;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class SessionManager {
    private final Map<Long, Set<Channel>> ONLINE_USERS = new ConcurrentHashMap<>();

    public void addSession(Long userId, Channel channel) {
        ONLINE_USERS.computeIfAbsent(
                userId,
                k -> ConcurrentHashMap.newKeySet()
        ).add(channel);
    }

    public void removeSession(Long userId, Channel channel) {
        ONLINE_USERS.computeIfPresent(
                userId,
                (k, v) -> {
                    v.remove(channel);
                    return v;
                }
        );
    }

    public int getOnlineUserCount() {
        return ONLINE_USERS.size();
    }

    public void sendMessageToUser(Long userId, String messageText) {
        Set<Channel> channels = ONLINE_USERS.get(userId);
        if (channels == null || channels.isEmpty()) {
            log.info("用户 {} 不在线，跳过推送", userId);
            return;
        }

        // 构造消息帧
        TextWebSocketFrame frame = new TextWebSocketFrame(messageText);

        channels.forEach(ch -> {
            if (ch.isActive()) {
                // 注意：向多个 Channel 发送同一个 Frame 时，Netty 要求手动增加引用计数
                // 否则第一个发送完成后，Frame 可能被回收，导致后续发送失败
                ch.writeAndFlush(frame.retain());
            }
        });
    }
}
