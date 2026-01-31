package org.foreverempty.common.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserSimpleVO {
    private Long id;
    private String username;
    private String nickname;
    private String avatar;
}
