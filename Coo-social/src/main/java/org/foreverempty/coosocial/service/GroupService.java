package org.foreverempty.coosocial.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.foreverempty.common.Result;
import org.foreverempty.common.context.UserContext;
import org.foreverempty.common.vo.UserSimpleVO;
import org.foreverempty.coosocial.content.GroupJoinRequestStatus;
import org.foreverempty.coosocial.content.GroupJoinRequestType;
import org.foreverempty.coosocial.content.GroupPermission;
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
import org.foreverempty.coosocial.entity.Group;
import org.foreverempty.coosocial.entity.GroupJoinRequest;
import org.foreverempty.coosocial.entity.GroupMember;
import org.foreverempty.coosocial.entity.GroupTitle;
import org.foreverempty.coosocial.feign.UserFeignClient;
import org.foreverempty.coosocial.mapper.GroupJoinRequestMapper;
import org.foreverempty.coosocial.mapper.GroupMapper;
import org.foreverempty.coosocial.mapper.GroupMemberMapper;
import org.foreverempty.coosocial.mapper.GroupTitleMapper;
import org.foreverempty.coosocial.vo.GroupChatMemberAccessVO;
import org.foreverempty.coosocial.vo.GroupInfoVO;
import org.foreverempty.coosocial.vo.GroupJoinRequestVO;
import org.foreverempty.coosocial.vo.GroupListVO;
import org.foreverempty.coosocial.vo.GroupMemberVO;
import org.foreverempty.coosocial.vo.GroupSearchVO;
import org.foreverempty.coosocial.vo.GroupTitleVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class GroupService {
    private static final String SYSTEM_OWNER = "OWNER";
    private static final String SYSTEM_MANAGER = "MANAGER";
    private static final String SYSTEM_MEMBER = "MEMBER";
    private static final String SYSTEM_CUSTOM = "CUSTOM";
    private static final int TITLE_SORT_STEP = 10;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final List<String> OWNER_PERMISSIONS = Arrays.stream(GroupPermission.values())
            .map(Enum::name)
            .toList();
    private static final List<String> DEFAULT_MANAGER_PERMISSIONS = Arrays.stream(GroupPermission.values())
            .filter(permission -> permission != GroupPermission.GROUP_TRANSFER_OWNER)
            .map(Enum::name)
            .toList();
    private static final List<String> DEFAULT_MEMBER_PERMISSIONS = List.of(
            GroupPermission.GROUP_VIEW.name(),
            GroupPermission.GROUP_FILE_VIEW.name(),
            GroupPermission.GROUP_FILE_UPLOAD.name()
    );

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

    public Result<List<GroupListVO>> getMyGroups() {
        try {
            Long currentUserId = requireCurrentUserId();
            List<GroupMember> memberships = groupMemberMapper.selectList(new LambdaQueryWrapper<GroupMember>()
                    .eq(GroupMember::getUserId, currentUserId));
            if (memberships.isEmpty()) {
                return Result.success(Collections.emptyList());
            }
            Map<Long, GroupMember> membershipMap = memberships.stream()
                    .collect(Collectors.toMap(GroupMember::getGroupId, item -> item, (left, right) -> left, LinkedHashMap::new));
            List<Group> groups = groupMapper.selectBatchIds(membershipMap.keySet());
            Map<Long, Integer> memberCountMap = groupMemberMapper.selectList(new LambdaQueryWrapper<GroupMember>()
                            .in(GroupMember::getGroupId, membershipMap.keySet()))
                    .stream()
                    .collect(Collectors.groupingBy(GroupMember::getGroupId, Collectors.collectingAndThen(Collectors.counting(), Long::intValue)));
            List<GroupListVO> result = new ArrayList<>();
            for (Group group : groups) {
                if (group == null) {
                    continue;
                }
                SystemTitles systemTitles = ensureSystemTitles(group);
                GroupMember member = membershipMap.get(group.getId());
                ensureMemberTitleBinding(group, member, systemTitles);
                GroupTitle title = systemTitles.getTitleById().get(member.getTitleId());
                result.add(toGroupListVO(group, member, title, memberCountMap.getOrDefault(group.getId(), 0)));
            }
            result.sort(Comparator.comparing(GroupListVO::getId).reversed());
            return Result.success(result);
        } catch (Exception ex) {
            log.error("getMyGroups failed", ex);
            return Result.error(ex.getMessage());
        }
    }

    public Result<List<GroupSearchVO>> searchGroups(String keyword) {
        try {
            Long currentUserId = requireCurrentUserId();
            String trimmed = keyword == null ? "" : keyword.trim();
            List<Group> groups = groupMapper.selectList(new LambdaQueryWrapper<Group>()
                    .and(StringUtils.hasText(trimmed), wrapper -> wrapper
                            .like(Group::getName, trimmed)
                            .or()
                            .eq(Group::getId, parseLong(trimmed))));
            if (groups.isEmpty()) {
                return Result.success(Collections.emptyList());
            }
            Set<Long> groupIds = groups.stream().map(Group::getId).collect(Collectors.toCollection(LinkedHashSet::new));
            Map<Long, Integer> memberCountMap = groupMemberMapper.selectList(new LambdaQueryWrapper<GroupMember>()
                            .in(GroupMember::getGroupId, groupIds))
                    .stream()
                    .collect(Collectors.groupingBy(GroupMember::getGroupId, Collectors.collectingAndThen(Collectors.counting(), Long::intValue)));
            Set<Long> joinedIds = groupMemberMapper.selectList(new LambdaQueryWrapper<GroupMember>()
                            .eq(GroupMember::getUserId, currentUserId)
                            .in(GroupMember::getGroupId, groupIds)
                            .select(GroupMember::getGroupId))
                    .stream()
                    .map(GroupMember::getGroupId)
                    .collect(Collectors.toSet());
            Set<Long> pendingIds = groupJoinRequestMapper.selectList(new LambdaQueryWrapper<GroupJoinRequest>()
                            .eq(GroupJoinRequest::getFromUserId, currentUserId)
                            .eq(GroupJoinRequest::getType, GroupJoinRequestType.APPLY.name())
                            .eq(GroupJoinRequest::getStatus, GroupJoinRequestStatus.PENDING.name())
                            .in(GroupJoinRequest::getGroupId, groupIds)
                            .select(GroupJoinRequest::getGroupId))
                    .stream()
                    .map(GroupJoinRequest::getGroupId)
                    .collect(Collectors.toSet());
            List<GroupSearchVO> result = groups.stream()
                    .map(group -> toGroupSearchVO(group, joinedIds.contains(group.getId()), pendingIds.contains(group.getId()), memberCountMap.getOrDefault(group.getId(), 0)))
                    .toList();
            return Result.success(result);
        } catch (Exception ex) {
            log.error("searchGroups failed, keyword={}", keyword, ex);
            return Result.error(ex.getMessage());
        }
    }

    @Transactional
    public Result<GroupInfoVO> createGroup(GroupCreateDTO dto) {
        try {
            Long currentUserId = requireCurrentUserId();
            if (dto == null || !StringUtils.hasText(dto.getName())) {
                return Result.error("群名称不能为空");
            }
            Group group = new Group();
            group.setName(dto.getName().trim());
            group.setOwnerId(currentUserId);
            group.setAvatar(dto.getAvatar());
            group.setCoverUrl(dto.getCoverUrl());
            group.setNotice(dto.getNotice());
            group.setInviteAuditMode(dto.getInviteAuditMode() == null ? 0 : dto.getInviteAuditMode());
            group.setCreateTime(LocalDateTime.now());
            group.setUpdateTime(LocalDateTime.now());
            groupMapper.insert(group);

            SystemTitles systemTitles = ensureSystemTitles(group);
            addMember(group.getId(), currentUserId, systemTitles.getOwnerTitle().getId());

            Set<Long> initialMemberIds = dto.getInitialMemberIds() == null
                    ? Collections.emptySet()
                    : dto.getInitialMemberIds().stream()
                    .filter(Objects::nonNull)
                    .filter(id -> !Objects.equals(id, currentUserId))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            for (Long memberId : initialMemberIds) {
                addMember(group.getId(), memberId, systemTitles.getMemberTitle().getId());
            }
            GroupMember ownerMember = requireMember(group.getId(), currentUserId);
            return Result.success(toGroupInfoVO(group, ownerMember, systemTitles.getOwnerTitle(), OWNER_PERMISSIONS, 1 + initialMemberIds.size()));
        } catch (Exception ex) {
            log.error("createGroup failed", ex);
            return Result.error(ex.getMessage());
        }
    }

    public Result<GroupInfoVO> getGroupInfo(Long groupId) {
        try {
            OperatorContext context = loadOperatorContext(groupId);
            int memberCount = Math.toIntExact(groupMemberMapper.selectCount(new LambdaQueryWrapper<GroupMember>()
                    .eq(GroupMember::getGroupId, groupId)));
            GroupTitle title = context.getSystemTitles().getTitleById().get(context.getMember().getTitleId());
            return Result.success(toGroupInfoVO(context.getGroup(), context.getMember(), title, context.getPermissions(), memberCount));
        } catch (Exception ex) {
            log.error("getGroupInfo failed, groupId={}", groupId, ex);
            return Result.error(ex.getMessage());
        }
    }

    @Transactional
    public Result<String> updateGroup(Long groupId, GroupUpdateDTO dto) {
        try {
            OperatorContext context = loadOperatorContext(groupId);
            if (!hasPermission(context.getGroup(), context.getMember(), context.getSystemTitles(), GroupPermission.GROUP_EDIT_INFO)) {
                return Result.error("无权限修改群资料");
            }
            if (dto == null) {
                return Result.error("参数错误");
            }
            Group group = context.getGroup();
            if (StringUtils.hasText(dto.getName())) {
                group.setName(dto.getName().trim());
            }
            if (dto.getAvatar() != null) {
                group.setAvatar(dto.getAvatar());
            }
            if (dto.getCoverUrl() != null) {
                group.setCoverUrl(dto.getCoverUrl());
            }
            if (dto.getNotice() != null) {
                group.setNotice(dto.getNotice());
            }
            if (dto.getInviteAuditMode() != null) {
                group.setInviteAuditMode(dto.getInviteAuditMode());
            }
            group.setUpdateTime(LocalDateTime.now());
            groupMapper.updateById(group);
            return Result.success("群资料已更新");
        } catch (Exception ex) {
            log.error("updateGroup failed, groupId={}", groupId, ex);
            return Result.error(ex.getMessage());
        }
    }

    @Transactional
    public Result<String> updateRemark(Long groupId, GroupRemarkDTO dto) {
        try {
            Long currentUserId = requireCurrentUserId();
            GroupMember member = requireMember(groupId, currentUserId);
            member.setRemark(dto == null ? null : trimToNull(dto.getRemark()));
            member.setUpdateTime(LocalDateTime.now());
            groupMemberMapper.updateById(member);
            return Result.success("群备注已更新");
        } catch (Exception ex) {
            log.error("updateRemark failed, groupId={}", groupId, ex);
            return Result.error(ex.getMessage());
        }
    }

    public Result<List<GroupMemberVO>> getMembers(Long groupId) {
        try {
            OperatorContext context = loadOperatorContext(groupId);
            List<GroupMember> members = groupMemberMapper.selectList(new LambdaQueryWrapper<GroupMember>()
                    .eq(GroupMember::getGroupId, groupId));
            members.forEach(item -> ensureMemberTitleBinding(context.getGroup(), item, context.getSystemTitles()));
            Map<Long, UserSimpleVO> userMap = getUserMap(members.stream().map(GroupMember::getUserId).collect(Collectors.toSet()));
            List<GroupMemberVO> result = members.stream()
                    .sorted(Comparator.comparingInt(item -> getRank(context.getGroup(), item, context.getSystemTitles())))
                    .map(member -> toGroupMemberVO(
                            context.getGroup(),
                            member,
                            context.getSystemTitles().getTitleById().get(member.getTitleId()),
                            userMap.get(member.getUserId()),
                            context.getSystemTitles()))
                    .toList();
            return Result.success(result);
        } catch (Exception ex) {
            log.error("getMembers failed, groupId={}", groupId, ex);
            return Result.error(ex.getMessage());
        }
    }

    @Transactional
    public Result<String> updateMemberTitle(Long groupId, Long userId, GroupMemberTitleDTO dto) {
        try {
            if (dto == null || dto.getTitleId() == null) {
                return Result.error("头衔不能为空");
            }
            OperatorContext context = loadOperatorContext(groupId);
            if (!hasPermission(context.getGroup(), context.getMember(), context.getSystemTitles(), GroupPermission.GROUP_ASSIGN_TITLE)) {
                return Result.error("无权限修改成员头衔");
            }
            GroupMember target = requireMember(groupId, userId);
            if (!canManageTarget(context.getGroup(), context.getMember(), target, context.getSystemTitles())) {
                return Result.error("不能操作同级或上级成员");
            }
            GroupTitle title = requireTitle(groupId, dto.getTitleId());
            if (SYSTEM_OWNER.equals(title.getSystemKey())) {
                return Result.error("不能直接分配群主头衔");
            }
            target.setTitleId(title.getId());
            target.setUpdateTime(LocalDateTime.now());
            groupMemberMapper.updateById(target);
            return Result.success("成员头衔已更新");
        } catch (Exception ex) {
            log.error("updateMemberTitle failed, groupId={}, userId={}", groupId, userId, ex);
            return Result.error(ex.getMessage());
        }
    }

    @Transactional
    public Result<String> removeMember(Long groupId, Long userId) {
        try {
            OperatorContext context = loadOperatorContext(groupId);
            if (!hasPermission(context.getGroup(), context.getMember(), context.getSystemTitles(), GroupPermission.GROUP_REMOVE_MEMBER)) {
                return Result.error("无权限移除成员");
            }
            GroupMember target = requireMember(groupId, userId);
            if (!canManageTarget(context.getGroup(), context.getMember(), target, context.getSystemTitles())) {
                return Result.error("不能移除同级或上级成员");
            }
            groupMemberMapper.deleteById(target.getId());
            return Result.success("成员已移除");
        } catch (Exception ex) {
            log.error("removeMember failed, groupId={}, userId={}", groupId, userId, ex);
            return Result.error(ex.getMessage());
        }
    }

    @Transactional
    public Result<GroupMemberVO> updateMemberNickname(Long groupId, Long userId, GroupMemberNicknameDTO dto) {
        try {
            OperatorContext context = loadOperatorContext(groupId);
            Long targetUserId = userId == null ? context.getUserId() : userId;
            GroupMember target = requireMember(groupId, targetUserId);
            boolean selfEdit = Objects.equals(context.getUserId(), targetUserId);
            if (!selfEdit) {
                if (!hasPermission(context.getGroup(), context.getMember(), context.getSystemTitles(), GroupPermission.GROUP_EDIT_MEMBER_NICKNAME)) {
                    return Result.error("无权限修改成员群昵称");
                }
                if (!canManageTarget(context.getGroup(), context.getMember(), target, context.getSystemTitles())) {
                    return Result.error("不能修改同级或上级成员");
                }
            }
            target.setNicknameInGroup(dto == null ? null : trimToNull(dto.getNicknameInGroup()));
            target.setUpdateTime(LocalDateTime.now());
            groupMemberMapper.updateById(target);
            Map<Long, UserSimpleVO> userMap = getUserMap(Set.of(target.getUserId()));
            GroupTitle title = context.getSystemTitles().getTitleById().get(target.getTitleId());
            return Result.success(toGroupMemberVO(context.getGroup(), target, title, userMap.get(target.getUserId()), context.getSystemTitles()));
        } catch (Exception ex) {
            log.error("updateMemberNickname failed, groupId={}, userId={}", groupId, userId, ex);
            return Result.error(ex.getMessage());
        }
    }

    @Transactional
    public Result<String> leaveGroup(Long groupId) {
        try {
            Long currentUserId = requireCurrentUserId();
            Group group = requireGroup(groupId);
            if (Objects.equals(group.getOwnerId(), currentUserId)) {
                return Result.error("群主不能直接退群");
            }
            GroupMember member = requireMember(groupId, currentUserId);
            groupMemberMapper.deleteById(member.getId());
            return Result.success("已退群");
        } catch (Exception ex) {
            log.error("leaveGroup failed, groupId={}", groupId, ex);
            return Result.error(ex.getMessage());
        }
    }

    @Transactional
    public Result<String> transferOwner(Long groupId, GroupTransferOwnerDTO dto) {
        try {
            if (dto == null || dto.getTargetUserId() == null) {
                return Result.error("目标成员不能为空");
            }
            Long currentUserId = requireCurrentUserId();
            Group group = requireGroup(groupId);
            if (!Objects.equals(group.getOwnerId(), currentUserId)) {
                return Result.error("只有群主可以转让群主");
            }
            SystemTitles systemTitles = ensureSystemTitles(group);
            GroupMember currentOwner = requireMember(groupId, currentUserId);
            GroupMember target = requireMember(groupId, dto.getTargetUserId());
            currentOwner.setTitleId(systemTitles.getManagerTitle().getId());
            currentOwner.setUpdateTime(LocalDateTime.now());
            groupMemberMapper.updateById(currentOwner);
            target.setTitleId(systemTitles.getOwnerTitle().getId());
            target.setUpdateTime(LocalDateTime.now());
            groupMemberMapper.updateById(target);
            group.setOwnerId(dto.getTargetUserId());
            group.setUpdateTime(LocalDateTime.now());
            groupMapper.updateById(group);
            return Result.success("群主已转让");
        } catch (Exception ex) {
            log.error("transferOwner failed, groupId={}", groupId, ex);
            return Result.error(ex.getMessage());
        }
    }

    @Transactional
    public Result<String> deleteGroup(Long groupId) {
        try {
            Long currentUserId = requireCurrentUserId();
            Group group = requireGroup(groupId);
            if (!Objects.equals(group.getOwnerId(), currentUserId)) {
                return Result.error("只有群主可以解散群");
            }
            groupJoinRequestMapper.delete(new LambdaQueryWrapper<GroupJoinRequest>().eq(GroupJoinRequest::getGroupId, groupId));
            groupMemberMapper.delete(new LambdaQueryWrapper<GroupMember>().eq(GroupMember::getGroupId, groupId));
            groupTitleMapper.delete(new LambdaQueryWrapper<GroupTitle>().eq(GroupTitle::getGroupId, groupId));
            groupMapper.deleteById(groupId);
            return Result.success("群已解散");
        } catch (Exception ex) {
            log.error("deleteGroup failed, groupId={}", groupId, ex);
            return Result.error(ex.getMessage());
        }
    }

    public Result<List<GroupTitleVO>> getTitles(Long groupId) {
        try {
            OperatorContext context = loadOperatorContext(groupId);
            List<GroupTitle> titles = context.getSystemTitles().getOrdered();
            Map<Long, Integer> memberCountMap = groupMemberMapper.selectList(new LambdaQueryWrapper<GroupMember>()
                            .eq(GroupMember::getGroupId, groupId))
                    .stream()
                    .collect(Collectors.groupingBy(GroupMember::getTitleId, Collectors.collectingAndThen(Collectors.counting(), Long::intValue)));
            List<GroupTitleVO> result = titles.stream()
                    .map(title -> toGroupTitleVO(title, memberCountMap.getOrDefault(title.getId(), 0)))
                    .toList();
            return Result.success(result);
        } catch (Exception ex) {
            log.error("getTitles failed, groupId={}", groupId, ex);
            return Result.error(ex.getMessage());
        }
    }

    @Transactional
    public Result<String> createTitle(Long groupId, GroupTitleCreateDTO dto) {
        try {
            OperatorContext context = loadOperatorContext(groupId);
            if (!hasPermission(context.getGroup(), context.getMember(), context.getSystemTitles(), GroupPermission.GROUP_MANAGE_TITLE)) {
                return Result.error("无权限管理头衔");
            }
            if (dto == null || !StringUtils.hasText(dto.getName())) {
                return Result.error("头衔名称不能为空");
            }
            GroupTitle title = new GroupTitle();
            title.setGroupId(groupId);
            title.setSystemKey(SYSTEM_CUSTOM);
            title.setName(dto.getName().trim());
            int maxSort = context.getSystemTitles().getOrdered().stream()
                    .map(GroupTitle::getSort)
                    .filter(Objects::nonNull)
                    .max(Integer::compareTo)
                    .orElse(TITLE_SORT_STEP);
            title.setSort(dto.getSort() == null ? maxSort + TITLE_SORT_STEP : dto.getSort());
            title.setIsDefault(0);
            title.setPermissions(toJson(dto.getPermissions()));
            title.setCreateTime(LocalDateTime.now());
            title.setUpdateTime(LocalDateTime.now());
            groupTitleMapper.insert(title);
            return Result.success("头衔已创建");
        } catch (Exception ex) {
            log.error("createTitle failed, groupId={}", groupId, ex);
            return Result.error(ex.getMessage());
        }
    }

    @Transactional
    public Result<String> updateTitle(Long groupId, Long titleId, GroupTitleUpdateDTO dto) {
        try {
            OperatorContext context = loadOperatorContext(groupId);
            if (!hasPermission(context.getGroup(), context.getMember(), context.getSystemTitles(), GroupPermission.GROUP_MANAGE_TITLE)) {
                return Result.error("无权限管理头衔");
            }
            GroupTitle title = requireTitle(groupId, titleId);
            if (SYSTEM_OWNER.equals(title.getSystemKey())) {
                return Result.error("群主头衔不可修改");
            }
            if (dto == null) {
                return Result.error("参数错误");
            }
            if (StringUtils.hasText(dto.getName())) {
                title.setName(dto.getName().trim());
            }
            if (dto.getSort() != null) {
                title.setSort(dto.getSort());
            }
            title.setPermissions(toJson(dto.getPermissions()));
            title.setUpdateTime(LocalDateTime.now());
            groupTitleMapper.updateById(title);
            return Result.success("头衔已更新");
        } catch (Exception ex) {
            log.error("updateTitle failed, groupId={}, titleId={}", groupId, titleId, ex);
            return Result.error(ex.getMessage());
        }
    }

    @Transactional
    public Result<String> setDefaultTitle(Long groupId, Long titleId) {
        try {
            OperatorContext context = loadOperatorContext(groupId);
            if (!hasPermission(context.getGroup(), context.getMember(), context.getSystemTitles(), GroupPermission.GROUP_MANAGE_TITLE)) {
                return Result.error("无权限管理头衔");
            }
            GroupTitle title = requireTitle(groupId, titleId);
            if (SYSTEM_OWNER.equals(title.getSystemKey())) {
                return Result.error("群主头衔不能设为默认");
            }
            groupTitleMapper.update(null, new LambdaUpdateWrapper<GroupTitle>()
                    .eq(GroupTitle::getGroupId, groupId)
                    .set(GroupTitle::getIsDefault, 0)
                    .set(GroupTitle::getUpdateTime, LocalDateTime.now()));
            title.setIsDefault(1);
            title.setUpdateTime(LocalDateTime.now());
            groupTitleMapper.updateById(title);
            context.getGroup().setDefaultTitleId(title.getId());
            groupMapper.updateById(context.getGroup());
            return Result.success("默认头衔已更新");
        } catch (Exception ex) {
            log.error("setDefaultTitle failed, groupId={}, titleId={}", groupId, titleId, ex);
            return Result.error(ex.getMessage());
        }
    }

    @Transactional
    public Result<String> sortTitles(Long groupId, GroupTitleSortDTO dto) {
        try {
            OperatorContext context = loadOperatorContext(groupId);
            if (!hasPermission(context.getGroup(), context.getMember(), context.getSystemTitles(), GroupPermission.GROUP_MANAGE_TITLE)) {
                return Result.error("无权限管理头衔");
            }
            if (dto == null || dto.getTitleIds() == null || dto.getTitleIds().isEmpty()) {
                return Result.error("头衔排序不能为空");
            }
            List<GroupTitle> titles = context.getSystemTitles().getOrdered();
            Set<Long> existingIds = titles.stream().map(GroupTitle::getId).collect(Collectors.toCollection(LinkedHashSet::new));
            LinkedHashSet<Long> submittedIds = dto.getTitleIds().stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (!existingIds.equals(submittedIds)) {
                return Result.error("头衔排序数据不完整");
            }
            List<Long> orderedIds = new ArrayList<>(dto.getTitleIds());
            orderedIds.remove(context.getSystemTitles().getOwnerTitle().getId());
            orderedIds.add(0, context.getSystemTitles().getOwnerTitle().getId());
            for (int i = 0; i < orderedIds.size(); i++) {
                GroupTitle title = context.getSystemTitles().getTitleById().get(orderedIds.get(i));
                if (title == null) {
                    continue;
                }
                title.setSort(i * TITLE_SORT_STEP);
                title.setUpdateTime(LocalDateTime.now());
                groupTitleMapper.updateById(title);
            }
            repairDefaultTitle(context.getGroup());
            return Result.success("头衔排序已更新");
        } catch (Exception ex) {
            log.error("sortTitles failed, groupId={}", groupId, ex);
            return Result.error(ex.getMessage());
        }
    }

    @Transactional
    public Result<String> deleteTitle(Long groupId, Long titleId) {
        try {
            OperatorContext context = loadOperatorContext(groupId);
            if (!hasPermission(context.getGroup(), context.getMember(), context.getSystemTitles(), GroupPermission.GROUP_MANAGE_TITLE)) {
                return Result.error("无权限管理头衔");
            }
            GroupTitle title = requireTitle(groupId, titleId);
            if (SYSTEM_OWNER.equals(title.getSystemKey())) {
                return Result.error("群主头衔不可删除");
            }
            long count = groupMemberMapper.selectCount(new LambdaQueryWrapper<GroupMember>()
                    .eq(GroupMember::getGroupId, groupId)
                    .eq(GroupMember::getTitleId, titleId));
            if (count > 0) {
                return Result.error("仍有成员使用该头衔");
            }
            groupTitleMapper.deleteById(titleId);
            repairDefaultTitle(context.getGroup());
            return Result.success("头衔已删除");
        } catch (Exception ex) {
            log.error("deleteTitle failed, groupId={}, titleId={}", groupId, titleId, ex);
            return Result.error(ex.getMessage());
        }
    }

    @Transactional
    public Result<String> inviteMembers(Long groupId, GroupInviteDTO dto) {
        try {
            OperatorContext context = loadOperatorContext(groupId);
            if (!hasPermission(context.getGroup(), context.getMember(), context.getSystemTitles(), GroupPermission.GROUP_INVITE_MEMBER)) {
                return Result.error("无权限邀请成员");
            }
            if (dto == null || dto.getTargetUserIds() == null || dto.getTargetUserIds().isEmpty()) {
                return Result.error("请选择邀请成员");
            }
            Set<Long> targetUserIds = dto.getTargetUserIds().stream()
                    .filter(Objects::nonNull)
                    .filter(id -> !Objects.equals(id, context.getUserId()))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (targetUserIds.isEmpty()) {
                return Result.error("请选择有效邀请成员");
            }
            Set<Long> existingMemberIds = groupMemberMapper.selectList(new LambdaQueryWrapper<GroupMember>()
                            .eq(GroupMember::getGroupId, groupId)
                            .in(GroupMember::getUserId, targetUserIds)
                            .select(GroupMember::getUserId))
                    .stream()
                    .map(GroupMember::getUserId)
                    .collect(Collectors.toSet());
            LocalDateTime now = LocalDateTime.now();
            for (Long targetUserId : targetUserIds) {
                if (existingMemberIds.contains(targetUserId)) {
                    continue;
                }
                GroupJoinRequest request = groupJoinRequestMapper.selectOne(new LambdaQueryWrapper<GroupJoinRequest>()
                        .eq(GroupJoinRequest::getGroupId, groupId)
                        .eq(GroupJoinRequest::getType, GroupJoinRequestType.INVITE.name())
                        .eq(GroupJoinRequest::getTargetUserId, targetUserId)
                        .eq(GroupJoinRequest::getStatus, GroupJoinRequestStatus.PENDING.name())
                        .last("LIMIT 1"));
                if (request != null) {
                    continue;
                }
                request = new GroupJoinRequest();
                request.setGroupId(groupId);
                request.setType(GroupJoinRequestType.INVITE.name());
                request.setFromUserId(context.getUserId());
                request.setTargetUserId(targetUserId);
                request.setStatus(GroupJoinRequestStatus.PENDING.name());
                request.setReason(dto.getReason());
                request.setExpireTime(now.plusDays(7));
                request.setCreateTime(now);
                request.setUpdateTime(now);
                groupJoinRequestMapper.insert(request);
            }
            return Result.success("邀请已发出");
        } catch (Exception ex) {
            log.error("inviteMembers failed, groupId={}", groupId, ex);
            return Result.error(ex.getMessage());
        }
    }

    @Transactional
    public Result<String> applyToGroup(Long groupId, GroupApplyDTO dto) {
        try {
            Long currentUserId = requireCurrentUserId();
            Group group = requireGroup(groupId);
            GroupMember existing = groupMemberMapper.selectOne(new LambdaQueryWrapper<GroupMember>()
                    .eq(GroupMember::getGroupId, groupId)
                    .eq(GroupMember::getUserId, currentUserId)
                    .last("LIMIT 1"));
            if (existing != null) {
                return Result.error("你已经在群内");
            }
            GroupJoinRequest pending = groupJoinRequestMapper.selectOne(new LambdaQueryWrapper<GroupJoinRequest>()
                    .eq(GroupJoinRequest::getGroupId, groupId)
                    .eq(GroupJoinRequest::getType, GroupJoinRequestType.APPLY.name())
                    .eq(GroupJoinRequest::getFromUserId, currentUserId)
                    .eq(GroupJoinRequest::getStatus, GroupJoinRequestStatus.PENDING.name())
                    .last("LIMIT 1"));
            if (pending != null) {
                return Result.error("你已提交过申请");
            }
            GroupJoinRequest request = new GroupJoinRequest();
            request.setGroupId(groupId);
            request.setType(GroupJoinRequestType.APPLY.name());
            request.setFromUserId(currentUserId);
            request.setStatus(GroupJoinRequestStatus.PENDING.name());
            request.setReason(dto == null ? null : dto.getReason());
            request.setExpireTime(LocalDateTime.now().plusDays(7));
            request.setCreateTime(LocalDateTime.now());
            request.setUpdateTime(LocalDateTime.now());
            groupJoinRequestMapper.insert(request);
            return Result.success("申请已提交");
        } catch (Exception ex) {
            log.error("applyToGroup failed, groupId={}", groupId, ex);
            return Result.error(ex.getMessage());
        }
    }

    public Result<List<GroupJoinRequestVO>> getJoinRequests(Long groupId) {
        try {
            OperatorContext context = loadOperatorContext(groupId);
            boolean canReviewInvite = hasPermission(context.getGroup(), context.getMember(), context.getSystemTitles(), GroupPermission.GROUP_REVIEW_INVITE);
            boolean canReviewApply = hasPermission(context.getGroup(), context.getMember(), context.getSystemTitles(), GroupPermission.GROUP_REVIEW_APPLY);
            if (!canReviewInvite && !canReviewApply) {
                return Result.error("无权限查看审批记录");
            }
            LambdaQueryWrapper<GroupJoinRequest> wrapper = new LambdaQueryWrapper<GroupJoinRequest>()
                    .eq(GroupJoinRequest::getGroupId, groupId)
                    .orderByDesc(GroupJoinRequest::getCreateTime);
            if (canReviewInvite && !canReviewApply) {
                wrapper.eq(GroupJoinRequest::getType, GroupJoinRequestType.INVITE.name());
            } else if (!canReviewInvite) {
                wrapper.eq(GroupJoinRequest::getType, GroupJoinRequestType.APPLY.name());
            }
            List<GroupJoinRequest> requests = groupJoinRequestMapper.selectList(wrapper);
            Set<Long> userIds = new LinkedHashSet<>();
            for (GroupJoinRequest request : requests) {
                userIds.add(request.getFromUserId());
                if (request.getTargetUserId() != null) {
                    userIds.add(request.getTargetUserId());
                }
            }
            Map<Long, UserSimpleVO> userMap = getUserMap(userIds);
            return Result.success(requests.stream().map(request -> toJoinRequestVO(request, userMap)).toList());
        } catch (Exception ex) {
            log.error("getJoinRequests failed, groupId={}", groupId, ex);
            return Result.error(ex.getMessage());
        }
    }

    @Transactional
    public Result<String> auditJoinRequest(Long groupId, Long requestId, GroupJoinAuditDTO dto) {
        try {
            OperatorContext context = loadOperatorContext(groupId);
            GroupJoinRequest request = groupJoinRequestMapper.selectOne(new LambdaQueryWrapper<GroupJoinRequest>()
                    .eq(GroupJoinRequest::getId, requestId)
                    .eq(GroupJoinRequest::getGroupId, groupId)
                    .last("LIMIT 1"));
            if (request == null) {
                return Result.error("审批记录不存在");
            }
            boolean approve = dto != null && Boolean.TRUE.equals(dto.getApprove());
            boolean canReview = GroupJoinRequestType.INVITE.name().equals(request.getType())
                    ? hasPermission(context.getGroup(), context.getMember(), context.getSystemTitles(), GroupPermission.GROUP_REVIEW_INVITE)
                    : hasPermission(context.getGroup(), context.getMember(), context.getSystemTitles(), GroupPermission.GROUP_REVIEW_APPLY);
            if (!canReview) {
                return Result.error("无权限审批");
            }
            if (!GroupJoinRequestStatus.PENDING.name().equals(request.getStatus())) {
                return Result.success("审批已处理");
            }
            request.setAuditBy(context.getUserId());
            request.setReason(dto == null ? request.getReason() : dto.getRemark());
            request.setStatus(approve ? GroupJoinRequestStatus.APPROVED.name() : GroupJoinRequestStatus.REJECTED.name());
            request.setUpdateTime(LocalDateTime.now());
            groupJoinRequestMapper.updateById(request);
            if (approve) {
                SystemTitles systemTitles = context.getSystemTitles();
                Long targetUserId = GroupJoinRequestType.APPLY.name().equals(request.getType())
                        ? request.getFromUserId()
                        : request.getTargetUserId();
                if (targetUserId != null) {
                    addMember(groupId, targetUserId, context.getGroup().getDefaultTitleId() != null
                            ? context.getGroup().getDefaultTitleId()
                            : systemTitles.getMemberTitle().getId());
                }
            }
            return Result.success(approve ? "审批已通过" : "审批已拒绝");
        } catch (Exception ex) {
            log.error("auditJoinRequest failed, groupId={}, requestId={}", groupId, requestId, ex);
            return Result.error(ex.getMessage());
        }
    }

    public Result<GroupChatMemberAccessVO> getMemberAccess(Long groupId, Long userId) {
        try {
            Group group = requireGroup(groupId);
            SystemTitles systemTitles = ensureSystemTitles(group);
            GroupMember member = requireMember(groupId, userId);
            List<String> permissions = resolvePermissions(group, member, systemTitles);
            GroupChatMemberAccessVO vo = new GroupChatMemberAccessVO();
            vo.setGroupId(groupId);
            vo.setUserId(userId);
            vo.setMember(true);
            vo.setPermissions(permissions);
            vo.setRecallAnytime(permissions.contains(GroupPermission.GROUP_RECALL_ANYTIME.name()) || Objects.equals(group.getOwnerId(), userId));
            return Result.success(vo);
        } catch (Exception ex) {
            log.error("getMemberAccess failed, groupId={}, userId={}", groupId, userId, ex);
            return Result.error(ex.getMessage());
        }
    }

    public Result<List<Long>> getMemberIds(Long groupId) {
        try {
            requireGroup(groupId);
            List<Long> memberIds = groupMemberMapper.selectList(new LambdaQueryWrapper<GroupMember>()
                            .eq(GroupMember::getGroupId, groupId)
                            .select(GroupMember::getUserId))
                    .stream()
                    .map(GroupMember::getUserId)
                    .toList();
            return Result.success(memberIds);
        } catch (Exception ex) {
            log.error("getMemberIds failed, groupId={}", groupId, ex);
            return Result.error(ex.getMessage());
        }
    }

    private Long requireCurrentUserId() {
        Long currentUserId = UserContext.getUserId();
        if (currentUserId == null) {
            throw new IllegalStateException("Unauthorized");
        }
        return currentUserId;
    }

    private Group requireGroup(Long groupId) {
        Group group = groupMapper.selectById(groupId);
        if (group == null) {
            throw new IllegalArgumentException("群不存在");
        }
        return group;
    }

    private GroupMember requireMember(Long groupId, Long userId) {
        GroupMember member = groupMemberMapper.selectOne(new LambdaQueryWrapper<GroupMember>()
                .eq(GroupMember::getGroupId, groupId)
                .eq(GroupMember::getUserId, userId)
                .last("LIMIT 1"));
        if (member == null) {
            throw new IllegalArgumentException("你不是群成员");
        }
        return member;
    }

    private GroupTitle requireTitle(Long groupId, Long titleId) {
        GroupTitle title = groupTitleMapper.selectOne(new LambdaQueryWrapper<GroupTitle>()
                .eq(GroupTitle::getGroupId, groupId)
                .eq(GroupTitle::getId, titleId)
                .last("LIMIT 1"));
        if (title == null) {
            throw new IllegalArgumentException("头衔不存在");
        }
        return title;
    }

    private SystemTitles ensureSystemTitles(Group group) {
        List<GroupTitle> existingTitles = groupTitleMapper.selectList(new LambdaQueryWrapper<GroupTitle>()
                .eq(GroupTitle::getGroupId, group.getId())
                .orderByAsc(GroupTitle::getSort, GroupTitle::getCreateTime));
        GroupTitle ownerTitle = upsertSystemTitle(group, SYSTEM_OWNER, "群主", 0, false, OWNER_PERMISSIONS);
        GroupTitle managerTitle = ensureManagerTitle(group, existingTitles);
        GroupTitle memberTitle = ensureMemberTitle(group, existingTitles);

        List<GroupTitle> refreshedTitles = groupTitleMapper.selectList(new LambdaQueryWrapper<GroupTitle>()
                .eq(GroupTitle::getGroupId, group.getId())
                .orderByAsc(GroupTitle::getSort, GroupTitle::getCreateTime));

        Long defaultTitleId = group.getDefaultTitleId();
        if (defaultTitleId == null || defaultTitleId.equals(ownerTitle.getId())) {
            group.setDefaultTitleId(memberTitle.getId());
            groupMapper.updateById(group);
        }

        SystemTitles systemTitles = new SystemTitles();
        systemTitles.setOwnerTitle(ownerTitle);
        systemTitles.setManagerTitle(managerTitle);
        systemTitles.setMemberTitle(memberTitle);
        systemTitles.setOrdered(refreshedTitles);
        systemTitles.setTitleById(refreshedTitles.stream()
                .collect(Collectors.toMap(GroupTitle::getId, item -> item, (left, right) -> left, LinkedHashMap::new)));
        return systemTitles;
    }

    private GroupTitle upsertSystemTitle(Group group, String systemKey, String name, int sort, boolean isDefault, List<String> permissions) {
        GroupTitle title = groupTitleMapper.selectOne(new LambdaQueryWrapper<GroupTitle>()
                .eq(GroupTitle::getGroupId, group.getId())
                .eq(GroupTitle::getSystemKey, systemKey)
                .last("LIMIT 1"));
        LocalDateTime now = LocalDateTime.now();
        if (title == null) {
            title = new GroupTitle();
            title.setGroupId(group.getId());
            title.setSystemKey(systemKey);
            title.setCreateTime(now);
        }
        title.setName(name);
        title.setSort(sort);
        title.setIsDefault(isDefault ? 1 : 0);
        title.setPermissions(toJson(permissions));
        title.setUpdateTime(now);
        if (title.getId() == null) {
            groupTitleMapper.insert(title);
        } else {
            groupTitleMapper.updateById(title);
        }
        return title;
    }

    private GroupTitle ensureManagerTitle(Group group, List<GroupTitle> existingTitles) {
        GroupTitle managerTitle = existingTitles.stream()
                .filter(item -> SYSTEM_MANAGER.equals(item.getSystemKey()))
                .findFirst()
                .orElse(null);
        if (managerTitle == null) {
            int managerSort = Math.max(TITLE_SORT_STEP,
                    existingTitles.stream()
                            .map(GroupTitle::getSort)
                            .filter(Objects::nonNull)
                            .filter(sort -> sort > 0)
                            .min(Integer::compareTo)
                            .orElse(TITLE_SORT_STEP));
            return upsertSystemTitle(group, SYSTEM_MANAGER, "管理", managerSort, false, DEFAULT_MANAGER_PERMISSIONS);
        }
        managerTitle.setSystemKey(SYSTEM_MANAGER);
        if (!StringUtils.hasText(managerTitle.getPermissions())) {
            managerTitle.setPermissions(toJson(DEFAULT_MANAGER_PERMISSIONS));
        }
        if (managerTitle.getSort() == null || managerTitle.getSort() <= 0) {
            managerTitle.setSort(TITLE_SORT_STEP);
        }
        if (managerTitle.getUpdateTime() == null) {
            managerTitle.setUpdateTime(LocalDateTime.now());
        }
        groupTitleMapper.updateById(managerTitle);
        return managerTitle;
    }

    private GroupTitle ensureMemberTitle(Group group, List<GroupTitle> existingTitles) {
        GroupTitle memberTitle = existingTitles.stream()
                .filter(item -> SYSTEM_MEMBER.equals(item.getSystemKey()))
                .findFirst()
                .orElse(null);
        if (memberTitle == null) {
            int sort = existingTitles.stream()
                    .map(GroupTitle::getSort)
                    .filter(Objects::nonNull)
                    .max(Integer::compareTo)
                    .orElse(TITLE_SORT_STEP) + TITLE_SORT_STEP;
            return upsertSystemTitle(group, SYSTEM_MEMBER, "成员", sort, true, DEFAULT_MEMBER_PERMISSIONS);
        }
        memberTitle.setSystemKey(SYSTEM_MEMBER);
        if (!StringUtils.hasText(memberTitle.getPermissions())) {
            memberTitle.setPermissions(toJson(DEFAULT_MEMBER_PERMISSIONS));
        }
        if (memberTitle.getSort() == null || memberTitle.getSort() <= 0) {
            memberTitle.setSort(TITLE_SORT_STEP * 2);
        }
        groupTitleMapper.updateById(memberTitle);
        return memberTitle;
    }

    private void ensureMemberTitleBinding(Group group, GroupMember member, SystemTitles systemTitles) {
        if (member == null) {
            return;
        }
        Long expectedTitleId;
        if (Objects.equals(group.getOwnerId(), member.getUserId())) {
            expectedTitleId = systemTitles.getOwnerTitle().getId();
        } else if (member.getTitleId() != null && systemTitles.getTitleById().containsKey(member.getTitleId())) {
            expectedTitleId = member.getTitleId();
        } else if (group.getDefaultTitleId() != null && !Objects.equals(group.getDefaultTitleId(), systemTitles.getOwnerTitle().getId())) {
            expectedTitleId = group.getDefaultTitleId();
        } else {
            expectedTitleId = systemTitles.getMemberTitle().getId();
        }
        if (!Objects.equals(expectedTitleId, member.getTitleId())) {
            member.setTitleId(expectedTitleId);
            member.setUpdateTime(LocalDateTime.now());
            groupMemberMapper.updateById(member);
        }
    }

    private List<String> resolvePermissions(Group group, GroupMember member, SystemTitles systemTitles) {
        if (Objects.equals(group.getOwnerId(), member.getUserId())) {
            return OWNER_PERMISSIONS;
        }
        ensureMemberTitleBinding(group, member, systemTitles);
        GroupTitle title = systemTitles.getTitleById().get(member.getTitleId());
        if (title == null) {
            return List.of(GroupPermission.GROUP_VIEW.name());
        }
        LinkedHashSet<String> permissions = new LinkedHashSet<>(fromJson(title.getPermissions()));
        permissions.add(GroupPermission.GROUP_VIEW.name());
        return new ArrayList<>(permissions);
    }

    private boolean hasPermission(Group group, GroupMember member, SystemTitles systemTitles, GroupPermission permission) {
        if (permission == GroupPermission.GROUP_TRANSFER_OWNER) {
            return Objects.equals(group.getOwnerId(), member.getUserId());
        }
        return resolvePermissions(group, member, systemTitles).contains(permission.name());
    }

    private boolean canManageTarget(Group group, GroupMember operator, GroupMember target, SystemTitles systemTitles) {
        if (operator == null || target == null || Objects.equals(operator.getUserId(), target.getUserId())) {
            return false;
        }
        if (Objects.equals(group.getOwnerId(), operator.getUserId())) {
            return !Objects.equals(group.getOwnerId(), target.getUserId());
        }
        if (Objects.equals(group.getOwnerId(), target.getUserId())) {
            return false;
        }
        return getRank(group, operator, systemTitles) < getRank(group, target, systemTitles);
    }

    private int getRank(Group group, GroupMember member, SystemTitles systemTitles) {
        if (Objects.equals(group.getOwnerId(), member.getUserId())) {
            return Integer.MIN_VALUE;
        }
        ensureMemberTitleBinding(group, member, systemTitles);
        GroupTitle title = systemTitles.getTitleById().get(member.getTitleId());
        if (title == null || title.getSort() == null) {
            return Integer.MAX_VALUE;
        }
        return title.getSort();
    }

    private void addMember(Long groupId, Long userId, Long titleId) {
        GroupMember existing = groupMemberMapper.selectOne(new LambdaQueryWrapper<GroupMember>()
                .eq(GroupMember::getGroupId, groupId)
                .eq(GroupMember::getUserId, userId)
                .last("LIMIT 1"));
        if (existing != null) {
            existing.setTitleId(titleId);
            existing.setUpdateTime(LocalDateTime.now());
            groupMemberMapper.updateById(existing);
            return;
        }
        GroupMember member = new GroupMember();
        member.setGroupId(groupId);
        member.setUserId(userId);
        member.setTitleId(titleId);
        member.setCreateTime(LocalDateTime.now());
        member.setUpdateTime(LocalDateTime.now());
        groupMemberMapper.insert(member);
    }

    private void repairDefaultTitle(Group group) {
        SystemTitles systemTitles = ensureSystemTitles(group);
        if (group.getDefaultTitleId() != null
                && !Objects.equals(group.getDefaultTitleId(), systemTitles.getOwnerTitle().getId())
                && systemTitles.getTitleById().containsKey(group.getDefaultTitleId())) {
            return;
        }
        GroupTitle fallback = systemTitles.getOrdered().stream()
                .filter(item -> !SYSTEM_OWNER.equals(item.getSystemKey()))
                .max(Comparator.comparing(GroupTitle::getSort, Comparator.nullsLast(Integer::compareTo)))
                .orElse(systemTitles.getMemberTitle());
        group.setDefaultTitleId(fallback.getId());
        groupMapper.updateById(group);
    }

    private Map<Long, UserSimpleVO> getUserMap(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Result<List<UserSimpleVO>> result = userFeignClient.getUserBatch(new ArrayList<>(userIds));
        if (result == null || result.getData() == null) {
            return Collections.emptyMap();
        }
        return result.getData().stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(UserSimpleVO::getId, item -> item, (left, right) -> left, LinkedHashMap::new));
    }

    private GroupListVO toGroupListVO(Group group, GroupMember member, GroupTitle title, int memberCount) {
        GroupListVO vo = new GroupListVO();
        vo.setId(group.getId());
        vo.setName(group.getName());
        vo.setOwnerId(group.getOwnerId());
        vo.setAvatar(group.getAvatar());
        vo.setCoverUrl(group.getCoverUrl());
        vo.setNotice(group.getNotice());
        vo.setRemark(member.getRemark());
        vo.setMemberCount(memberCount);
        vo.setMyTitleId(member.getTitleId());
        vo.setMyTitleName(title != null ? title.getName() : null);
        vo.setMyNicknameInGroup(member.getNicknameInGroup());
        return vo;
    }

    private GroupSearchVO toGroupSearchVO(Group group, boolean joined, boolean pending, int memberCount) {
        GroupSearchVO vo = new GroupSearchVO();
        vo.setId(group.getId());
        vo.setName(group.getName());
        vo.setAvatar(group.getAvatar());
        vo.setCoverUrl(group.getCoverUrl());
        vo.setNotice(group.getNotice());
        vo.setMemberCount(memberCount);
        vo.setJoined(joined);
        vo.setPending(pending);
        return vo;
    }

    private GroupInfoVO toGroupInfoVO(Group group, GroupMember member, GroupTitle title, List<String> permissions, int memberCount) {
        GroupInfoVO vo = new GroupInfoVO();
        vo.setId(group.getId());
        vo.setName(group.getName());
        vo.setAvatar(group.getAvatar());
        vo.setCoverUrl(group.getCoverUrl());
        vo.setNotice(group.getNotice());
        vo.setRemark(member.getRemark());
        vo.setOwnerId(group.getOwnerId());
        vo.setInviteAuditMode(group.getInviteAuditMode());
        vo.setDefaultTitleId(group.getDefaultTitleId());
        vo.setMemberCount(memberCount);
        vo.setMyTitleId(member.getTitleId());
        vo.setMyTitleName(title != null ? title.getName() : null);
        vo.setMyNicknameInGroup(member.getNicknameInGroup());
        vo.setMyPermissions(permissions);
        vo.setFileCapacityMb(group.getFileCapacityMb());
        vo.setOversizeThresholdMb(group.getOversizeThresholdMb());
        vo.setTempExpireDays(group.getTempExpireDays());
        vo.setUsedStorageBytes(group.getUsedStorageBytes());
        return vo;
    }

    private GroupMemberVO toGroupMemberVO(Group group, GroupMember member, GroupTitle title, UserSimpleVO user, SystemTitles systemTitles) {
        GroupMemberVO vo = new GroupMemberVO();
        vo.setUserId(member.getUserId());
        if (user != null) {
            vo.setUsername(user.getUsername());
            vo.setNickname(user.getNickname());
            vo.setAvatar(user.getAvatar());
        }
        vo.setNicknameInGroup(member.getNicknameInGroup());
        vo.setTitleId(member.getTitleId());
        vo.setTitleName(title != null ? title.getName() : null);
        vo.setPermissions(resolvePermissions(group, member, systemTitles));
        String displayName = member.getNicknameInGroup();
        if (!StringUtils.hasText(displayName) && user != null) {
            displayName = StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername();
        }
        if (!StringUtils.hasText(displayName)) {
            displayName = String.valueOf(member.getUserId());
        }
        vo.setDisplayName(displayName);
        return vo;
    }

    private GroupTitleVO toGroupTitleVO(GroupTitle title, int memberCount) {
        GroupTitleVO vo = new GroupTitleVO();
        vo.setId(title.getId());
        vo.setSystemKey(title.getSystemKey());
        vo.setName(title.getName());
        vo.setIsDefault(title.getIsDefault() != null && title.getIsDefault() == 1);
        vo.setSort(title.getSort());
        vo.setMemberCount(memberCount);
        vo.setPermissions(fromJson(title.getPermissions()));
        return vo;
    }

    private GroupJoinRequestVO toJoinRequestVO(GroupJoinRequest request, Map<Long, UserSimpleVO> userMap) {
        GroupJoinRequestVO vo = new GroupJoinRequestVO();
        BeanUtils.copyProperties(request, vo);
        vo.setFromUser(userMap.get(request.getFromUserId()));
        if (request.getTargetUserId() != null) {
            vo.setTargetUser(userMap.get(request.getTargetUserId()));
        }
        return vo;
    }

    private String toJson(List<String> permissions) {
        try {
            return OBJECT_MAPPER.writeValueAsString(permissions == null ? Collections.emptyList() : permissions);
        } catch (Exception ex) {
            throw new IllegalStateException("序列化权限失败", ex);
        }
    }

    private List<String> fromJson(String permissions) {
        if (!StringUtils.hasText(permissions)) {
            return Collections.emptyList();
        }
        try {
            return OBJECT_MAPPER.readValue(permissions, new TypeReference<List<String>>() {});
        } catch (Exception ex) {
            log.warn("parse permissions failed: {}", permissions, ex);
            return Collections.emptyList();
        }
    }

    private Long parseLong(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static class SystemTitles {
        private GroupTitle ownerTitle;
        private GroupTitle managerTitle;
        private GroupTitle memberTitle;
        private List<GroupTitle> ordered = Collections.emptyList();
        private Map<Long, GroupTitle> titleById = Collections.emptyMap();

        public GroupTitle getOwnerTitle() {
            return ownerTitle;
        }

        public void setOwnerTitle(GroupTitle ownerTitle) {
            this.ownerTitle = ownerTitle;
        }

        public GroupTitle getManagerTitle() {
            return managerTitle;
        }

        public void setManagerTitle(GroupTitle managerTitle) {
            this.managerTitle = managerTitle;
        }

        public GroupTitle getMemberTitle() {
            return memberTitle;
        }

        public void setMemberTitle(GroupTitle memberTitle) {
            this.memberTitle = memberTitle;
        }

        public List<GroupTitle> getOrdered() {
            return ordered;
        }

        public void setOrdered(List<GroupTitle> ordered) {
            this.ordered = ordered;
        }

        public Map<Long, GroupTitle> getTitleById() {
            return titleById;
        }

        public void setTitleById(Map<Long, GroupTitle> titleById) {
            this.titleById = titleById;
        }
    }

    @Data
    private static class OperatorContext {
        private Long userId;
        private Group group;
        private GroupMember member;
        private SystemTitles systemTitles;
        private List<String> permissions;
    }

    private OperatorContext loadOperatorContext(Long groupId) {
        Long userId = requireCurrentUserId();
        Group group = requireGroup(groupId);
        SystemTitles systemTitles = ensureSystemTitles(group);
        GroupMember member = requireMember(groupId, userId);
        ensureMemberTitleBinding(group, member, systemTitles);
        OperatorContext context = new OperatorContext();
        context.setUserId(userId);
        context.setGroup(group);
        context.setMember(member);
        context.setSystemTitles(systemTitles);
        context.setPermissions(resolvePermissions(group, member, systemTitles));
        return context;
    }
}
