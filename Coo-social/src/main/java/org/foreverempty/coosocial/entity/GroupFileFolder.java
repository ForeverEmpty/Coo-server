package org.foreverempty.coosocial.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("u_group_file_folder")
public class GroupFileFolder {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long groupId;
    private Long parentId;
    private String name;
    private Long createBy;
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

