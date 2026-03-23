package org.foreverempty.coosocial.controller;

import org.foreverempty.common.Result;
import org.foreverempty.coosocial.dto.GroupApplyDTO;
import org.foreverempty.coosocial.dto.GroupCreateDTO;
import org.foreverempty.coosocial.dto.GroupInviteDTO;
import org.foreverempty.coosocial.dto.GroupJoinAuditDTO;
import org.foreverempty.coosocial.dto.GroupMemberNicknameDTO;
import org.foreverempty.coosocial.dto.GroupMemberTitleDTO;
import org.foreverempty.coosocial.dto.GroupRemarkDTO;
import org.foreverempty.coosocial.dto.GroupTitleCreateDTO;
import org.foreverempty.coosocial.dto.GroupTitleSortDTO;
import org.foreverempty.coosocial.dto.GroupTitleUpdateDTO;
import org.foreverempty.coosocial.dto.GroupTransferOwnerDTO;
import org.foreverempty.coosocial.dto.GroupUpdateDTO;
import org.foreverempty.coosocial.service.GroupService;
import org.foreverempty.coosocial.vo.GroupChatMemberAccessVO;
import org.foreverempty.coosocial.vo.GroupInfoVO;
import org.foreverempty.coosocial.vo.GroupJoinRequestVO;
import org.foreverempty.coosocial.vo.GroupListVO;
import org.foreverempty.coosocial.vo.GroupMemberVO;
import org.foreverempty.coosocial.vo.GroupSearchVO;
import org.foreverempty.coosocial.vo.GroupTitleVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/group")
public class GroupController {

    @Autowired
    private GroupService groupService;

    @GetMapping("/list/my")
    public Result<List<GroupListVO>> getMyGroups() {
        return groupService.getMyGroups();
    }

    @GetMapping("/search")
    public Result<List<GroupSearchVO>> searchGroups(@RequestParam String keyword) {
        return groupService.searchGroups(keyword);
    }

    @PostMapping
    public Result<GroupInfoVO> createGroup(@RequestBody GroupCreateDTO dto) {
        return groupService.createGroup(dto);
    }

    @GetMapping("/{groupId}")
    public Result<GroupInfoVO> getGroupInfo(@PathVariable Long groupId) {
        return groupService.getGroupInfo(groupId);
    }

    @PutMapping("/{groupId}")
    public Result<String> updateGroup(@PathVariable Long groupId, @RequestBody GroupUpdateDTO dto) {
        return groupService.updateGroup(groupId, dto);
    }

    @PutMapping("/{groupId}/remark")
    public Result<String> updateRemark(@PathVariable Long groupId, @RequestBody GroupRemarkDTO dto) {
        return groupService.updateRemark(groupId, dto);
    }

    @GetMapping("/{groupId}/members")
    public Result<List<GroupMemberVO>> getMembers(@PathVariable Long groupId) {
        return groupService.getMembers(groupId);
    }

    @PutMapping("/{groupId}/member/{userId}/title")
    public Result<String> updateMemberTitle(@PathVariable Long groupId,
                                            @PathVariable Long userId,
                                            @RequestBody GroupMemberTitleDTO dto) {
        return groupService.updateMemberTitle(groupId, userId, dto);
    }

    @DeleteMapping("/{groupId}/member/{userId}")
    public Result<String> removeMember(@PathVariable Long groupId, @PathVariable Long userId) {
        return groupService.removeMember(groupId, userId);
    }

    @PutMapping("/{groupId}/member/{userId}/nickname")
    public Result<GroupMemberVO> updateMemberNickname(@PathVariable Long groupId,
                                                      @PathVariable Long userId,
                                                      @RequestBody GroupMemberNicknameDTO dto) {
        return groupService.updateMemberNickname(groupId, userId, dto);
    }

    @PutMapping("/{groupId}/my-nickname")
    public Result<GroupMemberVO> updateMyNickname(@PathVariable Long groupId,
                                                  @RequestBody GroupMemberNicknameDTO dto) {
        return groupService.updateMemberNickname(groupId, null, dto);
    }

    @PostMapping("/{groupId}/leave")
    public Result<String> leaveGroup(@PathVariable Long groupId) {
        return groupService.leaveGroup(groupId);
    }

    @PostMapping("/{groupId}/transfer-owner")
    public Result<String> transferOwner(@PathVariable Long groupId,
                                        @RequestBody GroupTransferOwnerDTO dto) {
        return groupService.transferOwner(groupId, dto);
    }

    @DeleteMapping("/{groupId}")
    public Result<String> deleteGroup(@PathVariable Long groupId) {
        return groupService.deleteGroup(groupId);
    }

    @GetMapping("/{groupId}/titles")
    public Result<List<GroupTitleVO>> getTitles(@PathVariable Long groupId) {
        return groupService.getTitles(groupId);
    }

    @PostMapping("/{groupId}/titles")
    public Result<String> createTitle(@PathVariable Long groupId, @RequestBody GroupTitleCreateDTO dto) {
        return groupService.createTitle(groupId, dto);
    }

    @PutMapping("/{groupId}/titles/{titleId}")
    public Result<String> updateTitle(@PathVariable Long groupId,
                                      @PathVariable Long titleId,
                                      @RequestBody GroupTitleUpdateDTO dto) {
        return groupService.updateTitle(groupId, titleId, dto);
    }

    @PutMapping("/{groupId}/titles/{titleId}/default")
    public Result<String> setDefaultTitle(@PathVariable Long groupId, @PathVariable Long titleId) {
        return groupService.setDefaultTitle(groupId, titleId);
    }

    @PutMapping("/{groupId}/titles/sort")
    public Result<String> sortTitles(@PathVariable Long groupId, @RequestBody GroupTitleSortDTO dto) {
        return groupService.sortTitles(groupId, dto);
    }

    @DeleteMapping("/{groupId}/titles/{titleId}")
    public Result<String> deleteTitle(@PathVariable Long groupId, @PathVariable Long titleId) {
        return groupService.deleteTitle(groupId, titleId);
    }

    @PostMapping("/{groupId}/invite")
    public Result<String> inviteMembers(@PathVariable Long groupId, @RequestBody GroupInviteDTO dto) {
        return groupService.inviteMembers(groupId, dto);
    }

    @PostMapping("/{groupId}/apply")
    public Result<String> applyToGroup(@PathVariable Long groupId, @RequestBody GroupApplyDTO dto) {
        return groupService.applyToGroup(groupId, dto);
    }

    @GetMapping("/{groupId}/join-requests")
    public Result<List<GroupJoinRequestVO>> getJoinRequests(@PathVariable Long groupId) {
        return groupService.getJoinRequests(groupId);
    }

    @PostMapping("/{groupId}/join-requests/{requestId}/audit")
    public Result<String> auditJoinRequest(@PathVariable Long groupId,
                                           @PathVariable Long requestId,
                                           @RequestBody GroupJoinAuditDTO dto) {
        return groupService.auditJoinRequest(groupId, requestId, dto);
    }

    @GetMapping("/internal/{groupId}/member/{userId}")
    public Result<GroupChatMemberAccessVO> getMemberAccess(@PathVariable Long groupId,
                                                           @PathVariable Long userId) {
        return groupService.getMemberAccess(groupId, userId);
    }

    @GetMapping("/internal/{groupId}/member-ids")
    public Result<List<Long>> getMemberIds(@PathVariable Long groupId) {
        return groupService.getMemberIds(groupId);
    }
}
