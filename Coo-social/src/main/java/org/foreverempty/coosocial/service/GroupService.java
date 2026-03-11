
package org.foreverempty.coosocial.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.foreverempty.common.Result;
import org.foreverempty.common.context.UserContext;
import org.foreverempty.common.vo.UserSimpleVO;
import org.foreverempty.coosocial.content.GroupInviteAuditMode;
import org.foreverempty.coosocial.content.GroupJoinRequestStatus;
import org.foreverempty.coosocial.content.GroupJoinRequestType;
import org.foreverempty.coosocial.content.GroupMemberRole;
import org.foreverempty.coosocial.content.GroupPermission;
import org.foreverempty.coosocial.dto.*;
import org.foreverempty.coosocial.entity.Group;
import org.foreverempty.coosocial.entity.GroupJoinRequest;
import org.foreverempty.coosocial.entity.GroupMember;
import org.foreverempty.coosocial.entity.GroupTitle;
import org.foreverempty.coosocial.feign.UserFeignClient;
import org.foreverempty.coosocial.mapper.GroupJoinRequestMapper;
import org.foreverempty.coosocial.mapper.GroupMapper;
import org.foreverempty.coosocial.mapper.GroupMemberMapper;
import org.foreverempty.coosocial.mapper.GroupTitleMapper;
import org.foreverempty.coosocial.vo.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class GroupService {
    private static final int DEFAULT_GROUP_FILE_CAPACITY_MB = 1024;
    private static final int DEFAULT_GROUP_FILE_OVERSIZE_THRESHOLD_MB = 100;
    private static final int DEFAULT_GROUP_FILE_TEMP_EXPIRE_DAYS = 7;

    private static final List<String> OWNER_PERMISSIONS = Arrays.stream(GroupPermission.values())
            .map(Enum::name)
            .toList();
    private static final List<String> SUPER_ADMIN_PERMISSIONS = Arrays.stream(GroupPermission.values())
            .filter(item -> item != GroupPermission.GROUP_TRANSFER_OWNER)
            .map(Enum::name)
            .toList();

    @Autowired
    private GroupMapper groupMapper;
    @Autowired
    private GroupMemberMapper groupMemberMapper;
    @Autowired
    private GroupTitleMapper groupTitleMapper;
    @Autowired
    private GroupJoinRequestMapper groupJoinRequestMapper;
    @Autowired
    private UserFeignClient userFeignClient;
    @Autowired
    private ObjectMapper objectMapper;

    public Result<List<GroupListVO>> getMyGroups() {
        Long currentUserId = UserContext.getUserId();
        List<GroupMember> memberships = groupMemberMapper.selectList(new LambdaQueryWrapper<GroupMember>()
                .eq(GroupMember::getUserId, currentUserId)
                .orderByDesc(GroupMember::getUpdateTime)
                .orderByDesc(GroupMember::getCreateTime));
        if (memberships == null || memberships.isEmpty()) {
            return Result.success(Collections.emptyList());
        }

        Map<Long, GroupMember> memberMap = memberships.stream()
                .filter(item -> item.getGroupId() != null)
                .collect(Collectors.toMap(GroupMember::getGroupId, Function.identity(), (left, right) -> left));
        List<Long> groupIds = new ArrayList<>(memberMap.keySet());
        List<Group> groups = groupMapper.selectBatchIds(groupIds);
        if (groups == null || groups.isEmpty()) {
            return Result.success(Collections.emptyList());
        }

        final Map<Long, Long> memberCountMap = loadGroupMemberCountMap(groupIds);
        Map<Long, GroupTitle> titleMap = loadTitleMap(groupIds);

        List<GroupListVO> result = groups.stream()
                .sorted(Comparator.comparing(Group::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(group -> {
                    GroupMember member = memberMap.get(group.getId());
                    GroupTitle title = member != null && member.getTitleId() != null
                            ? titleMap.get(member.getTitleId())
                            : null;
                    GroupListVO vo = new GroupListVO();
                    vo.setId(group.getId());
                    vo.setName(group.getName());
                    vo.setOwnerId(group.getOwnerId());
                    vo.setAvatar(group.getAvatar());
                    vo.setCoverUrl(group.getCoverUrl());
                    vo.setNotice(group.getNotice());
                    vo.setRemark(member != null ? member.getRemark() : null);
                    vo.setMemberCount(Math.toIntExact(memberCountMap.getOrDefault(group.getId(), 0L)));
                    vo.setMyRole(member != null ? member.getRole() : null);
                    vo.setMyTitleId(member != null ? member.getTitleId() : null);
                    vo.setMyTitleName(title != null ? title.getName() : null);
                    vo.setMyNicknameInGroup(member != null ? member.getNicknameInGroup() : null);
                    return vo;
                })
                .toList();
        return Result.success(result);
    }

    public Result<List<GroupSearchVO>> searchGroups(String keyword) {
        Long currentUserId = UserContext.getUserId();
        String normalized = keyword == null ? "" : keyword.trim();
        if (!StringUtils.hasText(normalized)) {
            return Result.success(Collections.emptyList());
        }

        LambdaQueryWrapper<Group> wrapper = new LambdaQueryWrapper<Group>()
                .like(Group::getName, normalized)
                .last("LIMIT 20");
        if (normalized.chars().allMatch(Character::isDigit)) {
            wrapper.or().eq(Group::getId, Long.parseLong(normalized));
        }

        List<Group> groups = groupMapper.selectList(wrapper);
        if (groups.isEmpty()) {
            return Result.success(Collections.emptyList());
        }

        List<Long> groupIds = groups.stream().map(Group::getId).toList();
        List<GroupMember> joinedMembers = groupMemberMapper.selectList(new LambdaQueryWrapper<GroupMember>()
                .eq(GroupMember::getUserId, currentUserId)
                .in(GroupMember::getGroupId, groupIds));
        Set<Long> joinedIds = joinedMembers.stream().map(GroupMember::getGroupId).collect(Collectors.toSet());

        List<GroupJoinRequest> pendingRequests = groupJoinRequestMapper
                .selectList(new LambdaQueryWrapper<GroupJoinRequest>()
                        .eq(GroupJoinRequest::getStatus, GroupJoinRequestStatus.PENDING.name())
                        .in(GroupJoinRequest::getGroupId, groupIds)
                        .and(inner -> inner.eq(GroupJoinRequest::getFromUserId, currentUserId)
                                .or()
                                .eq(GroupJoinRequest::getTargetUserId, currentUserId)));
        Set<Long> pendingIds = pendingRequests.stream().map(GroupJoinRequest::getGroupId).collect(Collectors.toSet());

        List<GroupMember> allMembers = groupMemberMapper.selectList(new LambdaQueryWrapper<GroupMember>()
                .in(GroupMember::getGroupId, groupIds));
        Map<Long, Long> memberCountMap = allMembers.stream()
                .collect(Collectors.groupingBy(GroupMember::getGroupId, Collectors.counting()));

        List<GroupSearchVO> result = groups.stream().map(group -> {
            GroupSearchVO vo = new GroupSearchVO();
            vo.setId(group.getId());
            vo.setName(group.getName());
            vo.setAvatar(group.getAvatar());
            vo.setCoverUrl(group.getCoverUrl());
            vo.setNotice(group.getNotice());
            vo.setMemberCount(Math.toIntExact(memberCountMap.getOrDefault(group.getId(), 0L)));
            vo.setJoined(joinedIds.contains(group.getId()));
            vo.setPending(pendingIds.contains(group.getId()));
            return vo;
        }).toList();
        return Result.success(result);
    }

    @Transactional
    public Result<GroupInfoVO> createGroup(GroupCreateDTO dto) {
        Long currentUserId = UserContext.getUserId();
        if (dto == null || !StringUtils.hasText(dto.getName())) {
            return Result.error("Group name cannot be empty");
        }

        Group group = new Group();
        group.setName(dto.getName().trim());
        group.setOwnerId(currentUserId);
        group.setAvatar(trimToNull(dto.getAvatar()));
        group.setCoverUrl(trimToNull(dto.getCoverUrl()));
        group.setNotice(trimToNull(dto.getNotice()));
        group.setInviteAuditMode(normalizeInviteAuditMode(dto.getInviteAuditMode()));
        group.setFileCapacityMb(DEFAULT_GROUP_FILE_CAPACITY_MB);
        group.setOversizeThresholdMb(DEFAULT_GROUP_FILE_OVERSIZE_THRESHOLD_MB);
        group.setTempExpireDays(DEFAULT_GROUP_FILE_TEMP_EXPIRE_DAYS);
        group.setUsedStorageBytes(0L);
        group.setCreateTime(LocalDateTime.now());
        group.setUpdateTime(LocalDateTime.now());
        groupMapper.insert(group);

        GroupTitle defaultTitle = new GroupTitle();
        defaultTitle.setGroupId(group.getId());
        defaultTitle.setName("成员");
        defaultTitle.setIsDefault(1);
        defaultTitle.setSort(0);
        defaultTitle.setPermissions(writePermissions(List.of(
                GroupPermission.GROUP_VIEW.name(),
                GroupPermission.GROUP_FILE_VIEW.name(),
                GroupPermission.GROUP_FILE_UPLOAD.name()
        )));
        defaultTitle.setCreateTime(LocalDateTime.now());
        defaultTitle.setUpdateTime(LocalDateTime.now());
        groupTitleMapper.insert(defaultTitle);

        group.setDefaultTitleId(defaultTitle.getId());
        group.setUpdateTime(LocalDateTime.now());
        groupMapper.updateById(group);

        addMember(group, currentUserId, GroupMemberRole.OWNER, null);

        Set<Long> initialIds = new LinkedHashSet<>();
        if (dto.getInitialMemberIds() != null) {
            initialIds.addAll(dto.getInitialMemberIds().stream().filter(Objects::nonNull).toList());
        }
        initialIds.remove(currentUserId);
        for (Long memberId : initialIds) {
            addMember(group, memberId, GroupMemberRole.MEMBER, null);
        }

        return getGroupInfo(group.getId());
    }

    public Result<GroupInfoVO> getGroupInfo(Long groupId) {
        Long currentUserId = UserContext.getUserId();
        Group group = groupMapper.selectById(groupId);
        if (group == null) {
            return Result.error("Group not found");
        }

        GroupMember currentMember = getRequiredMember(groupId, currentUserId);
        if (currentMember == null) {
            return Result.error("No permission");
        }

        GroupTitle title = currentMember.getTitleId() != null ? groupTitleMapper.selectById(currentMember.getTitleId())
                : null;
        int memberCount = Math.toIntExact(groupMemberMapper.selectCount(new LambdaQueryWrapper<GroupMember>()
                .eq(GroupMember::getGroupId, groupId)));

        GroupInfoVO vo = new GroupInfoVO();
        vo.setId(group.getId());
        vo.setName(group.getName());
        vo.setAvatar(group.getAvatar());
        vo.setCoverUrl(group.getCoverUrl());
        vo.setNotice(group.getNotice());
        vo.setRemark(currentMember.getRemark());
        vo.setOwnerId(group.getOwnerId());
        vo.setInviteAuditMode(group.getInviteAuditMode());
        vo.setDefaultTitleId(group.getDefaultTitleId());
        vo.setMemberCount(memberCount);
        vo.setMyRole(currentMember.getRole());
        vo.setMyTitleId(currentMember.getTitleId());
        vo.setMyTitleName(title != null ? title.getName() : null);
        vo.setMyNicknameInGroup(currentMember.getNicknameInGroup());
        vo.setMyPermissions(resolvePermissions(currentMember, title));
        vo.setFileCapacityMb(group.getFileCapacityMb());
        vo.setOversizeThresholdMb(group.getOversizeThresholdMb());
        vo.setTempExpireDays(group.getTempExpireDays());
        vo.setUsedStorageBytes(group.getUsedStorageBytes());
        return Result.success(vo);
    }

    @Transactional
    public Result<String> updateGroup(Long groupId, GroupUpdateDTO dto) {
        Long currentUserId = UserContext.getUserId();
        Group group = groupMapper.selectById(groupId);
        if (group == null) {
            return Result.error("Group not found");
        }
        GroupMember currentMember = getRequiredMember(groupId, currentUserId);
        if (currentMember == null) {
            return Result.error("No permission");
        }
        if (dto == null) {
            return Result.error("Invalid payload");
        }

        if (dto.getName() != null || dto.getAvatar() != null || dto.getCoverUrl() != null || dto.getInviteAuditMode() != null) {
            if (!hasPermission(currentMember, GroupPermission.GROUP_EDIT_INFO)) {
                return Result.error("No permission to edit group info");
            }
        }
        if (dto.getNotice() != null && !hasPermission(currentMember, GroupPermission.GROUP_EDIT_NOTICE)) {
            return Result.error("No permission to edit group notice");
        }

        if (dto.getName() != null) {
            String name = dto.getName().trim();
            if (!StringUtils.hasText(name)) {
                return Result.error("Group name cannot be empty");
            }
            group.setName(name);
        }
        if (dto.getAvatar() != null) {
            group.setAvatar(trimToNull(dto.getAvatar()));
        }
        if (dto.getCoverUrl() != null) {
            group.setCoverUrl(trimToNull(dto.getCoverUrl()));
        }
        if (dto.getNotice() != null) {
            group.setNotice(trimToNull(dto.getNotice()));
        }
        if (dto.getInviteAuditMode() != null) {
            group.setInviteAuditMode(normalizeInviteAuditMode(dto.getInviteAuditMode()));
        }
        group.setUpdateTime(LocalDateTime.now());
        groupMapper.updateById(group);
        return Result.success("Group updated");
    }

    @Transactional
    public Result<String> updateRemark(Long groupId, GroupRemarkDTO dto) {
        Long currentUserId = UserContext.getUserId();
        GroupMember currentMember = getRequiredMember(groupId, currentUserId);
        if (currentMember == null) {
            return Result.error("No permission");
        }

        String remark = dto == null ? null : trimToNull(dto.getRemark());
        if (remark != null && remark.length() > 50) {
            return Result.error("Remark cannot exceed 50 characters");
        }

        currentMember.setRemark(remark);
        currentMember.setUpdateTime(LocalDateTime.now());
        groupMemberMapper.updateById(currentMember);
        return Result.success("Group remark updated");
    }

    public Result<List<GroupMemberVO>> getMembers(Long groupId) {
        Long currentUserId = UserContext.getUserId();
        GroupMember currentMember = getRequiredMember(groupId, currentUserId);
        if (currentMember == null) {
            return Result.error("No permission");
        }

        List<GroupMember> members = groupMemberMapper.selectList(new LambdaQueryWrapper<GroupMember>()
                .eq(GroupMember::getGroupId, groupId)
                .orderByAsc(GroupMember::getRole)
                .orderByAsc(GroupMember::getCreateTime));
        if (members.isEmpty()) {
            return Result.success(Collections.emptyList());
        }

        Map<Long, GroupTitle> titleMap = loadTitleMap(List.of(groupId));
        Map<Long, UserSimpleVO> userMap = loadUserMap(members.stream().map(GroupMember::getUserId).toList());

        List<GroupMemberVO> result = members.stream().map(member -> {
            GroupTitle title = titleMap.get(member.getTitleId());
            UserSimpleVO user = userMap.get(member.getUserId());
            GroupMemberVO vo = new GroupMemberVO();
            vo.setUserId(member.getUserId());
            vo.setUsername(user != null ? user.getUsername() : null);
            vo.setNickname(user != null ? user.getNickname() : String.valueOf(member.getUserId()));
            vo.setAvatar(user != null ? user.getAvatar() : null);
            vo.setNicknameInGroup(member.getNicknameInGroup());
            vo.setTitleId(member.getTitleId());
            vo.setTitleName(title != null ? title.getName() : null);
            vo.setRole(member.getRole());
            vo.setPermissions(resolvePermissions(member, title));
            vo.setDisplayName(resolveDisplayName(member, user));
            return vo;
        }).toList();
        return Result.success(result);
    }

    @Transactional
    public Result<String> updateMemberTitle(Long groupId, Long userId, GroupMemberTitleDTO dto) {
        Long currentUserId = UserContext.getUserId();
        GroupMember currentMember = getRequiredMember(groupId, currentUserId);
        if (!hasPermission(currentMember, GroupPermission.GROUP_ASSIGN_TITLE)) {
            return Result.error("No permission");
        }
        GroupMember targetMember = getRequiredMember(groupId, userId);
        if (targetMember == null) {
            return Result.error("Member not found");
        }
        if (GroupMemberRole.fromCode(targetMember.getRole()) == GroupMemberRole.OWNER) {
            return Result.error("Owner title cannot be changed");
        }
        if (dto == null || dto.getTitleId() == null) {
            return Result.error("Title id cannot be empty");
        }
        GroupTitle title = groupTitleMapper.selectById(dto.getTitleId());
        if (title == null || !Objects.equals(title.getGroupId(), groupId)) {
            return Result.error("Title not found");
        }
        targetMember.setTitleId(title.getId());
        targetMember.setUpdateTime(LocalDateTime.now());
        groupMemberMapper.updateById(targetMember);
        return Result.success("Member title updated");
    }

    @Transactional
    public Result<String> removeMember(Long groupId, Long userId) {
        Long currentUserId = UserContext.getUserId();
        GroupMember currentMember = getRequiredMember(groupId, currentUserId);
        if (!hasPermission(currentMember, GroupPermission.GROUP_REMOVE_MEMBER)) {
            return Result.error("No permission");
        }
        if (Objects.equals(currentUserId, userId)) {
            return Result.error("Please use leave group action");
        }
        GroupMember targetMember = getRequiredMember(groupId, userId);
        if (targetMember == null) {
            return Result.error("Member not found");
        }
        if (GroupMemberRole.fromCode(targetMember.getRole()) == GroupMemberRole.OWNER) {
            return Result.error("Cannot remove owner");
        }
        groupMemberMapper.deleteById(targetMember.getId());
        return Result.success("Member removed");
    }

    @Transactional
    public Result<String> leaveGroup(Long groupId) {
        Long currentUserId = UserContext.getUserId();
        GroupMember currentMember = getRequiredMember(groupId, currentUserId);
        if (currentMember == null) {
            return Result.error("Member not found");
        }
        if (GroupMemberRole.fromCode(currentMember.getRole()) == GroupMemberRole.OWNER) {
            return Result.error("Owner must transfer ownership or disband the group first");
        }
        groupMemberMapper.deleteById(currentMember.getId());
        return Result.success("Left group successfully");
    }

    @Transactional
    public Result<String> transferOwner(Long groupId, GroupTransferOwnerDTO dto) {
        Long currentUserId = UserContext.getUserId();
        if (dto == null || dto.getTargetUserId() == null) {
            return Result.error("Target user is required");
        }

        Group group = groupMapper.selectById(groupId);
        if (group == null) {
            return Result.error("Group not found");
        }
        if (!Objects.equals(group.getOwnerId(), currentUserId)) {
            return Result.error("Only owner can transfer ownership");
        }

        GroupMember ownerMember = getRequiredMember(groupId, currentUserId);
        GroupMember targetMember = getRequiredMember(groupId, dto.getTargetUserId());
        if (ownerMember == null || targetMember == null) {
            return Result.error("Target member not found");
        }
        if (Objects.equals(currentUserId, dto.getTargetUserId())) {
            return Result.success("Ownership transferred");
        }

        ownerMember.setRole(GroupMemberRole.SUPER_ADMIN.getCode());
        ownerMember.setUpdateTime(LocalDateTime.now());
        groupMemberMapper.updateById(ownerMember);

        targetMember.setRole(GroupMemberRole.OWNER.getCode());
        targetMember.setTitleId(null);
        targetMember.setUpdateTime(LocalDateTime.now());
        groupMemberMapper.updateById(targetMember);

        group.setOwnerId(targetMember.getUserId());
        group.setUpdateTime(LocalDateTime.now());
        groupMapper.updateById(group);
        return Result.success("Ownership transferred");
    }

    @Transactional
    public Result<String> deleteGroup(Long groupId) {
        Long currentUserId = UserContext.getUserId();
        Group group = groupMapper.selectById(groupId);
        if (group == null) {
            return Result.error("Group not found");
        }
        if (!Objects.equals(group.getOwnerId(), currentUserId)) {
            return Result.error("Only owner can disband the group");
        }

        groupJoinRequestMapper.delete(new LambdaQueryWrapper<GroupJoinRequest>()
                .eq(GroupJoinRequest::getGroupId, groupId));
        groupMemberMapper.delete(new LambdaQueryWrapper<GroupMember>()
                .eq(GroupMember::getGroupId, groupId));
        groupTitleMapper.delete(new LambdaQueryWrapper<GroupTitle>()
                .eq(GroupTitle::getGroupId, groupId));
        groupMapper.deleteById(groupId);
        return Result.success("Group deleted");
    }

    @Transactional
    public Result<GroupMemberVO> updateMemberNickname(Long groupId, Long userId, GroupMemberNicknameDTO dto) {
        Long currentUserId = UserContext.getUserId();
        Long targetUserId = userId == null ? currentUserId : userId;
        GroupMember currentMember = getRequiredMember(groupId, currentUserId);
        if (currentMember == null) {
            return Result.error("No permission");
        }
        if (!Objects.equals(currentUserId, targetUserId)
                && !hasPermission(currentMember, GroupPermission.GROUP_EDIT_MEMBER_NICKNAME)) {
            return Result.error("No permission");
        }
        GroupMember targetMember = getRequiredMember(groupId, targetUserId);
        if (targetMember == null) {
            return Result.error("Member not found");
        }

        String nickname = dto == null ? null : trimToNull(dto.getNicknameInGroup());
        if (nickname != null && nickname.length() > 32) {
            return Result.error("Nickname cannot exceed 32 characters");
        }
        targetMember.setNicknameInGroup(nickname);
        targetMember.setUpdateTime(LocalDateTime.now());
        groupMemberMapper.updateById(targetMember);

        Map<Long, UserSimpleVO> userMap = loadUserMap(List.of(targetUserId));
        GroupTitle title = targetMember.getTitleId() != null ? groupTitleMapper.selectById(targetMember.getTitleId())
                : null;
        UserSimpleVO user = userMap.get(targetUserId);
        GroupMemberVO vo = new GroupMemberVO();
        vo.setUserId(targetUserId);
        vo.setUsername(user != null ? user.getUsername() : null);
        vo.setNickname(user != null ? user.getNickname() : String.valueOf(targetUserId));
        vo.setAvatar(user != null ? user.getAvatar() : null);
        vo.setNicknameInGroup(targetMember.getNicknameInGroup());
        vo.setTitleId(targetMember.getTitleId());
        vo.setTitleName(title != null ? title.getName() : null);
        vo.setRole(targetMember.getRole());
        vo.setPermissions(resolvePermissions(targetMember, title));
        vo.setDisplayName(resolveDisplayName(targetMember, user));
        return Result.success(vo);
    }

    public Result<GroupChatMemberAccessVO> getMemberAccess(Long groupId, Long userId) {
        GroupMember member = getRequiredMember(groupId, userId);
        GroupChatMemberAccessVO vo = new GroupChatMemberAccessVO();
        vo.setGroupId(groupId);
        vo.setUserId(userId);
        vo.setMember(member != null);
        if (member == null) {
            vo.setPermissions(Collections.emptyList());
            vo.setRecallAnytime(false);
            return Result.success(vo);
        }
        GroupTitle title = member.getTitleId() != null ? groupTitleMapper.selectById(member.getTitleId()) : null;
        List<String> permissions = resolvePermissions(member, title);
        vo.setPermissions(permissions);
        vo.setRecallAnytime(permissions.contains(GroupPermission.GROUP_RECALL_ANYTIME.name()));
        return Result.success(vo);
    }

    public Result<List<Long>> getMemberIds(Long groupId) {
        List<GroupMember> members = groupMemberMapper.selectList(new LambdaQueryWrapper<GroupMember>()
                .eq(GroupMember::getGroupId, groupId)
                .select(GroupMember::getUserId));
        List<Long> ids = members.stream()
                .map(GroupMember::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return Result.success(ids);
    }

    public Result<List<GroupTitleVO>> getTitles(Long groupId) {
        Long currentUserId = UserContext.getUserId();
        GroupMember currentMember = getRequiredMember(groupId, currentUserId);
        if (currentMember == null) {
            return Result.error("No permission");
        }
        List<GroupTitle> titles = groupTitleMapper.selectList(new LambdaQueryWrapper<GroupTitle>()
                .eq(GroupTitle::getGroupId, groupId)
                .orderByAsc(GroupTitle::getSort)
                .orderByAsc(GroupTitle::getCreateTime));
        if (titles.isEmpty()) {
            return Result.success(Collections.emptyList());
        }
        List<GroupMember> members = groupMemberMapper.selectList(new LambdaQueryWrapper<GroupMember>()
                .eq(GroupMember::getGroupId, groupId));
        Map<Long, Long> titleCountMap = members.stream()
                .filter(item -> item.getTitleId() != null)
                .collect(Collectors.groupingBy(GroupMember::getTitleId, Collectors.counting()));

        List<GroupTitleVO> result = titles.stream().map(title -> {
            GroupTitleVO vo = new GroupTitleVO();
            vo.setId(title.getId());
            vo.setName(title.getName());
            vo.setIsDefault(title.getIsDefault() != null && title.getIsDefault() == 1);
            vo.setSort(title.getSort());
            vo.setMemberCount(Math.toIntExact(titleCountMap.getOrDefault(title.getId(), 0L)));
            vo.setPermissions(readPermissions(title.getPermissions()));
            return vo;
        }).toList();
        return Result.success(result);
    }

    @Transactional
    public Result<String> createTitle(Long groupId, GroupTitleCreateDTO dto) {
        Long currentUserId = UserContext.getUserId();
        GroupMember currentMember = getRequiredMember(groupId, currentUserId);
        if (!hasPermission(currentMember, GroupPermission.GROUP_MANAGE_TITLE)) {
            return Result.error("No permission");
        }
        if (dto == null || !StringUtils.hasText(dto.getName())) {
            return Result.error("Title name cannot be empty");
        }
        GroupTitle title = new GroupTitle();
        title.setGroupId(groupId);
        title.setName(dto.getName().trim());
        title.setIsDefault(0);
        title.setSort(dto.getSort() == null ? 0 : dto.getSort());
        title.setPermissions(writePermissions(sanitizePermissions(dto.getPermissions())));
        title.setCreateTime(LocalDateTime.now());
        title.setUpdateTime(LocalDateTime.now());
        groupTitleMapper.insert(title);
        return Result.success("Title created");
    }

    @Transactional
    public Result<String> updateTitle(Long groupId, Long titleId, GroupTitleUpdateDTO dto) {
        Long currentUserId = UserContext.getUserId();
        GroupMember currentMember = getRequiredMember(groupId, currentUserId);
        if (!hasPermission(currentMember, GroupPermission.GROUP_MANAGE_TITLE)) {
            return Result.error("No permission");
        }
        GroupTitle title = groupTitleMapper.selectById(titleId);
        if (title == null || !Objects.equals(title.getGroupId(), groupId)) {
            return Result.error("Title not found");
        }
        if (dto == null || !StringUtils.hasText(dto.getName())) {
            return Result.error("Title name cannot be empty");
        }
        title.setName(dto.getName().trim());
        title.setSort(dto.getSort() == null ? 0 : dto.getSort());
        title.setPermissions(writePermissions(sanitizePermissions(dto.getPermissions())));
        title.setUpdateTime(LocalDateTime.now());
        groupTitleMapper.updateById(title);
        return Result.success("Title updated");
    }

    @Transactional
    public Result<String> setDefaultTitle(Long groupId, Long titleId) {
        Long currentUserId = UserContext.getUserId();
        GroupMember currentMember = getRequiredMember(groupId, currentUserId);
        if (!hasPermission(currentMember, GroupPermission.GROUP_MANAGE_TITLE)) {
            return Result.error("No permission");
        }
        GroupTitle title = groupTitleMapper.selectById(titleId);
        if (title == null || !Objects.equals(title.getGroupId(), groupId)) {
            return Result.error("Title not found");
        }
        List<GroupTitle> titles = groupTitleMapper.selectList(new LambdaQueryWrapper<GroupTitle>()
                .eq(GroupTitle::getGroupId, groupId));
        for (GroupTitle item : titles) {
            item.setIsDefault(Objects.equals(item.getId(), titleId) ? 1 : 0);
            item.setUpdateTime(LocalDateTime.now());
            groupTitleMapper.updateById(item);
        }
        Group group = groupMapper.selectById(groupId);
        if (group != null) {
            group.setDefaultTitleId(titleId);
            group.setUpdateTime(LocalDateTime.now());
            groupMapper.updateById(group);
        }
        return Result.success("Default title updated");
    }

    @Transactional
    public Result<String> deleteTitle(Long groupId, Long titleId) {
        Long currentUserId = UserContext.getUserId();
        GroupMember currentMember = getRequiredMember(groupId, currentUserId);
        if (!hasPermission(currentMember, GroupPermission.GROUP_MANAGE_TITLE)) {
            return Result.error("No permission");
        }
        GroupTitle title = groupTitleMapper.selectById(titleId);
        if (title == null || !Objects.equals(title.getGroupId(), groupId)) {
            return Result.error("Title not found");
        }
        if (title.getIsDefault() != null && title.getIsDefault() == 1) {
            return Result.error("Default title cannot be deleted");
        }
        long count = groupMemberMapper.selectCount(new LambdaQueryWrapper<GroupMember>()
                .eq(GroupMember::getGroupId, groupId)
                .eq(GroupMember::getTitleId, titleId));
        if (count > 0) {
            return Result.error("Please move members away before deleting this title");
        }
        groupTitleMapper.deleteById(titleId);
        return Result.success("Title deleted");
    }

    @Transactional
    public Result<String> inviteMembers(Long groupId, GroupInviteDTO dto) {
        Long currentUserId = UserContext.getUserId();
        Group group = groupMapper.selectById(groupId);
        if (group == null) {
            return Result.error("Group not found");
        }
        GroupMember currentMember = getRequiredMember(groupId, currentUserId);
        if (!hasPermission(currentMember, GroupPermission.GROUP_INVITE_MEMBER)) {
            return Result.error("No permission");
        }
        if (dto == null || dto.getTargetUserIds() == null || dto.getTargetUserIds().isEmpty()) {
            return Result.error("Target users cannot be empty");
        }

        GroupInviteAuditMode mode = GroupInviteAuditMode.fromCode(group.getInviteAuditMode());
        Set<Long> targetIds = dto.getTargetUserIds().stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        targetIds.remove(currentUserId);
        if (targetIds.isEmpty()) {
            return Result.error("Target users cannot be empty");
        }

        int invited = 0;
        int pending = 0;
        for (Long targetUserId : targetIds) {
            if (getRequiredMember(groupId, targetUserId) != null) {
                continue;
            }
            if (mode.requiresInviteAudit()) {
                if (hasPendingRequest(groupId, GroupJoinRequestType.INVITE, currentUserId, targetUserId)) {
                    continue;
                }
                GroupJoinRequest request = new GroupJoinRequest();
                request.setGroupId(groupId);
                request.setType(GroupJoinRequestType.INVITE.name());
                request.setFromUserId(currentUserId);
                request.setTargetUserId(targetUserId);
                request.setStatus(GroupJoinRequestStatus.PENDING.name());
                request.setReason(trimToNull(dto.getReason()));
                request.setExpireTime(LocalDateTime.now().plusDays(7));
                request.setCreateTime(LocalDateTime.now());
                request.setUpdateTime(LocalDateTime.now());
                groupJoinRequestMapper.insert(request);
                pending++;
            } else {
                addMember(group, targetUserId, GroupMemberRole.MEMBER, null);
                invited++;
            }
        }
        return Result.success(String.format("Invite completed: %d joined, %d pending", invited, pending));
    }

    @Transactional
    public Result<String> applyToGroup(Long groupId, GroupApplyDTO dto) {
        Long currentUserId = UserContext.getUserId();
        Group group = groupMapper.selectById(groupId);
        if (group == null) {
            return Result.error("Group not found");
        }
        if (getRequiredMember(groupId, currentUserId) != null) {
            return Result.error("Already in group");
        }
        if (hasPendingRequest(groupId, GroupJoinRequestType.APPLY, currentUserId, null)) {
            return Result.error("Apply request already exists");
        }

        GroupInviteAuditMode mode = GroupInviteAuditMode.fromCode(group.getInviteAuditMode());
        if (mode.requiresApplyAudit()) {
            GroupJoinRequest request = new GroupJoinRequest();
            request.setGroupId(groupId);
            request.setType(GroupJoinRequestType.APPLY.name());
            request.setFromUserId(currentUserId);
            request.setTargetUserId(null);
            request.setStatus(GroupJoinRequestStatus.PENDING.name());
            request.setReason(trimToNull(dto != null ? dto.getReason() : null));
            request.setExpireTime(LocalDateTime.now().plusDays(7));
            request.setCreateTime(LocalDateTime.now());
            request.setUpdateTime(LocalDateTime.now());
            groupJoinRequestMapper.insert(request);
            return Result.success("Apply submitted");
        }

        addMember(group, currentUserId, GroupMemberRole.MEMBER, null);
        return Result.success("Joined successfully");
    }

    public Result<List<GroupJoinRequestVO>> getJoinRequests(Long groupId) {
        Long currentUserId = UserContext.getUserId();
        GroupMember currentMember = getRequiredMember(groupId, currentUserId);
        if (currentMember == null) {
            return Result.error("No permission");
        }
        boolean canReviewInvite = hasPermission(currentMember, GroupPermission.GROUP_REVIEW_INVITE);
        boolean canReviewApply = hasPermission(currentMember, GroupPermission.GROUP_REVIEW_APPLY);
        if (!canReviewInvite && !canReviewApply) {
            return Result.error("No permission");
        }

        List<GroupJoinRequest> requests = groupJoinRequestMapper.selectList(new LambdaQueryWrapper<GroupJoinRequest>()
                .eq(GroupJoinRequest::getGroupId, groupId)
                .eq(GroupJoinRequest::getStatus, GroupJoinRequestStatus.PENDING.name())
                .orderByDesc(GroupJoinRequest::getCreateTime));
        if (requests.isEmpty()) {
            return Result.success(Collections.emptyList());
        }

        List<GroupJoinRequest> filtered = requests.stream().filter(item -> {
            if (GroupJoinRequestType.INVITE.name().equals(item.getType())) {
                return canReviewInvite;
            }
            return canReviewApply;
        }).toList();
        if (filtered.isEmpty()) {
            return Result.success(Collections.emptyList());
        }

        Set<Long> userIds = new HashSet<>();
        filtered.forEach(item -> {
            if (item.getFromUserId() != null) {
                userIds.add(item.getFromUserId());
            }
            if (item.getTargetUserId() != null) {
                userIds.add(item.getTargetUserId());
            }
        });
        Map<Long, UserSimpleVO> userMap = loadUserMap(new ArrayList<>(userIds));

        List<GroupJoinRequestVO> result = filtered.stream().map(item -> {
            GroupJoinRequestVO vo = new GroupJoinRequestVO();
            vo.setId(item.getId());
            vo.setGroupId(item.getGroupId());
            vo.setType(item.getType());
            vo.setStatus(item.getStatus());
            vo.setReason(item.getReason());
            vo.setAuditBy(item.getAuditBy());
            vo.setCreateTime(item.getCreateTime());
            vo.setFromUser(userMap.get(item.getFromUserId()));
            vo.setTargetUser(userMap.get(item.getTargetUserId()));
            return vo;
        }).toList();
        return Result.success(result);
    }

    @Transactional
    public Result<String> auditJoinRequest(Long groupId, Long requestId, GroupJoinAuditDTO dto) {
        Long currentUserId = UserContext.getUserId();
        GroupMember currentMember = getRequiredMember(groupId, currentUserId);
        if (currentMember == null) {
            return Result.error("No permission");
        }

        GroupJoinRequest request = groupJoinRequestMapper.selectById(requestId);
        if (request == null || !Objects.equals(request.getGroupId(), groupId)) {
            return Result.error("Join request not found");
        }
        if (!GroupJoinRequestStatus.PENDING.name().equals(request.getStatus())) {
            return Result.error("Join request already processed");
        }

        GroupJoinRequestType requestType = GroupJoinRequestType.valueOf(request.getType());
        if (requestType == GroupJoinRequestType.INVITE
                && !hasPermission(currentMember, GroupPermission.GROUP_REVIEW_INVITE)) {
            return Result.error("No permission");
        }
        if (requestType == GroupJoinRequestType.APPLY
                && !hasPermission(currentMember, GroupPermission.GROUP_REVIEW_APPLY)) {
            return Result.error("No permission");
        }

        boolean approve = dto != null && Boolean.TRUE.equals(dto.getApprove());
        request.setStatus(approve ? GroupJoinRequestStatus.APPROVED.name() : GroupJoinRequestStatus.REJECTED.name());
        request.setAuditBy(currentUserId);
        if (dto != null && StringUtils.hasText(dto.getRemark())) {
            request.setReason(dto.getRemark().trim());
        }
        request.setUpdateTime(LocalDateTime.now());
        groupJoinRequestMapper.updateById(request);

        if (approve) {
            Group group = groupMapper.selectById(groupId);
            Long userId = requestType == GroupJoinRequestType.INVITE ? request.getTargetUserId()
                    : request.getFromUserId();
            if (group != null && userId != null && getRequiredMember(groupId, userId) == null) {
                addMember(group, userId, GroupMemberRole.MEMBER, null);
            }
        }
        return Result.success("Join request processed");
    }

    private Map<Long, GroupTitle> loadTitleMap(List<Long> groupIds) {
        if (groupIds == null || groupIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<GroupTitle> titles = groupTitleMapper.selectList(new LambdaQueryWrapper<GroupTitle>()
                .in(GroupTitle::getGroupId, groupIds));
        return titles.stream().collect(Collectors.toMap(GroupTitle::getId, Function.identity(), (a, b) -> a));
    }

    private Map<Long, Long> loadGroupMemberCountMap(List<Long> groupIds) {
        if (groupIds == null || groupIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            List<Map<String, Object>> allMemberRows = groupMemberMapper.selectMaps(new QueryWrapper<GroupMember>()
                    .select("group_id")
                    .in("group_id", groupIds));
            return allMemberRows.stream()
                    .map(row -> row.get("group_id"))
                    .filter(Number.class::isInstance)
                    .map(Number.class::cast)
                    .collect(Collectors.groupingBy(Number::longValue, Collectors.counting()));
        } catch (Exception e) {
            log.warn("Count group members failed for groupIds={}", groupIds, e);
            return Collections.emptyMap();
        }
    }

    private Map<Long, UserSimpleVO> loadUserMap(List<Long> userIds) {
        List<Long> ids = userIds == null ? Collections.emptyList()
                : userIds.stream()
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            Result<List<UserSimpleVO>> result = userFeignClient.getUserBatch(ids);
            List<UserSimpleVO> users = result != null ? result.getData() : null;
            if (users == null || users.isEmpty()) {
                return Collections.emptyMap();
            }
            return users.stream().collect(Collectors.toMap(UserSimpleVO::getId, Function.identity(), (a, b) -> a));
        } catch (Exception e) {
            log.error("Load user batch failed, ids={}", ids, e);
            return Collections.emptyMap();
        }
    }

    private GroupMember getRequiredMember(Long groupId, Long userId) {
        if (groupId == null || userId == null) {
            return null;
        }
        return groupMemberMapper.selectOne(new LambdaQueryWrapper<GroupMember>()
                .eq(GroupMember::getGroupId, groupId)
                .eq(GroupMember::getUserId, userId)
                .last("LIMIT 1"));
    }

    private boolean hasPendingRequest(Long groupId, GroupJoinRequestType type, Long fromUserId, Long targetUserId) {
        LambdaQueryWrapper<GroupJoinRequest> wrapper = new LambdaQueryWrapper<GroupJoinRequest>()
                .eq(GroupJoinRequest::getGroupId, groupId)
                .eq(GroupJoinRequest::getType, type.name())
                .eq(GroupJoinRequest::getStatus, GroupJoinRequestStatus.PENDING.name())
                .eq(GroupJoinRequest::getFromUserId, fromUserId);
        if (targetUserId == null) {
            wrapper.isNull(GroupJoinRequest::getTargetUserId);
        } else {
            wrapper.eq(GroupJoinRequest::getTargetUserId, targetUserId);
        }
        return groupJoinRequestMapper.selectCount(wrapper) > 0;
    }

    private GroupMember addMember(Group group, Long userId, GroupMemberRole role, String nicknameInGroup) {
        GroupMember exists = getRequiredMember(group.getId(), userId);
        if (exists != null) {
            return exists;
        }
        GroupMember member = new GroupMember();
        member.setGroupId(group.getId());
        member.setUserId(userId);
        member.setRemark(null);
        member.setNicknameInGroup(trimToNull(nicknameInGroup));
        member.setRole(role.getCode());
        member.setTitleId(role == GroupMemberRole.MEMBER ? group.getDefaultTitleId() : null);
        member.setMuteUntil(null);
        member.setCreateTime(LocalDateTime.now());
        member.setUpdateTime(LocalDateTime.now());
        groupMemberMapper.insert(member);
        return member;
    }

    private List<String> resolvePermissions(GroupMember member, GroupTitle title) {
        if (member == null) {
            return Collections.emptyList();
        }
        GroupMemberRole role = GroupMemberRole.fromCode(member.getRole());
        if (role == GroupMemberRole.OWNER) {
            return OWNER_PERMISSIONS;
        }
        if (role == GroupMemberRole.SUPER_ADMIN) {
            return SUPER_ADMIN_PERMISSIONS;
        }
        List<String> permissions = new ArrayList<>(readPermissions(title != null ? title.getPermissions() : null));
        if (!permissions.contains(GroupPermission.GROUP_VIEW.name())) {
            permissions.add(0, GroupPermission.GROUP_VIEW.name());
        }
        return permissions;
    }

    private boolean hasPermission(GroupMember member, GroupPermission permission) {
        if (member == null) {
            return false;
        }
        GroupMemberRole role = GroupMemberRole.fromCode(member.getRole());
        if (role == GroupMemberRole.OWNER) {
            return true;
        }
        if (role == GroupMemberRole.SUPER_ADMIN) {
            return permission != GroupPermission.GROUP_TRANSFER_OWNER;
        }
        GroupTitle title = member.getTitleId() != null ? groupTitleMapper.selectById(member.getTitleId()) : null;
        return readPermissions(title != null ? title.getPermissions() : null).contains(permission.name());
    }

    private List<String> readPermissions(String permissions) {
        if (!StringUtils.hasText(permissions)) {
            return Collections.emptyList();
        }
        try {
            List<String> raw = objectMapper.readValue(permissions, new TypeReference<List<String>>() {
            });
            return sanitizePermissions(raw);
        } catch (Exception e) {
            log.warn("Read group permissions failed: {}", permissions, e);
            return Collections.emptyList();
        }
    }

    private String writePermissions(List<String> permissions) {
        try {
            return objectMapper.writeValueAsString(sanitizePermissions(permissions));
        } catch (Exception e) {
            throw new IllegalStateException("Serialize group permissions failed", e);
        }
    }

    private List<String> sanitizePermissions(List<String> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return new ArrayList<>(List.of(GroupPermission.GROUP_VIEW.name()));
        }
        Set<String> allowSet = Arrays.stream(GroupPermission.values()).map(Enum::name).collect(Collectors.toSet());
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String permission : permissions) {
            if (!StringUtils.hasText(permission)) {
                continue;
            }
            String value = permission.trim();
            if (allowSet.contains(value)) {
                values.add(value);
            }
        }
        values.add(GroupPermission.GROUP_VIEW.name());
        return new ArrayList<>(values);
    }

    private String resolveDisplayName(GroupMember member, UserSimpleVO user) {
        if (member != null && StringUtils.hasText(member.getNicknameInGroup())) {
            return member.getNicknameInGroup();
        }
        if (user != null && StringUtils.hasText(user.getNickname())) {
            return user.getNickname();
        }
        if (member != null && member.getUserId() != null) {
            return String.valueOf(member.getUserId());
        }
        return "Unknown";
    }

    private Integer normalizeInviteAuditMode(Integer mode) {
        return GroupInviteAuditMode.fromCode(mode).getCode();
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime time) {
            return time;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof java.util.Date date) {
            return LocalDateTime.ofInstant(date.toInstant(), TimeZone.getDefault().toZoneId());
        }
        return null;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
