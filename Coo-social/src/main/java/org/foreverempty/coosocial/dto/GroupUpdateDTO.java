package org.foreverempty.coosocial.dto;

import lombok.Data;

@Data
public class GroupUpdateDTO {
    private String name;
    private String avatar;
    private String notice;
    private Integer inviteAuditMode;
}
