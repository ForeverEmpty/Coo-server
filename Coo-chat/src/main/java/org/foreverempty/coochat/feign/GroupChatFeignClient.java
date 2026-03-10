package org.foreverempty.coochat.feign;

import org.foreverempty.common.Result;
import org.foreverempty.coochat.feign.vo.GroupChatMemberAccessVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient(name = "Coo-social")
public interface GroupChatFeignClient {

    @GetMapping("/group/internal/{groupId}/member/{userId}")
    Result<GroupChatMemberAccessVO> getMemberAccess(@PathVariable("groupId") Long groupId,
                                                    @PathVariable("userId") Long userId);

    @GetMapping("/group/internal/{groupId}/member-ids")
    Result<List<Long>> getMemberIds(@PathVariable("groupId") Long groupId);
}
