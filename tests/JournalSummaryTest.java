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
        has(JournalSummary.formatDetailed("finished",false,10,1,0,0,1,0,-1,"系统删除返回 0，但短信仍存在"),"删除失败 1 条");
        has(JournalSummary.formatDetailed("finished",false,10,1,0,0,1,0,-1,"系统删除返回 0，但短信仍存在"),"未处理 9 条");
        has(JournalSummary.formatDetailed("running",false,10,1,1,0,0,0,42,""),"任务被中断");
        has(JournalSummary.formatDetailed("finished",false,10,1,0,0,0,1,42,"核验失败"),"结果未确认 1 条");
        System.out.println("11 journal summary assertions passed");
    }
}
