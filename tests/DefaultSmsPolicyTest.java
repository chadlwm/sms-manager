import com.chad.smsmanager.DefaultSmsPolicy;
import static com.chad.smsmanager.DefaultSmsPolicy.Status.*;
public class DefaultSmsPolicyTest {
    static int count;
    static void check(DefaultSmsPolicy.Status want,Boolean role,String current){count++;if(DefaultSmsPolicy.classify(role,"self",current)!=want)throw new AssertionError("role="+role+" current="+current);}
    public static void main(String[] args){
        check(HELD,true,"self");check(HELD,true,null);check(INCONSISTENT,true,"other");
        check(NOT_HELD,false,"other");check(NOT_HELD,false,null);check(INCONSISTENT,false,"self");
        check(HELD,null,"self");check(NOT_HELD,null,"other");check(UNKNOWN,null,null);
        System.out.println(count+" default SMS policy cases passed");
    }
}
