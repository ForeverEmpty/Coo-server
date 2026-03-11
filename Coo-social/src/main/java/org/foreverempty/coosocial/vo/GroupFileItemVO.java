package org.foreverempty.coosocial.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class GroupFileItemVO {
    private Long id;
    private Long groupId;
    private Long folderId;
    private String fileName;
    private String url;
    private Long fileSize;
    private String mimeType;
    private String source;
    private String sourceMessageId;
    private Boolean temp;
    private LocalDateTime expireAt;
    private Long createBy;
    private LocalDateTime createTime;
}

