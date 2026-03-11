package org.foreverempty.coosocial.vo;

import lombok.Data;

import java.util.List;

@Data
public class ChatSessionConfigVO {
    private List<String> pinnedChatIds;
    private List<String> hiddenRecentChatIds;
    private List<String> mutedChatIds;
}
