package water.fvec;

import java.util.*;
import water.*;
import water.parser.ParseTime;
import water.util.Log;
import water.util.PrettyPrint;
import water.util.UnsafeUtils;

// An uncompressed chunk of data, supporting an append operation
public class NewChunk extends Chunk {
  final int _cidx;
  // We can record the following (mixed) data types:
  // 1- doubles, in _ds including NaN for NA & 0; _ls==_xs==null
  // 2- scaled decimals from parsing, in _ls & _xs; _ds==null
  // 3- zero: requires _ls==0 && _xs==0
  // 4- NA: either _ls==0 && _xs==Integer.MIN_VALUE, OR _ds=NaN
  // 5- Enum: _xs==(Integer.MIN_VALUE+1) && _ds==null
  // 6- Str: _ss holds appended string bytes (with trailing 0), _is[] hodls offsets into _ss[]
  // Chunk._len is the count of elements appended
  // Sparse: if _len != _len2, then _ls/_ds are compressed to non-zero's only,
  // and _xs is the row number.  Still _len2 is count of elements including
  // zeros, and _len is count of non-zeros.
  public transient long   _ls[];   // Mantissa
  public transient int    _xs[];   // Exponent, or if _ls==0, NA or Enum or Rows
  public transient int    _id[];   // Indices (row numbers) of stored values, used for sparse
  public transient double _ds[];   // Doubles, for inflating via doubles
  public transient byte   _ss[];       // Bytes of appended strings, including trailing 0
  public transient int    _is[];       // _is[] index of strings - holds offsets into _ss[]. _is[i] == -1 means NA/sparse

  void zero_exp_mant() { _ls=new long[0]; _xs=new int[0]; }
  void zero_indices() { _id=new int[0]; }

  long  [] alloc_mantissa(int l) { return _ls = MemoryManager.malloc8(l); }
  int   [] alloc_exponent(int l) { return _xs = MemoryManager.malloc4(l); }
  int   [] alloc_indices(int l)  { return _id = MemoryManager.malloc4(l); }
  double[] alloc_doubles(int l)  { return _ds = MemoryManager.malloc8d(l); }

  final protected long  [] mantissa() { return _ls; }
  final protected int   [] exponent() { return _xs; }
  final protected int   []  indices() { return _id; }
  final protected double[]  doubles() { return _ds; }

  public int _sslen;                   // Next offset into _ss for placing next String
  public final int sslen() { return _sslen; }
  public int _len2;                    // Actual rows, if the data is sparse
  public final int len2() { return _len2; }
  public int set_len2(int _len2) { return this._len2 = _len2; }
  private int _naCnt=-1;                // Count of NA's   appended
  protected int naCnt() { return _naCnt; }               // Count of NA's   appended
  private int _strCnt;                  // Count of Enum's appended
  protected int strCnt() { return _strCnt; }                 // Count of Enum's appended
  private int _nzCnt;                   // Count of non-zero's appended
  private int _uuidCnt;                 // Count of UUIDs

  public final int _timCnt[] = new int[ParseTime.TIME_PARSE.length]; // Count of successful time parses
  protected static final int MIN_SPARSE_RATIO = 32;

  public NewChunk( Vec vec, int cidx ) { _vec = vec; _cidx = cidx; }
  public NewChunk( Vec vec, int cidx, long[] mantissa, int[] exponent, int[] indices, double[] doubles) {
    _vec = vec; _cidx = cidx;
    _ls = mantissa;
    _xs = exponent;
    _id = indices;
    _ds = doubles;
    if (_ls != null && len()==0) set_len(set_len2(_ls.length));
    if (_xs != null && len()==0) set_len(set_len2(_xs.length));
    if (_id != null && len()==0) set_len(set_len2(_id.length));
    if (_ds != null && len()==0) set_len(set_len2(_ds.length));
  }

  // Constructor used when inflating a Chunk.
  public NewChunk( Chunk C ) {
    this(C._vec, C._vec.elem2ChunkIdx(C._start));
    _start = C._start;
  }

  // Pre-sized newchunks.
  public NewChunk( Vec vec, int cidx, int len ) {
    this(vec,cidx);
    _ds = new double[len];
    Arrays.fill(_ds, Double.NaN);
    set_len(set_len2(len));
  }

  public final class Value {
    int _gId; // row number in dense (ie counting zeros)
    int _lId; // local array index of this value, equal to _gId if dense

    public Value(int lid, int gid){_lId = lid; _gId = gid;}
    public final int rowId0(){return _gId;}
    public void add2Chunk(NewChunk c){
      if(_ds == null) c.addNum(_ls[_lId],_xs[_lId]);
      else {
        if( _ls != null ) c.addUUID(_ls[_lId], Double.doubleToRawLongBits(_ds[_lId]));
        else c.addNum(_ds[_lId]);
      }
    }
  }

  public Iterator<Value> values(int fromIdx, int toIdx){
    final int lId, gId;
    final int to = Math.min(toIdx, len2());

    if(sparse()){
      int x = Arrays.binarySearch(_id,0, len(),fromIdx);
      if(x < 0) x = -x -1;
      lId = x;
      gId = x == len() ? len2() :_id[x];
    } else
      lId = gId = fromIdx;
    final Value v = new Value(lId,gId);
    final Value next = new Value(lId,gId);
    return new Iterator<Value>(){
      @Override public final boolean hasNext(){return next._gId < to;}
      @Override public final Value next(){
        if(!hasNext())throw new NoSuchElementException();
        v._gId = next._gId; v._lId = next._lId;
        next._lId++;
        if(sparse()) next._gId = next._lId < len() ?_id[next._lId]: len2();
        else next._gId++;
        return v;
      }
      @Override
      public void remove() {throw new UnsupportedOperationException();}
    };
  }




