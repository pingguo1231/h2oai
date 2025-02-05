package water;

import static org.junit.Assert.*;
import org.junit.*;

import java.util.concurrent.ExecutionException;
import jsr166y.CountedCompleter;
import water.fvec.Chunk;
import water.fvec.Vec;

public class MRThrow extends TestUtil {
  public MRThrow() { super(5); }

  // ---
  // Map in h2o.jar - a multi-megabyte file - into Arraylets.
  // Run a distributed byte histogram.  Throw an exception in *some* map call,
  // and make sure it's forwarded to the invoke.
  @Test public void testInvokeThrow() {
    int sz = H2O.CLOUD.size();
    Vec vec = Vec.makeCon(0,null,(sz+1)*Vec.CHUNK_SZ+1);
    try {
      for(int i = 0; i < sz; ++i){
        ByteHistoThrow bh = new ByteHistoThrow(H2O.CLOUD._memary[i]);
        Throwable ex=null;
        try {
          bh.doAll(vec); // invoke should throw DistributedException wrapped up in RunTimeException
        } catch( RuntimeException e ) {
          assertTrue((ex=e).getMessage().contains("test"));
        } catch( Throwable e2 ) {
          (ex=e2).printStackTrace();
          fail("Expected RuntimeException, got " + ex.toString());
        }
        if( ex == null ) fail("should've thrown");
      }
    } finally {
      if( vec != null ) vec.remove(); // remove from DKV
    }
  }

  @Test public void testContinuationThrow() throws InterruptedException, ExecutionException {
    int sz = H2O.CLOUD.size();
    Vec vec = Vec.makeCon(0,null,(sz+1)*Vec.CHUNK_SZ+1);
    try {
      for(int i = 0; i < H2O.CLOUD._memary.length; ++i){
        final ByteHistoThrow bh = new ByteHistoThrow(H2O.CLOUD._memary[i]);
        final boolean [] ok = new boolean[]{false};
        try {
          bh.setCompleter(new CountedCompleter() {
              @Override public void compute() { tryComplete(); }
              @Override public boolean onExceptionalCompletion(Throwable ex, CountedCompleter cc){
                ok[0] = ex.getMessage().contains("test");
                return super.onExceptionalCompletion(ex,cc);
              }
          });
          bh.asyncExec(vec);
          // If the chosen file is too small for the cluster, some nodes will have *no* work
          // and so no exception is thrown.
          int MAX_CNT=5;
          while( !ok[0] && MAX_CNT-- > 0 ) {
            Thread.sleep(1000);
          }
        } catch( DException.DistributedException e ) {
          assertTrue(e.getMessage().contains("test"));
//        } catch( ExecutionException e ) { // caught on self
//          assertTrue(e.getMessage().contains("test"));
        } catch( java.lang.AssertionError ae ) {
          throw ae;             // Standard junit failure reporting assertion
        } catch(Throwable ex) {
          ex.printStackTrace();
          fail("Unexpected exception" + ex.toString());
        }
      }
    } finally {
      if( vec != null ) vec.remove(); // remove from DKV
    }
  }

  // Byte-wise histogram
  public static class ByteHistoThrow extends MRTask<ByteHistoThrow> {
    final H2ONode _throwAt;
    int[] _x;
    ByteHistoThrow( H2ONode h2o ) { _throwAt = h2o; }
    // Count occurrences of bytes
    @Override public void map( Chunk chk ) {
      _x = new int[256];            // One-time set histogram array
      byte[] bits = chk.getBytes(); // Raw file bytes
      for( byte b : bits )          // Compute local histogram
        _x[b&0xFF]++;
      if( H2O.SELF.equals(_throwAt) )
        throw new RuntimeException("test");
    }
    // ADD together all results
    @Override public void reduce( ByteHistoThrow bh ) { water.util.ArrayUtils.add(_x,bh._x); }
  }
}
