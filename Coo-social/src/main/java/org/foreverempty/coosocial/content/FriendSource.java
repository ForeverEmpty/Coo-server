package org.foreverempty.coosocial.content;

import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Locale;

public enum FriendSource {
    SEARCH("SEARCH"),
    QR("QR"),
    GROUP("GROUP");

    private final String code;

    FriendSource(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static FriendSource fromCode(String code) {
        if (!StringUtils.hasText(code)) {
            return SEARCH;
        }

        String normalized = code.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(item -> item.code.equals(normalized))
                .findFirst()
                .orElse(null);
    }
}
