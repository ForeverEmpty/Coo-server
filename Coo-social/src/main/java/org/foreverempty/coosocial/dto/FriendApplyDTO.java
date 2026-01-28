package org.foreverempty.coosocial.dto;

import lombok.Data;

@Data
public class FriendApplyDTO {
    private String targetId;
    private String msg;
    private String remark;
}