  // Heuristic to decide the basic type of a column
  public byte type() {
    if( _naCnt == -1 ) {        // No rollups yet?
      int nas=0, ss=0, nzs=0;
      if( _ds != null && _ls != null ) { // UUID?
        for( int i=0; i< len(); i++ )
          if( _xs != null && _xs[i]==Integer.MIN_VALUE )  nas++;
          else if( _ds[i] !=0 || _ls[i] != 0 ) nzs++;
        _uuidCnt = len2() -nas;
      } else if( _ds != null ) { // Doubles?
        assert _xs==null;
        for( int i = 0; i < len(); ++i) if( Double.isNaN(_ds[i]) ) nas++; else if( _ds[i]!=0 ) nzs++;
      } else {                  // Longs and enums?
        if( _ls != null )
          for( int i=0; i< len(); i++ )
            if( isNA2(i) ) nas++;
            else {
              if( isEnum2(i)   ) ss++;
              if( _ls[i] != 0 ) nzs++;
            }
      }
      _nzCnt=nzs;  _strCnt=ss;  _naCnt=nas;
    }
    // Now run heuristic for type
    if(_naCnt == len2())          // All NAs ==> NA Chunk
      return AppendableVec.NA;
    if(_is != null)
      return AppendableVec.STRING;
    if(_strCnt > 0 && _strCnt + _naCnt == len2())
      return AppendableVec.ENUM; // All are Strings+NAs ==> Enum Chunk
    // UUIDs?
    if( _uuidCnt > 0 ) return AppendableVec.UUID;
    // Larger of time & numbers
    int timCnt=0; for( int t : _timCnt ) timCnt+=t;
    int nums = len2() -_naCnt-timCnt;
    return timCnt >= nums ? AppendableVec.TIME : AppendableVec.NUMBER;
  }

  protected final boolean isNA2(int idx) {
    return (_ds == null) ? (_ls[idx] == Long.MAX_VALUE && _xs[idx] == Integer.MIN_VALUE) : Double.isNaN(_ds[idx]);
  }
  protected final boolean isEnum2(int idx) {
    return _xs!=null && _xs[idx]==Integer.MIN_VALUE+1;
  }
  protected final boolean isEnum(int idx) {
    if(_id == null)return isEnum2(idx);
    int j = Arrays.binarySearch(_id,0, len(),idx);
    return j>=0 && isEnum2(j);
  }

  public void addEnum(int e) {append2(e,Integer.MIN_VALUE+1);}
  public void addNA() {
    if( isUUID() ) addUUID(C16Chunk._LO_NA,C16Chunk._HI_NA);
    else if( isStr() ) addStr(null);
    else if (_ds != null) addNum(Double.NaN);
    else append2(Long.MAX_VALUE,Integer.MIN_VALUE);
  }
  public void addNum (long val, int exp) {
    if( isUUID() ) addNA();
    else if(_ds != null) {
      assert _ls == null;
      addNum(val*PrettyPrint.pow10(exp));
    } else {
      if( val == 0 ) exp = 0;// Canonicalize zero
      long t;                // Remove extra scaling
      while( exp < 0 && exp > -9999999 && (t=val/10)*10==val ) { val=t; exp++; }
      append2(val,exp);
    }
  }
  // Fast-path append double data
  public void addNum(double d) {
    if( isUUID() ) { addNA(); return; }
    if(_id == null || d != 0) {
      if(_ls != null)switch_to_doubles();
      if( _ds == null || len() >= _ds.length ) {
        append2slowd();
        // call addNum again since append2slow might have flipped to sparse
        addNum(d);
        assert len() <= len2();
        return;
      }
      if(_id != null)_id[len()] = len2();
      _ds[len()] = d;
      set_len(len() + 1);
    }
    set_len2(len2() + 1);
    assert len() <= len2();
  }

  private void append_ss(String str) {
    if (_ss == null) {
      _ss = MemoryManager.malloc1((str.length()+1) * 4);
    }
    while (_ss.length < (_sslen + str.length() + 1)) {
      _ss = MemoryManager.arrayCopyOf(_ss,_ss.length << 1);
    }
    for (byte b : str.getBytes())
      _ss[_sslen++] = b;
    _ss[_sslen++] = (byte)0; // for trailing 0;
  }
  // Append a String, stored in _ss & _is
  public void addStr(String str) {
    if(_id == null || str != null) {
      if(_is == null || len() >= _is.length) {
        append2slowstr();
        addStr(str);
        assert len() <= len2();
        return;
      }
      if (str != null) {
        if(_id != null)_id[len()] = len2();
        _is[len()] = _sslen;
        set_len(len() + 1);
        append_ss(str);
      } else {
        _is[len()] = -1;
        set_len(len() + 1);
      }
    }
    set_len2(len2() + 1);
    assert len() <= len2();
  }

  // Append a UUID, stored in _ls & _ds
  public void addUUID( long lo, long hi ) {
    if( _ls==null || _ds== null || len() >= _ls.length )
      append2slowUUID();
    _ls[len()] = lo;
    _ds[len()] = Double.longBitsToDouble(hi);
    set_len(len() + 1);
    set_len2(len2() + 1);
    assert len() <= len2();
  }
  public void addUUID( Chunk c, long row ) {
    if( c.isNA(row) ) addUUID(C16Chunk._LO_NA,C16Chunk._HI_NA);
    else addUUID(c.at16l(row),c.at16h(row));
  }
  public void addUUID( Chunk c, int row ) {
    if( c.isNA0(row) ) addUUID(C16Chunk._LO_NA,C16Chunk._HI_NA);
    else addUUID(c.at16l0(row),c.at16h0(row));
  }

