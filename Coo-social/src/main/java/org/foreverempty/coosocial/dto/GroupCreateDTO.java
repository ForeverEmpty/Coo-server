package org.foreverempty.coosocial.dto;

import lombok.Data;

import java.util.List;

@Data
public class GroupCreateDTO {
    private String name;
    private String avatar;
    private String coverUrl;
    private String notice;
    private Integer inviteAuditMode;
    private List<Long> initialMemberIds;
}
