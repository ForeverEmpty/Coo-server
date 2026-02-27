package org.foreverempty.coosocial.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FriendApplyVO {
    private Long id;
    private Long fromId;
    private Long toId;
    private String source; // SEARCH/QR/GROUP
    private String nickname;
    private String avatar;
    private String msg;
    private Integer status; // 0-pending 1-approved 2-rejected 3-ignored
    private LocalDateTime createTime;
}