  public final boolean isUUID(){return _ls != null && _ds != null; }
  public final boolean isStr(){return _is != null; }
  public final boolean sparse(){return _id != null;}

  public void addZeros(int n){
    if(!sparse()) for(int i = 0; i < n; ++i)addNum(0,0);
    else set_len2(len2() + n);
  }
  // Append all of 'nc' onto the current NewChunk.  Kill nc.
  public void add( NewChunk nc ) {
    assert _cidx >= 0;
    assert len() <= len2();
    assert nc.len() <= nc.len2() :"_len = " + nc.len() + ", _len2 = " + nc.len2();
    if( nc.len2() == 0 ) return;
    if(len2() == 0){
      _ls = nc._ls; nc._ls = null;
      _xs = nc._xs; nc._xs = null;
      _id = nc._id; nc._id = null;
      _ds = nc._ds; nc._ds = null;
      set_len(nc.len());
      set_len2(nc.len2());
      return;
    }
    if(nc.sparse() != sparse()){ // for now, just make it dense
      cancel_sparse();
      nc.cancel_sparse();
    }
    if( _ds != null ) throw H2O.unimpl();
    while( len() + nc.len() >= _xs.length )
      _xs = MemoryManager.arrayCopyOf(_xs,_xs.length<<1);
    _ls = MemoryManager.arrayCopyOf(_ls,_xs.length);
    System.arraycopy(nc._ls,0,_ls, len(), nc.len());
    System.arraycopy(nc._xs,0,_xs, len(), nc.len());
    if(_id != null) {
      assert nc._id != null;
      _id = MemoryManager.arrayCopyOf(_id,_xs.length);
      System.arraycopy(nc._id,0,_id, len(), nc.len());
      for(int i = len(); i < len() + nc.len(); ++i) _id[i] += len2();
    } else assert nc._id == null;

    set_len(len() + nc.len());
    set_len2(len2() + nc.len2());
    nc._ls = null;  nc._xs = null; nc._id = null; nc.set_len(nc.set_len2(0));
    assert len() <= len2();
  }

  // Fast-path append long data
  void append2( long l, int x ) {
    if(_id == null || l != 0){
      if(_ls == null || len() == _ls.length) {
        append2slow();
        // again call append2 since calling append2slow might have changed things (eg might have switched to sparse and l could be 0)
        append2(l,x);
        return;
      }
      _ls[len()] = l;
      _xs[len()] = x;
      if(_id  != null)_id[len()] = len2();
      set_len(len() + 1);
    }
    set_len2(len2() + 1);
    assert len() <= len2();
  }

  // Slow-path append data
  private void append2slowd() {
    if( len() > Vec.CHUNK_SZ )
      throw new ArrayIndexOutOfBoundsException(len());
    assert _ls==null;
    if(_ds != null && _ds.length > 0){
      if(_id == null){ // check for sparseness
        int nzs = 0; // assume one non-zero for the element currently being stored
        for(double d:_ds)if(d != 0)++nzs;
        if((nzs+1)*MIN_SPARSE_RATIO < len2())
          set_sparse(nzs);
      } else _id = MemoryManager.arrayCopyOf(_id, len() << 1);
      _ds = MemoryManager.arrayCopyOf(_ds, len() << 1);
    } else alloc_doubles(4);
    assert len() == 0 || _ds.length > len() :"_ds.length = " + _ds.length + ", _len = " + len();
  }
  // Slow-path append data
  private void append2slowUUID() {
    if( len() > Vec.CHUNK_SZ )
      throw new ArrayIndexOutOfBoundsException(len());
    if( _ds==null && _ls!=null ) { // This can happen for columns with all NAs and then a UUID
      _xs=null;
      alloc_doubles(len());
      Arrays.fill(_ls,C16Chunk._LO_NA);
      Arrays.fill(_ds,Double.longBitsToDouble(C16Chunk._HI_NA));
    }
    if( _ls != null && _ls.length > 0 ) {
      _ls = MemoryManager.arrayCopyOf(_ls, len() <<1);
      _ds = MemoryManager.arrayCopyOf(_ds, len() <<1);
    } else {
      alloc_mantissa(4);
      alloc_doubles(4);
    }
    assert len() == 0 || _ls.length > len() :"_ls.length = " + _ls.length + ", _len = " + len();
  }
  // Slow-path append string
  private void append2slowstr() {
    if( len() > Vec.CHUNK_SZ )
      throw new ArrayIndexOutOfBoundsException(len());
    if(_is != null && _is.length > 0){
      /*
        Uncomment this block to support sparse string chunks

      if(_id == null){ // check for sparseness
        int nzs = 1; // assume one non-null for the element currently being stored
        for( int i=0; i<_is.length; i++ ) if( _is[0] != -1 ) ++nzs;
        if( nzs*MIN_SPARSE_RATIO < _len2)
          set_sparse(nzs);
      } else {
        if((MIN_SPARSE_RATIO*(_len) >> 1) > _len2)  cancel_sparse();
        else _id = MemoryManager.arrayCopyOf(_id,_len<<1);
      }
      */
      _is = MemoryManager.arrayCopyOf(_is,len()<<1);
      /* initialize the memory extension with -1s */
      for (int i = len(); i < _is.length; i++) _is[i] = -1;
    } else {
      _is = MemoryManager.malloc4 (4);
        /* initialize everything with -1s */
      for (int i = 0; i < _is.length; i++) _is[i] = -1;
    }
    assert len() == 0 || _is.length > len():"_ls.length = " + _is.length + ", len() = " + len();

  }
  // Slow-path append data
  private void append2slow( ) {
    if( len() > Vec.CHUNK_SZ )
      throw new ArrayIndexOutOfBoundsException("NewChunk cannot handle more than " + Vec.CHUNK_SZ + " elements.");
    assert _ds==null;
    if(_ls != null && _ls.length > 0){
      if(_id == null){ // check for sparseness
        int nzs = 0;
        for(int i = 0; i < _ls.length; ++i) if(_ls[i] != 0 || _xs[i] != 0)++nzs;
        if((nzs+1)*MIN_SPARSE_RATIO < len2()){
          set_sparse(nzs);
          assert len() == 0 || len() <= _ls.length:"_len = " + len() + ", _ls.length = " + _ls.length + ", nzs = " + nzs +  ", len2 = " + len2();
          assert _id.length == _ls.length;
          assert len() <= len2();
          return;
        }
      } else {
        // verify we're still sufficiently sparse
        if((MIN_SPARSE_RATIO*(len()) >> 1) > len2())  cancel_sparse();
        else _id = MemoryManager.arrayCopyOf(_id, len() <<1);
      }
      _ls = MemoryManager.arrayCopyOf(_ls, len() <<1);
      _xs = MemoryManager.arrayCopyOf(_xs, len() <<1);
    } else {
      alloc_mantissa(4);
      alloc_exponent(4);
      if (_id != null) alloc_indices(4);
    }
    assert len() == 0 || len() < _ls.length:"_len = " + len() + ", _ls.length = " + _ls.length;
    assert _id == null || _id.length == _ls.length;
    assert len() <= len2();
  }

