package org.foreverempty.cooauth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.foreverempty.cooauth.entity.User;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
