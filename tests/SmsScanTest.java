import com.chad.smsmanager.*;
import java.util.*;
public class SmsScanTest {
    static int count;static void eq(long a,long b){count++;if(a!=b)throw new AssertionError(a+" != "+b);}
    static SmsSearch.Query all(){return new SmsSearch.Query("","",true,null,null);}
    static SmsScan.Result scan(int scope,int page,SmsSearch.Query facet){SmsScan scan=new SmsScan(all(),facet,Arrays.asList("106","1069"),scope,page);for(int i=0;i<203;i++)scan.accept(new SmsRecord(i,"1069001",i%2==0?"【品牌】内容":"无签名",i,i%3+1));return scan.finish();}
    public static void main(String[] args){
        SmsScan.Result first=scan(0,0,all());eq(203,first.read);eq(203,first.ids.length);eq(100,first.visible.size());eq(203,first.statistics.matched);
        SmsScan.Result second=scan(0,1,all());eq(203,second.ids.length);eq(100,second.visible.size());eq(100,second.visible.get(0).id);
        SmsScan.Result last=scan(0,2,all());eq(3,last.visible.size());eq(200,last.visible.get(0).id);
        SmsScan.Result inbox=scan(1,0,all());eq(203,inbox.read);eq(68,inbox.ids.length);eq(68,inbox.statistics.matched);
        SmsScan.Result sent=scan(2,0,all());eq(68,sent.ids.length);
        SmsScan.Result brand=scan(0,0,new SmsSearch.Query("","",true,null,"品牌"));eq(102,brand.ids.length);eq(203,brand.statistics.matched);eq(100,brand.visible.size());
        long brandBucket=0;for(SmsSearch.Bucket b:brand.statistics.signatures)if(b.key.equals("品牌"))brandBucket=b.count;eq(102,brandBucket);
        SmsSelection selection=new SmsSelection();selection.replace(first.ids);eq(203,selection.size());selection.replace(SmsSelection.intersect(selection.snapshot(),brand.ids));eq(102,selection.size());
        SmsScan.Result empty=scan(0,0,new SmsSearch.Query("","999",true,null,null));eq(0,empty.ids.length);eq(0,empty.visible.size());eq(203,empty.statistics.matched);
        System.out.println(count+" scan/pagination/scope integration assertions passed");
    }
}