  // Do any final actions on a completed NewVector.  Mostly: compress it, and
  // do a DKV put on an appropriate Key.  The original NewVector goes dead
  // (does not live on inside the K/V store).
  public Chunk new_close() {
    Chunk chk = compress();
    if(_vec instanceof AppendableVec)
      ((AppendableVec)_vec).closeChunk(this);
    return chk;
  }
  public void close(Futures fs) { close(_cidx,fs); }

  protected void switch_to_doubles(){
    assert _ds == null;
    double [] ds = MemoryManager.malloc8d(len());
    for(int i = 0; i < len(); ++i)
      if(isNA2(i) || isEnum2(i)) ds[i] = Double.NaN;
      else  ds[i] = _ls[i]*PrettyPrint.pow10(_xs[i]);
    _ls = null;
    _xs = null;
    _ds = ds;
  }

  protected void set_sparse(int nzeros){
    if(len() == nzeros)return;
    if(_id != null){ // we have sparse represenation but some 0s in it!
      int [] id = MemoryManager.malloc4(nzeros);
      int j = 0;
      if(_ds != null){
        double [] ds = MemoryManager.malloc8d(nzeros);
        for(int i = 0; i < len(); ++i){
          if(_ds[i] != 0){
            ds[j] = _ds[i];
            id[j] = _id[i];
            ++j;
          }
        }
        _ds = ds;
      } else {
        long [] ls = MemoryManager.malloc8(nzeros);
        int [] xs = MemoryManager.malloc4(nzeros);
        for(int i = 0; i < len(); ++i){
          if(_ls[i] != 0){
            ls[j] = _ls[i];
            xs[j] = _xs[i];
            id[j] = _id[i];
            ++j;
          }
        }
        _ls = ls;
        _xs = xs;
      }
      _id = id;
      assert j == nzeros;
      return;
    }
    assert len() == len2() :"_len = " + len() + ", _len2 = " + len2() + ", nzeros = " + nzeros;
    int zs = 0;
    if(_is != null) {
      assert nzeros < _is.length;
      _id = MemoryManager.malloc4(_is.length);
      for (int i = 0; i < len(); i++) {
        if (_is[i] == -1) zs++;
        else {
          _is[i-zs] = _is[i];
          _id[i-zs] = i;
        }
      }
    } else if(_ds == null){
      assert nzeros < len();
      _id = alloc_indices(_ls.length);
      for(int i = 0; i < len(); ++i){
        if((_ls == null || _ls[i] == 0) && (_xs == null || _xs[i] == 0))++zs;
        else {
          _ls[i-zs] = _ls[i];
          _xs[i-zs] = _xs[i];
          _id[i-zs] = i;
        }
      }
    } else {
      assert nzeros < _ds.length;
      _id = alloc_indices(_ds.length);
      for(int i = 0; i < len(); ++i){
        if(_ds[i] == 0)++zs;
        else {
          _ds[i-zs] = _ds[i];
          _id[i-zs] = i;
        }
      }
    }
    assert zs == (len() - nzeros);
    set_len(nzeros);
  }
  protected void cancel_sparse(){
    if(len() != len2()){
      if(_is != null){
        int [] is = MemoryManager.malloc4(len2());
        for(int i = 0; i < len2(); i++) is[i] = -1;
        for (int i = 0; i < len(); i++) is[_id[i]] = _is[i];
        _is = is;
      } else if(_ds == null){
        int []  xs = MemoryManager.malloc4(len2());
        long [] ls = MemoryManager.malloc8(len2());
        for(int i = 0; i < len(); ++i){
          xs[_id[i]] = _xs[i];
          ls[_id[i]] = _ls[i];
        }
        _xs = xs;
        _ls = ls;
      } else {
        double [] ds = MemoryManager.malloc8d(len2());
        for(int i = 0; i < len(); ++i) ds[_id[i]] = _ds[i];
        _ds = ds;
      }
      _id = null;
      set_len(len2());
    }
  }
  // Study this NewVector and determine an appropriate compression scheme.
  // Return the data so compressed.
  static final int MAX_FLOAT_MANTISSA = 0x7FFFFF;

