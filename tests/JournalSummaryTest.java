import com.chad.smsmanager.JournalSummary;
public class JournalSummaryTest {
    static void has(String text, String part) { if (!text.contains(part)) throw new AssertionError(text); }
    public static void main(String[] args) {
        if (!JournalSummary.format("",false,0,0,-1,"").isEmpty()) throw new AssertionError();
        has(JournalSummary.format("running",true,8,3,-1,""),"仍在处理");
        String interrupted=JournalSummary.format("running",false,8,3,14,"");
        has(interrupted,"任务被中断，未自动重试"); has(interrupted,"已确认删除 3 条"); has(interrupted,"操作结果未确认");
        has(JournalSummary.format("finished",false,8,8,-1,"清理完成。"),"清理完成");
        has(JournalSummary.format("finished",false,8,2,-1,"飞行模式已关闭，已停止"),"已停止");
        System.out.println("7 journal summary assertions passed");
    }
}
