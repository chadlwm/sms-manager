package com.chad.smsmanager;

/** Pure decision logic: callback success alone never authorizes deletion. */
public final class DefaultSmsPolicy {
    public enum Status { HELD, NOT_HELD, UNKNOWN, INCONSISTENT }
    private DefaultSmsPolicy() {}
    public static Status classify(Boolean roleHeld, String ownPackage, String defaultPackage) {
        if (Boolean.TRUE.equals(roleHeld)) return defaultPackage==null || ownPackage.equals(defaultPackage) ? Status.HELD : Status.INCONSISTENT;
        boolean packageMatches=ownPackage.equals(defaultPackage);
        if (Boolean.FALSE.equals(roleHeld)) return packageMatches ? Status.INCONSISTENT : Status.NOT_HELD;
        if (packageMatches) return Status.HELD;
        return defaultPackage==null ? Status.UNKNOWN : Status.NOT_HELD;
    }
}
