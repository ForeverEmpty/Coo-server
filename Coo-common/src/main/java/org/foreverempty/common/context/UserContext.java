package org.foreverempty.common.context;

public class UserContext {
    private static final ThreadLocal<Long> THREAD_LOCAL = new ThreadLocal<>();

    public static void setUserId(Long userId) {
        THREAD_LOCAL.set(userId);
    }

    public static Long getUserId() {
        return THREAD_LOCAL.get();
    }

    public static void remove() {
        THREAD_LOCAL.remove();
    }
}
