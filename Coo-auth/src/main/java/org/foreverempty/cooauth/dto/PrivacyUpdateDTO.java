package org.foreverempty.cooauth.dto;

import lombok.Data;

@Data
public class PrivacyUpdateDTO {
    private Boolean publicGender;
    private Boolean publicBirthday;
    private Boolean publicRegion;
    private Boolean publicJob;
    private Boolean publicMutualFriend;
}
