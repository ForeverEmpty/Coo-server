package org.foreverempty.common.model;

import lombok.Data;

@Data
public class ChatData {
    private String fromId;
    private String toId;
    private Integer chatType;    // 1-私聊, 2-群聊
    private Integer contentType; // 对应 ContentType 编号
    private String content;      // 文本内容
    private String url;          // 文件/图片地址
    private String fileName;
    private Long fileSize;
    private Long timestamp;

    // --- 引用功能 ---
    private ReplyModel replyTo;  // 如果是回复消息，则不为空

    @Data
    public static class ReplyModel {
        private String messageId;   // 被引用的消息ID
        private String senderName;  // 被引用者的昵称
        private String content;     // 被引用的简短内容摘要
    }
}
