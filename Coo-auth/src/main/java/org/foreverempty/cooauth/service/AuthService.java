package org.foreverempty.cooauth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.foreverempty.common.Result;
import org.foreverempty.common.context.UserContext;
import org.foreverempty.common.utils.JwtUtils;
import org.foreverempty.common.utils.PrivacyUtils;
import org.foreverempty.cooauth.entity.User;
import org.foreverempty.cooauth.entity.UserInfo;
import org.foreverempty.cooauth.mapper.UserInfoMapper;
import org.foreverempty.cooauth.mapper.UserMapper;
import org.foreverempty.cooauth.vo.UserFullVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
