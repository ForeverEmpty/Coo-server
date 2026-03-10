package org.foreverempty.coosocial.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.foreverempty.common.PageResult;
import org.foreverempty.common.Result;
import org.foreverempty.common.context.UserContext;
import org.foreverempty.common.vo.UserFullVO;
import org.foreverempty.common.vo.UserSimpleVO;
import org.foreverempty.coosocial.content.FriendApplyStatus;
import org.foreverempty.coosocial.content.FriendSource;
import org.foreverempty.coosocial.content.FriendStatus;
import org.foreverempty.coosocial.dto.ChatSessionConfigDTO;
import org.foreverempty.coosocial.dto.FriendApplyDTO;
import org.foreverempty.coosocial.dto.FriendAuditDTO;
import org.foreverempty.coosocial.dto.FriendGroupAddDTO;
import org.foreverempty.coosocial.dto.FriendGroupUpdateDTO;
import org.foreverempty.coosocial.dto.FriendGroupSortDTO;
import org.foreverempty.coosocial.dto.FriendRelationUpdateDTO;
import org.foreverempty.coosocial.entity.ChatSessionConfig;
import org.foreverempty.coosocial.entity.Friend;
import org.foreverempty.coosocial.entity.FriendApply;
import org.foreverempty.coosocial.entity.FriendGroup;
import org.foreverempty.coosocial.feign.UserFeignClient;
import org.foreverempty.coosocial.mapper.ChatSessionConfigMapper;
import org.foreverempty.coosocial.mapper.FriendApplyMapper;
import org.foreverempty.coosocial.mapper.FriendGroupMapper;
import org.foreverempty.coosocial.mapper.FriendMapper;
import org.foreverempty.coosocial.vo.ChatSessionConfigVO;
import org.foreverempty.coosocial.vo.FriendApplyVO;
import org.foreverempty.coosocial.vo.FriendGroupVO;
import org.foreverempty.coosocial.vo.MutualFriendListVO;
import org.foreverempty.coosocial.vo.FriendVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FriendService {
    private static final int MAX_CHAT_ID_LIST_SIZE = 1000;
    private static final int DEFAULT_MUTUAL_LIMIT = 6;
    private static final int MAX_MUTUAL_LIMIT = 20;

    @Autowired
    private FriendMapper friendMapper;

    @Autowired
    private FriendGroupMapper friendGroupMapper;

    @Autowired
    private FriendApplyMapper friendApplyMapper;

    @Autowired
    private ChatSessionConfigMapper chatSessionConfigMapper;

    @Autowired
    private UserFeignClient userFeignClient;

    @Autowired
    private ObjectMapper objectMapper;

    public Result<List<FriendGroupVO>> getFriendList() {
        Long currentUserId = UserContext.getUserId();

        List<FriendGroup> dbGroups = friendGroupMapper.selectList(
                new LambdaQueryWrapper<FriendGroup>()
                        .eq(FriendGroup::getUserId, currentUserId)
                        .orderByAsc(FriendGroup::getSort));

        List<FriendVO> allFriends = this.getFriendListInternal(currentUserId);

        Map<Long, FriendGroupVO> groupMap = new LinkedHashMap<>();

        FriendGroupVO defaultGroup = new FriendGroupVO();
        defaultGroup.setGroupId(0L);
        defaultGroup.setGroupName("我的好友");
        defaultGroup.setChildren(new ArrayList<>());
        groupMap.put(0L, defaultGroup);

        if (dbGroups != null) {
            for (FriendGroup group : dbGroups) {
                FriendGroupVO vo = new FriendGroupVO();
                vo.setGroupId(group.getId());
                vo.setGroupName(group.getName());
                vo.setChildren(new ArrayList<>());
                groupMap.put(group.getId(), vo);
            }
        }

        for (FriendVO friend : allFriends) {
            Long groupId = friend.getGroupId() == null ? 0L : friend.getGroupId();
            FriendGroupVO group = groupMap.get(groupId);

            if (group == null) {
                group = defaultGroup;
            }

            group.getChildren().add(friend);
        }

        return Result.success(new ArrayList<>(groupMap.values()));
    }

    public Result<List<UserSimpleVO>> searchFriend(String keyword) {
        return userFeignClient.searchUser(keyword);
    }

    public Result<PageResult<UserSimpleVO>> searchAllFriend(String keyword, int pageNum, int pageSize) {
        return userFeignClient.searchAllUsers(keyword, pageNum, pageSize);
    }

    public Result<String> sendApply(FriendApplyDTO dto) {
        Long currentUserId = UserContext.getUserId();
        Long targetId = dto.getTargetId();
        FriendSource source = FriendSource.fromCode(dto.getSource());
        Long groupId = dto.getGroupId();

        if (source == null) {
            return Result.error("Invalid source, only SEARCH/QR/GROUP are supported");
        }

        if (currentUserId.equals(targetId)) {
            return Result.error("Do not apply to yourself");
        }

        Result<String> groupValidationResult = validateGroupBelongsToCurrentUser(currentUserId, groupId);
        if (groupValidationResult != null) {
            return groupValidationResult;
        }

        Friend relation = friendMapper.selectOne(
                new LambdaQueryWrapper<Friend>()
                        .eq(Friend::getUserId, currentUserId)
                        .eq(Friend::getFriendId, targetId));

        if (relation != null) {
            FriendStatus relationStatus = FriendStatus.fromCode(relation.getStatus());
            if (relationStatus == null
                    || relationStatus == FriendStatus.NORMAL
                    || relationStatus == FriendStatus.BLOCKED) {
                return Result.error("Friendship already exists");
            }
        }

        Friend reverseRelation = friendMapper.selectOne(
                new LambdaQueryWrapper<Friend>()
                        .eq(Friend::getUserId, targetId)
                        .eq(Friend::getFriendId, currentUserId)
                        .eq(Friend::getStatus, FriendStatus.NORMAL.getCode()));
        if (reverseRelation != null) {
            return Result.error("Friendship already exists");
        }

        FriendApply exist = friendApplyMapper.selectOne(
                new LambdaQueryWrapper<FriendApply>()
                        .eq(FriendApply::getFromId, currentUserId)
                        .eq(FriendApply::getToId, targetId)
                        .eq(FriendApply::getStatus, FriendApplyStatus.PENDING.getCode()));

        if (exist != null) {
            return Result.error("Friend apply already exists");
        }

        FriendApply apply = new FriendApply();
        apply.setFromId(currentUserId);
        apply.setToId(targetId);
        apply.setSource(source.getCode());
        apply.setMsg(dto.getMsg());
        apply.setGroupId(normalizeGroupId(groupId));
        apply.setStatus(FriendApplyStatus.PENDING.getCode());
        friendApplyMapper.insert(apply);

        return Result.success("Apply sent");
    }

    public Result<List<FriendApplyVO>> getApplyList() {
        Long currentUserId = UserContext.getUserId();

        List<FriendApply> dbApplies = friendApplyMapper.selectList(
                new LambdaQueryWrapper<FriendApply>()
                        .eq(FriendApply::getToId, currentUserId)
                        .orderByDesc(FriendApply::getId));

        if (dbApplies.isEmpty()) {
            return Result.success(Collections.emptyList());
        }

        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        List<FriendApply> visibleApplies = dbApplies.stream()
                .filter(apply -> !isExpiredRejected(apply, sevenDaysAgo))
                .toList();

        if (visibleApplies.isEmpty()) {
            return Result.success(Collections.emptyList());
        }

        List<Long> fromIds = visibleApplies.stream()
                .map(FriendApply::getFromId)
                .distinct()
                .toList();

        Result<List<UserSimpleVO>> rpcResult = userFeignClient.getUserBatch(fromIds);

        Map<String, UserSimpleVO> userMap = rpcResult.getData().stream()
                .collect(Collectors.toMap(v -> v.getId().toString(), v -> v));

        List<FriendApplyVO> vos = visibleApplies.stream().map(apply -> {
            FriendApplyVO vo = new FriendApplyVO();
            BeanUtils.copyProperties(apply, vo);

            UserSimpleVO user = userMap.get(apply.getFromId().toString());

            if (user != null) {
                vo.setNickname(user.getNickname());
                vo.setAvatar(user.getAvatar());
            }

            return vo;
        }).toList();

        return Result.success(vos);
    }

    public Result<List<FriendApplyVO>> getSentApplyList() {
        Long currentUserId = UserContext.getUserId();
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);

        List<FriendApply> dbApplies = friendApplyMapper.selectList(
                new LambdaQueryWrapper<FriendApply>()
                        .eq(FriendApply::getFromId, currentUserId)
                        .eq(FriendApply::getStatus, FriendApplyStatus.PENDING.getCode())
                        .ge(FriendApply::getCreateTime, sevenDaysAgo)
                        .orderByDesc(FriendApply::getId));

        if (dbApplies == null || dbApplies.isEmpty()) {
            return Result.success(Collections.emptyList());
        }

        List<Long> toIds = dbApplies.stream()
                .map(FriendApply::getToId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<String, UserSimpleVO> userMap = Collections.emptyMap();
        if (!toIds.isEmpty()) {
            try {
                Result<List<UserSimpleVO>> rpcResult = userFeignClient.getUserBatch(toIds);
                List<UserSimpleVO> users = rpcResult != null ? rpcResult.getData() : null;
                if (users != null) {
                    userMap = users.stream()
                            .collect(Collectors.toMap(v -> v.getId().toString(), v -> v));
                }
            } catch (Exception e) {
                log.error("Get sent apply list failed, RPC call exception", e);
            }
        }

        Map<String, UserSimpleVO> finalUserMap = userMap;
        List<FriendApplyVO> vos = dbApplies.stream().map(apply -> {
            FriendApplyVO vo = new FriendApplyVO();
            BeanUtils.copyProperties(apply, vo);

            if (apply.getToId() != null) {
                UserSimpleVO user = finalUserMap.get(apply.getToId().toString());
                if (user != null) {
                    vo.setNickname(user.getNickname());
                    vo.setAvatar(user.getAvatar());
                }
            }

            return vo;
        }).toList();

        return Result.success(vos);
    }

    @Transactional
    public int recoverExpiredIgnoredApplies() {
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);

        int updatedByUpdateTime = friendApplyMapper.update(
                null,
                new LambdaUpdateWrapper<FriendApply>()
                        .set(FriendApply::getStatus, FriendApplyStatus.PENDING.getCode())
                        .eq(FriendApply::getStatus, FriendApplyStatus.IGNORED.getCode())
                        .isNotNull(FriendApply::getUpdateTime)
                        .le(FriendApply::getUpdateTime, sevenDaysAgo));

        int updatedByCreateTime = friendApplyMapper.update(
                null,
                new LambdaUpdateWrapper<FriendApply>()
                        .set(FriendApply::getStatus, FriendApplyStatus.PENDING.getCode())
                        .eq(FriendApply::getStatus, FriendApplyStatus.IGNORED.getCode())
                        .isNull(FriendApply::getUpdateTime)
                        .le(FriendApply::getCreateTime, sevenDaysAgo));

        return updatedByUpdateTime + updatedByCreateTime;
    }

    @Transactional
    public Result<String> auditApply(FriendAuditDTO dto) {
        Long currentUserId = UserContext.getUserId();

        FriendApply apply = friendApplyMapper.selectById(dto.getApplyId());

        if (apply == null) {
            return Result.error("Apply not found");
        }

        if (!apply.getToId().equals(currentUserId)) {
            return Result.error("Do not have permission");
        }

        if (!Objects.equals(apply.getStatus(), FriendApplyStatus.PENDING.getCode())) {
            return Result.error("Apply already processed");
        }

        apply.setStatus(dto.getStatus());
        friendApplyMapper.updateById(apply);

        if (Objects.equals(dto.getStatus(), FriendApplyStatus.APPROVED.getCode())) {
            Long friendId = apply.getFromId();
            FriendSource source = FriendSource.fromCode(apply.getSource());
            if (source == null) {
                source = FriendSource.SEARCH;
            }

            saveOrUpdateFriendRelation(currentUserId, friendId, dto.getRemark(), source.getCode(), true,
                    dto.getGroupId());
            saveOrUpdateFriendRelation(friendId, currentUserId, "", source.getCode(), false,
                    normalizeGroupId(apply.getGroupId()));
        }

        return Result.success("Apply processed");
    }

    @Transactional
    public Result<String> unignoreApply(Long applyId) {
        Long currentUserId = UserContext.getUserId();

        FriendApply apply = friendApplyMapper.selectById(applyId);
        if (apply == null) {
            return Result.error("Apply not found");
        }

        if (!apply.getToId().equals(currentUserId)) {
            return Result.error("Do not have permission");
        }

        if (!Objects.equals(apply.getStatus(), FriendApplyStatus.IGNORED.getCode())) {
            return Result.error("Apply is not ignored");
        }

        apply.setStatus(FriendApplyStatus.PENDING.getCode());
        friendApplyMapper.updateById(apply);

        return Result.success("Apply restored");
    }

    @Transactional
    public Result<String> deleteFriend(Long friendId) {
        Long currentUserId = UserContext.getUserId();

        Friend relation = friendMapper.selectOne(
                new LambdaQueryWrapper<Friend>()
                        .eq(Friend::getUserId, currentUserId)
                        .eq(Friend::getFriendId, friendId));

        if (relation == null) {
            return Result.error("Friend relation not found");
        }

        friendMapper.deleteById(relation.getId());

        friendMapper.update(
                null,
                new LambdaUpdateWrapper<Friend>()
                        .set(Friend::getStatus, FriendStatus.ONE_WAY.getCode())
                        .eq(Friend::getUserId, friendId)
                        .eq(Friend::getFriendId, currentUserId)
                        .eq(Friend::getStatus, FriendStatus.NORMAL.getCode()));

        return Result.success("Friend deleted");
    }

    @Transactional
    public Result<String> updateFriendRelation(FriendRelationUpdateDTO dto) {
        Long currentUserId = UserContext.getUserId();
        if (dto == null || dto.getFriendId() == null) {
            return Result.error("Friend id cannot be empty");
        }

        Result<String> groupValidationResult = validateGroupBelongsToCurrentUser(currentUserId, dto.getGroupId());
        if (groupValidationResult != null) {
            return groupValidationResult;
        }

        Friend relation = friendMapper.selectOne(
                new LambdaQueryWrapper<Friend>()
                        .eq(Friend::getUserId, currentUserId)
                        .eq(Friend::getFriendId, dto.getFriendId())
                        .in(Friend::getStatus, FriendStatus.NORMAL.getCode(), FriendStatus.ONE_WAY.getCode()));

        if (relation == null) {
            return Result.error("Friend relation not found");
        }

        if (dto.getRemark() != null) {
            relation.setRemark(dto.getRemark().trim());
        }

        if (dto.getGroupId() != null) {
            Long normalizedGroupId = normalizeGroupId(dto.getGroupId());
            relation.setGroupId(normalizedGroupId == null ? 0L : normalizedGroupId);
        }

        friendMapper.updateById(relation);
        return Result.success("Friend relation updated");
    }

    public Result<ChatSessionConfigVO> getChatSessionConfig() {
        Long currentUserId = UserContext.getUserId();

        ChatSessionConfig config = chatSessionConfigMapper.selectOne(
                new LambdaQueryWrapper<ChatSessionConfig>()
                        .eq(ChatSessionConfig::getUserId, currentUserId)
                        .last("limit 1"));

        ChatSessionConfigVO vo = new ChatSessionConfigVO();
        if (config == null) {
            vo.setPinnedChatIds(Collections.emptyList());
            vo.setHiddenRecentChatIds(Collections.emptyList());
            return Result.success(vo);
        }

        vo.setPinnedChatIds(parseChatIdList(config.getPinnedChatIds()));
        vo.setHiddenRecentChatIds(parseChatIdList(config.getHiddenRecentChatIds()));
        return Result.success(vo);
    }

    @Transactional
    public Result<String> saveChatSessionConfig(ChatSessionConfigDTO dto) {
        Long currentUserId = UserContext.getUserId();

        List<String> pinnedChatIds = sanitizeChatIdList(dto == null ? null : dto.getPinnedChatIds());
        List<String> hiddenRecentChatIds = sanitizeChatIdList(dto == null ? null : dto.getHiddenRecentChatIds());

        String pinnedJson;
        String hiddenJson;
        try {
            pinnedJson = objectMapper.writeValueAsString(pinnedChatIds);
            hiddenJson = objectMapper.writeValueAsString(hiddenRecentChatIds);
        } catch (Exception e) {
            log.error("Serialize chat session config failed, userId={}", currentUserId, e);
            return Result.error("Save chat session config failed");
        }

        ChatSessionConfig config = chatSessionConfigMapper.selectOne(
                new LambdaQueryWrapper<ChatSessionConfig>()
                        .eq(ChatSessionConfig::getUserId, currentUserId)
                        .last("limit 1"));

        if (config == null) {
            ChatSessionConfig insert = new ChatSessionConfig();
            insert.setUserId(currentUserId);
            insert.setPinnedChatIds(pinnedJson);
            insert.setHiddenRecentChatIds(hiddenJson);
            chatSessionConfigMapper.insert(insert);
            return Result.success("Chat session config saved");
        }

        config.setPinnedChatIds(pinnedJson);
        config.setHiddenRecentChatIds(hiddenJson);
        chatSessionConfigMapper.updateById(config);
        return Result.success("Chat session config saved");
    }

    public Result<UserFullVO> getFriendInfo(Long userId) {
        Long currentUserId = UserContext.getUserId();

        // 1. 获取用户信息
        Result<UserFullVO> userResult = userFeignClient.getUserInfo(userId);
        if (userResult.getCode() != 200 || userResult.getData() == null) {
            return userResult;
        }

        UserFullVO vo = userResult.getData();

        // 2. 检查好友关系
        Long count = friendMapper.selectCount(
                new LambdaQueryWrapper<Friend>()
                        .eq(Friend::getUserId, currentUserId)
                        .eq(Friend::getFriendId, userId)
                        .eq(Friend::getStatus, FriendStatus.NORMAL.getCode()));

        vo.setIsFriend(count > 0);

        return Result.success(vo);
    }

    public Result<MutualFriendListVO> getMutualFriends(Long targetUserId, Integer limit) {
        Long currentUserId = UserContext.getUserId();
        if (targetUserId == null || Objects.equals(currentUserId, targetUserId)) {
            return Result.success(emptyMutualFriendList());
        }

        int safeLimit = limit == null ? DEFAULT_MUTUAL_LIMIT : limit;
        safeLimit = Math.max(1, Math.min(MAX_MUTUAL_LIMIT, safeLimit));

        Result<UserFullVO> targetResult = userFeignClient.getUserInfo(targetUserId);
        UserFullVO targetInfo = targetResult == null ? null : targetResult.getData();
        if (targetResult == null
                || targetResult.getCode() != 200
                || targetInfo == null
                || !Boolean.TRUE.equals(targetInfo.getPublicMutualFriend())) {
            return Result.success(emptyMutualFriendList());
        }

        Long total = friendMapper.countMutualFriendIds(
                currentUserId,
                targetUserId,
                FriendStatus.NORMAL.getCode());
        if (total == null || total <= 0) {
            return Result.success(emptyMutualFriendList());
        }

        List<Long> mutualIds = friendMapper.selectMutualFriendIds(
                currentUserId,
                targetUserId,
                FriendStatus.NORMAL.getCode(),
                safeLimit);

        List<UserSimpleVO> users = Collections.emptyList();
        if (mutualIds != null && !mutualIds.isEmpty()) {
            try {
                Result<List<UserSimpleVO>> batchResult = userFeignClient.getUserBatch(mutualIds);
                if (batchResult != null && batchResult.getCode() == 200 && batchResult.getData() != null) {
                    Map<Long, UserSimpleVO> userMap = batchResult.getData().stream()
                            .collect(Collectors.toMap(UserSimpleVO::getId, item -> item, (a, b) -> a));
                    users = mutualIds.stream()
                            .map(userMap::get)
                            .filter(Objects::nonNull)
                            .toList();
                }
            } catch (Exception e) {
                log.error("Get mutual friend details failed, currentUserId={}, targetUserId={}",
                        currentUserId, targetUserId, e);
            }
        }

        MutualFriendListVO vo = new MutualFriendListVO();
        vo.setTotal(total);
        vo.setList(users);
        return Result.success(vo);
    }

    private List<FriendVO> getFriendListInternal(Long userId) {
        List<Friend> relations = friendMapper.selectList(
                new LambdaQueryWrapper<Friend>()
                        .eq(Friend::getUserId, userId)
                        .in(Friend::getStatus, FriendStatus.NORMAL.getCode(), FriendStatus.ONE_WAY.getCode()));

        if (relations == null || relations.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> friendIds = relations.stream()
                .map(Friend::getFriendId)
                .collect(Collectors.toList());

        List<UserSimpleVO> userInfos = new ArrayList<>();
        try {
            Result<List<UserSimpleVO>> rpcResult = userFeignClient.getUserBatch(friendIds);
            if (rpcResult != null && rpcResult.getCode() == 200 && rpcResult.getData() != null) {
                userInfos = rpcResult.getData();
            }
        } catch (Exception e) {
            log.error("Get friend list failed, RPC call exception", e);
        }

        Map<String, UserSimpleVO> userMap = userInfos.stream()
                .collect(Collectors.toMap(v -> v.getId().toString(), v -> v));

        return relations.stream().map(rel -> {
            FriendVO vo = new FriendVO();
            String friendIdStr = rel.getFriendId().toString();

            vo.setId(rel.getFriendId());
            vo.setRemark(rel.getRemark());
            vo.setGroupId(rel.getGroupId() == null ? 0L : rel.getGroupId());
            vo.setStatus(rel.getStatus());

            UserSimpleVO remoteUser = userMap.get(friendIdStr);
            if (remoteUser != null) {
                vo.setNickname(remoteUser.getNickname());
                vo.setAvatar(remoteUser.getAvatar());
            } else {
                vo.setNickname("用户" + friendIdStr);
                vo.setAvatar("");
            }

            String finalName = StringUtils.hasText(vo.getRemark()) ? vo.getRemark() : vo.getNickname();
            vo.setShowName(finalName);

            return vo;
        }).collect(Collectors.toList());
    }

    private boolean isExpiredRejected(FriendApply apply, LocalDateTime time) {
        FriendApplyStatus status = FriendApplyStatus.fromCode(apply.getStatus());
        if (status == null || (status != FriendApplyStatus.REJECTED && status != FriendApplyStatus.APPROVED)) {
            return false;
        }

        LocalDateTime processedTime = apply.getUpdateTime() != null
                ? apply.getUpdateTime()
                : apply.getCreateTime();

        return processedTime != null && !processedTime.isAfter(time);
    }

    public Result<String> addGroup(FriendGroupAddDTO dto) {
        Long currentUserId = UserContext.getUserId();
        String name = dto.getName();
        if (!StringUtils.hasText(name)) {
            return Result.error("Name cannot be empty");
        }

        Long count = friendGroupMapper.selectCount(
                new LambdaQueryWrapper<FriendGroup>()
                        .eq(FriendGroup::getUserId, currentUserId)
                        .eq(FriendGroup::getName, name));
        if (count > 0) {
            return Result.error("Group name already exists");
        }

        FriendGroup lastGroup = friendGroupMapper.selectOne(
                new LambdaQueryWrapper<FriendGroup>()
                        .eq(FriendGroup::getUserId, currentUserId)
                        .orderByDesc(FriendGroup::getSort)
                        .last("limit 1"));

        int sort = lastGroup == null ? 1 : (lastGroup.getSort() == null ? 1 : lastGroup.getSort() + 1);

        FriendGroup group = new FriendGroup();
        group.setUserId(currentUserId);
        group.setName(name);
        group.setSort(sort);
        friendGroupMapper.insert(group);

        return Result.success("Group added successfully");
    }

    public Result<String> updateGroup(FriendGroupUpdateDTO dto) {
        Long currentUserId = UserContext.getUserId();
        FriendGroup group = friendGroupMapper.selectById(dto.getGroupId());
        if (group == null || !group.getUserId().equals(currentUserId)) {
            return Result.error("Group does not exist or no permission");
        }

        String name = dto.getName();
        if (!StringUtils.hasText(name)) {
            return Result.error("Name cannot be empty");
        }

        Long count = friendGroupMapper.selectCount(
                new LambdaQueryWrapper<FriendGroup>()
                        .eq(FriendGroup::getUserId, currentUserId)
                        .eq(FriendGroup::getName, name)
                        .ne(FriendGroup::getId, dto.getGroupId()));
        if (count > 0) {
            return Result.error("Group name already exists");
        }

        group.setName(name);
        friendGroupMapper.updateById(group);

        return Result.success("Group updated successfully");
    }

    @Transactional
    public Result<String> deleteGroup(Long groupId) {
        Long currentUserId = UserContext.getUserId();
        FriendGroup group = friendGroupMapper.selectById(groupId);
        if (group == null || !group.getUserId().equals(currentUserId)) {
            return Result.error("Group does not exist or no permission");
        }

        friendGroupMapper.deleteById(groupId);

        Friend update = new Friend();
        update.setGroupId(0L);
        friendMapper.update(update, new LambdaUpdateWrapper<Friend>()
                .eq(Friend::getUserId, currentUserId)
                .eq(Friend::getGroupId, groupId));

        return Result.success("Group deleted successfully");
    }

    @Transactional
    public Result<String> sortGroups(FriendGroupSortDTO dto) {
        Long currentUserId = UserContext.getUserId();
        List<Long> groupIds = dto.getGroupIds();
        if (groupIds == null || groupIds.isEmpty()) {
            return Result.success("No need to sort");
        }

        for (int i = 0; i < groupIds.size(); i++) {
            Long groupId = groupIds.get(i);
            FriendGroup group = friendGroupMapper.selectById(groupId);
            if (group != null && group.getUserId().equals(currentUserId)) {
                group.setSort(i);
                friendGroupMapper.updateById(group);
            }
        }
        return Result.success("Sorting completed successfully");
    }

    private Result<String> validateGroupBelongsToCurrentUser(Long currentUserId, Long groupId) {
        Long normalizedGroupId = normalizeGroupId(groupId);
        if (normalizedGroupId == null) {
            return null;
        }

        FriendGroup group = friendGroupMapper.selectById(normalizedGroupId);
        if (group == null || !Objects.equals(group.getUserId(), currentUserId)) {
            return Result.error("Invalid group id");
        }
        return null;
    }

    private Long normalizeGroupId(Long groupId) {
        if (groupId == null || groupId <= 0L) {
            return null;
        }
        return groupId;
    }

    private void saveOrUpdateFriendRelation(Long userId,
            Long friendId,
            String remark,
            String source,
            boolean overwriteRemark,
            Long groupId) {
        Friend relation = friendMapper.selectOne(
                new LambdaQueryWrapper<Friend>()
                        .eq(Friend::getUserId, userId)
                        .eq(Friend::getFriendId, friendId));

        if (relation == null) {
            Friend insert = new Friend();
            insert.setUserId(userId);
            insert.setFriendId(friendId);
            insert.setRemark(remark);
            insert.setStatus(FriendStatus.NORMAL.getCode());
            insert.setSource(source);
            if (groupId != null) {
                insert.setGroupId(groupId);
            }
            friendMapper.insert(insert);
            return;
        }

        relation.setStatus(FriendStatus.NORMAL.getCode());
        relation.setSource(source);
        if (overwriteRemark) {
            relation.setRemark(remark);
        }
        if (groupId != null) {
            relation.setGroupId(groupId);
        }
        friendMapper.updateById(relation);
    }

    private List<String> parseChatIdList(String rawJson) {
        if (!StringUtils.hasText(rawJson)) {
            return Collections.emptyList();
        }
        try {
            List<String> list = objectMapper.readValue(rawJson, new TypeReference<>() {
            });
            return sanitizeChatIdList(list);
        } catch (Exception e) {
            log.warn("Parse chat session config failed, raw={}", rawJson, e);
            return Collections.emptyList();
        }
    }

    private List<String> sanitizeChatIdList(List<String> rawList) {
        if (rawList == null || rawList.isEmpty()) {
            return Collections.emptyList();
        }

        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String chatId : rawList) {
            if (!StringUtils.hasText(chatId)) {
                continue;
            }
            normalized.add(chatId.trim());
            if (normalized.size() >= MAX_CHAT_ID_LIST_SIZE) {
                break;
            }
        }
        return new ArrayList<>(normalized);
    }

    private MutualFriendListVO emptyMutualFriendList() {
        MutualFriendListVO vo = new MutualFriendListVO();
        vo.setTotal(0L);
        vo.setList(Collections.emptyList());
        return vo;
    }
}
