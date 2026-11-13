package com.chad.smsmanager;
import java.util.Arrays;
/** Primitive-ID selection: no SMS bodies and no boxed object per selected message. */
public final class SmsSelection {
    private long[] ids=new long[0];
    public int size(){return ids.length;}
    public boolean isEmpty(){return ids.length==0;}
    public boolean contains(long id){return Arrays.binarySearch(ids,id)>=0;}
    public void clear(){ids=new long[0];}
    public long[] snapshot(){return ids.clone();}
    public void replace(long[] values){ids=sortedUnique(values);}
    public void toggle(long id,boolean checked){
        int at=Arrays.binarySearch(ids,id);if(checked&&at<0){at=-at-1;long[] next=new long[ids.length+1];System.arraycopy(ids,0,next,0,at);next[at]=id;System.arraycopy(ids,at,next,at+1,ids.length-at);ids=next;}
        else if(!checked&&at>=0){long[] next=new long[ids.length-1];System.arraycopy(ids,0,next,0,at);System.arraycopy(ids,at+1,next,at,ids.length-at-1);ids=next;}
    }
    public static long[] sortedUnique(long[] input){long[] out=input.clone();Arrays.sort(out);int size=0;for(long id:out)if(size==0||out[size-1]!=id)out[size++]=id;return Arrays.copyOf(out,size);}
    public static long[] intersect(long[] a,long[] b){a=sortedUnique(a);b=sortedUnique(b);long[] out=new long[Math.min(a.length,b.length)];int i=0,j=0,n=0;while(i<a.length&&j<b.length){if(a[i]==b[j]){out[n++]=a[i];i++;j++;}else if(a[i]<b[j])i++;else j++;}return Arrays.copyOf(out,n);}
}
