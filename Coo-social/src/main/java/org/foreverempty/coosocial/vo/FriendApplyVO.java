package org.foreverempty.coosocial.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FriendApplyVO {
    private Long id;          // 申请记录ID
    private Long fromId;      // 申请人ID
    private String nickname;  // 申请人昵称
    private String avatar;    // 申请人头像
    private String msg;       // 验证消息
    private Integer status;   // 0-待处理 1-已同意 2-已拒绝
    private LocalDateTime createTime;
}
