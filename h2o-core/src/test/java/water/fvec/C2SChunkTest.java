package water.fvec;

import org.junit.*;

import water.Futures;
import water.TestUtil;
import java.util.Arrays;
import java.util.Iterator;

public class C2SChunkTest extends TestUtil {
  @Test
  public void test_inflate_impl() {
    for (int l=0; l<2; ++l) {
      NewChunk nc = new NewChunk(null, 0);

      // -32.767, 0.34, 0, 32.767, NA for l==0
      // NA, -32.767, 0.34, 0, 32.767, NA for l==1
      long[] man = new long[]{-32767, 34, 0, 32767};
      int[] exp = new int[]{-3, -2, 1, -3};
      if (l==1) nc.addNA(); //-32768
      for (int i = 0; i < man.length; ++i) nc.addNum(man[i], exp[i]);
      nc.addNA();

      Chunk cc = nc.compress();
      Assert.assertEquals(man.length + 1 + l, cc.len());
      Assert.assertTrue(cc instanceof C2SChunk);
      if (l==1) {
        Assert.assertTrue(cc.isNA0(0));
        Assert.assertTrue(cc.isNA(0));
      }
      for (int i = 0; i < man.length; ++i) {
        Assert.assertEquals((float) (man[i] * Math.pow(10, exp[i])), (float) cc.at0(l + i), 0);
        Assert.assertEquals((float) (man[i] * Math.pow(10, exp[i])), (float) cc.at(l + i), 0);
      }
      Assert.assertTrue(cc.isNA0(man.length + l));
      Assert.assertTrue(cc.isNA(man.length + l));

      nc = cc.inflate_impl(new NewChunk(null, 0));
      nc.values(0, nc.len());
      Assert.assertEquals(man.length + 1 + l, nc.len());
      Assert.assertEquals(man.length + 1 + l, nc.sparseLen());
      if (l==1) {
        Assert.assertTrue(nc.isNA0(0));
        Assert.assertTrue(nc.isNA(0));
      }
      for (int i = 0; i < man.length; ++i) {
        Assert.assertEquals((float) (man[i] * Math.pow(10, exp[i])), (float) nc.at0(l + i), 0);
        Assert.assertEquals((float) (man[i] * Math.pow(10, exp[i])), (float) nc.at(l + i), 0);
      }
      Assert.assertTrue(nc.isNA0(man.length + l));
      Assert.assertTrue(nc.isNA(man.length + l));

      Chunk cc2 = nc.compress();
      Assert.assertEquals(man.length + 1 + l, cc.len());
      if (l==1) {
        Assert.assertTrue(cc2.isNA0(0));
        Assert.assertTrue(cc2.isNA(0));
      }
      for (int i = 0; i < man.length; ++i) {
        Assert.assertEquals((float) (man[i] * Math.pow(10, exp[i])), (float) cc2.at0(l + i), 0);
        Assert.assertEquals((float) (man[i] * Math.pow(10, exp[i])), (float) cc2.at(l + i), 0);
      }
      Assert.assertTrue(cc2.isNA0(man.length + l));
      Assert.assertTrue(cc2.isNA(man.length + l));
      Assert.assertTrue(cc2 instanceof C2SChunk);

      Assert.assertTrue(Arrays.equals(cc._mem, cc2._mem));
    }
  }
  @Test public void test_inflate_impl2() {
    for (int l=0; l<2; ++l) {
      NewChunk nc = new NewChunk(null, 0);

      long[] man = new long[]{-12767, 34, 0, 52767};
      int[] exp = new int[]{-3, -2, 1, -3};
      if (l==1) nc.addNA(); //-32768
      for (int i = 0; i < man.length; ++i) nc.addNum(man[i], exp[i]);
      nc.addNA();

      Chunk cc = nc.compress();
      Assert.assertEquals(man.length + 1 + l, cc.len());
      Assert.assertTrue(cc instanceof C2SChunk);
      if (l==1) {
        Assert.assertTrue(cc.isNA0(0));
        Assert.assertTrue(cc.isNA(0));
      }
      for (int i = 0; i < man.length; ++i) {
        Assert.assertEquals((float) (man[i] * Math.pow(10, exp[i])), (float) cc.at0(l + i), 0);
        Assert.assertEquals((float) (man[i] * Math.pow(10, exp[i])), (float) cc.at(l + i), 0);
      }
      Assert.assertTrue(cc.isNA0(man.length + l));

      nc = cc.inflate_impl(new NewChunk(null, 0));
      nc.values(0, nc.len());
      Assert.assertEquals(man.length + 1 + l, nc.len());
      Assert.assertEquals(man.length + 1 + l, nc.sparseLen());
      if (l==1) {
        Assert.assertTrue(nc.isNA0(0));
        Assert.assertTrue(nc.isNA(0));
      }
      for (int i = 0; i < man.length; ++i) {
        Assert.assertEquals((float) (man[i] * Math.pow(10, exp[i])), (float) nc.at0(l + i), 0);
        Assert.assertEquals((float) (man[i] * Math.pow(10, exp[i])), (float) nc.at(l + i), 0);
      }
      Assert.assertTrue(nc.isNA0(man.length + l));
      Assert.assertTrue(nc.isNA(man.length + l));

      Chunk cc2 = nc.compress();
      Assert.assertEquals(man.length + 1 + l, cc.len());
      if (l==1) {
        Assert.assertTrue(cc2.isNA0(0));
        Assert.assertTrue(cc2.isNA(0));
      }
      for (int i = 0; i < man.length; ++i) {
        Assert.assertEquals((float) (man[i] * Math.pow(10, exp[i])), (float) cc2.at0(l + i), 0);
        Assert.assertEquals((float) (man[i] * Math.pow(10, exp[i])), (float) cc2.at(l + i), 0);
      }
      Assert.assertTrue(cc2.isNA0(man.length + l));
      Assert.assertTrue(cc2.isNA(man.length + l));
      Assert.assertTrue(cc2 instanceof C2SChunk);

      Assert.assertTrue(Arrays.equals(cc._mem, cc2._mem));
    }
  }

  @Test public void test_setNA() {
    // Create a vec with one chunk with 15 elements, and set its numbers
    Vec vec = new Vec(Vec.newKey(), new long[]{0,15}).makeZeros(1,null,null,null,null)[0];
    int[] vals = new int[]{0, 3, 0, 6, 0, 0, 0, -32769, 0, 12, 234, 32765, 0, 0, 19};
    Vec.Writer w = vec.open();
    for (int i =0; i<vals.length; ++i) w.set(i, vals[i]);
    w.close();

    Chunk cc = vec.chunkForChunkIdx(0);
    assert cc instanceof C2SChunk;
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
    nc.values(0, nc.len());
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
    Assert.assertTrue(cc2 instanceof C2SChunk);
    for (int na : NAs) Assert.assertTrue(cc.isNA0(na));
    for (int na : NAs) Assert.assertTrue(cc.isNA(na));
    for (int notna : notNAs) Assert.assertTrue(!cc.isNA0(notna));
    for (int notna : notNAs) Assert.assertTrue(!cc.isNA(notna));

    Assert.assertTrue(Arrays.equals(cc._mem, cc2._mem));
    vec.remove();
  }
}
