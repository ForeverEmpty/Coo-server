package org.foreverempty.coosocial.controller;

import org.foreverempty.common.PageResult;
import org.foreverempty.common.Result;
import org.foreverempty.common.vo.UserSimpleVO;
import org.foreverempty.common.vo.UserFullVO;
import org.foreverempty.coosocial.dto.FriendApplyDTO;
import org.foreverempty.coosocial.dto.FriendAuditDTO;
import org.foreverempty.coosocial.dto.FriendGroupAddDTO;
import org.foreverempty.coosocial.dto.FriendRelationUpdateDTO;
import org.foreverempty.coosocial.dto.FriendGroupUpdateDTO;
import org.foreverempty.coosocial.dto.FriendGroupSortDTO;
import org.foreverempty.coosocial.dto.ChatSessionConfigDTO;
import org.foreverempty.coosocial.service.FriendService;
import org.foreverempty.coosocial.vo.FriendApplyVO;
import org.foreverempty.coosocial.vo.FriendGroupVO;
import org.foreverempty.coosocial.vo.ChatSessionConfigVO;
import org.foreverempty.coosocial.vo.MutualFriendListVO;
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

    @GetMapping("/info/{id}")
    public Result<UserFullVO> getFriendInfo(@PathVariable Long id) {
        return friendService.getFriendInfo(id);
    }

    @GetMapping("/mutual/{targetId}")
    public Result<MutualFriendListVO> getMutualFriends(@PathVariable Long targetId,
            @RequestParam(defaultValue = "6") Integer limit) {
        return friendService.getMutualFriends(targetId, limit);
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

    @GetMapping("/apply/list")
    public Result<List<FriendApplyVO>> applyList() {
        return friendService.getApplyList();
    }

    @GetMapping("/apply/list/sent")
    public Result<List<FriendApplyVO>> sentApplyList() {
        return friendService.getSentApplyList();
    }

    @PostMapping("/audit")
    public Result<String> audit(@RequestBody FriendAuditDTO dto) {
        return friendService.auditApply(dto);
    }

    @PostMapping("/unignore/{applyId}")
    public Result<String> unignore(@PathVariable Long applyId) {
        return friendService.unignoreApply(applyId);
    }

    @PostMapping("/group")
    public Result<String> addGroup(@RequestBody FriendGroupAddDTO dto) {
        return friendService.addGroup(dto);
    }

    @PutMapping("/group")
    public Result<String> updateGroup(@RequestBody FriendGroupUpdateDTO dto) {
        return friendService.updateGroup(dto);
    }

    @DeleteMapping("/group/{groupId}")
    public Result<String> deleteGroup(@PathVariable Long groupId) {
        return friendService.deleteGroup(groupId);
    }

    @PutMapping("/group/sort")
    public Result<String> sortGroups(@RequestBody FriendGroupSortDTO dto) {
        return friendService.sortGroups(dto);
    }

    @DeleteMapping("/{friendId}")
    public Result<String> deleteFriend(@PathVariable Long friendId) {
        return friendService.deleteFriend(friendId);
    }

    @PutMapping("/relation")
    public Result<String> updateFriendRelation(@RequestBody FriendRelationUpdateDTO dto) {
        return friendService.updateFriendRelation(dto);
    }

    @GetMapping("/chat/session-config")
    public Result<ChatSessionConfigVO> getChatSessionConfig() {
        return friendService.getChatSessionConfig();
    }

    @PutMapping("/chat/session-config")
    public Result<String> saveChatSessionConfig(@RequestBody ChatSessionConfigDTO dto) {
        return friendService.saveChatSessionConfig(dto);
    }
}
