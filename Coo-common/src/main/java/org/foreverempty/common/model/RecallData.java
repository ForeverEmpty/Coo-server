package org.foreverempty.common.model;

import lombok.Data;

@Data
public class RecallData {
    private String messageId;
    private String fromId;
    private String toId;
    private Integer chatType;
    private String operatorId;
    private Long timestamp;
}
