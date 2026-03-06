package org.foreverempty.coochat.vo;

import lombok.Data;
import org.foreverempty.common.model.ChatData;

@Data
public class ChatHistoryMessageVO {
    private String id;
    private String fromId;
    private String toId;
    private Integer chatType;
    private Integer contentType;
    private String content;
    private String url;
    private String fileName;
    private Long fileSize;
    private Long timestamp;
    private Integer status;
    private ChatData.ReplyModel replyTo;
}
