package water.fvec;

import java.util.*;
import water.*;
import water.parser.*;
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
  // 6- Str: _ss holds appended string bytes (with trailing 0), _is[] holds offsets into _ss[]
  // Chunk._len is the count of elements appended
  // Sparse: if _sparseLen != _len, then _ls/_ds are compressed to non-zero's only,
  // and _xs is the row number.  Still _len is count of elements including
  // zeros, and _sparseLen is count of non-zeros.
  public transient long   _ls[];   // Mantissa
  public transient int    _xs[];   // Exponent, or if _ls==0, NA or Enum or Rows
  public transient int    _id[];   // Indices (row numbers) of stored values, used for sparse
  public transient double _ds[];   // Doubles, for inflating via doubles
  public transient byte   _ss[];   // Bytes of appended strings, including trailing 0
  public transient int    _is[];   // _is[] index of strings - holds offsets into _ss[]. _is[i] == -1 means NA/sparse

  long  [] alloc_mantissa(int l) { return _ls = MemoryManager.malloc8(l); }
  int   [] alloc_exponent(int l) { return _xs = MemoryManager.malloc4(l); }
  int   [] alloc_indices(int l)  { return _id = MemoryManager.malloc4(l); }
  double[] alloc_doubles(int l)  { return _ds = MemoryManager.malloc8d(l); }
  int   [] alloc_str_indices(int l) { return _is = MemoryManager.malloc4(l); }

  final protected long  [] mantissa() { return _ls; }
  final protected int   [] exponent() { return _xs; }
  final protected int   []  indices() { return _id; }
  final protected double[]  doubles() { return _ds; }

  @Override public boolean isSparse() { return sparse(); }

  public int _sslen;                   // Next offset into _ss for placing next String
  public final int sslen() { return _sslen; }

  public int _sparseLen;
  int set_sparseLen(int l) { return this._sparseLen = l; }
  @Override public int sparseLen() { return _sparseLen; }

  private int _naCnt=-1;                // Count of NA's   appended
  protected int naCnt() { return _naCnt; }               // Count of NA's   appended
  private int _enumCnt;                  // Count of Enum's appended
  protected int enumCnt() { return _enumCnt; }                 // Count of Enum's appended
  private int _strCnt;                  // Count of string's appended
  protected int strCnt() { return _strCnt; }                 // Count of strings's appended
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
    if (_ls != null && sparseLen()==0) set_sparseLen(set_len(_ls.length));
    if (_xs != null && sparseLen()==0) set_sparseLen(set_len(_xs.length));
    if (_id != null && sparseLen()==0) set_sparseLen(set_len(_id.length));
    if (_ds != null && sparseLen()==0) set_sparseLen(set_len(_ds.length));
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
    set_sparseLen(set_len(len));
  }

  public NewChunk convertEnum2Str(ValueString[] emap) {
    NewChunk strChunk = new NewChunk(_vec, _cidx);
    int j = 0, l = len();
    for( int i = 0; i < l; ++i ) {
      if( _id != null && _id.length > 0 && (j < _id.length && _id[j] == i ) ) // Sparse storage
        // adjust for enum ids using 1-based indexing
        strChunk.addStr(emap[(int) _ls[j++] - 1]);
      else if (_xs[i] != Integer.MIN_VALUE) // Enum value isn't NA
        strChunk.addStr(emap[(int) _ls[i] - 1]);
      else
        strChunk.addNA();
    }
    if (_id != null)
      assert j == sparseLen() :"j = " + j + ", sparseLen = " + sparseLen();
    return strChunk;
  }

  public final class Value {
    int _gId; // row number in dense (ie counting zeros)
    int _lId; // local array index of this value, equal to _gId if dense

    public Value(int lid, int gid){_lId = lid; _gId = gid;}
    public final int rowId0(){return _gId;}
    public void add2Chunk(NewChunk c){
      if (_ds == null && _ss == null) {
          c.addNum(_ls[_lId],_xs[_lId]);
      } else {
        if (_ls != null) {
          c.addUUID(_ls[_lId], Double.doubleToRawLongBits(_ds[_lId]));
        } else if (_ss != null) {
          int sidx = _is[_lId];
          int slen = _lId+1 < _is.length ? _is[_lId+1]-sidx : _sslen - sidx;
          ValueString vstr = new ValueString().set(_ss, sidx, slen);
          c.addStr(vstr);
        } else
          c.addNum(_ds[_lId]);
      }
    }
  }

  public Iterator<Value> values(int fromIdx, int toIdx){
    final int lId, gId;
    final int to = Math.min(toIdx, len());

    if(sparse()){
      int x = Arrays.binarySearch(_id,0, sparseLen(),fromIdx);
      if(x < 0) x = -x -1;
      lId = x;
      gId = x == sparseLen() ? len() :_id[x];
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
        if(sparse()) next._gId = next._lId < sparseLen() ?_id[next._lId]: len();
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
      int nas=0, es=0, nzs=0, ss=0;
      if( _ds != null && _ls != null ) { // UUID?
        for( int i=0; i< sparseLen(); i++ )
          if( _xs != null && _xs[i]==Integer.MIN_VALUE )  nas++;
          else if( _ds[i] !=0 || _ls[i] != 0 ) nzs++;
        _uuidCnt = len() -nas;
      } else if( _ds != null ) { // Doubles?
        assert _xs==null;
        for( int i = 0; i < sparseLen(); ++i) if( Double.isNaN(_ds[i]) ) nas++; else if( _ds[i]!=0 ) nzs++;
      } else {
        if( _ls != null ) // Longs and enums?
          for( int i=0; i< sparseLen(); i++ )
            if( isNA2(i) ) nas++;
            else {
              if( isEnum2(i)   ) es++;
              if( _ls[i] != 0 ) nzs++;
            }
        if( _is != null )  // Strings
          for( int i=0; i< sparseLen(); i++ )
            if( isNA2(i) ) nas++;
            else ss++;
      }
      _nzCnt=nzs;  _enumCnt =es;  _naCnt=nas; _strCnt = ss;
    }
    // Now run heuristic for type
    if(_naCnt == len())          // All NAs ==> NA Chunk
      return AppendableVec.NA;
    if(_strCnt > 0)
      return AppendableVec.STRING;
    if(_enumCnt > 0 && _enumCnt + _naCnt == len())
      return AppendableVec.ENUM; // All are Strings+NAs ==> Enum Chunk
    // UUIDs?
    if( _uuidCnt > 0 ) return AppendableVec.UUID;
    // Larger of time & numbers
    int timCnt=0; for( int t : _timCnt ) timCnt+=t;
    int nums = len() -_naCnt-timCnt;
    return timCnt >= nums ? AppendableVec.TIME : AppendableVec.NUMBER;
  }

  //what about sparse reps?
  protected final boolean isNA2(int idx) {
    if (isUUID()) return _ls[idx]==C16Chunk._LO_NA && Double.doubleToRawLongBits(_ds[idx])==C16Chunk._HI_NA;
    if (isString()) return _is[idx] == -1;
    return (_ds == null) ? (_ls[idx] == Long.MAX_VALUE && _xs[idx] == Integer.MIN_VALUE) : Double.isNaN(_ds[idx]);
  }
  protected final boolean isEnum2(int idx) {
    return _xs!=null && _xs[idx]==Integer.MIN_VALUE+1;
  }
  protected final boolean isEnum(int idx) {
    if(_id == null)return isEnum2(idx);
    int j = Arrays.binarySearch(_id,0, sparseLen(),idx);
    return j>=0 && isEnum2(j);
  }

  public void addEnum(int e) {append2(e,Integer.MIN_VALUE+1);}
  public void addNA() {
    if( isUUID() ) addUUID(C16Chunk._LO_NA, C16Chunk._HI_NA);
    else if( isString() ) addStr(null);
    else if (_ds != null) addNum(Double.NaN);
    else append2(Long.MAX_VALUE,Integer.MIN_VALUE);
  }
  public void addNum (long val, int exp) {
    if( isUUID() || isString() ) addNA();
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
    if( isUUID() || isString() ) { addNA(); return; }
    if(_id == null || d != 0) {
      if(_ls != null)switch_to_doubles();
      if( _ds == null || sparseLen() >= _ds.length ) {
        append2slowd();
        // call addNum again since append2slow might have flipped to sparse
        addNum(d);
        assert sparseLen() <= len();
        return;
      }
      if(_id != null)_id[sparseLen()] = len();
      _ds[sparseLen()] = d;
      set_sparseLen(sparseLen() + 1);
    }
    set_len(len() + 1);
    assert sparseLen() <= len();
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

  private void append_ss(ValueString str) {
    int strlen = str.get_length();
    int off = str.get_off();
    byte b[] = str.get_buf();

    if (_ss == null) {
      _ss = MemoryManager.malloc1((strlen + 1) * 4);
    }
    while (_ss.length < (_sslen + strlen + 1)) {
      _ss = MemoryManager.arrayCopyOf(_ss,_ss.length << 1);
    }
    for (int i = off; i < off+strlen; i++)
      _ss[_sslen++] = b[i];
    _ss[_sslen++] = (byte)0; // for trailing 0;
  }

  // Append a String, stored in _ss & _is
  public void addStr(ValueString str) {
    if(_id == null || str != null) {
      if(_is == null || sparseLen() >= _is.length) {
        append2slowstr();
        addStr(str);
        assert sparseLen() <= len();
        return;
      }
      if (str != null) {
        if(_id != null)_id[sparseLen()] = len();
        _is[sparseLen()] = _sslen;
        set_sparseLen(sparseLen() + 1);
        append_ss(str);
      } else if (_id == null) {
        _is[sparseLen()] = -1;
        set_sparseLen(sparseLen() + 1);
      }
    }
    set_len(len() + 1);
    assert sparseLen() <= len();
  }

  // Append a UUID, stored in _ls & _ds
  public void addUUID( long lo, long hi ) {
    if( _ls==null || _ds== null || sparseLen() >= _ls.length )
      append2slowUUID();
    _ls[sparseLen()] = lo;
    _ds[sparseLen()] = Double.longBitsToDouble(hi);
    set_sparseLen(sparseLen() + 1);
    set_len(len() + 1);
    assert sparseLen() <= len();
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
  public final boolean isString(){return _is != null; }
  public final boolean sparse(){return _id != null;}

  public void addZeros(int n){
    if(!sparse()) for(int i = 0; i < n; ++i)addNum(0,0);
    else set_len(len() + n);
  }
  // Append all of 'nc' onto the current NewChunk.  Kill nc.
  public void add( NewChunk nc ) {
    assert _cidx >= 0;
    assert sparseLen() <= len();
    assert nc.sparseLen() <= nc.len() :"_len = " + nc.sparseLen() + ", _len2 = " + nc.len();
    if( nc.len() == 0 ) return;
    if(len() == 0){
      _ls = nc._ls; nc._ls = null;
      _xs = nc._xs; nc._xs = null;
      _id = nc._id; nc._id = null;
      _ds = nc._ds; nc._ds = null;
      _is = nc._is; nc._is = null;
      _ss = nc._ss; nc._ss = null;
      set_sparseLen(nc.sparseLen());
      set_len(nc.len());
      return;
    }
    if(nc.sparse() != sparse()){ // for now, just make it dense
      cancel_sparse();
      nc.cancel_sparse();
    }
    if( _ds != null ) throw H2O.unimpl();
    while( sparseLen() + nc.sparseLen() >= _xs.length )
      _xs = MemoryManager.arrayCopyOf(_xs,_xs.length<<1);
    _ls = MemoryManager.arrayCopyOf(_ls,_xs.length);
    System.arraycopy(nc._ls,0,_ls, sparseLen(), nc.sparseLen());
    System.arraycopy(nc._xs,0,_xs, sparseLen(), nc.sparseLen());
    if(_id != null) {
      assert nc._id != null;
      _id = MemoryManager.arrayCopyOf(_id,_xs.length);
      System.arraycopy(nc._id,0,_id, sparseLen(), nc.sparseLen());
      for(int i = sparseLen(); i < sparseLen() + nc.sparseLen(); ++i) _id[i] += len();
    } else assert nc._id == null;

    set_sparseLen(sparseLen() + nc.sparseLen());
    set_len(len() + nc.len());
    nc._ls = null;  nc._xs = null; nc._id = null; nc.set_sparseLen(nc.set_len(0));
    assert sparseLen() <= len();
  }

  // Fast-path append long data
  void append2( long l, int x ) {
    if(_id == null || l != 0){
      if(_ls == null || sparseLen() == _ls.length) {
        append2slow();
        // again call append2 since calling append2slow might have changed things (eg might have switched to sparse and l could be 0)
        append2(l,x);
        return;
      }
      _ls[sparseLen()] = l;
      _xs[sparseLen()] = x;
      if(_id  != null)_id[sparseLen()] = len();
      set_sparseLen(sparseLen() + 1);
    }
    set_len(len() + 1);
    assert sparseLen() <= len();
  }

  // Slow-path append data
  private void append2slowd() {
    if( sparseLen() > Vec.CHUNK_SZ )
      throw new ArrayIndexOutOfBoundsException(sparseLen());
    assert _ls==null;
    if(_ds != null && _ds.length > 0){
      if(_id == null){ // check for sparseness
        int nzs = 0; // assume one non-zero for the element currently being stored
        for(double d:_ds)if(d != 0)++nzs;
        if((nzs+1)*MIN_SPARSE_RATIO < len())
          set_sparse(nzs);
      } else _id = MemoryManager.arrayCopyOf(_id, sparseLen() << 1);
      _ds = MemoryManager.arrayCopyOf(_ds, sparseLen() << 1);
    } else {
      alloc_doubles(4);
      if (sparse()) alloc_indices(4);
    }
    assert sparseLen() == 0 || _ds.length > sparseLen() :"_ds.length = " + _ds.length + ", _len = " + sparseLen();
  }
  // Slow-path append data
  private void append2slowUUID() {
    if( sparseLen() > Vec.CHUNK_SZ )
      throw new ArrayIndexOutOfBoundsException(sparseLen());
    if( _ds==null && _ls!=null ) { // This can happen for columns with all NAs and then a UUID
      _xs=null;
      alloc_doubles(sparseLen());
      Arrays.fill(_ls,C16Chunk._LO_NA);
      Arrays.fill(_ds,Double.longBitsToDouble(C16Chunk._HI_NA));
    }
    if( _ls != null && _ls.length > 0 ) {
      _ls = MemoryManager.arrayCopyOf(_ls, sparseLen() <<1);
      _ds = MemoryManager.arrayCopyOf(_ds, sparseLen() <<1);
    } else {
      alloc_mantissa(4);
      alloc_doubles(4);
    }
    assert sparseLen() == 0 || _ls.length > sparseLen() :"_ls.length = " + _ls.length + ", _len = " + sparseLen();
  }
  // Slow-path append string
  private void append2slowstr() {
    if( sparseLen() > Vec.CHUNK_SZ )
      throw new ArrayIndexOutOfBoundsException(sparseLen());

    // In case of all NAs and then a string, convert NAs to string NAs
    if (_xs != null) {
      _xs = null; _ls = null;
      alloc_str_indices(sparseLen());
      Arrays.fill(_is,-1);
    }

    if(_is != null && _is.length > 0){
      // Check for sparseness
      if(_id == null){
        int nzs = 0; // assume one non-null for the element currently being stored
        for( int i:_is) if( i != -1 ) ++nzs;
        if( (nzs+1)*MIN_SPARSE_RATIO < len())
          set_sparse(nzs);
      } else {
        if((MIN_SPARSE_RATIO*(_sparseLen) >> 1) > len())  cancel_sparse();
        else _id = MemoryManager.arrayCopyOf(_id,_sparseLen<<1);
      }

      _is = MemoryManager.arrayCopyOf(_is, sparseLen()<<1);
      /* initialize the memory extension with -1s */
      for (int i = sparseLen(); i < _is.length; i++) _is[i] = -1;
    } else {
      _is = MemoryManager.malloc4 (4);
        /* initialize everything with -1s */
      for (int i = 0; i < _is.length; i++) _is[i] = -1;
      if (sparse()) alloc_indices(4);
    }
    assert sparseLen() == 0 || _is.length > sparseLen():"_ls.length = " + _is.length + ", len() = " + sparseLen();

  }
  // Slow-path append data
  private void append2slow( ) {
    if( sparseLen() > Vec.CHUNK_SZ )
      throw new ArrayIndexOutOfBoundsException("NewChunk cannot handle more than " + Vec.CHUNK_SZ + " elements.");
    assert _ds==null;
    if(_ls != null && _ls.length > 0){
      if(_id == null){ // check for sparseness
        int nzs = 0;
        for(int i = 0; i < _ls.length; ++i) if(_ls[i] != 0 || _xs[i] != 0)++nzs;
        if((nzs+1)*MIN_SPARSE_RATIO < len()){
          set_sparse(nzs);
          assert sparseLen() == 0 || sparseLen() <= _ls.length:"_len = " + sparseLen() + ", _ls.length = " + _ls.length + ", nzs = " + nzs +  ", len2 = " + len();
          assert _id.length == _ls.length;
          assert sparseLen() <= len();
          return;
        }
      } else {
        // verify we're still sufficiently sparse
        if((MIN_SPARSE_RATIO*(sparseLen()) >> 1) > len())  cancel_sparse();
        else _id = MemoryManager.arrayCopyOf(_id, sparseLen() <<1);
      }
      _ls = MemoryManager.arrayCopyOf(_ls, sparseLen() <<1);
      _xs = MemoryManager.arrayCopyOf(_xs, sparseLen() <<1);
    } else {
      alloc_mantissa(4);
      alloc_exponent(4);
      if (_id != null) alloc_indices(4);
    }
    assert sparseLen() == 0 || sparseLen() < _ls.length:"_len = " + sparseLen() + ", _ls.length = " + _ls.length;
    assert _id == null || _id.length == _ls.length;
    assert sparseLen() <= len();
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
    double [] ds = MemoryManager.malloc8d(sparseLen());
    for(int i = 0; i < sparseLen(); ++i)
      if(isNA2(i) || isEnum2(i)) ds[i] = Double.NaN;
      else  ds[i] = _ls[i]*PrettyPrint.pow10(_xs[i]);
    _ls = null;
    _xs = null;
    _ds = ds;
  }

  protected void set_sparse(int nzeros){
    if(sparseLen() == nzeros && len() != 0)return;
    if(_id != null) { // we have sparse representation but some 0s in it!
      int[] id = MemoryManager.malloc4(nzeros);
      int j = 0;
      if (_ds != null) {
        double[] ds = MemoryManager.malloc8d(nzeros);
        for (int i = 0; i < sparseLen(); ++i) {
          if (_ds[i] != 0) {
            ds[j] = _ds[i];
            id[j] = _id[i];
            ++j;
          }
        }
        _ds = ds;
      } else if (_is != null) {
        int [] is = MemoryManager.malloc4(nzeros);
        for (int i = 0; i < sparseLen(); i++) {
          if (_is[i] != -1) {
            is[j] = _is[i];
            id[j] = id[i];
            ++j;
          }
        }
      } else {
        long [] ls = MemoryManager.malloc8(nzeros);
        int [] xs = MemoryManager.malloc4(nzeros);
        for(int i = 0; i < sparseLen(); ++i){
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
    assert sparseLen() == len() :"_len = " + sparseLen() + ", _len2 = " + len() + ", nzeros = " + nzeros;
    int zs = 0;
    if(_is != null) {
      assert nzeros < _is.length;
      _id = MemoryManager.malloc4(_is.length);
      for (int i = 0; i < sparseLen(); i++) {
        if (_is[i] == -1) zs++;
        else {
          _is[i-zs] = _is[i];
          _id[i-zs] = i;
        }
      }
    } else if(_ds == null){
      if (len() == 0) {
        _ls = new long[0];
        _xs = new int[0];
        _id = new int[0];
        set_sparseLen(0);
        return;
      } else {
        assert nzeros < sparseLen();
        _id = alloc_indices(_ls.length);
        for (int i = 0; i < sparseLen(); ++i) {
          if (_ls[i] == 0 && _xs[i] == 0) ++zs;
          else {
            _ls[i - zs] = _ls[i];
            _xs[i - zs] = _xs[i];
            _id[i - zs] = i;
          }
        }
      }
    } else {
      assert nzeros < _ds.length;
      _id = alloc_indices(_ds.length);
      for(int i = 0; i < sparseLen(); ++i){
        if(_ds[i] == 0)++zs;
        else {
          _ds[i-zs] = _ds[i];
          _id[i-zs] = i;
        }
      }
    }
    assert zs == (sparseLen() - nzeros);
    set_sparseLen(nzeros);
  }
  protected void cancel_sparse(){
    if(sparseLen() != len()){
      if(_is != null){
        int [] is = MemoryManager.malloc4(len());
        for(int i = 0; i < len(); i++) is[i] = -1;
        for (int i = 0; i < sparseLen(); i++) is[_id[i]] = _is[i];
        _is = is;
      } else if(_ds == null){
        int []  xs = MemoryManager.malloc4(len());
        long [] ls = MemoryManager.malloc8(len());
        for(int i = 0; i < sparseLen(); ++i){
          xs[_id[i]] = _xs[i];
          ls[_id[i]] = _ls[i];
        }
        _xs = xs;
        _ls = ls;
      } else {
        double [] ds = MemoryManager.malloc8d(len());
        for(int i = 0; i < sparseLen(); ++i) ds[_id[i]] = _ds[i];
        _ds = ds;
      }
      set_sparseLen(len());
    }
    _id = null;
  }
  // Study this NewVector and determine an appropriate compression scheme.
  // Return the data so compressed.

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

  private static long leRange(long lemin, long lemax){
    if(lemin < 0 && lemax >= (Long.MAX_VALUE + lemin))
      return Long.MAX_VALUE; // if overflow return 64 as the max possible value
    long res = lemax - lemin;
    assert res >= 0;
    return res;
  }

  private Chunk compress2() {
    // Check for basic mode info: all missing or all strings or mixed stuff
    byte mode = type();
    if( mode==AppendableVec.NA ) // ALL NAs, nothing to do
      return new C0DChunk(Double.NaN, sparseLen());
    if( mode==AppendableVec.STRING )
      return new CStrChunk(_sslen, _ss, sparseLen(), _is);
    boolean rerun=false;
    if(mode == AppendableVec.ENUM){
      for( int i=0; i< sparseLen(); i++ )
        if(isEnum2(i))
          _xs[i] = 0;
        else if(!isNA2(i)){
          setNA_impl2(i);
          ++_naCnt;
        }
        // Smack any mismatched string/numbers
    } else if(mode == AppendableVec.NUMBER){
      for( int i=0; i< sparseLen(); i++ )
        if(isEnum2(i)) {
          setNA_impl2(i);
          rerun = true;
        }
    }
    if( rerun ) { _naCnt = -1;  type(); } // Re-run rollups after dropping all numbers/enums
    boolean sparse = false;
    // sparse? treat as sparse iff we have at least MIN_SPARSE_RATIOx more zeros than nonzeros
    if(MIN_SPARSE_RATIO*(_naCnt + _nzCnt) < len()) {
      set_sparse(_naCnt + _nzCnt);
      sparse = true;
    } else if (sparseLen() != len())
      cancel_sparse();

    // If the data is UUIDs there's not much compression going on
    if( _ds != null && _ls != null )
      return chunkUUID();

    // If the data was set8 as doubles, we do a quick check to see if it's
    // plain longs.  If not, we give up and use doubles.
    if( _ds != null ) {
      int i=0;
      boolean isConstant = true;
      boolean isInteger = true;
      if ( sparse ) {
        isConstant = sparseLen() == 0;
        for( ; i< sparseLen(); i++ ) {
          if (!Double.isNaN(_ds[i])) isInteger &= (double) (long) _ds[i] == _ds[i];
        }
      } else {
        assert(_ds.length >= len());
        for( ; i< len(); i++ ) {
          if (!Double.isNaN(_ds[i])) isInteger &= (double) (long) _ds[i] == _ds[i];
          isConstant &= _ds[i] == _ds[0];
        }
        assert(sparseLen() == len());
      }
      if (!isInteger) {
        if (isConstant) return new C0DChunk(_ds[0], len());
        if (sparse) return new CXDChunk(len(), sparseLen(), 8, bufD(8));
        else return chunkD();
      }

      _ls = new long[_ds.length]; // Else flip to longs
      _xs = new int [_ds.length];
      double [] ds = _ds;
      _ds = null;
      final int naCnt = _naCnt;
      for( i=0; i< sparseLen(); i++ )   // Inject all doubles into longs
        if( Double.isNaN(ds[i]) )setNA_impl2(i);
        else                     _ls[i] = (long)ds[i];
      // setNA_impl2 will set _naCnt to -1!
      // we already know what the naCnt is (it did not change!) so set it back to correct value
      _naCnt = naCnt;
    }

    // IF (_len > _sparseLen) THEN Sparse
    // Check for compressed *during appends*.  Here we know:
    // - No specials; _xs[]==0.
    // - No floats; _ds==null
    // - NZ length in _sparseLen, actual length in _len.
    // - Huge ratio between _len and _sparseLen, and we do NOT want to inflate to
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

    for( int i=0; i< sparseLen(); i++ ) {
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
      floatOverflow = l < Integer.MIN_VALUE+1 && l > Integer.MAX_VALUE;
      xmin = Math.min(xmin,x);
    }

    if(len() != sparseLen()){ // sparse?  then compare vs implied 0s
      if( min > 0 ) { min = 0; llo=0; xlo=0; }
      if( max < 0 ) { max = 0; lhi=0; xhi=0; }
      xmin = Math.min(xmin,0);
    }

    // Constant column?
    if( _naCnt==0 && (min==max)) {
      if (llo == lhi && xlo == 0 && xhi == 0)
        return new C0LChunk(llo, len());
      else if ((long)min == min)
        return new C0LChunk((long)min, len());
      else
        return new C0DChunk(min, len());
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
          ? new CX0Chunk(len(), sparseLen(),bufS(0))// No NAs, can store as sparse bitvector
          : new CXIChunk(len(), sparseLen(),1,bufS(1)); // have NAs, store as sparse 1byte values
      }

      int bpv = _enumCnt +_naCnt > 0 ? 2 : 1;   // Bit-vector
      byte[] cbuf = bufB(bpv);
      return new CBSChunk(cbuf, cbuf[0], cbuf[1]);
    }

    final boolean fpoint = xmin < 0 || min < Long.MIN_VALUE || max > Long.MAX_VALUE;

    if( sparse ) {
      if(fpoint) return new CXDChunk(len(), sparseLen(),8,bufD(8));
      int sz = 8;
      if( Short.MIN_VALUE <= min && max <= Short.MAX_VALUE ) sz = 2;
      else if( Integer.MIN_VALUE <= min && max <= Integer.MAX_VALUE ) sz = 4;
      return new CXIChunk(len(), sparseLen(),sz,bufS(sz));
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
    final long leRange = leRange(lemin,lemax);
    if( fpoint ) {
      if( (int)lemin == lemin && (int)lemax == lemax ) {
        if(leRange < 255) // Fits in scaled biased byte?
          return new C1SChunk( bufX(lemin,xmin,C1SChunk._OFF,0),(int)lemin,PrettyPrint.pow10(xmin));
        if(leRange < 65535) { // we use signed 2B short, add -32k to the bias!
          long bias = 32767 + lemin;
          return new C2SChunk( bufX(bias,xmin,C2SChunk._OFF,1),(int)bias,PrettyPrint.pow10(xmin));
        }
      }
      if(leRange < 4294967295l) {
        long bias = 2147483647l + lemin;
        return new C4SChunk( bufX(bias,xmin,C4SChunk._OFF,2),bias,PrettyPrint.pow10(xmin));
      }
      return chunkD();
    } // else an integer column

    // Compress column into a byte
    if(xmin == 0 &&  0<=lemin && lemax <= 255 && ((_naCnt + _enumCnt)==0) )
      return new C1NChunk( bufX(0,0,C1NChunk._OFF,0));
    if( lemin < Integer.MIN_VALUE ) return new C8Chunk( bufX(0,0,0,3));
    if( leRange < 255 ) {    // Span fits in a byte?
      if(0 <= min && max < 255 ) // Span fits in an unbiased byte?
        return new C1Chunk( bufX(0,0,C1Chunk._OFF,0));
      return new C1SChunk( bufX(lemin,xmin,C1SChunk._OFF,0),(int)lemin,PrettyPrint.pow10i(xmin));
    }

    // Compress column into a short
    if( leRange < 65535 ) {               // Span fits in a biased short?
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
    final int ridsz = len() >= 65535?4:2;
    final int elmsz = ridsz + valsz;
    int off = CXIChunk._OFF;
    byte [] buf = MemoryManager.malloc1(off + sparseLen() *elmsz,true);
    for( int i=0; i< sparseLen(); i++, off += elmsz ) {
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
    final int ridsz = len() >= 65535?4:2;
    final int elmsz = ridsz + valsz;
    int off = CXDChunk._OFF;
    byte [] buf = MemoryManager.malloc1(off + sparseLen() *elmsz,true);
    for( int i=0; i< sparseLen(); i++, off += elmsz ) {
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
    byte[] bs = new byte[(len() <<log)+off];
    int j = 0;
    for( int i=0; i< len(); i++ ) {
      long le = -bias;
      if(_id == null || _id.length == 0 || (j < _id.length && _id[j] == i)){
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
    assert j == sparseLen() :"j = " + j + ", len = " + sparseLen() + ", len2 = " + len() + ", id[j] = " + _id[j];
    return bs;
  }

  // Compute a compressed double buffer
  private Chunk chunkD() {
    final byte [] bs = MemoryManager.malloc1(len() *8,true);
    int j = 0;
    for(int i = 0; i < len(); ++i){
      double d = 0;
      if(_id == null || _id.length == 0 || (j < _id.length && _id[j] == i)) {
        d = _ds != null?_ds[j]:(isNA2(j)||isEnum(j))?Double.NaN:_ls[j]*PrettyPrint.pow10(_xs[j]);
        ++j;
      }
      UnsafeUtils.set8d(bs, 8*i, d);
    }
    assert j == sparseLen() :"j = " + j + ", _len = " + sparseLen();
    return new C8DChunk(bs);
  }

  // Compute a compressed UUID buffer
  private Chunk chunkUUID() {
    final byte [] bs = MemoryManager.malloc1(len() *16,true);
    int j = 0;
    for( int i = 0; i < len(); ++i ) {
      long lo = 0, hi=0;
      if( _id == null || _id.length == 0 || (j < _id.length && _id[j] == i ) ) {
        lo = _ls[j];
        hi = Double.doubleToRawLongBits(_ds[j++]);
        if( _xs != null && _xs[j] == Integer.MAX_VALUE){// NA?
          lo = Long.MIN_VALUE; hi = 0;                  // Canonical NA value
        }
      }
      UnsafeUtils.set8(bs, 16*i  , lo);
      UnsafeUtils.set8(bs, 16 * i + 8, hi);
    }
    assert j == sparseLen() :"j = " + j + ", _len = " + sparseLen();
    return new C16Chunk(bs);
  }

  // Compute compressed boolean buffer
  private byte[] bufB(int bpv) {
    assert bpv == 1 || bpv == 2 : "Only bit vectors with/without NA are supported";
    final int off = CBSChunk._OFF;
    int clen  = off + CBSChunk.clen(len(), bpv);
    byte bs[] = new byte[clen];
    // Save the gap = number of unfilled bits and bpv value
    bs[0] = (byte) (((len() *bpv)&7)==0 ? 0 : (8-((len() *bpv)&7)));
    bs[1] = (byte) bpv;

    // Dense bitvector
    int  boff = 0;
    byte b    = 0;
    int  idx  = CBSChunk._OFF;
    int j = 0;
    for (int i=0; i< len(); i++) {
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
    assert j == sparseLen();
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
    if(sparseLen() != len()){ // sparse?
      int idx = Arrays.binarySearch(_id,0, sparseLen(),i);
      if(idx >= 0)i = idx;
      else cancel_sparse(); // for now don't bother setting the sparse value
    }
    _ls[i]=l; _xs[i]=0;
    _naCnt = -1;
    return true;
  }

  @Override public boolean set_impl(int i, double d) {
    if(_ds == null){
      assert sparseLen() == 0 || _ls != null;
      switch_to_doubles();
    }
    if(sparseLen() != len()){ // sparse?
      int idx = Arrays.binarySearch(_id,0, sparseLen(),i);
      if(idx >= 0)i = idx;
      else cancel_sparse(); // for now don't bother setting the sparse value
    }
    assert i < sparseLen();
    _ds[i] = d;
    _naCnt = -1;
    return true;
  }
  @Override boolean set_impl(int i, float f) {  return set_impl(i,(double)f); }

  @Override boolean set_impl(int i, String str) {
    if(sparseLen() != len()){ // sparse?
      int idx = Arrays.binarySearch(_id,0, sparseLen(),i);
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
    _naCnt = -1;
    return true;
  }
  @Override boolean setNA_impl(int i) {
    if( isNA_impl(i) ) return true;
    if(sparseLen() != len()){
      int idx = Arrays.binarySearch(_id,0, sparseLen(),i);
      if(idx >= 0) i = idx;
      else cancel_sparse(); // todo - do not necessarily cancel sparse here
    }
    return setNA_impl2(i);
  }
  @Override public long   at8_impl( int i ) {
    if( len() != sparseLen()) {
      int idx = Arrays.binarySearch(_id,0, sparseLen(),i);
      if(idx >= 0) i = idx;
      else return 0;
    }
    if(isNA2(i))throw new RuntimeException("Attempting to access NA as integer value.");
    if( _ls == null ) return (long)_ds[i];
    return _ls[i]*PrettyPrint.pow10i(_xs[i]);
  }
  @Override public double atd_impl( int i ) {
    if( len() != sparseLen()) {
      int idx = Arrays.binarySearch(_id,0, sparseLen(),i);
      if(idx >= 0) i = idx;
      else return 0;
    }
    // if exponent is Integer.MIN_VALUE (for missing value) or >=0, then go the integer path (at8_impl)
    // negative exponents need to be handled right here
    if( _ds == null ) return isNA2(i) || _xs[i] >= 0 ? at8_impl(i) : _ls[i]*Math.pow(10,_xs[i]);
    assert _xs==null; return _ds[i];
  }
  @Override protected long at16l_impl(int idx) {
    if(_ls[idx] == C16Chunk._LO_NA) throw new RuntimeException("Attempting to access NA as integer value.");
    return _ls[idx];
  }
  @Override protected long at16h_impl(int idx) {
    long hi = Double.doubleToRawLongBits(_ds[idx]);
    if(hi == C16Chunk._HI_NA) throw new RuntimeException("Attempting to access NA as integer value.");
    return hi;
  }
  @Override public boolean isNA_impl( int i ) {
    if( len() != sparseLen()) {
      int idx = Arrays.binarySearch(_id,0, sparseLen(),i);
      if(idx >= 0) i = idx;
      else return false;
    }
    return isNA2(i);
  }
  @Override public ValueString atStr_impl( ValueString vstr, int i ) {
    if( sparseLen() != len() ) {
      int idx = Arrays.binarySearch(_id,0,sparseLen(),i);
      if(idx >= 0) i = idx;
      else return null;
    }

    if( _is[i] == CStrChunk.NA ) return null;

    int len;
    for( len = 0; _ss[_is[i] + len] != 0; len++ ) ;
    return vstr.set(_ss, _is[i], len);
  }
  @Override public NewChunk read_impl(AutoBuffer bb) { throw H2O.fail(); }
  @Override public AutoBuffer write_impl(AutoBuffer bb) { throw H2O.fail(); }
  @Override NewChunk inflate_impl(NewChunk nc) { throw H2O.fail(); }
  @Override public String toString() { return "NewChunk._len="+ sparseLen(); }
}