  Chunk compress() {
    Chunk res = compress2();
    // force everything to null after compress to free up the memory
    _id = null;
    _xs = null;
    _ds = null;
    _ls = null;
    _is = null;
    _ss = null;
    return res;
  }
  private Chunk compress2() {
    // Check for basic mode info: all missing or all strings or mixed stuff
    byte mode = type();
    if( mode==AppendableVec.NA ) // ALL NAs, nothing to do
      return new C0DChunk(Double.NaN, len());
    if( mode==AppendableVec.STRING )
      return chunkStr();
    boolean rerun=false;
    if(mode == AppendableVec.ENUM){
      for( int i=0; i< len(); i++ )
        if(isEnum2(i))
          _xs[i] = 0;
        else if(!isNA2(i)){
          setNA_impl2(i);
          ++_naCnt;
        }
        // Smack any mismatched string/numbers
    } else if(mode == AppendableVec.NUMBER){
      for( int i=0; i< len(); i++ )
        if(isEnum2(i)) {
          setNA_impl2(i);
          rerun = true;
        }
    }
    if( rerun ) { _naCnt = -1;  type(); } // Re-run rollups after dropping all numbers/enums
    boolean sparse = false;
    // sparse? treat as sparse iff we have at least MIN_SPARSE_RATIOx more zeros than nonzeros
    if(MIN_SPARSE_RATIO*(_naCnt + _nzCnt) < len2()) {
      set_sparse(_naCnt + _nzCnt);
      sparse = true;
    }

    // If the data is UUIDs there's not much compression going on
    if( _ds != null && _ls != null )
      return chunkUUID();

    // If the data was set8 as doubles, we do a quick check to see if it's
    // plain longs.  If not, we give up and use doubles.
    if( _ds != null ) {
      int i=0;
      boolean isConstant = true;
      boolean isInteger = true;
      for( ; i< len(); i++ ) {
        if (!Double.isNaN(_ds[i])) isInteger &= (double) (long) _ds[i] == _ds[i];
        isConstant &= _ds[i] == _ds[0];
      }
      if (!isInteger) {
        if (isConstant) return new C0DChunk(_ds[0], len());
        if (sparse) return new CXDChunk(len2(), len(), 8, bufD(8));
        else return chunkD();
      }

      _ls = new long[_ds.length]; // Else flip to longs
      _xs = new int [_ds.length];
      double [] ds = _ds;
      _ds = null;
      final int naCnt = _naCnt;
      for( i=0; i< len(); i++ )   // Inject all doubles into longs
        if( Double.isNaN(ds[i]) )setNA_impl2(i);
        else                     _ls[i] = (long)ds[i];
      // setNA_impl2 will set _naCnt to -1!
      // we already know what the naCnt is (it did not change!) so set it back to correct value
      _naCnt = naCnt;
    }

    // IF (_len2 > _len) THEN Sparse
    // Check for compressed *during appends*.  Here we know:
    // - No specials; _xs[]==0.
    // - No floats; _ds==null
    // - NZ length in _len, actual length in _len2.
    // - Huge ratio between _len2 and _len, and we do NOT want to inflate to
    //   the larger size; we need to keep it all small all the time.
    // - Rows in _xs

    // Data in some fixed-point format, not doubles
    // See if we can sanely normalize all the data to the same fixed-point.
    int  xmin = Integer.MAX_VALUE;   // min exponent found
    boolean floatOverflow = false;
    double min = Double.POSITIVE_INFINITY;
    double max = Double.NEGATIVE_INFINITY;
    int p10iLength = PrettyPrint.powers10i.length;
    long llo=Long   .MAX_VALUE, lhi=Long   .MIN_VALUE;
    int  xlo=Integer.MAX_VALUE, xhi=Integer.MIN_VALUE;

    for( int i=0; i< len(); i++ ) {
      if( isNA2(i) ) continue;
      long l = _ls[i];
      int  x = _xs[i];
      assert x != Integer.MIN_VALUE:"l = " + l + ", x = " + x;
      if( x==Integer.MIN_VALUE+1) x=0; // Replace enum flag with no scaling
      assert l!=0 || x==0:"l == 0 while x = " + x + " ls = " + Arrays.toString(_ls);      // Exponent of zero is always zero
      long t;                   // Remove extra scaling
      while( l!=0 && (t=l/10)*10==l ) { l=t; x++; }
      // Compute per-chunk min/max
      double d = l*PrettyPrint.pow10(x);
      if( d < min ) { min = d; llo=l; xlo=x; }
      if( d > max ) { max = d; lhi=l; xhi=x; }
      floatOverflow = Math.abs(l) > MAX_FLOAT_MANTISSA;
      xmin = Math.min(xmin,x);
    }

    if(len2() != len()){ // sparse?  then compare vs implied 0s
      if( min > 0 ) { min = 0; llo=0; xlo=0; }
      if( max < 0 ) { max = 0; lhi=0; xhi=0; }
      xmin = Math.min(xmin,0);
    }

    // Constant column?
    if( _naCnt==0 && (min==max)) {
      if (llo == lhi && xlo == 0 && xhi == 0)
        return new C0LChunk(llo, len2());
      else if ((long)min == min)
        return new C0LChunk((long)min, len2());
      else
        return new C0DChunk(min, len2());
    }

    // Compute min & max, as scaled integers in the xmin scale.
    // Check for overflow along the way
    boolean overflow = ((xhi-xmin) >= p10iLength) || ((xlo-xmin) >= p10iLength);
    long lemax=0, lemin=0;
    if( !overflow ) {           // Can at least get the power-of-10 without overflow
      long pow10 = PrettyPrint.pow10i(xhi-xmin);
      lemax = lhi*pow10;
      // Hacker's Delight, Section 2-13, checking overflow.
      // Note that the power-10 is always positive, so the test devolves this:
      if( (lemax/pow10) != lhi ) overflow = true;
      // Note that xlo might be > xmin; e.g. { 101e-49 , 1e-48}.
      long pow10lo = PrettyPrint.pow10i(xlo-xmin);
      lemin = llo*pow10lo;
      if( (lemin/pow10lo) != llo ) overflow = true;
    }

    // Boolean column?
    if (max == 1 && min == 0 && xmin == 0 && !overflow) {
      if(sparse) { // Very sparse?
        return  _naCnt==0
          ? new CX0Chunk(len2(), len(),bufS(0))// No NAs, can store as sparse bitvector
          : new CXIChunk(len2(), len(),1,bufS(1)); // have NAs, store as sparse 1byte values
      }

      int bpv = _strCnt+_naCnt > 0 ? 2 : 1;   // Bit-vector
      byte[] cbuf = bufB(bpv);
      return new CBSChunk(cbuf, cbuf[0], cbuf[1]);
    }

    final boolean fpoint = xmin < 0 || min < Long.MIN_VALUE || max > Long.MAX_VALUE;

    if( sparse ) {
      if(fpoint) return new CXDChunk(len2(), len(),8,bufD(8));
      int sz = 8;
      if( Short.MIN_VALUE <= min && max <= Short.MAX_VALUE ) sz = 2;
      else if( Integer.MIN_VALUE <= min && max <= Integer.MAX_VALUE ) sz = 4;
      return new CXIChunk(len2(), len(),sz,bufS(sz));
    }
    // Exponent scaling: replacing numbers like 1.3 with 13e-1.  '13' fits in a
    // byte and we scale the column by 0.1.  A set of numbers like
    // {1.2,23,0.34} then is normalized to always be represented with 2 digits
    // to the right: {1.20,23.00,0.34} and we scale by 100: {120,2300,34}.
    // This set fits in a 2-byte short.

    // We use exponent-scaling for bytes & shorts only; it's uncommon (and not
    // worth it) for larger numbers.  We need to get the exponents to be
    // uniform, so we scale up the largest lmax by the largest scale we need
    // and if that fits in a byte/short - then it's worth compressing.  Other
    // wise we just flip to a float or double representation.
    if( overflow || (fpoint && floatOverflow) || -35 > xmin || xmin > 35 )
      return chunkD();
    if( fpoint ) {
      if( (int)lemin == lemin && (int)lemax == lemax ) {
        if(lemax-lemin < 255 && (int)lemin == lemin ) // Fits in scaled biased byte?
          return new C1SChunk( bufX(lemin,xmin,C1SChunk._OFF,0),(int)lemin,PrettyPrint.pow10(xmin));
        if(lemax-lemin < 65535 ) { // we use signed 2B short, add -32k to the bias!
          long bias = 32767 + lemin;
          return new C2SChunk( bufX(bias,xmin,C2SChunk._OFF,1),(int)bias,PrettyPrint.pow10(xmin));
        }
        if(lemax - lemin < Integer.MAX_VALUE)
          return new C4SChunk(bufX(lemin, xmin,C4SChunk._OFF,2),(int)lemin,PrettyPrint.pow10(xmin));
      }
      return chunkD();
    } // else an integer column

    // Compress column into a byte
    if(xmin == 0 &&  0<=lemin && lemax <= 255 && ((_naCnt + _strCnt)==0) )
      return new C1NChunk( bufX(0,0,C1NChunk._OFF,0));
    if( lemin < Integer.MIN_VALUE ) return new C8Chunk( bufX(0,0,0,3));
    if( lemax-lemin < 255 ) {    // Span fits in a byte?
      if(0 <= min && max < 255 ) // Span fits in an unbiased byte?
        return new C1Chunk( bufX(0,0,C1Chunk._OFF,0));
      return new C1SChunk( bufX(lemin,xmin,C1SChunk._OFF,0),(int)lemin,PrettyPrint.pow10i(xmin));
    }

    // Compress column into a short
    if( lemax-lemin < 65535 ) {               // Span fits in a biased short?
      if( xmin == 0 && Short.MIN_VALUE < lemin && lemax <= Short.MAX_VALUE ) // Span fits in an unbiased short?
        return new C2Chunk( bufX(0,0,C2Chunk._OFF,1));
      int bias = (int)(lemin-(Short.MIN_VALUE+1));
      return new C2SChunk( bufX(bias,xmin,C2SChunk._OFF,1),bias,PrettyPrint.pow10i(xmin));
    }
    // Compress column into ints
    if( Integer.MIN_VALUE < min && max <= Integer.MAX_VALUE )
      return new C4Chunk( bufX(0,0,0,2));
    return new C8Chunk( bufX(0,0,0,3));
  }

