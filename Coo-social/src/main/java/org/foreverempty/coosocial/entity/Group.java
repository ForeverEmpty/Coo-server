package org.foreverempty.coosocial.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("u_group")
public class Group {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String name;
    private Long ownerId;
    private String avatar;
    private String notice;
    private LocalDateTime createTime;
}
