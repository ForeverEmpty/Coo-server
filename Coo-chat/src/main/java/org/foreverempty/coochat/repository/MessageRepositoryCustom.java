package org.foreverempty.coochat.repository;

import org.foreverempty.coochat.entity.ChatMessage;
import org.foreverempty.coochat.repository.model.RecentPrivateChatSession;

import java.util.List;

public interface MessageRepositoryCustom {
    List<ChatMessage> queryPrivateHistory(String currentUserId, String peerId, Long cursor, int limit);

    List<RecentPrivateChatSession> queryRecentPrivateSessions(String currentUserId, int limit);
}
