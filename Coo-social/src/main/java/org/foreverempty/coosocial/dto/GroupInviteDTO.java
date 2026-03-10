package org.foreverempty.coosocial.dto;

import lombok.Data;

import java.util.List;

@Data
public class GroupInviteDTO {
    private List<Long> targetUserIds;
    private String reason;
}
