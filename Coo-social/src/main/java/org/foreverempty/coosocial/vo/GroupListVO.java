package org.foreverempty.coosocial.vo;

import lombok.Data;

@Data
public class GroupListVO {
    private Long id;
    private String name;
    private String avatar;
    private String notice;
    private String remark;
    private Integer memberCount;
    private Integer myRole;
    private Long myTitleId;
    private String myTitleName;
    private String myNicknameInGroup;
}
