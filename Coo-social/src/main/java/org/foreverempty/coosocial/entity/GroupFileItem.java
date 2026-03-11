package org.foreverempty.coosocial.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("u_group_file_item")
public class GroupFileItem {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long groupId;
    private Long folderId;
    private String fileName;
    private String url;
    private String objectKey;
    private Long fileSize;
    private String mimeType;
    private String source;
    private String sourceMessageId;
    private Integer isTemp;
    private LocalDateTime expireAt;
    private Long chargedBytes;
    private Integer deleted;
    private LocalDateTime deletedAt;
    private Long createBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

