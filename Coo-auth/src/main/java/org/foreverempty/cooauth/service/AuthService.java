package org.foreverempty.cooauth.service;

import com.alibaba.cloud.commons.lang.StringUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.foreverempty.common.Result;
import org.foreverempty.common.context.UserContext;
import org.foreverempty.common.utils.JwtUtils;
import org.foreverempty.common.utils.PrivacyUtils;
import org.foreverempty.common.utils.RandomNickName;
import org.foreverempty.common.vo.UserSimpleVO;
import org.foreverempty.cooauth.dto.PrivacyUpdateDTO;
import org.foreverempty.cooauth.dto.UpdateProfileDTO;
import org.foreverempty.cooauth.entity.User;
import org.foreverempty.cooauth.entity.UserInfo;
import org.foreverempty.cooauth.mapper.UserInfoMapper;
import org.foreverempty.cooauth.mapper.UserMapper;
import org.foreverempty.common.vo.UserFullVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AuthService {
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private UserInfoMapper userInfoMapper;

    private final BCryptPasswordEncoder bCryptPasswordEncoder = new BCryptPasswordEncoder();

    @Transactional
    public Result<String> register(String username, String password) {
        User existUser = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username)
        );

        if (existUser != null) {
            return Result.error("Username is exist");
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(bCryptPasswordEncoder.encode(password));
        user.setNickname(RandomNickName.getRandomNickName());
        user.setStatus(1);

        int rows = userMapper.insert(user);
        if (rows < 0) {
            return Result.error("Register failed");
        }

        UserInfo userInfo = new UserInfo();
        userInfo.setUserId(user.getId());
        userInfo.setSignature("这个家伙很懒，什么都没写");
        userInfoMapper.insert(userInfo);

        return Result.success("Register succeed");
    }

    public Result<String> login(String username, String password) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username)
        );

        if (user == null || !bCryptPasswordEncoder.matches(password, user.getPassword())) {
            return Result.error("Username or Password is incorrect");
        }

        String token = JwtUtils.createToken(user.getId());

        return Result.success(token);
    }

    public Result<UserFullVO> getMyInfo() {
        Long userId = UserContext.getUserId();

        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error("User not found");
        }

        return getUserInfo(userId);
    }

    public Result<UserFullVO> getUserInfo(Long userId) {
        Long currentUserId = UserContext.getUserId();

        User user = userMapper.selectById(userId);
        UserInfo info = userInfoMapper.selectById(userId);

        if (user == null) return Result.error("User not found");

        UserFullVO vo = new UserFullVO();
        BeanUtils.copyProperties(user, vo);

        if (info != null) {
            BeanUtils.copyProperties(info, vo);
        }

        boolean isMe = userId.equals(currentUserId);
        vo.setIsMe(isMe);

        if (!isMe && info != null) {
            PrivacyUtils.applyPrivacy(vo, info);
        }

        return Result.success(vo);
    }

    public Result<String> updateAvatar(String url) {
        Long userId = UserContext.getUserId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error("User not found");
        }

        user.setAvatar(url);

        int rows = userMapper.updateById(user);
        if (rows < 0) {
            return Result.error("Update Avatar failed");
        }

        log.info("User {} Update Avatar: {}", userId, url);
        return Result.success("Update Avatar succeed");
    }

    public Result<String> updateBackground(String url) {
        Long userId = UserContext.getUserId();
        UserInfo info = userInfoMapper.selectById(userId);
        if (info == null) {
            return Result.error("User Info not found");
        }

        info.setBackground(url);

        int rows = userInfoMapper.updateById(info);
        if (rows < 0) {
            return Result.error("Update Background failed");
        }

        log.info("User {} Update Background: {}", userId, url);
        return Result.success("Update Background succeed");
    }

    @Transactional
    public Result<String> updatePrivacy(PrivacyUpdateDTO dto) {
        Long userId = UserContext.getUserId();

        UserInfo info = new UserInfo();
        info.setUserId(userId);

        BeanUtils.copyProperties(dto, info);

        int rows = userInfoMapper.updateById(info);

        if (rows < 0) {
            return Result.error("Update Privacy failed");
        }

        log.info("User {} Update Privacy: {}", userId, dto);
        return Result.success("Update Privacy succeed");
    }

    @Transactional
    public Result<String> updateProfile(UpdateProfileDTO dto) {
        Long userId = UserContext.getUserId();

        if (dto.getNickname() != null) {
            User user = new User();
            user.setId(userId);
            user.setNickname(dto.getNickname());
            userMapper.updateById(user);
        }

        UserInfo info = new UserInfo();
        info.setUserId(userId);
        BeanUtils.copyProperties(dto, info);

        int rows = userInfoMapper.updateById(info);

        if (rows == 0){
            userInfoMapper.insert(info);
        }

        if (rows < 0) {
            return Result.error("Update Profile failed");
        }

        log.info("User {} Update Profile: {}", userId, dto);
        return Result.success("Update Profile succeed");
    }

    public Result<List<UserSimpleVO>> getUserBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Result.success(Collections.emptyList());
        }

        List<User> users = userMapper.selectBatchIds(ids);

        List<UserSimpleVO> vos = users.stream().map(u -> {
            UserSimpleVO vo = new UserSimpleVO();
            BeanUtils.copyProperties(u, vo);
            vo.setId(u.getId().toString());
            return vo;
        }).collect(Collectors.toList());

        return Result.success(vos);
    }

    public Result<List<UserSimpleVO>> searchUser(String keyword) {
        if (StringUtils.isBlank(keyword)) {
            return Result.success(Collections.emptyList());
        }

        List<User> users = userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .like(User::getUsername, keyword)
                        .or()
                        .like(User::getNickname, keyword)
                        .last("limit 20")
        );

        List<UserSimpleVO> vos = users.stream().map(u -> new UserSimpleVO(
                        u.getId().toString(),
                        u.getUsername(),
                        u.getNickname(),
                        u.getAvatar()
        )).toList();

        return Result.success(vos);
    }
}
