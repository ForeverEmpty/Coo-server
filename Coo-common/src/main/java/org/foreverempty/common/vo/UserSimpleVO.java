package org.foreverempty.common.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserSimpleVO {
    private String id;
    private String username;
    private String nickname;
    private String avatar;
}
