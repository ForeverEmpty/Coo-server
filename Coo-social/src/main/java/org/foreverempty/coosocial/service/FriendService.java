package org.foreverempty.coosocial.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.foreverempty.common.Result;
import org.foreverempty.common.context.UserContext;
import org.foreverempty.common.vo.UserSimpleVO;
import org.foreverempty.coosocial.entity.Friend;
import org.foreverempty.coosocial.feign.UserFeignClient;
import org.foreverempty.coosocial.mapper.FriendMapper;
import org.foreverempty.coosocial.vo.FriendVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class FriendService {

    @Autowired
    private FriendMapper friendMapper;

    @Autowired
    private UserFeignClient userFeignClient;

    public Result<List<FriendVO>> getFriendList() {
        Long currentUserId = UserContext.getUserId();

        List<Friend> relations = friendMapper.selectList(
                new LambdaQueryWrapper<Friend>()
                        .eq(Friend::getUserId, currentUserId)
                        .eq(Friend::getStatus, 1)
        );

        if (relations.isEmpty()) {
            return Result.success(Collections.emptyList());
        }

        List<Long> friendIds = relations.stream()
                .map(Friend::getFriendId)
                .toList();

        Result<List<UserSimpleVO>> rpcResult = userFeignClient.getUserBatch(friendIds);
        List<UserSimpleVO> userInfos = rpcResult.getData();

        Map<String, UserSimpleVO> userMap = userInfos.stream()
                .collect(
                        Collectors.toMap(
                                UserSimpleVO::getId,
                                user -> user
                        )
                );

        List<FriendVO> voList = relations.stream()
                .map(relation -> {
                    FriendVO vo = new FriendVO();
                    vo.setId(relation.getFriendId().toString());
                    vo.setRemark(relation.getRemark());

                    UserSimpleVO user = userMap.get(relation.getFriendId().toString());

                    if (user != null) {
                        vo.setNickname(user.getNickname());
                        vo.setAvatar(user.getAvatar());
                    }

                    vo.setShowName(StringUtils.hasText(vo.getRemark()) ? vo.getRemark() : vo.getNickname());

                    return vo;
                })
                .toList();

                return Result.success(voList);
    }
}
