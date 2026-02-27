package org.foreverempty.coosocial.dto;

import lombok.Data;

@Data
public class FriendApplyDTO {
    private Long targetId;
    private String msg;
    private String remark;
    private String source;
    private Long groupId;
}
