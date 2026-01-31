package org.foreverempty.cooauth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

@Data
@TableName("u_user_info")
public class UserInfo {
    @TableId(type = IdType.INPUT)
    private Long userId;

    private Integer gender;
    private Boolean publicGender;

    private LocalDate birthday;
    private Boolean publicBirthday;

    private String signature;

    private String region;
    private Boolean publicRegion;

    private String job;
    private Boolean publicJob;

    private String background;

    private Boolean publicMutualFriend;
}
