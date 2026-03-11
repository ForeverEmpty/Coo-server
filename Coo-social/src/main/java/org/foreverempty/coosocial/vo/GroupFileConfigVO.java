package org.foreverempty.coosocial.vo;

import lombok.Data;

@Data
public class GroupFileConfigVO {
    private Integer fileCapacityMb;
    private Integer oversizeThresholdMb;
    private Integer tempExpireDays;
    private Long usedStorageBytes;
    private Long remainingStorageBytes;
}

