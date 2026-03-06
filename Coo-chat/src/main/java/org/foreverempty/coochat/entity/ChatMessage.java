package org.foreverempty.coochat.entity;

import lombok.Data;
import org.foreverempty.common.model.ChatData;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.IndexDirection;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "chat_message")
@CompoundIndexes({
        @CompoundIndex(name = "idx_from_to_ts", def = "{'fromId':1,'toId':1,'timestamp':-1}"),
        @CompoundIndex(name = "idx_to_from_ts", def = "{'toId':1,'fromId':1,'timestamp':-1}")
})
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
