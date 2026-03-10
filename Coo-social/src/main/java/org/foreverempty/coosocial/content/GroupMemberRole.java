package org.foreverempty.coosocial.content;

public enum GroupMemberRole {
    OWNER(1),
    SUPER_ADMIN(2),
    MEMBER(3);

    private final int code;

    GroupMemberRole(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static GroupMemberRole fromCode(Integer code) {
        if (code == null) {
            return MEMBER;
        }
        for (GroupMemberRole role : values()) {
            if (role.code == code) {
                return role;
            }
        }
        return MEMBER;
    }
}
