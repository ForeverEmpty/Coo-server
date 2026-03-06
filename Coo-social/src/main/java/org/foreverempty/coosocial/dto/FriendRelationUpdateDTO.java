package org.foreverempty.coosocial.dto;

import lombok.Data;

@Data
public class FriendRelationUpdateDTO {
    private Long friendId;
    private String remark;
    private Long groupId;
}
