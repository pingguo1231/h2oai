package water;

import static org.junit.Assert.*;

import java.io.File;
import java.lang.reflect.*;
import java.util.ArrayList;
import org.junit.*;
import org.junit.rules.*;
import org.junit.runners.model.Statement;
import org.junit.runner.Description;
import water.fvec.*;
import water.util.Log;
import water.util.Timer;
import water.parser.ValueString;

@Ignore("Support for tests, but no actual tests here")
public class TestUtil {
  private static boolean _stall_called_before = false;
  protected static int _initial_keycnt = 0;
  protected static int MINCLOUDSIZE;

  public TestUtil() { this(1); }
  public TestUtil(int minCloudSize) { MINCLOUDSIZE = Math.max(MINCLOUDSIZE,minCloudSize); }

  // ==== Test Setup & Teardown Utilities ====
  // Stall test until we see at least X members of the Cloud
  public static void stall_till_cloudsize(int x) {
    if (! _stall_called_before) {
      if (H2O.getCloudSize() < x) {
        // Figure out how to build cloud here, if desired.
        _stall_called_before = true;
      }
    }

    H2O.waitForCloudSize(x, 10000);
  }

  @BeforeClass()
  public static void setupCloud() {
    H2O.main(new String[] {});
    _stall_called_before = true; // multinode-in-1-jvm launch off by default
    stall_till_cloudsize(MINCLOUDSIZE);
    _initial_keycnt = H2O.store_size();
  }

  @AfterClass
  public static void checkLeakedKeys() {
    int leaked_keys = H2O.store_size() - _initial_keycnt;
    if( leaked_keys > 0 ) {
      for( Key k : H2O.localKeySet() ) {
        Value value = H2O.raw_get(k);
        // Ok to leak VectorGroups
        if( value.isVecGroup() || k == Job.LIST ) leaked_keys--;
        else System.err.println("Leaked key: " + k + " = " + TypeMap.className(value.type()));
      }
    }
    assertTrue("No keys leaked", leaked_keys <= 0);
    _initial_keycnt = H2O.store_size();
  }


  /** Execute this rule before each test to print test name and test class */
  @Rule public TestRule logRule = new TestRule() {

    @Override public Statement apply(Statement base, Description description) {
      Log.info("###########################################################");
      Log.info("  * Test class name:  " + description.getClassName());
      Log.info("  * Test method name: " + description.getMethodName());
      Log.info("###########################################################");
      return base;
    }
  };

  @Rule public TestRule timerRule = new TestRule() {
    @Override public Statement apply(Statement base, Description description) {
      return new TimerStatement(base, description.getClassName()+"#"+description.getMethodName());
    }
    class TimerStatement extends Statement {
      private final Statement _base;
      private final String _tname;
      Throwable _ex;
      public TimerStatement(Statement base, String tname) { _base = base; _tname = tname;}
      @Override public void evaluate() throws Throwable {
        Timer t = new Timer();
        try {
          _base.evaluate();
        } catch( Throwable ex ) {
          _ex=ex;
          throw _ex;
        } finally {
          Log.info("#### TEST "+_tname+" EXECUTION TIME: " + t.toString());
        }
      }
    }
  };

  // ==== Data Frame Creation Utilities ====

  /** Hunt for test files in likely places.  Null if cannot find.
   *  @param fname Test filename
   *  @return      Found file or null */
  protected File find_test_file( String fname ) {
    // When run from eclipse, the working directory is different.
    // Try pointing at another likely place
    File file = new File(fname);
    if( !file.exists() )
      file = new File("target/" + fname);
    if( !file.exists() )
      file = new File("../" + fname);
    if( !file.exists() )
      file = new File("../target/" + fname);
    if( !file.exists() )
      file = null;
    return file;
  }

  /** Find & parse a CSV file.  NPE if file not found.
   *  @param fname Test filename
   *  @return      Frame or NPE */
  protected Frame parse_test_file( String fname ) {
    NFSFileVec nfs = NFSFileVec.make(find_test_file(fname));
    return water.parser.ParseDataset2.parse(Key.make(),nfs._key);
  }

