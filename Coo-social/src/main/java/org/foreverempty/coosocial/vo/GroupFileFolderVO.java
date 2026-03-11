package org.foreverempty.coosocial.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class GroupFileFolderVO {
    private Long id;
    private Long groupId;
    private Long parentId;
    private String name;
    private Long createBy;
    private LocalDateTime createTime;
}