  private static long [] NAS = {C1Chunk._NA,C2Chunk._NA,C4Chunk._NA,C8Chunk._NA};

  // Compute a sparse integer buffer
  private byte[] bufS(final int valsz){
    int log = 0;
    while((1 << log) < valsz)++log;
    assert valsz == 0 || (1 << log) == valsz;
    final int ridsz = len2() >= 65535?4:2;
    final int elmsz = ridsz + valsz;
    int off = CXIChunk._OFF;
    byte [] buf = MemoryManager.malloc1(off + len() *elmsz,true);
    for( int i=0; i< len(); i++, off += elmsz ) {
      if(ridsz == 2)
        UnsafeUtils.set2(buf,off,(short)_id[i]);
      else
        UnsafeUtils.set4(buf,off,_id[i]);
      if(valsz == 0){
        assert _xs[i] == 0 && _ls[i] == 1;
        continue;
      }
      assert _xs[i] == Integer.MIN_VALUE || _xs[i] >= 0:"unexpected exponent " + _xs[i]; // assert we have int or NA
      final long lval = _xs[i] == Integer.MIN_VALUE ? NAS[log] : _ls[i]*PrettyPrint.pow10i(_xs[i]);
      switch(valsz){
        case 1:
          buf[off+ridsz] = (byte)lval;
          break;
        case 2:
          short sval = (short)lval;
          UnsafeUtils.set2(buf,off+ridsz,sval);
          break;
        case 4:
          int ival = (int)lval;
          UnsafeUtils.set4(buf, off+ridsz, ival);
          break;
        case 8:
          UnsafeUtils.set8(buf, off+ridsz, lval);
          break;
        default:
          throw H2O.unimpl();
      }
    }
    assert off==buf.length;
    return buf;
  }

