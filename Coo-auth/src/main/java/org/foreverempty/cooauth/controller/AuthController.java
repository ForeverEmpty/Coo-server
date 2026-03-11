package org.foreverempty.cooauth.controller;

import org.foreverempty.common.PageResult;
import org.foreverempty.common.Result;
import org.foreverempty.common.vo.UserSimpleVO;
import org.foreverempty.cooauth.dto.PrivacyUpdateDTO;
import org.foreverempty.cooauth.dto.UpdateProfileDTO;
import org.foreverempty.cooauth.service.AuthService;
import org.foreverempty.common.vo.UserFullVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("")
public class AuthController {
    @Autowired
    private AuthService authService;

    @PostMapping("/register")
    public Result<String> register(@RequestBody Map<String, String> params) {
        String username = params.get("username");
        String password = params.get("password");
        String nickname = params.get("nickname");

        if (username == null || password == null) {
            return Result.error("Username or Password is null");
        }

        return authService.register(username, password, nickname);
    }

    @PostMapping("/login")
    public Result<String> login(@RequestBody Map<String, String> params) {
        return authService.login(params.get("username"), params.get("password"));
    }

    @GetMapping("/me")
    public Result<UserFullVO> me() {
        return authService.getMyInfo();
    }

    @GetMapping("/info/{id}")
    public Result<UserFullVO> getUserInfo(@PathVariable Long id) {
        return authService.getUserInfo(id);
    }

    @PostMapping("/avatar/update")
    public Result<String> updateAvatar(@RequestBody Map<String, String> params) {
        return authService.updateAvatar(params.get("avatar"));
    }

    @PostMapping("/background/update")
    public Result<String> updateBackground(@RequestBody Map<String, String> params) {
        return authService.updateBackground(params.get("background"));
    }

    @PostMapping("/file/upload")
    public Result<String> uploadProfileFile(@RequestParam("file") MultipartFile file) {
        return authService.uploadProfileFile(file);
    }

    @PostMapping("/privacy/update")
    public Result<String> updatePrivacy(@RequestBody PrivacyUpdateDTO dto) {
        return authService.updatePrivacy(dto);
    }

    @PostMapping("/profile/update")
    public Result<String> updateProfile(@RequestBody UpdateProfileDTO dto) {
        return authService.updateProfile(dto);
    }

    @PostMapping("/user/batch")
    public Result<List<UserSimpleVO>> getUserBatch(@RequestBody List<Long> ids) {
        return authService.getUserBatch(ids);
    }

    @GetMapping("/user/search")
    public Result<List<UserSimpleVO>> searchUser(@RequestParam String keyword) {
        return authService.searchUser(keyword);
    }

    @GetMapping("/user/search/global")
    public Result<PageResult<UserSimpleVO>> searchAllUsers(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return authService.searchAllUsers(keyword, pageNum, pageSize);
    }
}
