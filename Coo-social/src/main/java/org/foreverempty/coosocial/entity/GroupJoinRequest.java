package org.foreverempty.coosocial.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("u_group_join_request")
public class GroupJoinRequest {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long groupId;
    private String type;
    private Long fromUserId;
    private Long targetUserId;
    private String status;
    private Long auditBy;
    private String reason;
    private LocalDateTime expireTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
