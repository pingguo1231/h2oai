package water.fvec;

import static org.junit.Assert.*;
import org.junit.*;

import water.DKV;
import water.Futures;
import water.TestUtil;
import java.util.Arrays;
import java.util.Iterator;

/** Test for CBSChunk implementation.
 *
 * The objective of the test is to verify compression method, not the H2O environment.
 *
 * NOTE: The test is attempt to not require H2O infrastructure to run.
 * It tries to use Mockito (perhaps PowerMock in the future) to wrap
 * expected results. In this case expectation is little bit missused
 * since it is used to avoid DKV call.
 * */
public class CBSChunkTest extends TestUtil {
  void testImpl(long[] ls, int[] xs, int expBpv, int expGap, int expClen, int expNA) {
    AppendableVec av = new AppendableVec(Vec.newKey());
    // Create a new chunk
    NewChunk nc = new NewChunk(av,0, ls, xs, null, null);
    nc.type();                  // Compute rollups, including NA
    assertEquals(expNA, nc.naCnt());
    // Compress chunk
    Chunk cc = nc.compress();
    assert cc instanceof CBSChunk;
    Futures fs = new Futures();
    cc._vec = av.close(fs);
    fs.blockForPending();
    Assert.assertTrue("Found chunk class " + cc.getClass() + " but expected " + CBSChunk.class, CBSChunk.class.isInstance(cc));
    assertEquals(nc.len(), cc.len());
    assertEquals(expBpv, ((CBSChunk)cc).bpv());
    assertEquals(expGap, ((CBSChunk)cc).gap());
    assertEquals(expClen, cc._mem.length - CBSChunk._OFF);
    // Also, we can decompress correctly
    for( int i=0; i<ls.length; i++ )
      if(xs[i]==0)assertEquals(ls[i], cc.at80(i));
      else assertTrue(cc.isNA0(i));

    // materialize the vector (prerequisite to free the memory)
    Vec vv = av.close(fs);
    fs.blockForPending();
    vv.remove();
  }

  // Test one bit per value compression which is used
  // for data without NAs
  @Test public void test1BPV() {
    // Simple case only compressing into 4bits of one byte
    testImpl(new long[] {1,0,1,1},
             new int [] {0,0,0,0},
             1, 4, 1, 0);
    // Filling whole byte
    testImpl(new long[] {1,0,0,0,1,1,1,0},
             new int [] {0,0,0,0,0,0,0,0},
             1, 0, 1, 0);
    // Crossing the border of two bytes by 1bit
    testImpl(new long[] {1,0,0,0,1,1,1,0, 1},
             new int [] {0,0,0,0,0,0,0,0, 0},
             1, 7, 2, 0);
  }

