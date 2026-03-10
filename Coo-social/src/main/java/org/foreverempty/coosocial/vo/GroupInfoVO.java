package org.foreverempty.coosocial.vo;

import lombok.Data;

import java.util.List;

@Data
public class GroupInfoVO {
    private Long id;
    private String name;
    private String avatar;
    private String notice;
    private String remark;
    private Long ownerId;
    private Integer inviteAuditMode;
    private Long defaultTitleId;
    private Integer memberCount;
    private Integer myRole;
    private Long myTitleId;
    private String myTitleName;
    private String myNicknameInGroup;
    private List<String> myPermissions;
}
