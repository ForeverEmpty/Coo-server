package org.foreverempty.coosocial.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class GroupFileUploadVO {
    private Long fileId;
    private String url;
    private String fileName;
    private Long fileSize;
    private Boolean temp;
    private LocalDateTime expireAt;
}

