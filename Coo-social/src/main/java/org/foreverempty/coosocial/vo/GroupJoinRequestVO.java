package org.foreverempty.coosocial.vo;

import lombok.Data;
import org.foreverempty.common.vo.UserSimpleVO;

import java.time.LocalDateTime;

@Data
public class GroupJoinRequestVO {
    private Long id;
    private Long groupId;
    private String type;
    private String status;
    private String reason;
    private Long auditBy;
    private LocalDateTime createTime;
    private UserSimpleVO fromUser;
    private UserSimpleVO targetUser;
}
