package org.foreverempty.coosocial.vo;

import lombok.Data;

import java.util.List;

@Data
public class GroupTitleVO {
    private Long id;
    private String name;
    private Boolean isDefault;
    private Integer sort;
    private Integer memberCount;
    private List<String> permissions;
}
