package water.fvec;

import java.util.Iterator;
import water.AutoBuffer;
import water.H2O;
import water.util.UnsafeUtils;

// Sparse chunk.
public class CXIChunk extends Chunk {
  private transient int _valsz; // byte size of stored value
  protected final int valsz() { return _valsz; }
  private transient int _valsz_log; //
  private transient int _ridsz; // byte size of stored (chunk-relative) row nums
  protected final int ridsz() { return _ridsz; }
  private transient int _sz_log;
  protected static final int _OFF = 6;
  private transient int _lastOff = _OFF;

  private static final long [] NAS = {C1Chunk._NA,C2Chunk._NA,C4Chunk._NA,C8Chunk._NA};

  protected CXIChunk(int len, int nzs, int valsz, byte [] buf){
    assert (valsz == 0 || valsz == 1 || valsz == 2 || valsz == 4 || valsz == 8);
    set_len(len);
    int log = 0;
    while((1 << log) < valsz)++log;
    assert valsz == 0 || (1 << log) == valsz;
    _valsz = valsz;
    _valsz_log = log;

    _ridsz = (len >= 65535)?4:2;
    _sz_log = log+((len >= 65535)?2:1);

    UnsafeUtils.set4(buf, 0, len);
    byte b = (byte) _ridsz;
    buf[4] = b;
    buf[5] = (byte) _valsz;
    _mem = buf;
  }

  @Override public final boolean isSparse() {return true;}
  @Override public final int sparseLen(){ return (_mem.length - _OFF)>>_sz_log; }
  @Override public final int nonzeros(int [] arr){
    int len = sparseLen();
    int off = _OFF;
    final int inc = _valsz + 2;
    for(int i = 0; i < len; ++i, off += inc) arr[i] = UnsafeUtils.get2(_mem, off)&0xFFFF;
    return len;
  }

  @Override boolean set_impl(int idx, long l)   { return false; }
  @Override boolean set_impl(int idx, double d) { return false; }
  @Override boolean set_impl(int idx, float f ) { return false; }
  @Override boolean setNA_impl(int idx)         { return false; }

  @Override protected long at8_impl(int idx) {
    int off = findOffset(idx);
    if(getId(off) != idx)return 0;
    long v = getIValue(off);
    if( v== NAS[_valsz_log])
      throw new IllegalArgumentException("at8 but value is missing");
    return v;
  }
  @Override protected double atd_impl(int idx) {
    int off = findOffset(idx);
    if(getId(off) != idx)return 0;
    long v =  getIValue(off);
    return (v == NAS[_valsz_log])?Double.NaN:v;
  }

  @Override protected boolean isNA_impl( int i ) {
    int off = findOffset(i);
    if(getId(off) != i)return false;
    return getIValue(off) == NAS[_valsz_log];
  }

  @Override NewChunk inflate_impl(NewChunk nc) {
    final int slen = sparseLen();
    nc.set_len(len());
    nc.set_sparseLen(slen);
    nc.alloc_mantissa(slen);
    nc.alloc_exponent(slen);
    nc.alloc_indices(slen);
    int off = _OFF;
    for( int i = 0; i < slen; ++i, off += _ridsz + _valsz) {
      nc.indices()[i] = getId(off);
      long v = getIValue(off);
      if(v == NAS[_valsz_log])
        nc.setNA_impl2(i);
      else
        nc.mantissa()[i] = v;
    }
    return nc;
  }

  // get id of nth (chunk-relative) stored element
  protected final int getId(int off){
    return _ridsz == 2
      ?UnsafeUtils.get2(_mem,off)&0xFFFF
      :UnsafeUtils.get4(_mem,off);
  }
  // get offset of nth (chunk-relative) stored element
  private int getOff(int n){return _OFF + (_ridsz + _valsz)*n;}
  // extract integer value from an (byte)offset
  protected final long getIValue(int off){
    switch(_valsz){
      case 1: return _mem[off+ _ridsz]&0xFF;
      case 2: return UnsafeUtils.get2(_mem, off + _ridsz);
      case 4: return UnsafeUtils.get4(_mem, off + _ridsz);
      case 8: return UnsafeUtils.get8(_mem, off + _ridsz);
      default: throw H2O.unimpl();
   } 
  }

  // find offset of the chunk-relative row id, or -1 if not stored (i.e. sparse zero)
  protected final int findOffset(int idx) {
    if(idx >= len())throw new IndexOutOfBoundsException();
    final byte [] mem = _mem;
    int sparseLen = sparseLen();
    if(sparseLen == 0)return 0;
    final int off = _lastOff;
    int lastIdx = getId(off);
    // check the last accessed elem
    if( idx == lastIdx ) return off;
    if( idx > lastIdx ) {       // check the next one
      final int nextOff = off + _ridsz + _valsz;
      if( nextOff < mem.length ) {
        int nextId =  getId(nextOff);
        if( idx <  nextId ) return off;
        if( idx == nextId ) return (_lastOff = nextOff);
      }
    }
    // no match so far, do binary search
    int lo=0, hi = sparseLen;
    while( lo+1 != hi ) {
      int mid = (hi+lo)>>>1;
      if( idx < getId(getOff(mid))) hi = mid;
      else          lo = mid;
    }
    int y =  getOff(lo);
    _lastOff = y;
    return y;
  }

  @Override public final int nextNZ(int rid){
    final int off = rid == -1?_OFF:findOffset(rid);
    int x = getId(off);
    if(x > rid)return x;
    if(off < _mem.length - _ridsz - _valsz)
      return getId(off + _ridsz + _valsz);
    return len();
  }

  @Override public AutoBuffer write_impl(AutoBuffer bb) { return bb.putA1(_mem, _mem.length); }
  @Override public CXIChunk read_impl(AutoBuffer bb) {
    _mem   = bb.bufClose();
    _start = -1;
    set_len(UnsafeUtils.get4(_mem,0));
    _ridsz = _mem[4];
    _valsz = _mem[5];
    int x = _valsz;
    int log = 0;
    while(x > 1){
      x = x >>> 1;
      ++log;
    }
    _valsz_log = log;
    return this;
  }

  public abstract  class Value {
    protected int _off = 0;
    public int rowInChunk(){return getId(_off);}
    public abstract long asLong();
    public abstract double asDouble();
    public abstract boolean isNA();
  }

  public final class SparseIterator implements Iterator<Value> {
    final Value _val;
    public SparseIterator(Value v){_val = v;}
    @Override public final boolean hasNext(){return _val._off < _mem.length - (_ridsz + _valsz);}
    @Override public final Value next(){
      if(_val._off == 0)_val._off = _OFF;
      else _val._off += (_ridsz + _valsz);
      return _val;
    }
    @Override public final void remove(){throw new UnsupportedOperationException();}
  }
  public Iterator<Value> values(){
    return new SparseIterator(new Value(){
      @Override public final long asLong(){
        long v = getIValue(_off);
        if(v == NAS[(_valsz >>> 1) - 1]) throw new IllegalArgumentException("at8 but value is missing");
        return v;
      }
      @Override public final double asDouble() {
      long v = getIValue(_off);
      return (v == NAS[_valsz_log -1])?Double.NaN:v;
      }
      @Override public final boolean isNA(){
        long v = getIValue(_off);
        return (v == NAS[_valsz_log]);
      }
    });
  }
}
