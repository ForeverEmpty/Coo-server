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
    private final Map<Long, Set<Channel>> onlineUsers = new ConcurrentHashMap<>();

    public void addSession(Long userId, Channel channel) {
        onlineUsers.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(channel);
    }

    public void removeSession(Long userId, Channel channel) {
        onlineUsers.computeIfPresent(
                userId,
                (k, v) -> {
                    v.remove(channel);
                    return v;
                }
        );
    }

    public int getOnlineUserCount() {
        return onlineUsers.size();
    }

    public void sendMessageToUser(Long userId, String messageText) {
        Set<Channel> channels = onlineUsers.get(userId);
        if (channels == null || channels.isEmpty()) {
            log.info("User {} is offline, skip push message", userId);
            return;
        }

        TextWebSocketFrame frame = new TextWebSocketFrame(messageText);

        channels.forEach(ch -> {
            if (ch.isActive()) {
                // Use retain() because the same frame is reused for multiple channels.
                ch.writeAndFlush(frame.retain());
            }
        });
    }
}
