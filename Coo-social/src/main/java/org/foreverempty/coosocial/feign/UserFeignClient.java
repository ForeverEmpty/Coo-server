package org.foreverempty.coosocial.feign;

import org.foreverempty.common.PageResult;
import org.foreverempty.common.Result;
import org.foreverempty.common.vo.UserSimpleVO;
import org.foreverempty.common.vo.UserFullVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "Coo-auth")
public interface UserFeignClient {

    @PostMapping("/user/batch")
    Result<List<UserSimpleVO>> getUserBatch(@RequestBody List<Long> ids);

    @GetMapping("/user/search")
    Result<List<UserSimpleVO>> searchUser(@RequestParam String keyword);

    @GetMapping("/user/search/global")
    Result<PageResult<UserSimpleVO>> searchAllUsers(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize
    );

    @GetMapping("/info/{id}")
    Result<UserFullVO> getUserInfo(@PathVariable Long id);
}
