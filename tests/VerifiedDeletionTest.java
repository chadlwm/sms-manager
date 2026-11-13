import com.chad.smsmanager.VerifiedDeletion;
import static com.chad.smsmanager.VerifiedDeletion.Kind.*;
public class VerifiedDeletionTest {
    static int checks;
    static void check(boolean condition) { checks++; if (!condition) throw new AssertionError("check " + checks); }
    static final class Fake implements VerifiedDeletion.Store {
        boolean present=true, removeItem, removeFallback, throwItem, throwFallback;
        int rowsItem, rowsFallback, reads, failRead=-1, itemCalls, fallbackCalls;
        public boolean exists(long id) throws Exception { check(id==42); if (++reads==failRead) throw new IllegalStateException(); return present; }
        public int deleteItem(long id) throws Exception { check(id==42); itemCalls++; if(removeItem)present=false; if(throwItem)throw new SecurityException(); return rowsItem; }
        public int deleteSelected(long id) throws Exception { check(id==42); fallbackCalls++; if(removeFallback)present=false; if(throwFallback)throw new SecurityException(); return rowsFallback; }
    }
    public static void main(String[] args) {
        Fake f=new Fake();
        VerifiedDeletion.Result r=VerifiedDeletion.run(f,()->true,42);
        check(r.kind==FAILED); check(r.detail.contains("仍存在")); check(f.itemCalls==1&&f.fallbackCalls==1);
        f=new Fake(); f.rowsItem=1; f.removeItem=true; r=VerifiedDeletion.run(f,()->true,42);
        check(r.kind==DELETED); check(f.fallbackCalls==0);
        f=new Fake(); f.removeFallback=true; f.rowsFallback=1; r=VerifiedDeletion.run(f,()->true,42);
        check(r.kind==DELETED); check(f.fallbackCalls==1);
        f=new Fake(); f.present=false; r=VerifiedDeletion.run(f,()->true,42);
        check(r.kind==ALREADY_ABSENT); check(f.itemCalls==0&&f.fallbackCalls==0);
        f=new Fake(); f.failRead=1; r=VerifiedDeletion.run(f,()->true,42);
        check(r.kind==FAILED); check(f.itemCalls==0);
        f=new Fake(); f.removeItem=true; f.rowsItem=1; f.failRead=2; r=VerifiedDeletion.run(f,()->true,42);
        check(r.kind==UNCONFIRMED); check(f.fallbackCalls==0);
        f=new Fake(); f.throwItem=true; r=VerifiedDeletion.run(f,()->true,42);
        check(r.kind==FAILED); check(r.detail.contains("SecurityException")); check(f.fallbackCalls==0);
        f=new Fake(); f.throwItem=true; f.removeItem=true; r=VerifiedDeletion.run(f,()->true,42);
        check(r.kind==DELETED); check(f.fallbackCalls==0);
        f=new Fake(); f.rowsItem=1; r=VerifiedDeletion.run(f,()->true,42);
        check(r.kind==FAILED); check(f.fallbackCalls==0);
        f=new Fake(); r=VerifiedDeletion.run(f,()->false,42);
        check(r.kind==FAILED); check(f.itemCalls==0);
        f=new Fake(); int[] guardCalls={0}; r=VerifiedDeletion.run(f,()->++guardCalls[0]==1,42);
        check(r.kind==FAILED); check(f.fallbackCalls==0);
        f=new Fake(); f.failRead=3; r=VerifiedDeletion.run(f,()->true,42);
        check(r.kind==UNCONFIRMED); check(f.fallbackCalls==1);
        f=new Fake(); f.removeItem=true; f.rowsItem=0; r=VerifiedDeletion.run(f,()->true,42);
        check(r.kind==DELETED); check(f.fallbackCalls==0);
        System.out.println("13 verified deletion scenarios passed ("+checks+" assertions)");
    }
}
