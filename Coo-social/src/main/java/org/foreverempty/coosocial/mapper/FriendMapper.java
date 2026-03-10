package org.foreverempty.coosocial.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Mapper;
import org.foreverempty.coosocial.entity.Friend;

import java.util.List;

@Mapper
public interface FriendMapper extends BaseMapper<Friend> {
    @Select("""
            SELECT COUNT(1)
            FROM u_friend f1
            INNER JOIN u_friend f2 ON f1.friend_id = f2.friend_id
            WHERE f1.user_id = #{currentUserId}
              AND f2.user_id = #{targetUserId}
              AND f1.status = #{status}
              AND f2.status = #{status}
              AND f1.friend_id <> #{currentUserId}
              AND f1.friend_id <> #{targetUserId}
            """)
    Long countMutualFriendIds(@Param("currentUserId") Long currentUserId,
                              @Param("targetUserId") Long targetUserId,
                              @Param("status") Integer status);

    @Select("""
            SELECT f1.friend_id
            FROM u_friend f1
            INNER JOIN u_friend f2 ON f1.friend_id = f2.friend_id
            WHERE f1.user_id = #{currentUserId}
              AND f2.user_id = #{targetUserId}
              AND f1.status = #{status}
              AND f2.status = #{status}
              AND f1.friend_id <> #{currentUserId}
              AND f1.friend_id <> #{targetUserId}
            ORDER BY f1.create_time DESC, f1.id DESC
            LIMIT #{limit}
            """)
    List<Long> selectMutualFriendIds(@Param("currentUserId") Long currentUserId,
                                     @Param("targetUserId") Long targetUserId,
                                     @Param("status") Integer status,
                                     @Param("limit") Integer limit);
}
