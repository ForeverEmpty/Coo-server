package org.foreverempty.cooauth.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class UpdateProfileDTO {
    private String nickname;
    private String gender;
    private LocalDate birthday;
    private String signature;
    private String region;
    private String job;
}
