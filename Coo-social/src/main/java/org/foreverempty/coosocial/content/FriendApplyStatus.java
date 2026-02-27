package org.foreverempty.coosocial.content;

import java.util.Arrays;

public enum FriendApplyStatus {
    PENDING(0),
    APPROVED(1),
    REJECTED(2),
    IGNORED(3);

    private final int code;

    FriendApplyStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static FriendApplyStatus fromCode(Integer code) {
        if (code == null) {
            return null;
        }

        return Arrays.stream(values())
                .filter(item -> item.code == code)
                .findFirst()
                .orElse(null);
    }
}