  // Compute a sparse float buffer
  private byte[] bufD(final int valsz){
    int log = 0;
    while((1 << log) < valsz)++log;
    assert (1 << log) == valsz;
    final int ridsz = len2() >= 65535?4:2;
    final int elmsz = ridsz + valsz;
    int off = CXDChunk._OFF;
    byte [] buf = MemoryManager.malloc1(off + len() *elmsz,true);
    for( int i=0; i< len(); i++, off += elmsz ) {
      if(ridsz == 2)
        UnsafeUtils.set2(buf,off,(short)_id[i]);
      else
        UnsafeUtils.set4(buf,off,_id[i]);
      final double dval = _ds == null?isNA2(i)?Double.NaN:_ls[i]*PrettyPrint.pow10(_xs[i]):_ds[i];
      switch(valsz){
        case 4:
          UnsafeUtils.set4f(buf, off + ridsz, (float) dval);
          break;
        case 8:
          UnsafeUtils.set8d(buf, off + ridsz, dval);
          break;
        default:
          throw H2O.unimpl();
      }
    }
    assert off==buf.length;
    return buf;
  }
  // Compute a compressed integer buffer
  private byte[] bufX( long bias, int scale, int off, int log ) {
    byte[] bs = new byte[(len2() <<log)+off];
    int j = 0;
    for( int i=0; i< len2(); i++ ) {
      long le = -bias;
      if(_id == null || (j < _id.length && _id[j] == i)){
        if( isNA2(j) ) {
          le = NAS[log];
        } else {
          int x = (_xs[j]==Integer.MIN_VALUE+1 ? 0 : _xs[j])-scale;
          le += x >= 0
              ? _ls[j]*PrettyPrint.pow10i( x)
              : _ls[j]/PrettyPrint.pow10i(-x);
        }
        ++j;
      }
      switch( log ) {
      case 0:          bs [i    +off] = (byte)le ; break;
      case 1: UnsafeUtils.set2(bs,(i<<1)+off,  (short)le); break;
      case 2: UnsafeUtils.set4(bs,(i<<2)+off,    (int)le); break;
      case 3: UnsafeUtils.set8(bs,(i<<3)+off,         le); break;
      default: throw H2O.fail();
      }
    }
    assert j == len() :"j = " + j + ", len = " + len() + ", len2 = " + len2() + ", id[j] = " + _id[j];
    return bs;
  }

  // Compute a compressed double buffer
  private Chunk chunkD() {
    final byte [] bs = MemoryManager.malloc1(len2() *8,true);
    int j = 0;
    for(int i = 0; i < len2(); ++i){
      double d = 0;
      if(_id == null || (j < _id.length && _id[j] == i)) {
        d = _ds != null?_ds[j]:(isNA2(j)||isEnum(j))?Double.NaN:_ls[j]*PrettyPrint.pow10(_xs[j]);
        ++j;
      }
      UnsafeUtils.set8d(bs, 8*i, d);
    }
    assert j == len() :"j = " + j + ", _len = " + len();
    return new C8DChunk(bs);
  }

  // Compute a compressed double buffer
  private Chunk chunkUUID() {
    final byte [] bs = MemoryManager.malloc1(len2() *16,true);
    int j = 0;
    for( int i = 0; i < len2(); ++i ) {
      long lo = 0, hi=0;
      if( _id == null || (j < _id.length && _id[j] == i ) ) {
        lo = _ls[j];
        hi = Double.doubleToRawLongBits(_ds[j++]);
        if( _xs != null && _xs[j] == Integer.MAX_VALUE){// NA?
          lo = Long.MIN_VALUE; hi = 0;                  // Canonical NA value
        }
      }
      UnsafeUtils.set8(bs, 16*i  , lo);
      UnsafeUtils.set8(bs, 16 * i + 8, hi);
    }
    assert j == len() :"j = " + j + ", _len = " + len();
    return new C16Chunk(bs);
  }

  private Chunk chunkStr() {
    final byte [] strbuf = Arrays.copyOf(_ss, _sslen);
    final byte [] idbuf = MemoryManager.malloc1(len2()*4,true);
    for( int i = 0; i < len2(); ++i ) {
      UnsafeUtils.set4(idbuf, 4*i, _is[i]);
    }
    return new CStrChunk(strbuf, idbuf, len2());
  }

