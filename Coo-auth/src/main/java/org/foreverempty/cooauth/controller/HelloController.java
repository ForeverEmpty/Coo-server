package org.foreverempty.cooauth.controller;

import org.foreverempty.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HelloController {
    @GetMapping("/hello")
    public Result<String> hello(@RequestHeader("user-id") Long userId) {
        return Result.success("Hello World" + userId);
    }
}