  // Test two bits per value compression used for case with NAs
  // used for data containing NAs
  @Test public void test2BPV() {
   // Simple case only compressing 2*3bits into 1byte including 1 NA
   testImpl(new long[] {0,Long.MAX_VALUE,                  1},
            new int [] {0,Integer.MIN_VALUE,0},
            2, 2, 1, 1);
   // Filling whole byte, one NA
   testImpl(new long[] {1,Long.MAX_VALUE                ,0,1},
            new int [] {0,Integer.MIN_VALUE,0,0},
            2, 0, 1, 1);
   // crossing the border of two bytes by 4bits, one NA
   testImpl(new long[] {1,0,Long.MAX_VALUE,                1, 0,0},
            new int [] {0,0,Integer.MIN_VALUE,0, 0,0},
            2, 4, 2, 1);
   // Two full bytes, 5 NAs
   testImpl(new long[] {Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,1, 0,Long.MAX_VALUE,1,Long.MAX_VALUE},
            new int [] {Integer.MIN_VALUE,Integer.MIN_VALUE,Integer.MIN_VALUE,0, 0,Integer.MIN_VALUE,0,Integer.MIN_VALUE},
            2, 0, 2, 5);
  }
  @Test public void test_inflate_impl() {
    for (int l=0; l<2; ++l) {
      NewChunk nc = new NewChunk(null, 0);

      int[] vals = new int[]{0, 1, 0, 1, 0, 0, 1};
      if (l==1) nc.addNA();
      for (int v : vals) nc.addNum(v);
      nc.addNA();

      Chunk cc = nc.compress();
      Assert.assertEquals(vals.length + 1 + l, cc.len());
      Assert.assertTrue(cc instanceof CBSChunk);
      for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], cc.at80(l+i));
      for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], cc.at8(l+i));
      Assert.assertTrue(cc.isNA0(vals.length+l));
      Assert.assertTrue(cc.isNA(vals.length+l));

      nc = new NewChunk(null, 0);
      cc.inflate_impl(nc);
      Assert.assertEquals(vals.length+l+1, nc.sparseLen());
      Assert.assertEquals(vals.length+l+1, nc.len());

      Iterator<NewChunk.Value> it = nc.values(0, vals.length+1+l);
      for (int i = 0; i < vals.length+1+l; ++i) Assert.assertTrue(it.next().rowId0() == i);
      Assert.assertTrue(!it.hasNext());

      if (l==1) {
        Assert.assertTrue(nc.isNA0(0));
        Assert.assertTrue(nc.isNA(0));
      }
      for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], nc.at80(l+i));
      for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], nc.at8(l+i));
      Assert.assertTrue(nc.isNA0(vals.length+l));
      Assert.assertTrue(nc.isNA(vals.length+l));

      Chunk cc2 = nc.compress();
      Assert.assertEquals(vals.length + 1 + l, cc.len());
      Assert.assertTrue(cc2 instanceof CBSChunk);
      for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], cc2.at80(l+i));
      for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], cc2.at8(l+i));
      Assert.assertTrue(cc2.isNA0(vals.length + l));
      Assert.assertTrue(cc2.isNA(vals.length + l));

      Assert.assertTrue(Arrays.equals(cc._mem, cc2._mem));
    }
  }

  @Test public void test_setNA() {
    // Create a vec with one chunk with 15 elements, and set its numbers
    Vec vec = new Vec(Vec.newKey(), new long[]{0,15}).makeZeros(1,null,null,null,null)[0];
    int[] vals = new int[]{0, 1, 0, 1, 0, 0, 0, 1, 0, 1, 0, 1, 0, 0, 1};
    Vec.Writer w = vec.open();
    for (int i =0; i<vals.length; ++i) w.set(i, vals[i]);
    w.close();

    Chunk cc = vec.chunkForChunkIdx(0);
    assert cc instanceof CBSChunk;
    Futures fs = new Futures();
    fs.blockForPending();

    for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], cc.at80(i));
    for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], cc.at8(i));

    int[] NAs = new int[]{1, 5, 2};
    int[] notNAs = new int[]{0, 3, 4, 6, 7, 8, 9, 10, 11, 12, 13, 14};
    for (int na : NAs) cc.setNA(na);

    for (int na : NAs) Assert.assertTrue(cc.isNA0(na));
    for (int na : NAs) Assert.assertTrue(cc.isNA(na));
    for (int notna : notNAs) Assert.assertTrue(!cc.isNA0(notna));
    for (int notna : notNAs) Assert.assertTrue(!cc.isNA(notna));

    NewChunk nc = new NewChunk(null, 0);
    cc.inflate_impl(nc);
    Assert.assertEquals(vals.length, nc.sparseLen());
    Assert.assertEquals(vals.length, nc.len());

    Iterator<NewChunk.Value> it = nc.values(0, vals.length);
    for (int i = 0; i < vals.length; ++i) Assert.assertTrue(it.next().rowId0() == i);
    Assert.assertTrue(!it.hasNext());

    for (int na : NAs) Assert.assertTrue(cc.isNA0(na));
    for (int na : NAs) Assert.assertTrue(cc.isNA(na));
    for (int notna : notNAs) Assert.assertTrue(!cc.isNA0(notna));
    for (int notna : notNAs) Assert.assertTrue(!cc.isNA(notna));

    Chunk cc2 = nc.compress();
    Assert.assertEquals(vals.length, cc.len());
    Assert.assertTrue(cc2 instanceof CBSChunk);
    for (int na : NAs) Assert.assertTrue(cc.isNA0(na));
    for (int na : NAs) Assert.assertTrue(cc.isNA(na));
    for (int notna : notNAs) Assert.assertTrue(!cc.isNA0(notna));
    for (int notna : notNAs) Assert.assertTrue(!cc.isNA(notna));

    Assert.assertTrue(Arrays.equals(cc._mem, cc2._mem));
    vec.remove();
  }


}
