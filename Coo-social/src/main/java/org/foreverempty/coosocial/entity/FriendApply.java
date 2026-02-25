package org.foreverempty.coosocial.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("u_friend_apply")
public class FriendApply {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long fromId;
    private Long toId;
    private String msg;
    /**
     * 0-待处理, 1-已同意, 2-已拒绝
     */
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
