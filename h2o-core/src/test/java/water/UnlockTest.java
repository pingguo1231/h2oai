package water;

import org.testng.AssertJUnit;
import org.testng.annotations.*;

import water.api.UnlockTask;
import water.fvec.Frame;
import water.util.Log;

@Test(groups={"multi-node"})
public class UnlockTest extends TestUtil {
  UnlockTest() { super(3); }

  @Test
  public void run() {
    // Put chunks into KV store
    Frame f = new TestUtil().parse_test_file("smalldata/junit/syn_2659x1049.csv.gz");
    // Create two lockable frames in KV store
    Frame fr1 = new Frame(Key.make(), f.names(), f.vecs());
    Frame fr2 = new Frame(Key.make(), f.names(), f.vecs());
    // Lock the frames against writes
    fr1.delete_and_lock(null);
    fr2.delete_and_lock(null);
    int i = 0;
    try {
      // try to delete the write-locked frames -> will throw an exception
      fr1.delete();
      fr2.delete(); // won't be reached
    } catch (Throwable t) {
      Log.info("Correctly unable to delete (was locked): " + t.getClass()); //either AssertionError if local or DistributedException if remote
      i++;
    } finally {
      // second attempt: will unlock and delete properly
      new UnlockTask().doAllNodes(); // without this line, there will be a leak (and assertion won't be shown)
      fr1.delete();
      fr2.delete();
      f.delete();
      Log.info("Able to delete after unlocking.");
    }
    AssertJUnit.assertTrue(i == 1);
  }
}
