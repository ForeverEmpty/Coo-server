package org.foreverempty.cooauth.controller;

import org.foreverempty.common.Result;
import org.foreverempty.cooauth.entity.User;
import org.foreverempty.cooauth.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    @Autowired
    private AuthService authService;

    @PostMapping("/register")
    public Result<String> register(@RequestBody Map<String, String> params) {
        String username = params.get("username");
        String password = params.get("password");

        if (username == null || password == null) {
            return Result.error("Username or Password is null");
        }

        return authService.register(username, password);
    }

    @PostMapping("/login")
    public Result<String> login(@RequestBody Map<String, String> params) {
        return authService.login(params.get("username"), params.get("password"));
    }

    @GetMapping("/me")
    public Result<User> me() {
        return authService.getMyInfo();
    }
}
