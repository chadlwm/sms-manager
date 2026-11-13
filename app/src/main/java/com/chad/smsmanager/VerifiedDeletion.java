package com.chad.smsmanager;

/** Device-independent deletion protocol. A provider row count alone is never proof. */
public final class VerifiedDeletion {
    public enum Kind { DELETED, ALREADY_ABSENT, FAILED, UNCONFIRMED }
    public static final class Result {
        public final Kind kind;
        public final String detail;
        Result(Kind kind, String detail) { this.kind = kind; this.detail = detail; }
    }
    public interface Store {
        boolean exists(long id) throws Exception;
        int deleteItem(long id) throws Exception;
        int deleteSelected(long id) throws Exception;
    }
    public interface Guard { boolean mayDelete(); }
    private VerifiedDeletion() {}
    public static Result run(Store store, Guard guard, long id) {
        try {
            if (!store.exists(id)) return new Result(Kind.ALREADY_ABSENT, "所选短信已不存在，未重复删除。");
        } catch (Exception e) {
            return new Result(Kind.FAILED, "删除前无法读取短信，未执行删除（" + e.getClass().getSimpleName() + "）。请检查短信读取权限。");
        }
        if (!guard.mayDelete()) return new Result(Kind.FAILED, "操作条件已改变，未执行删除。");
        int affected = -1;
        String error = "";
        try { affected = store.deleteItem(id); }
        catch (Exception e) { error = e.getClass().getSimpleName(); }
        final boolean stillExists;
        try { stillExists = store.exists(id); }
        catch (Exception e) { return unconfirmed(e); }
        if (!stillExists) return new Result(Kind.DELETED, "已重新查询确认短信不在系统短信库中。");
        // Retry only a known no-op, scoped to the same ID. Never broaden to a conversation.
        if (affected == 0 && error.isEmpty() && guard.mayDelete()) {
            try { affected = store.deleteSelected(id); }
            catch (Exception e) { error = e.getClass().getSimpleName(); }
            try { if (!store.exists(id)) return new Result(Kind.DELETED, "兼容路径删除后已确认短信不在系统短信库中。"); }
            catch (Exception e) { return unconfirmed(e); }
        }
        String reason = error.isEmpty() ? "系统删除返回 " + affected + "，但重新查询后短信仍存在。" : "系统拒绝或未完成删除（" + error + "），重新查询后短信仍存在。";
        return new Result(Kind.FAILED, reason + "已停止后续删除。请检查本应用是否仍为默认短信应用，以及 ColorOS 短信/隐私权限；可能受系统限制，尚不能确定具体原因。");
    }
    private static Result unconfirmed(Exception e) {
        return new Result(Kind.UNCONFIRMED, "删除后的查询失败（" + e.getClass().getSimpleName() + "），无法确认是否已删除。已停止后续操作，不会自动重试；请重新查询核对。");
    }
}
