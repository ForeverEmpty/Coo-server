package org.foreverempty.coosocial.dto;

import lombok.Data;

import java.util.List;

@Data
public class GroupTitleUpdateDTO {
    private String name;
    private Integer sort;
    private List<String> permissions;
}
