package water.fvec;

import java.util.Arrays;
import java.util.Iterator;
import jsr166y.CountedCompleter;
import water.Futures;
import water.H2O;
import water.Key;
import water.MRTask;

/**
 * Created by tomasnykodym on 3/28/14.
 *
 * Utility to rebalance dataset so that it has requested number of chunks and each chunk has the same number of rows +/-1.
 *
 * It *does not* guarantee even chunk-node placement.
 * (This can not currently be done in H2O, since the placement of chunks is governed only by key-hash /vector group/ for Vecs)
 */
public class RebalanceDataSet extends H2O.H2OCountedCompleter {
  final Frame _in;
  final int _nchunks;
  Key _okey;
  Frame _out;
  final Key _jobKey;

  public RebalanceDataSet(Frame srcFrame, Key dstKey, int nchunks) { this(srcFrame, dstKey,nchunks,null,null);}
  public RebalanceDataSet(Frame srcFrame, Key dstKey, int nchunks, H2O.H2OCountedCompleter cmp, Key jobKey) {
    super(cmp);
    _in = srcFrame;
    _nchunks = nchunks;
    _jobKey = jobKey;
    _okey = dstKey;
  }

  public Frame getResult(){join(); return _out;}

  @Override public void compute2() {
    _in.read_lock(_jobKey);
    // Simply create a bogus new vector (don't even put it into KV) with
    // appropriate number of lines per chunk and then use it as a source to do
    // multiple makeZero calls to create empty vecs and than call RebalanceTask
    // on each one of them.  RebalanceTask will fetch the appropriate training_frame
    // chunks and fetch the data from them.
    int rpc = (int)(_in.numRows() / _nchunks);
    int rem = (int)(_in.numRows() % _nchunks);
    long[] espc = new long[_nchunks+1];
    Arrays.fill(espc,rpc);
    for( int i = 0; i < rem; ++i ) ++espc[i];
    long sum = 0;
    for( int i = 0; i < espc.length; ++i ) {
      long s = espc[i];
      espc[i] = sum;
      sum += s;
    }
    assert espc[espc.length-1] == _in.numRows():"unexpected number of rows, expected " + _in.numRows() + ", got " + espc[espc.length-1];
    final Vec[] srcVecs = _in.vecs();
    _out = new Frame(_okey,_in.names(), new Vec(Vec.newKey(),espc).makeZeros(srcVecs.length,_in.domains(),_in.uuids(), _in.strings(), _in.times()));
    _out.delete_and_lock(_jobKey);
    new RebalanceTask(this,srcVecs).asyncExec(_out);
  }

  @Override public void onCompletion(CountedCompleter caller) {
    assert _out.numRows() == _in.numRows();
    Vec vec = _out.anyVec();
    assert vec.nChunks() == _nchunks;
    _in.unlock(_jobKey);
    _out.update(_jobKey);
    _out.unlock(_jobKey);
  }
  @Override public boolean onExceptionalCompletion(Throwable t, CountedCompleter caller){
    _in.unlock(_jobKey);
    if(_out != null)_out.delete(_jobKey,new Futures()).blockForPending();
    return true;
  }

  public static class RebalanceTask extends MRTask<RebalanceTask> {
    final Vec [] _srcVecs;
    public RebalanceTask(H2O.H2OCountedCompleter cmp, Vec... srcVecs){super(cmp);_srcVecs = srcVecs;}

    @Override public boolean logVerbose() { return false; }

    private void rebalanceChunk(Vec srcVec, Chunk chk){
      NewChunk dst = new NewChunk(chk);
      dst.set_len(0);
      dst.set_sparseLen(dst.len());
      int rem = chk.len();
      while(rem > 0 && dst.len() < chk.len()){
        Chunk srcRaw = srcVec.chunkForRow(chk._start+ dst.len());
        NewChunk src = new NewChunk((srcRaw));
        src = srcRaw.inflate_impl(src);
        assert src.len() == srcRaw.len();
        int srcFrom = (int)(chk._start+ dst.len() - src._start);
        // check if the result is sparse (not exact since we only take subset of training_frame in general)
        if((src.sparse() && dst.sparse()) || (src.sparseLen() + dst.sparseLen() < NewChunk.MIN_SPARSE_RATIO*(src.len() + dst.len()))){
          src.set_sparse(src.sparseLen());
          dst.set_sparse(dst.sparseLen());
        }
        final int srcTo = srcFrom + rem;
        int off = srcFrom-1;
        Iterator<NewChunk.Value> it = src.values(Math.max(0,srcFrom),srcTo);
        while(it.hasNext()){
          NewChunk.Value v = it.next();
          final int rid = v.rowId0();
          assert  rid < srcTo;
          int add = rid - off;
          off = rid;
          dst.addZeros(add-1);
          v.add2Chunk(dst);
          rem -= add;
          assert rem >= 0;
        }
        int trailingZeros = Math.min(rem, src.len() - off -1);
        dst.addZeros(trailingZeros);
        rem -= trailingZeros;
      }
      assert rem == 0:"rem = " + rem;
      assert dst.len() == chk.len() :"len = " + dst.len() + ", _len = " + chk.len();
      dst.close(dst.cidx(),_fs);
    }
    @Override public void map(Chunk [] chks){
      for(int i = 0; i < chks.length; ++i)
        rebalanceChunk(_srcVecs[i],chks[i]);
    }
  }
}
