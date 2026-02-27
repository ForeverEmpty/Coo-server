package org.foreverempty.coosocial.vo;

import lombok.Data;

@Data
public class FriendVO {
    private Long id;        // 好友的用户ID
    private String nickname;  // 原始昵称
    private String remark;    // 我的备注
    private String avatar;    // 头像
    private String showName;  // 最终显示名 (计算属性)
    private Long groupId;
    private Integer status;
}