  /** Find & parse a folder of CSV files.  NPE if file not found.
   *  @param fname Test filename
   *  @return      Frame or NPE */
  protected Frame parse_test_folder( String fname ) {
    File folder = find_test_file(fname);
    assert folder.isDirectory();
    ArrayList<Key> keys = new ArrayList<>();
    for( File f : folder.listFiles() )
      if( f.isFile() )
        keys.add(NFSFileVec.make(f)._key);
    Key[] res = new Key[keys.size()];
    keys.toArray(res);
    return water.parser.ParseDataset2.parse(Key.make(),res);
  }

  /** A Numeric Vec from an array of ints
   *  @param rows Data
   *  @return The Vec  */
  public static Vec vec(int...rows) { return vec(null, rows); }
  /** A Enum/Factor Vec from an array of ints - with enum/domain mapping
   *  @param domain Enum/Factor names, mapped by the data values
   *  @param rows Data
   *  @return The Vec  */
  public static Vec vec(String[] domain, int ...rows) { 
    Key k = Vec.VectorGroup.VG_LEN1.addVec();
    Futures fs = new Futures();
    AppendableVec avec = new AppendableVec(k);
    avec.setDomain(domain);
    NewChunk chunk = new NewChunk(avec, 0);
    for( int r : rows ) chunk.addNum(r);
    chunk.close(0, fs);
    Vec vec = avec.close(fs);
    fs.blockForPending();
    return vec;
  }

  /** Create a new frame based on given row data.
   *  @param key   Key for the frame
   *  @param names names of frame columns
   *  @param rows  data given in the form of rows
   *  @return new frame which contains columns named according given names and including given data */
  public static Frame frame(Key key, String[] names, double[]... rows) {
    assert names == null || names.length == rows[0].length;
    Futures fs = new Futures();
    Vec[] vecs = new Vec[rows[0].length];
    Key keys[] = Vec.VectorGroup.VG_LEN1.addVecs(vecs.length);
    for( int c = 0; c < vecs.length; c++ ) {
      AppendableVec vec = new AppendableVec(keys[c]);
      NewChunk chunk = new NewChunk(vec, 0);
      for (double[] row : rows) chunk.addNum(row[c]);
      chunk.close(0, fs);
      vecs[c] = vec.close(fs);
    }
    fs.blockForPending();
    Frame fr = new Frame(key, names, vecs);
    if( key != null ) DKV.put(key,fr);
    return fr;
  }
  public static Frame frame(double[]... rows) { return frame(null,rows); }
  public static Frame frame(String[] names, double[]... rows) { return frame(Key.make(),names,rows); }

  // Shortcuts for initializing constant arrays
  public static String[]   ar (String ...a)   { return a; }
  public static long  []   ar (long   ...a)   { return a; }
  public static long[][]   ar (long[] ...a)   { return a; }
  public static int   []   ari(int    ...a)   { return a; }
  public static int [][]   ar (int[]  ...a)   { return a; }
  public static float []   arf(float  ...a)   { return a; }
  public static double[]   ard(double ...a)   { return a; }
  public static double[][] ard(double[] ...a) { return a; }


  // ==== Comparing Results ====

  /** Compare 2 doubles within a tolerance
   *  @param a double 
   *  @param b double
   *  @param abseps - Absolute allowed tolerance
   *  @param releps - Relative allowed tolerance
   *  @return true if equal within tolerances  */
  protected boolean compare(double a, double b, double abseps, double releps) {
    return
      Double.compare(a, b) == 0 || // check for equality
      Math.abs(a-b)/Math.max(a,b) < releps ||  // check for small relative error
      Math.abs(a - b) <= abseps; // check for small absolute error
  }

