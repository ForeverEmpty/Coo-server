package org.foreverempty.coosocial.vo;

import lombok.Data;

@Data
public class GroupSearchVO {
    private Long id;
    private String name;
    private String avatar;
    private String notice;
    private Integer memberCount;
    private Boolean joined;
    private Boolean pending;
}
