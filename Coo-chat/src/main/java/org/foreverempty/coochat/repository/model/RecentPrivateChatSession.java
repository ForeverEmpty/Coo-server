package org.foreverempty.coochat.repository.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.foreverempty.coochat.entity.ChatMessage;

@Data
@AllArgsConstructor
public class RecentPrivateChatSession {
    private String peerId;
    private ChatMessage lastMessage;
}
