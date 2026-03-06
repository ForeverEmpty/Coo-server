package org.foreverempty.coochat.dto;

import lombok.Data;

@Data
public class ChatHistoryQueryDTO {
    private String peerId;
    private String cursor;
    private Integer limit;
}
