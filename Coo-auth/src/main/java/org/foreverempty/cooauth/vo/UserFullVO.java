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

    @PrivacyField("publicBirthday")
    private LocalDate birthday;

    @PrivacyField("publicSignature")
    private String signature;

    @PrivacyField("publicRegion")
    private String region;

    @PrivacyField("publicJob")
    private String job;
}
