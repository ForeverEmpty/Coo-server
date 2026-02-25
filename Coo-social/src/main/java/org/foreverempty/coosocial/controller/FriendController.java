package org.foreverempty.coosocial.controller;

import org.foreverempty.common.PageResult;
import org.foreverempty.common.Result;
import org.foreverempty.common.vo.UserSimpleVO;
import org.foreverempty.coosocial.dto.FriendApplyDTO;
import org.foreverempty.coosocial.dto.FriendAuditDTO;
import org.foreverempty.coosocial.service.FriendService;
import org.foreverempty.coosocial.vo.FriendApplyVO;
import org.foreverempty.coosocial.vo.FriendGroupVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/friend")
public class FriendController {

    @Autowired
    private FriendService friendService;

    @GetMapping("/list")
    public Result<List<FriendGroupVO>> getFriendList() {
        return friendService.getFriendList();
    }

    @GetMapping("/search")
    public Result<List<UserSimpleVO>> searchFriend(@RequestParam String keyword) {
        return friendService.searchFriend(keyword);
    }

    @GetMapping("/search/global")
    public Result<PageResult<UserSimpleVO>> searchAllFriend(@RequestParam String keyword,
                                                            @RequestParam int pageNum,
                                                            @RequestParam int pageSize) {
        return friendService.searchAllFriend(keyword, pageNum, pageSize);
    }

    @PostMapping("/apply")
    public Result<String> sendApply(@RequestBody FriendApplyDTO dto) {
        return friendService.sendApply(dto);
    }

    @PostMapping("/apply/list")
    public Result<List<FriendApplyVO>> applyList() {
        return friendService.getApplyList();
    }

    @PostMapping("/audit")
    public Result<String> audit(@RequestBody FriendAuditDTO dto) {
        return friendService.auditApply(dto);
    }
}
