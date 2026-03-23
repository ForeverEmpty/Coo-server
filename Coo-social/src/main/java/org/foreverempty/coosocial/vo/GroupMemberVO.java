package org.foreverempty.coosocial.vo;

import lombok.Data;

import java.util.List;

@Data
public class GroupMemberVO {
    private Long userId;
    private String username;
    private String nickname;
    private String avatar;
    private String displayName;
    private String nicknameInGroup;
    private Long titleId;
    private String titleName;
    private List<String> permissions;
}
