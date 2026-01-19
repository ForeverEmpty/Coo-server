package org.foreverempty.cooauth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.foreverempty.common.Result;
import org.foreverempty.common.context.UserContext;
import org.foreverempty.common.utils.JwtUtils;
import org.foreverempty.cooauth.entity.User;
import org.foreverempty.cooauth.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    @Autowired
    private UserMapper userMapper;

    private final BCryptPasswordEncoder bCryptPasswordEncoder = new BCryptPasswordEncoder();

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

    public Result<User> getMyInfo() {
        Long userId = UserContext.getUserId();

        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error("User not found");
        }

        user.setPassword(null);
        return Result.success(user);
    }
}