  /** Compare 2 doubles within a tolerance
   *  @param fr1 Frame
   *  @param fr2 Frame
   *  @return true if equal  */
  protected static boolean isBitIdentical( Frame fr1, Frame fr2 ) {
    if( fr1.numCols() != fr2.numCols() ) return false;
    if( fr1.numRows() != fr2.numRows() ) return false;
    if( fr1.checkCompatible(fr2) )
      return !(new Cmp1().doAll(new Frame(fr1).add(fr2))._unequal);
    // Else do it the slow hard way
    return !(new Cmp2(fr2).doAll(fr1)._unequal);
  }
  // Fast compatible Frames
  private static class Cmp1 extends MRTask<Cmp1> {
    boolean _unequal;
    @Override public void map( Chunk chks[] ) {
      for( int cols=0; cols<chks.length>>1; cols++ ) {
        Chunk c0 = chks[cols                 ];
        Chunk c1 = chks[cols+(chks.length>>1)];
        for( int rows = 0; rows < chks[0].len(); rows++ ) {
          if (c0 instanceof C16Chunk && c1 instanceof C16Chunk) {
            if (! (c0.isNA0(rows) && c1.isNA0(rows))) {
              long lo0 = c0.at16l0(rows), lo1 = c1.at16l0(rows);
              long hi0 = c0.at16h0(rows), hi1 = c1.at16h0(rows);
              if (lo0 != lo1 || hi0 != hi1) {
                _unequal = true;
                return;
              }
            }
          } else if (c0 instanceof CStrChunk && c1 instanceof CStrChunk) {
            if (!(c0.isNA0(rows) && c1.isNA0(rows))) {
              ValueString v0 = new ValueString(), v1 = new ValueString();
              c0.atStr0(v0, rows); c1.atStr0(v1, rows);
              if (v0.compareTo(v1) != 0) {
                _unequal = true;
                return;
              }
            }
          }else {
            double d0 = c0.at0(rows), d1 = c1.at0(rows);
            if (!(Double.isNaN(d0) && Double.isNaN(d1)) && (d0 != d1)) {
              _unequal = true;
              return;
            }
          }
        }
      }
    }
    @Override public void reduce( Cmp1 cmp ) { _unequal |= cmp._unequal; }
  }
  // Slow incompatible frames
  private static class Cmp2 extends MRTask<Cmp2> {
    final Frame _fr;
    Cmp2( Frame fr ) { _fr = fr; }
    boolean _unequal;
    @Override public void map( Chunk chks[] ) {
      for( int cols=0; cols<chks.length>>1; cols++ ) {
        if( _unequal ) return;
        Chunk c0 = chks[cols];
        Vec v1 = _fr.vecs()[cols];
        for( int rows = 0; rows < chks[0].len(); rows++ ) {
          double d0 = c0.at0(rows), d1 = v1.at(c0.start() + rows);
          if( !(Double.isNaN(d0) && Double.isNaN(d1)) && (d0 != d1) ) {
            _unequal = true; return;
          }
        }
      }
    }
    @Override public void reduce( Cmp2 cmp ) { _unequal |= cmp._unequal; }
  }

  // Run tests from cmd-line since testng doesn't seem to be able to it.
  public static void main( String[] args ) {
    H2O.main(new String[0]);
    for( String arg : args ) {
      try {
        System.out.println("=== Starting "+arg);
        Class clz = Class.forName(arg);
        Method main = clz.getDeclaredMethod("main");
        main.invoke(null);
      } catch( InvocationTargetException ite ) {
        Throwable e = ite.getCause();
        e.printStackTrace();
        try { Thread.sleep(100); } catch( Exception ignore ) { }
      } catch( Exception e ) {
        e.printStackTrace();
        try { Thread.sleep(100); } catch( Exception ignore ) { }
      } finally {
        System.out.println("=== Stopping "+arg);
      }
    }
    try { Thread.sleep(100); } catch( Exception ignore ) { }
    if( args.length != 0 )
      UDPRebooted.T.shutdown.send(H2O.SELF);
  }
}
