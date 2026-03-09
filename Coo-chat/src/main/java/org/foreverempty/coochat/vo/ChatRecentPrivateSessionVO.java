package org.foreverempty.coochat.vo;

import lombok.Data;

@Data
public class ChatRecentPrivateSessionVO {
    private String peerId;
    private ChatHistoryMessageVO lastMessage;
}