  // Compute compressed boolean buffer
  private byte[] bufB(int bpv) {
    assert bpv == 1 || bpv == 2 : "Only bit vectors with/without NA are supported";
    final int off = CBSChunk._OFF;
    int clen  = off + CBSChunk.clen(len2(), bpv);
    byte bs[] = new byte[clen];
    // Save the gap = number of unfilled bits and bpv value
    bs[0] = (byte) (((len2() *bpv)&7)==0 ? 0 : (8-((len2() *bpv)&7)));
    bs[1] = (byte) bpv;

    // Dense bitvector
    int  boff = 0;
    byte b    = 0;
    int  idx  = CBSChunk._OFF;
    int j = 0;
    for (int i=0; i< len2(); i++) {
      byte val = 0;
      if(_id == null || (j < _id.length && _id[j] == i)) {
        assert bpv == 2 || !isNA2(j);
        val = (byte)(isNA2(j)?CBSChunk._NA:_ls[j]);
        ++j;
      }
      if( bpv==1 )
        b = CBSChunk.write1b(b, val, boff);
      else
        b = CBSChunk.write2b(b, val, boff);
      boff += bpv;
      if (boff>8-bpv) { assert boff == 8; bs[idx] = b; boff = 0; b = 0; idx++; }
    }
    assert j == len();
    assert bs[0] == (byte) (boff == 0 ? 0 : 8-boff):"b[0] = " + bs[0] + ", boff = " + boff + ", bpv = " + bpv;
    // Flush last byte
    if (boff>0) bs[idx] = b;
    return bs;
  }

  // Set & At on NewChunks are weird: only used after inflating some other
  // chunk.  At this point the NewChunk is full size, no more appends allowed,
  // and the xs exponent array should be only full of zeros.  Accesses must be
  // in-range and refer to the inflated values of the original Chunk.
  @Override boolean set_impl(int i, long l) {
    if( _ds   != null ) return set_impl(i,(double)l);
    if(len() != len2()){ // sparse?
      int idx = Arrays.binarySearch(_id,0, len(),i);
      if(idx >= 0)i = idx;
      else cancel_sparse(); // for now don't bother setting the sparse value
    }
    _ls[i]=l; _xs[i]=0;
    return true;
  }

  @Override public boolean set_impl(int i, double d) {
    if(_ds == null){
      assert len() == 0 || _ls != null;
      switch_to_doubles();
    }
    if(len() != len2()){ // sparse?
      int idx = Arrays.binarySearch(_id,0, len(),i);
      if(idx >= 0)i = idx;
      else cancel_sparse(); // for now don't bother setting the sparse value
    }
    assert i < len();
    _ds[i] = d;
    return true;
  }
  @Override boolean set_impl(int i, float f) {  return set_impl(i,(double)f); }

  @Override boolean set_impl(int i, String str) {
    if(len() != len2()){ // sparse?
      int idx = Arrays.binarySearch(_id,0,len(),i);
      if(idx >= 0)i = idx;
      else cancel_sparse(); // for now don't bother setting the sparse value
    }
    _is[i] = _sslen;
    append_ss(str);
    return true;
  }

  protected final boolean setNA_impl2(int i) {
    if( isNA2(i) ) return true;
    if( _ls != null ) { _ls[i] = Long.MAX_VALUE; _xs[i] = Integer.MIN_VALUE; }
    if( _ds != null ) { _ds[i] = Double.NaN; }
    return true;
  }
  @Override boolean setNA_impl(int i) {
    if( isNA_impl(i) ) return true;
    if(len() != len2()){
      int idx = Arrays.binarySearch(_id,0, len(),i);
      if(idx >= 0) i = idx;
      else cancel_sparse(); // todo - do not necessarily cancel sparse here
    }
    return setNA_impl2(i);
  }
  @Override public long   at8_impl( int i ) {
    if( len2() != len()) {
      int idx = Arrays.binarySearch(_id,0, len(),i);
      if(idx >= 0) i = idx;
      else return 0;
    }
    if( _ls == null ) return (long)_ds[i];
    return _ls[i]*PrettyPrint.pow10i(_xs[i]);
  }
  @Override public double atd_impl( int i ) {
    if( len2() != len()) {
      int idx = Arrays.binarySearch(_id,0, len(),i);
      if(idx >= 0) i = idx;
      else return 0;
    }
    if( _ds == null ) return at8_impl(i);
    assert _xs==null; return _ds[i];
  }
  @Override public boolean isNA_impl( int i ) {
    if( len2() != len()) {
      int idx = Arrays.binarySearch(_id,0, len(),i);
      if(idx >= 0) i = idx;
      else return false;
    }
    return isNA2(i);
  }
  @Override public String atStr_impl( int i ) {
    /*
      Uncomment this block to support sparse string chunks

    if( _len2 != _len ) {
      int idx = Arrays.binarySearch(_id,0,_len,i);
      if(idx >= 0) i = idx;
      else return null;
    }
    */
    int len;
    if (_is[i] != -1) { //check for NAs
      for (len = 0; _ss[_is[i] + len] != 0; len++) ;
      return new String(_ss, _is[i], len);
    } else return null;
  }
  @Override public NewChunk read_impl(AutoBuffer bb) { throw H2O.fail(); }
  @Override public AutoBuffer write_impl(AutoBuffer bb) { throw H2O.fail(); }
  @Override NewChunk inflate_impl(NewChunk nc) { throw H2O.fail(); }
  @Override public String toString() { return "NewChunk._len="+ len(); }
}
