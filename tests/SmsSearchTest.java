import com.chad.smsmanager.SmsSearch;
import com.chad.smsmanager.SmsRecord;
import java.util.*;

public final class SmsSearchTest {
    static int assertions;
    static void eq(Object expected,Object actual){assertions++;if(!Objects.equals(expected,actual))throw new AssertionError("Expected "+expected+" but got "+actual);}
    static void yes(boolean value){assertions++;if(!value)throw new AssertionError("Expected true");}
    static SmsRecord row(long id,String address,String body){return new SmsRecord(id,address,body,id,1);}
    static SmsSearch.Query query(String keyword,String sender,boolean exact,String prefix,String signature){return new SmsSearch.Query(keyword,sender,exact,prefix,signature);}
    static SmsSearch.Query all(){return query("","",false,"",null);}
    static long bucket(List<SmsSearch.Bucket> buckets,String key){for(SmsSearch.Bucket b:buckets)if(b.key.equals(key))return b.count;return -1;}
    static void rejects(Runnable work){assertions++;try{work.run();}catch(IllegalArgumentException expected){return;}throw new AssertionError("Expected rejection");}
    public static void main(String[] args){
        // Text search uses literal Java contains, never SQL/regex wildcard expansion.
        eq("abc 123",SmsSearch.normalize("\u3000ＡＢＣ\u00a0１２３\n"));
        eq("验证码",SmsSearch.normalize("验\u200b证\u200d码\ufeff"));
        eq("a b c",SmsSearch.normalize(" a\r\n\t b\u202fc "));
        eq("",SmsSearch.normalize(null));
        eq("",SmsSearch.normalize("\u200b\ufeff\u3000"));
        Locale original=Locale.getDefault();try{Locale.setDefault(new Locale("tr","TR"));eq("i",SmsSearch.normalize("I"));}finally{Locale.setDefault(original);}
        yes(SmsSearch.matches(row(1,"1069","您的验\u200b证码为１２３Ａ"),query("验证码为123a","",false,"",null)));
        yes(SmsSearch.matches(row(1,"1069","退款100%_done\\ok"),query("100%_done\\","",false,"",null)));
        yes(!SmsSearch.matches(row(1,"1069","退款100Xdone"),query("100%","",false,"",null)));
        yes(SmsSearch.matches(row(1,"1069","商家 O'Reilly"),query("O'Reilly","",false,"",null)));
        yes(SmsSearch.matches(row(1,"1069","abc\n\ndef"),query("ABC DEF","",false,"",null)));
        yes(!SmsSearch.matches(row(1,"1069",null),query("验证码","",false,"",null)));
        yes(SmsSearch.matches(row(1,null,null),all()));
        yes(SmsSearch.matches(row(1,"1069","验\r\n证\u3000码 123"),query("验证码123","",false,"",null)));
        eq("ab",SmsSearch.normalizeBody("A\u00a0 B"));
        // A keyword beyond any visible card preview or the first 64 KiB remains searchable.
        yes(SmsSearch.matches(row(4,"1069","前文".repeat(40000)+"隐藏在最后的关键词"),query("最后的关键词","",false,"",null)));
        // Sender and prefix use exactly the same canonicalization as bucket keys.
        yes(SmsSearch.matches(row(2,"１０６９００１",""),query("","1069001",true,"",null)));
        yes(!SmsSearch.matches(row(2,"1069001",""),query("","1069",true,"",null)));
        yes(SmsSearch.matches(row(2,"1069001",""),query("","1069",false,"",null)));
        yes(SmsSearch.matches(row(2,"1069001",""),query("","",false,"１０６９",null)));
        yes(!SmsSearch.matches(row(2,"21069",""),query("","",false,"1069",null)));
        yes(!SmsSearch.matches(row(2,"+861069",""),query("","",false,"1069",null)));
        yes(SmsSearch.matches(row(2,null,""),query("",SmsSearch.UNKNOWN_SENDER,true,"",null)));
        yes(!SmsSearch.matches(row(2,null,""),query("","",false,"1069",null)));
        // Only an edge tag is a signature. Leading tag wins over a valid trailing tag.
        eq("商家",SmsSearch.signature(" \u3000【商家】优惠内容 "));
        eq("商家",SmsSearch.signature("优惠内容【商家】 \n"));
        eq("shop",SmsSearch.signature("［ＳＨＯＰ］hello"));
        eq("shop",SmsSearch.signature("hello[SHOP]"));
        eq("a",SmsSearch.signature("[A]message[B]"));
        eq(SmsSearch.NO_SIGNATURE,SmsSearch.signature("正文[商家]还有正文"));
        eq(SmsSearch.NO_SIGNATURE,SmsSearch.signature("[]正文"));
        eq(SmsSearch.NO_SIGNATURE,SmsSearch.signature("【 】正文"));
        eq(SmsSearch.NO_SIGNATURE,SmsSearch.signature("【商家\n服务】正文"));
        eq(SmsSearch.NO_SIGNATURE,SmsSearch.signature("【商家]正文"));
        eq(SmsSearch.NO_SIGNATURE,SmsSearch.signature("[nested[inner]]正文"));
        eq(SmsSearch.NO_SIGNATURE,SmsSearch.signature(null));
        String forty="😀".repeat(40);
        eq(forty,SmsSearch.signature("【"+forty+"】正文"));
        eq(SmsSearch.NO_SIGNATURE,SmsSearch.signature("【"+forty+"😀】正文"));
        yes(SmsSearch.matches(row(3,"1069","[SHOP]hello"),query("","",false,"","shop")));
        yes(SmsSearch.matches(row(3,"1069","hello"),query("","",false,"",SmsSearch.NO_SIGNATURE)));
        yes(!SmsSearch.matches(row(3,"1069","[SHOP]hello"),query("","",false,"",SmsSearch.NO_SIGNATURE)));
        // Prefix config preserves ordering, normalizes and de-duplicates, with explicit bounds.
        eq(Arrays.asList("106","1069","+86"),SmsSearch.parsePrefixes("106,１０６；1069\n+86， \n"));
        eq(Collections.emptyList(),SmsSearch.parsePrefixes(null));
        rejects(()->SmsSearch.parsePrefixes("x".repeat(65)));
        StringJoiner many=new StringJoiner(",");for(int i=0;i<31;i++)many.add("p"+i);rejects(()->SmsSearch.parsePrefixes(many.toString()));
        // Base aggregation stays independent from the active clicked facet.
        List<SmsRecord> rows=Arrays.asList(row(10,"1069001","【甲】优惠"),row(11,"1069001","优惠【乙】"),row(12,"1068002","优惠"),row(13,null,"优惠"),row(14,"1069001","【甲】其他"));
        SmsSearch.Query base=query("优惠","",false,"",null);
        SmsSearch.Aggregator stats=new SmsSearch.Aggregator(base,Arrays.asList("106","1069","999"),1);
        for(SmsRecord r:rows)stats.accept(r);
        SmsSearch.Result result=stats.finish();
        eq(5L,result.scanned);eq(4L,result.matched);eq(1,result.preview.size());eq(4,result.ids.length);eq(10L,result.ids[0]);eq(13L,result.ids[3]);
        eq(2L,bucket(result.senders,"1069001"));eq(1L,bucket(result.senders,SmsSearch.UNKNOWN_SENDER));
        eq(3L,bucket(result.prefixes,"106"));eq(2L,bucket(result.prefixes,"1069"));eq(0L,bucket(result.prefixes,"999"));
        eq(1L,bucket(result.signatures,"甲"));eq(1L,bucket(result.signatures,"乙"));eq(2L,bucket(result.signatures,SmsSearch.NO_SIGNATURE));
        for(SmsSearch.Bucket b:result.senders){long count=0;for(SmsRecord r:rows)if(SmsSearch.matches(r,base)&&SmsSearch.matches(r,query("",b.key,true,"",null)))count++;eq(b.count,count);}
        for(SmsSearch.Bucket b:result.prefixes){long count=0;for(SmsRecord r:rows)if(SmsSearch.matches(r,base)&&SmsSearch.matches(r,query("","",false,b.key,null)))count++;eq(b.count,count);}
        for(SmsSearch.Bucket b:result.signatures){long count=0;for(SmsRecord r:rows)if(SmsSearch.matches(r,base)&&SmsSearch.matches(r,query("","",false,"",b.key)))count++;eq(b.count,count);}
        SmsSearch.Aggregator noRetain=new SmsSearch.Aggregator(base,Arrays.asList("106"),0,false);for(SmsRecord r:rows)noRetain.accept(r);
        SmsSearch.Result minimal=noRetain.finish();eq(0,minimal.preview.size());eq(0,minimal.ids.length);eq(4L,minimal.matched);eq(3L,bucket(minimal.prefixes,"106"));
        // Large corpus: exact totals and all IDs survive the bounded body preview.
        SmsSearch.Aggregator large=new SmsSearch.Aggregator(all(),Arrays.asList("106"),100);
        SmsSearch.LongIds ids=new SmsSearch.LongIds();
        for(int i=0;i<100000;i++){large.accept(row(i,"106"+(i%7),i%2==0?"【品牌】正文":"无标签"));ids.add(i);}
        SmsSearch.Result bulk=large.finish();eq(100000L,bulk.matched);eq(100,bulk.preview.size());eq(100000,bulk.ids.length);eq(99999L,bulk.ids[99999]);eq(50000L,bucket(bulk.signatures,"品牌"));eq(50000L,bucket(bulk.signatures,SmsSearch.NO_SIGNATURE));eq(100000L,bucket(bulk.prefixes,"106"));eq(100000,ids.size());eq(99999L,ids.toArray()[99999]);
        System.out.println(assertions+" SMS search/statistics assertions passed (including 100,000-row stream)");
    }
}
