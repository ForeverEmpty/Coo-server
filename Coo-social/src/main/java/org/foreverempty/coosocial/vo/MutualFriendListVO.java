package org.foreverempty.coosocial.vo;

import lombok.Data;
import org.foreverempty.common.vo.UserSimpleVO;

import java.util.List;

@Data
public class MutualFriendListVO {
    private Long total;
    private List<UserSimpleVO> list;
}

