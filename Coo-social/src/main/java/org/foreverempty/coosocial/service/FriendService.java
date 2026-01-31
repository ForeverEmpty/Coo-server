package org.foreverempty.coosocial.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.foreverempty.common.PageResult;
import org.foreverempty.common.Result;
import org.foreverempty.common.context.UserContext;
import org.foreverempty.common.vo.UserSimpleVO;
import org.foreverempty.coosocial.dto.FriendApplyDTO;
import org.foreverempty.coosocial.dto.FriendAuditDTO;
import org.foreverempty.coosocial.entity.Friend;
import org.foreverempty.coosocial.entity.FriendApply;
import org.foreverempty.coosocial.entity.FriendGroup;
import org.foreverempty.coosocial.feign.UserFeignClient;
import org.foreverempty.coosocial.mapper.FriendApplyMapper;
import org.foreverempty.coosocial.mapper.FriendGroupMapper;
import org.foreverempty.coosocial.mapper.FriendMapper;
import org.foreverempty.coosocial.vo.FriendApplyVO;
import org.foreverempty.coosocial.vo.FriendGroupVO;
import org.foreverempty.coosocial.vo.FriendVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FriendService {

    @Autowired
    private FriendMapper friendMapper;

    @Autowired
    private FriendGroupMapper friendGroupMapper;

    @Autowired
    private FriendApplyMapper friendApplyMapper;

    @Autowired
    private UserFeignClient userFeignClient;

    public Result<List<FriendGroupVO>> getFriendList() {
        Long currentUserId = UserContext.getUserId();

        List<FriendGroup> dbGroups = friendGroupMapper.selectList(
                new LambdaQueryWrapper<FriendGroup>()
                        .eq(FriendGroup::getUserId, currentUserId)
                        .orderByAsc(FriendGroup::getSort)
        );

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

        if (currentUserId.equals(targetId)) {
            return Result.error("Do not apply to yourself");
        }

        Long count = friendMapper.selectCount(
                new LambdaQueryWrapper<Friend>()
                        .eq(Friend::getUserId, currentUserId)
                        .eq(Friend::getFriendId, targetId)
        );

        if (count > 0) {
            return Result.error("Friendship already exists");
        }

        FriendApply exist = friendApplyMapper.selectOne(
                new LambdaQueryWrapper<FriendApply>()
                        .eq(FriendApply::getFromId, currentUserId)
                        .eq(FriendApply::getToId, targetId)
                        .eq(FriendApply::getStatus, 0)
        );

        if (exist != null) {
            return Result.error("Friend apply already exists");
        }

        FriendApply apply = new FriendApply();
        apply.setFromId(currentUserId);
        apply.setToId(targetId);
        apply.setMsg(dto.getMsg());
        apply.setStatus(0);
        friendApplyMapper.insert(apply);

        return Result.success("Apply sent");
    }

    public Result<List<FriendApplyVO>> getApplyList() {
        Long currentUserId = UserContext.getUserId();

        List<FriendApply> dbApplies = friendApplyMapper.selectList(
                new LambdaQueryWrapper<FriendApply>()
                        .eq(FriendApply::getToId, currentUserId)
                        .orderByDesc(FriendApply::getId)
        );

        if (dbApplies.isEmpty()) {
            return Result.success(Collections.emptyList());
        }

        List<Long> fromIds = dbApplies.stream()
                .map(FriendApply::getFromId)
                .distinct()
                .toList();

        Result<List<UserSimpleVO>> rpcResult = userFeignClient.getUserBatch(fromIds);

        Map<String, UserSimpleVO> userMap = rpcResult.getData().stream()
                .collect(Collectors.toMap(v -> v.getId().toString(), v -> v));

        List<FriendApplyVO> vos = dbApplies.stream().map(apply -> {
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

        if (apply.getStatus() != 0) {
            return Result.error("Apply already processed");
        }

        apply.setStatus(dto.getStatus());
        friendApplyMapper.updateById(apply);

        if (dto.getStatus() == 1) {
            Long friendId = apply.getFromId();

            Friend f1 = new Friend();
            f1.setUserId(currentUserId);
            f1.setFriendId(friendId);
            f1.setRemark(dto.getRemark());
            f1.setStatus(1);
            f1.setSource("APPLY");
            friendMapper.insert(f1);

            Friend f2 = new Friend();
            f2.setUserId(friendId);
            f2.setFriendId(currentUserId);
            f2.setRemark("");
            f2.setStatus(1);
            f2.setSource("APPLY");
            friendMapper.insert(f2);
        }

        return Result.success("Apply processed");
    }

    private List<FriendVO> getFriendListInternal(Long userId) {
        List<Friend> relations = friendMapper.selectList(
                new LambdaQueryWrapper<Friend>()
                        .eq(Friend::getUserId, userId)
                        .eq(Friend::getStatus, 1)
        );

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
}
