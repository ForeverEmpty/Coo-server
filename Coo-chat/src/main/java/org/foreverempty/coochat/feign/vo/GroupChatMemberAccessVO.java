package org.foreverempty.coochat.feign.vo;

import lombok.Data;

@Data
public class GroupChatMemberAccessVO {
    private Long groupId;
    private Long userId;
    private Boolean member;
    private Integer role;
}
