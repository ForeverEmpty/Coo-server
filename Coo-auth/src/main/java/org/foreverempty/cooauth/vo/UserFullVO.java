package org.foreverempty.cooauth.vo;

import lombok.Data;
import org.foreverempty.common.annotation.PrivacyField;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class UserFullVO {
    private String id;
    private String nickname;
    private String avatar;
    private LocalDateTime createTime;
    private Boolean isMe;

    @PrivacyField("publicGender")
    private Integer gender;
    private Boolean publicGender;

    @PrivacyField("publicBirthday")
    private LocalDate birthday;
    private Boolean publicBirthday;

    private String signature;

    @PrivacyField("publicRegion")
    private String region;
    private Boolean publicRegion;

    @PrivacyField("publicJob")
    private String job;
    private Boolean publicJob;

    private Boolean publicMutualFriend;
}
