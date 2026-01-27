package org.foreverempty.common.constant;

public enum ContentType {
    TEXT(1),    // 文本/表情
    IMAGE(2),   // 图片
    VIDEO(3),   // 视频
    FILE(4);    // 文件

    private final int code;
    ContentType(int code) { this.code = code; }
    public int getCode() { return code; }
}
