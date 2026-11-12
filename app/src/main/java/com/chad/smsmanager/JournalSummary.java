package com.chad.smsmanager;
/** Pure formatting logic so interrupted/ambiguous outcomes are regression-tested. */
public final class JournalSummary {
    private JournalSummary() {}
    public static String format(String state, boolean running, int total, int count, long pending, String result) {
        if (state.isEmpty()) return "";
        String text = "上次清理：计划 " + total + " 条，已确认删除 " + count + " 条。";
        if (state.equals("running")) text += running ? "仍在处理，请勿重复操作。" : "任务被中断，未自动重试。";
        else text += result;
        if (pending != -1) text += "有一条操作结果未确认，实际删除数可能更多；请重新查询核对。";
        return text;
    }
}
