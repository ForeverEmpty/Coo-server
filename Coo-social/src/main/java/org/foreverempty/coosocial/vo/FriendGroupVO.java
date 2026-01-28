package org.foreverempty.coosocial.vo;

import lombok.Data;

import java.util.List;

@Data
public class FriendGroupVO {
    private Long groupId;
    private String groupName;
    private List<FriendVO> children;
}
