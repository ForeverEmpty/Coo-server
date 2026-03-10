package org.foreverempty.coosocial.content;

public enum GroupInviteAuditMode {
    NONE(0),
    INVITE_ONLY(1),
    APPLY_ONLY(2),
    ALL(3);

    private final int code;

    GroupInviteAuditMode(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public boolean requiresInviteAudit() {
        return this == INVITE_ONLY || this == ALL;
    }

    public boolean requiresApplyAudit() {
        return this == APPLY_ONLY || this == ALL;
    }

    public static GroupInviteAuditMode fromCode(Integer code) {
        if (code == null) {
            return NONE;
        }
        for (GroupInviteAuditMode mode : values()) {
            if (mode.code == code) {
                return mode;
            }
        }
        return NONE;
    }
}
