package org.foreverempty.coochat.entity;

import lombok.Data;
import org.foreverempty.common.model.ChatData;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.IndexDirection;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collation = "chat_message")
public class ChatMessage {
    @Id
    private String id;

    @Indexed
    private String fromId;

    @Indexed
    private String toId;

    private Integer chatType;
    private Integer contentType;
    private String content;
    private String url;
    private String fileName;
    private Long fileSize;

    @Indexed(direction = IndexDirection.DESCENDING)
    private Long timestamp;

    /**
     * 状态：0-正常，1-已撤回
     */
    private Integer status = 0;

    /**
     * 引用信息
     */
    private ChatData.ReplyModel replyTo;
}
