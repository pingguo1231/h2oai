package water.fvec;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;
import water.TestUtil;

import java.util.Arrays;

public class C4FChunkTest extends TestUtil {
  //FIXME: We never actually create C4FChunks at this time (the NewChunk doesn't know how to discern floats from doubles)
  @Test (enabled=false)
  void test_inflate_impl() {
    for (int l=0; l<2; ++l) {
      NewChunk nc = new NewChunk(null, 0);

      float[] vals = new float[]{-234.324f, 0f, Float.NaN, Float.POSITIVE_INFINITY, Float.MIN_VALUE, Float.MAX_VALUE, Float.NEGATIVE_INFINITY };
      if (l==1) nc.addNA();
      for (float v : vals) nc.addNum(v);
      nc.addNA();

      Chunk cc = nc.compress();
      AssertJUnit.assertEquals(vals.length + 1 + l, cc.len());
      AssertJUnit.assertTrue(cc instanceof C4FChunk);
      for (int i = 0; i < vals.length; ++i) AssertJUnit.assertEquals(vals[i], (float)cc.at0(l+i));
      AssertJUnit.assertTrue(cc.isNA0(vals.length+l));

      Chunk cc2 = cc.inflate_impl(new NewChunk(null, 0)).compress();
      AssertJUnit.assertEquals(vals.length + 1 + l, cc.len());
      AssertJUnit.assertTrue(cc2 instanceof C4FChunk);
      for (int i = 0; i < vals.length; ++i) AssertJUnit.assertEquals(vals[i], (float)cc2.at0(l+i));
      AssertJUnit.assertTrue(cc2.isNA0(vals.length+l));

      AssertJUnit.assertTrue(Arrays.equals(cc._mem, cc2._mem));
    }
  }
}
