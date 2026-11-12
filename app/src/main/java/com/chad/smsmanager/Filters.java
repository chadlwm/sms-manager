package com.chad.smsmanager;
public final class Filters {
    private Filters() {}
    public static String pattern(String input) { return "%" + input.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"; }
}
