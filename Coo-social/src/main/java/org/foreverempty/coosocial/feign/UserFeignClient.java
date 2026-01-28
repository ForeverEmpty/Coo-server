package org.foreverempty.coosocial.feign;

import org.foreverempty.common.Result;
import org.foreverempty.common.vo.UserSimpleVO;
import org.foreverempty.common.vo.UserFullVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "Coo-auth")
public interface UserFeignClient {

    @PostMapping("/api/auth/user/batch")
    Result<List<UserSimpleVO>> getUserBatch(@RequestBody List<Long> ids);

    @GetMapping("/api/auth/user/search")
    Result<List<UserSimpleVO>> searchUser(@RequestParam String keyword);

    @GetMapping("/api/auth/info/{id}")
    Result<UserFullVO> getUserInfo(@PathVariable Long id);
}
