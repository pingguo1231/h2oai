package water.fvec;

import org.junit.*;

import water.Futures;
import water.TestUtil;
import java.util.Arrays;
import java.util.Iterator;

public class C8ChunkTest extends TestUtil {
  @Test
  public void test_inflate_impl() {
    for (int l=0; l<2; ++l) {
      NewChunk nc = new NewChunk(null, 0);

      long[] vals = new long[]{Long.MIN_VALUE+1, Integer.MIN_VALUE, 0, Integer.MAX_VALUE, Long.MAX_VALUE};
      if (l==1) nc.addNA();
      for (long v : vals) nc.addNum(v, 0);
      nc.addNA(); //-9223372036854775808l

      Chunk cc = nc.compress();
      Assert.assertEquals(vals.length + 1 + l, cc._len);
      Assert.assertTrue(cc instanceof C8Chunk);
      for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], cc.at80(l+i));
      for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], cc.at8(l+i));
      Assert.assertTrue(cc.isNA0(vals.length+l));
      Assert.assertTrue(cc.isNA(vals.length+l));

      nc = new NewChunk(null, 0);
      cc.inflate_impl(nc);
      nc.values(0, nc._len);
      if (l==1) Assert.assertTrue(cc.isNA0(0));
      Assert.assertEquals(vals.length+l+1, nc.sparseLen());
      Assert.assertEquals(vals.length+l+1, nc._len);
      Iterator<NewChunk.Value> it = nc.values(0, vals.length+1+l);
      for (int i = 0; i < vals.length+1+l; ++i) Assert.assertTrue(it.next().rowId0() == i);
      Assert.assertTrue(!it.hasNext());
      for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], nc.at80(l+i));
      for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], nc.at8(l+i));
      Assert.assertTrue(cc.isNA0(vals.length+l));

      Chunk cc2 = nc.compress();
      Assert.assertEquals(vals.length + 1 + l, cc._len);
      Assert.assertTrue(cc2 instanceof C8Chunk);
      for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], cc2.at80(l+i));
      for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], cc2.at8(l+i));
      Assert.assertTrue(cc2.isNA0(vals.length+l));
      Assert.assertTrue(cc2.isNA(vals.length+l));

      Assert.assertTrue(Arrays.equals(cc._mem, cc2._mem));
    }
  }

  @Test public void test_setNA() {
    // Create a vec with one chunk with 15 elements, and set its numbers
    Vec vec = new Vec(Vec.newKey(), new long[]{0,15}).makeZeros(1,null,null,null,null)[0];
    long[] vals = new long[]{Long.MIN_VALUE+1, 1, 0, 2, 0, 51, 0, 33, 0, 21234, 3422, 3767, 0, 0, Long.MAX_VALUE};
    Vec.Writer w = vec.open();
    for (int i =0; i<vals.length; ++i) w.set(i, vals[i]);
    w.close();

    Chunk cc = vec.chunkForChunkIdx(0);
    assert cc instanceof C8Chunk;
    Futures fs = new Futures();
    fs.blockForPending();

    for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], cc.at80(i), Double.MIN_VALUE);
    for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], cc.at8(i), Double.MIN_VALUE);

    int[] NAs = new int[]{1, 5, 2};
    int[] notNAs = new int[]{0, 3, 4, 6, 7, 8, 9, 10, 11, 12, 13, 14};
    for (int na : NAs) cc.setNA(na);

    for (int na : NAs) Assert.assertTrue(cc.isNA0(na));
    for (int na : NAs) Assert.assertTrue(cc.isNA(na));
    for (int notna : notNAs) Assert.assertTrue(!cc.isNA0(notna));
    for (int notna : notNAs) Assert.assertTrue(!cc.isNA(notna));

    NewChunk nc = new NewChunk(null, 0);
    cc.inflate_impl(nc);
    nc.values(0, nc._len);
    Assert.assertEquals(vals.length, nc.sparseLen());
    Assert.assertEquals(vals.length, nc._len);

    Iterator<NewChunk.Value> it = nc.values(0, vals.length);
    for (int i = 0; i < vals.length; ++i) Assert.assertTrue(it.next().rowId0() == i);
    Assert.assertTrue(!it.hasNext());

    for (int na : NAs) Assert.assertTrue(cc.isNA0(na));
    for (int na : NAs) Assert.assertTrue(cc.isNA(na));
    for (int notna : notNAs) Assert.assertTrue(!cc.isNA0(notna));
    for (int notna : notNAs) Assert.assertTrue(!cc.isNA(notna));

    Chunk cc2 = nc.compress();
    Assert.assertEquals(vals.length, cc._len);
    Assert.assertTrue(cc2 instanceof C8Chunk);
    for (int na : NAs) Assert.assertTrue(cc.isNA0(na));
    for (int na : NAs) Assert.assertTrue(cc.isNA(na));
    for (int notna : notNAs) Assert.assertTrue(!cc.isNA0(notna));
    for (int notna : notNAs) Assert.assertTrue(!cc.isNA(notna));

    Assert.assertTrue(Arrays.equals(cc._mem, cc2._mem));
    vec.remove();
  }
}
