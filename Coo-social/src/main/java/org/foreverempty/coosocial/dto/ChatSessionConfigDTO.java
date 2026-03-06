package org.foreverempty.coosocial.dto;

import lombok.Data;

import java.util.List;

@Data
public class ChatSessionConfigDTO {
    private List<String> pinnedChatIds;
    private List<String> hiddenRecentChatIds;
}
