import com.chad.smsmanager.Filters;
public class FiltersTest {
    static void eq(String expected,String actual){if(!expected.equals(actual))throw new AssertionError(expected+" != "+actual);}
    public static void main(String[] args){
        eq("%验证码%",Filters.pattern(" 验证码 "));
        eq("%%",Filters.pattern(""));
        eq("%100\\%%",Filters.pattern("100%"));
        eq("%a\\_b%",Filters.pattern("a_b"));
        eq("%a\\\\b%",Filters.pattern("a\\b"));
        eq("%O'Reilly%",Filters.pattern("O'Reilly"));
        System.out.println("6 filter tests passed");
    }
}
