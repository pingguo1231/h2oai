package water.api;

import water.*;
import water.util.Log;

import java.util.Set;

public class RemoveAllHandler extends Handler<RemoveAllHandler.RemoveAll,RemoveAllV1> {
  protected static final class RemoveAll extends Iced {  }
  @Override protected int min_ver() { return 1; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }
  @Override protected RemoveAllV1 schema(int version) { return new RemoveAllV1(); }
  @Override public void compute2() { throw H2O.unimpl(); }
  public RemoveAllV1 remove(int version, RemoveAll u) {
    Log.info("Removing all keys");
    Futures fs = new Futures();
    for (Job j : Job.jobs()) { j.cancel(); j.remove(fs); }
    fs.blockForPending();
    new RemoveAllTask().doAllNodes();
    Log.info("Finished removing keys");
    return schema(version).fillFromImpl(u);
  }

  public class RemoveAllTask extends MRTask<RemoveAllTask> {
    @Override public byte priority() { return H2O.GUI_PRIORITY; }

    @Override public void setupLocal() {
      final Set<Key> kys = H2O.localKeySet();
      Log.info("Removing "+kys.size()+ " keys from nodeIdx("+H2O.SELF.index()+") out of "+H2O.CLOUD.size()+" nodes.");
      Futures fs = new Futures();
      for (Key k : kys)
        DKV.remove(k, fs);
      fs.blockForPending();
      Log.info("Keys remaining: "+H2O.store_size());
    }
  }
}