package org.foreverempty.coosocial.dto;

import lombok.Data;

@Data
public class FriendAuditDTO {
    private Long applyId;
    private Integer status;
    private String remark;
    private Long groupId;
}
