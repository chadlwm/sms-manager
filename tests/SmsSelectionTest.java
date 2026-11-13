import com.chad.smsmanager.SmsSelection;
import java.util.Arrays;
public class SmsSelectionTest {
    static int count;static void eq(long[] expected,long[] got){count++;if(!Arrays.equals(expected,got))throw new AssertionError(Arrays.toString(got));}
    public static void main(String[] args){
        SmsSelection s=new SmsSelection();eq(new long[0],s.snapshot());s.replace(new long[]{7,3,7,2});eq(new long[]{2,3,7},s.snapshot());
        s.toggle(3,false);eq(new long[]{2,7},s.snapshot());s.toggle(5,true);eq(new long[]{2,5,7},s.snapshot());s.toggle(5,true);eq(new long[]{2,5,7},s.snapshot());
        eq(new long[]{2,7},SmsSelection.intersect(s.snapshot(),new long[]{9,7,2})); // new ID 9 is not silently selected
        long[] clone=s.snapshot();clone[0]=99;eq(new long[]{2,5,7},s.snapshot());s.clear();eq(new long[0],s.snapshot());
        long[] huge=new long[100000];for(int i=0;i<huge.length;i++)huge[i]=huge.length-i;s.replace(huge);
        if(s.size()!=100000||!s.contains(1)||!s.contains(100000)||s.contains(100001))throw new AssertionError();count++;
        s.toggle(50000,false);if(s.size()!=99999||s.contains(50000))throw new AssertionError();count++;
        System.out.println(count+" selection assertions passed (including 100k IDs)");
    }
}
