package org.foreverempty.coosocial.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("u_group")
public class Group {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String name;
    private Long ownerId;
    private String avatar;
    @TableField("cover_url")
    private String coverUrl;
    private String notice;
    private Integer inviteAuditMode;
    private Long defaultTitleId;
    private Integer fileCapacityMb;
    private Integer oversizeThresholdMb;
    private Integer tempExpireDays;
    private Long usedStorageBytes;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
