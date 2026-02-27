package org.foreverempty.coosocial.content;

import java.util.Arrays;

public enum FriendStatus {
    NORMAL(1),
    BLOCKED(2),
    ONE_WAY(3);

    private final int code;

    FriendStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static FriendStatus fromCode(Integer code) {
        if (code == null) {
            return null;
        }

        return Arrays.stream(values())
                .filter(item -> item.code == code)
                .findFirst()
                .orElse(null);
    }
}
