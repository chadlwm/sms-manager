package com.chad.smsmanager;
import java.util.*;
/** One complete provider scan, with a bounded visible page and full-result statistics/IDs. */
public final class SmsScan {
    public static final int PAGE_SIZE=100;
    private final SmsSearch.Aggregator statistics;
    private final SmsSearch.Query active;
    private final int scope,page;
    private final SmsSearch.LongIds ids=new SmsSearch.LongIds();
    private final ArrayList<SmsRecord> visible=new ArrayList<>();
    public long read;
    public SmsScan(SmsSearch.Query base,SmsSearch.Query active,List<String> prefixes,int scope,int page){
        if(scope<0||scope>2||page<0)throw new IllegalArgumentException("Invalid scope/page");
        statistics=new SmsSearch.Aggregator(base,prefixes,0,false);this.active=active;this.scope=scope;this.page=page;
    }
    public void accept(SmsRecord record){
        read++;
        if((scope==1&&record.type!=1)||(scope==2&&record.type!=2))return;
        if(statistics.accept(record)&&SmsSearch.matches(record,active)){
            long offset=ids.size();ids.add(record.id);
            if(offset>=(long)page*PAGE_SIZE&&offset<((long)page+1)*PAGE_SIZE)visible.add(record);
        }
    }
    public Result finish(){return new Result(read,statistics.finish(),ids.toArray(),new ArrayList<>(visible));}
    public static final class Result {
        public final long read;public final SmsSearch.Result statistics;public final long[] ids;public final List<SmsRecord> visible;
        private Result(long read,SmsSearch.Result stats,long[] ids,List<SmsRecord> visible){this.read=read;statistics=stats;this.ids=ids;this.visible=Collections.unmodifiableList(visible);}
    }
}
