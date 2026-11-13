package com.chad.smsmanager;

import java.text.Normalizer;
import java.util.*;

/** Deterministic local literal search shared by filtering and statistics. No SQL syntax. */
public final class SmsSearch {
    public static final String NO_SIGNATURE="\u0000";
    public static final String UNKNOWN_SENDER="\u0000";
    public static final int MAX_SIGNATURE_CODEPOINTS=40;
    public static final int MAX_CUSTOM_PREFIXES=30;
    public static final int MAX_PREFIX_CODEPOINTS=64;
    private SmsSearch() {}

    /** NFKC, ROOT lowercase, format-control removal, whitespace collapse, outer trim. */
    public static String normalize(String value) {
        if(value==null || value.isEmpty()) return "";
        String raw=Normalizer.normalize(value,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        StringBuilder out=new StringBuilder(raw.length()); boolean space=false;
        for(int offset=0;offset<raw.length();) {
            int cp=raw.codePointAt(offset); offset+=Character.charCount(cp);
            if(Character.getType(cp)==Character.FORMAT) continue;
            if(Character.isWhitespace(cp)||Character.isSpaceChar(cp)) { if(out.length()>0)space=true;continue; }
            if(space)out.append(' ');
            space=false;out.appendCodePoint(cp);
        }
        return out.toString();
    }
    /** Body searches additionally ignore every normalized whitespace, including line wrapping. */
    public static String normalizeBody(String value) { return normalize(value).replace(" ", ""); }
    public static String senderKey(String address) { String key=normalize(address);return key.isEmpty()?UNKNOWN_SENDER:key; }
    public static String senderLabel(String key) { return UNKNOWN_SENDER.equals(key)?"未知发件人":key; }
    public static String signatureLabel(String key) { return NO_SIGNATURE.equals(key)?"无签名":key; }

    /** Only brackets touching the trimmed body start/end; leading wins when both exist. */
    public static String signature(String body) {
        if(body==null) return NO_SIGNATURE;
        // Preserve line boundaries here: a signature may not span a newline.
        String raw=Normalizer.normalize(body,Normalizer.Form.NFKC);
        StringBuilder clean=new StringBuilder(raw.length());
        for(int offset=0;offset<raw.length();) {
            int cp=raw.codePointAt(offset);offset+=Character.charCount(cp);
            if(Character.getType(cp)!=Character.FORMAT)clean.appendCodePoint(cp);
        }
        String text=trimWhitespace(clean.toString());
        if(text.isEmpty())return NO_SIGNATURE;
        char first=text.charAt(0);
        if(first=='['||first=='【') {
            int end=text.indexOf(first=='['?']':'】',1);
            if(end>0) { String candidate=signatureCandidate(text.substring(1,end));if(candidate!=null)return candidate; }
        }
        char last=text.charAt(text.length()-1);
        if(last==']'||last=='】') {
            int start=text.lastIndexOf(last==']'?'[':'【');
            if(start>=0) { String candidate=signatureCandidate(text.substring(start+1,text.length()-1));if(candidate!=null)return candidate; }
        }
        return NO_SIGNATURE;
    }
    private static String trimWhitespace(String s) {
        int start=0,end=s.length();
        while(start<end) {int cp=s.codePointAt(start);if(!Character.isWhitespace(cp)&&!Character.isSpaceChar(cp))break;start+=Character.charCount(cp);}
        while(end>start) {int cp=s.codePointBefore(end);if(!Character.isWhitespace(cp)&&!Character.isSpaceChar(cp))break;end-=Character.charCount(cp);}
        return s.substring(start,end);
    }
    private static String signatureCandidate(String s) {
        for(int offset=0;offset<s.length();) {
            int cp=s.codePointAt(offset);offset+=Character.charCount(cp);
            if(cp=='['||cp==']'||cp=='【'||cp=='】'||Character.isISOControl(cp)||cp==0x2028||cp==0x2029)return null;
        }
        String candidate=normalize(s);
        int size=candidate.codePointCount(0,candidate.length());
        return size>0&&size<=MAX_SIGNATURE_CODEPOINTS?candidate:null;
    }

    public static final class Query {
        public final String keyword,sender,prefix,signature;
        public final boolean exactSender;
        public Query(String keyword,String sender,boolean exactSender,String prefix,String signature) {
            this.keyword=normalizeBody(keyword);this.sender=UNKNOWN_SENDER.equals(sender)?UNKNOWN_SENDER:normalize(sender);
            this.exactSender=exactSender;this.prefix=normalize(prefix);
            this.signature=signature==null?null:NO_SIGNATURE.equals(signature)?NO_SIGNATURE:normalize(signature);
        }
    }
    public static boolean matches(SmsRecord row,Query query) {
        String sender=senderKey(row.address);
        if(!query.sender.isEmpty()) {
            if(query.exactSender ? !sender.equals(query.sender) : !sender.contains(query.sender))return false;
        }
        if(!query.prefix.isEmpty() && (UNKNOWN_SENDER.equals(sender)||!sender.startsWith(query.prefix)))return false;
        if(!query.keyword.isEmpty()&&!normalizeBody(row.body).contains(query.keyword))return false;
        return query.signature==null||query.signature.equals(signature(row.body));
    }

    /** Input separators: newline, comma (ASCII/Chinese), semicolon. No empty prefix buckets. */
    public static List<String> parsePrefixes(String value) {
        LinkedHashSet<String> unique=new LinkedHashSet<>();
        if(value==null||value.trim().isEmpty())return new ArrayList<>();
        for(String piece:value.split("[\\r\\n,，;；]+")) {
            String prefix=normalize(piece);if(prefix.isEmpty())continue;
            if(prefix.codePointCount(0,prefix.length())>MAX_PREFIX_CODEPOINTS)throw new IllegalArgumentException("每个前缀最多 64 个字符");
            unique.add(prefix);if(unique.size()>MAX_CUSTOM_PREFIXES)throw new IllegalArgumentException("最多设置 30 个不同前缀");
        }
        return new ArrayList<>(unique);
    }
    public static final class LongIds {
        private long[] values=new long[256];private int size;
        public void add(long value){if(size==values.length)values=Arrays.copyOf(values,values.length*2);values[size++]=value;}
        public int size(){return size;}
        public long[] toArray(){return Arrays.copyOf(values,size);}
    }
    public static final class Bucket {
        public final String key,label;
        public final long count;
        Bucket(String key,String label,long count){this.key=key;this.label=label;this.count=count;}
    }
    public static final class Result {
        public final long scanned, matched;
        public final List<SmsRecord> preview;
        /** Exact current-query matches, not just preview; use only after explicit delete confirmation. */
        public final long[] ids;
        public final List<Bucket> senders,prefixes,signatures;
        Result(long scanned,long matched,List<SmsRecord> preview,long[] ids,List<Bucket> senders,List<Bucket> prefixes,List<Bucket> signatures) {
            this.scanned=scanned;this.matched=matched;this.preview=Collections.unmodifiableList(preview);this.ids=ids;
            this.senders=Collections.unmodifiableList(senders);this.prefixes=Collections.unmodifiableList(prefixes);this.signatures=Collections.unmodifiableList(signatures);
        }
    }
    /** Streaming accumulator: bounded bodies, one primitive long per matching ID, no full-body cache. */
    public static final class Aggregator {
        private final Query query;private final int limit;private final boolean collectIds;
        private final ArrayList<SmsRecord> preview=new ArrayList<>();
        private final Map<String,Long> senders=new HashMap<>(),signatures=new HashMap<>();
        private final LinkedHashMap<String,Long> prefixes=new LinkedHashMap<>();
        private long scanned,matched;private long[] ids=new long[256];private int size;
        public Aggregator(Query query,List<String> customPrefixes,int previewLimit) { this(query,customPrefixes,previewLimit,true); }
        /** Pass previewLimit=0 and collectIds=false for base statistics without retaining matching rows/IDs. */
        public Aggregator(Query query,List<String> customPrefixes,int previewLimit,boolean collectIds) {
            if(query==null||previewLimit<0)throw new IllegalArgumentException("Invalid query or preview limit");
            this.query=query;this.limit=previewLimit;this.collectIds=collectIds;
            if(customPrefixes!=null)for(String raw:customPrefixes){String prefix=normalize(raw);if(!prefix.isEmpty()){
                if(prefix.codePointCount(0,prefix.length())>MAX_PREFIX_CODEPOINTS)throw new IllegalArgumentException("每个前缀最多 64 个字符");
                prefixes.put(prefix,0L);if(prefixes.size()>MAX_CUSTOM_PREFIXES)throw new IllegalArgumentException("最多设置 30 个不同前缀");
            }}
        }
        /** Caller must check its cancellation/generation token before each accept(). */
        public boolean accept(SmsRecord row) {
            scanned++;if(!matches(row,query))return false;matched++;
            if(collectIds){if(size==ids.length)ids=Arrays.copyOf(ids,Math.max(256,ids.length*2));ids[size++]=row.id;}
            if(preview.size()<limit)preview.add(row);
            String sender=senderKey(row.address);increment(senders,sender);increment(signatures,signature(row.body));
            if(!UNKNOWN_SENDER.equals(sender))for(Map.Entry<String,Long> entry:prefixes.entrySet())if(sender.startsWith(entry.getKey()))entry.setValue(entry.getValue()+1);
            return true;
        }
        public Result finish() {return new Result(scanned,matched,new ArrayList<>(preview),Arrays.copyOf(ids,size),buckets(senders,false),prefixBuckets(),buckets(signatures,true));}
        private List<Bucket> prefixBuckets() {ArrayList<Bucket> result=new ArrayList<>();for(Map.Entry<String,Long> entry:prefixes.entrySet())result.add(new Bucket(entry.getKey(),entry.getKey(),entry.getValue()));return result;}
        private static void increment(Map<String,Long> map,String key){Long n=map.get(key);map.put(key,n==null?1L:n+1);}
        private static List<Bucket> buckets(Map<String,Long> map,boolean signature) {
            ArrayList<Bucket> result=new ArrayList<>();for(Map.Entry<String,Long> entry:map.entrySet())result.add(new Bucket(entry.getKey(),signature?signatureLabel(entry.getKey()):senderLabel(entry.getKey()),entry.getValue()));
            result.sort((a,b)->{int n=Long.compare(b.count,a.count);return n!=0?n:a.key.compareTo(b.key);});return result;
        }
    }
}
