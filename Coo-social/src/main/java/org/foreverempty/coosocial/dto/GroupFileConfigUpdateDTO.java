package org.foreverempty.coosocial.dto;

import lombok.Data;

@Data
public class GroupFileConfigUpdateDTO {
    private Integer fileCapacityMb;
    private Integer oversizeThresholdMb;
    private Integer tempExpireDays;
}

