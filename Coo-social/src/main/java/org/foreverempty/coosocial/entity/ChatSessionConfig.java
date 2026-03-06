package org.foreverempty.coosocial.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("u_chat_session_config")
public class ChatSessionConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String pinnedChatIds;
    private String hiddenRecentChatIds;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
