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
    public static String formatDetailed(String state, boolean running, int total, int processed, int removed, int missing, int failed, int unknown, long pending, String result) {
        if (state.isEmpty()) return "";
        String text = "计划 " + total + " 条 · 已处理 " + processed + " 条\n已核验删除 " + removed + " 条 · 原已不存在 " + missing + " 条\n删除失败 " + failed + " 条 · 结果未确认 " + unknown + " 条 · 未处理 " + Math.max(0, total - processed) + " 条\n";
        if (state.equals("running")) text += running ? "正在逐条删除并核验，请勿重复操作。" : "任务被中断，不会自动重试。请重新查询核对。";
        else text += result;
        if (pending != -1) text += "\n有一条操作结果尚未完整落盘或无法确认，实际删除数可能更多。";
        return text + "\n清理后请恢复原默认短信应用。";
    }
}
