package org.foreverempty.coosocial.vo;

import lombok.Data;

import java.util.List;

@Data
public class GroupChatMemberAccessVO {
    private Long groupId;
    private Long userId;
    private Boolean member;
    private List<String> permissions;
    private Boolean recallAnytime;
}
