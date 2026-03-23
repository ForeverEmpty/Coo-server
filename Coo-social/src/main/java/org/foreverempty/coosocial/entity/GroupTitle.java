package org.foreverempty.coosocial.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("u_group_title")
public class GroupTitle {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long groupId;
    private String systemKey;
    private String name;
    private Integer isDefault;
    private Integer sort;
    private String permissions;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
