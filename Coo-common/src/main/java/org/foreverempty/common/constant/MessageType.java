package org.foreverempty.common.constant;

public enum MessageType {
    CHAT,       // 普通聊天消息
    RECALL,     // 撤回通知
    PING,       // 客户端心跳
    PONG,       // 服务端响应
    ACK,        // 消息确认
    SYSTEM      // 系统通知
}
