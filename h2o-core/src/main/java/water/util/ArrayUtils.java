package water.util;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Random;

import static water.util.RandomUtils.getDeterRNG;

/* Bulk Array Utilities */
public class ArrayUtils {

  // Sum elements of an array
  public static long sum(final long[] from) {
    long result = 0;
    for (long d: from) result += d;
    return result;
  }
  public static int sum(final int[] from) {
    int result = 0;
    for( int d : from ) result += d;
    return result;
  }
  public static float sum(final float[] from) {
    float result = 0;
    for (float d: from) result += d;
    return result;
  }
  public static double sum(final double[] from) {
    double result = 0;
    for (double d: from) result += d;
    return result;
  }

  public static final double innerProduct(double [] x, double [] y){
    double result = 0;
    for (int i = 0; i < x.length; i++)
      result += x[i] * y[i];
    return result;
  }
  public static final double l2norm2(double [] x){
    double sum = 0;
    for(double d:x)
      sum += d*d;
    return sum;
  }
  public static final double l1norm(double [] x){
    double sum = 0;
    for(double d:x)
      sum += d >= 0?d:-d;
    return sum;
  }
  public static final double l2norm(double [] x){
    return Math.sqrt(l2norm2(x));
  }

  // Add arrays, element-by-element
  public static byte[] add(byte[] a, byte[] b) {
    for(int i = 0; i < a.length; i++ ) a[i] += b[i];
    return a;
  }
  public static int[] add(int[] a, int[] b) {
    for(int i = 0; i < a.length; i++ ) a[i] += b[i];
    return a;
  }
  public static int[][] add(int[][] a, int[][] b) {
    for(int i = 0; i < a.length; i++ ) add(a[i],b[i]);
    return a;
  }
  public static long[] add(long[] a, long[] b) {
    if( b==null ) return a;
    for(int i = 0; i < a.length; i++ ) a[i] += b[i];
    return a;
  }
  public static long[][] add(long[][] a, long[][] b) {
    for(int i = 0; i < a.length; i++ ) add(a[i],b[i]);
    return a;
  }
  public static long[][][] add(long[][][] a, long[][][] b) {
    for(int i = 0; i < a.length; i++ ) add(a[i],b[i]);
    return a;
  }
  public static float[] add(float[] a, float[] b) {
    if( b==null ) return a;
    for(int i = 0; i < a.length; i++ ) a[i] += b[i];
    return a;
  }
  public static float[][] add(float[][] a, float[][] b) {
    for(int i = 0; i < a.length; i++ ) add(a[i],b[i]);
    return a;
  }

  public static final  double [][] deepClone(double [][] ary){
    double [][] res = ary.clone();
    for(int i = 0 ; i < res.length; ++i)
      res[i] = ary[i].clone();
    return res;
  }

  public static double[] add(double[] a, double[] b) {
    if( a==null ) return b;
    for(int i = 0; i < a.length; i++ ) a[i] += b[i];
    return a;
  }

  // a <- b + c
  public static double[] add(double[] a, double[] b, double [] c) {
    for(int i = 0; i < a.length; i++ )
      a[i] = b[i] + c[i];
    return a;
  }
  public static double[][] add(double[][] a, double[][] b) {
    for(int i = 0; i < a.length; i++ ) a[i] = add(a[i],b[i]);
    return a;
  }

  public static double avg(double[] nums) {
    double sum = 0;
    for(double n: nums) sum+=n;
    return sum/nums.length;
  }
  public static double avg(long[] nums) {
    long sum = 0;
    for(long n: nums) sum+=n;
    return sum/nums.length;
  }
  public static float[] div(float[] nums, int n) {
    for (int i=0; i<nums.length; i++) nums[i] /= n;
    return nums;
  }
  public static float[] div(float[] nums, float n) {
    assert !Float.isInfinite(n) : "Trying to divide " + Arrays.toString(nums) + " by  " + n; // Almost surely not what you want
    for (int i=0; i<nums.length; i++) nums[i] /= n;
    return nums;
  }
  public static double[] div(double[] nums, double n) {
    assert !Double.isInfinite(n) : "Trying to divide " + Arrays.toString(nums) + " by  " + n; // Almost surely not what you want
    for (int i=0; i<nums.length; i++) nums[i] /= n;
    return nums;
  }
  public static float[] mult(float[] nums, float n) {
    assert !Float.isInfinite(n) : "Trying to multiply " + Arrays.toString(nums) + " by  " + n; // Almost surely not what you want
    for (int i=0; i<nums.length; i++) nums[i] *= n;
    return nums;
  }
  public static double[] mult(double[] nums, double n) {
    assert !Double.isInfinite(n) : "Trying to multiply " + Arrays.toString(nums) + " by  " + n; // Almost surely not what you want
    for (int i=0; i<nums.length; i++) nums[i] *= n;
    return nums;
  }

  // Convert array of primitives to an array of Strings.
  public static String[] toString(long[] dom) {
    String[] result = new String[dom.length];
    for (int i=0; i<dom.length; i++) result[i] = String.valueOf(dom[i]);
    return result;
  }
  public static String[] toString(int[] dom) {
    String[] result = new String[dom.length];
    for (int i=0; i<dom.length; i++) result[i] = String.valueOf(dom[i]);
    return result;
  }
  public static boolean contains(String[] names, String name) {
    for (String n : names) if (n.equals(name)) return true;
    return false;
  }

  static public boolean contains(int[] a, int d) {
    for (int anA : a) if (anA == d) return true;
    return false;
  }

  public static <T> T[] subarray(T[] a, int off, int len) {
    return Arrays.copyOfRange(a,off,off+len);
  }

  /** Returns the index of the largest value in the array.
   * In case of a tie, an the index is selected randomly.
   */
  public static int maxIndex(int[] from, Random rand) {
    assert rand != null;
    int result = 0;
    int maxCount = 0; // count of maximal element for a 1 item reservoir sample
    for( int i = 1; i < from.length; ++i ) {
      if( from[i] > from[result] ) {
        result = i;
        maxCount = 1;
      } else if( from[i] == from[result] ) {
        if( rand.nextInt(++maxCount) == 0 ) result = i;
      }
    }
    return result;
  }

  public static int maxIndex(float[] from, Random rand) {
    assert rand != null;
    int result = 0;
    int maxCount = 0; // count of maximal element for a 1 item reservoir sample
    for( int i = 1; i < from.length; ++i ) {
      if( from[i] > from[result] ) {
        result = i;
        maxCount = 1;
      } else if( from[i] == from[result] ) {
        if( rand.nextInt(++maxCount) == 0 ) result = i;
      }
    }
    return result;
  }

  public static int maxIndex(int[] from) {
    int result = 0;
    for (int i = 1; i<from.length; ++i)
      if (from[i]>from[result]) result = i;
    return result;
  }
  public static int maxIndex(long[] from) {
    int result = 0;
    for (int i = 1; i<from.length; ++i)
      if (from[i]>from[result]) result = i;
    return result;
  }
  public static int maxIndex(float[] from) {
    int result = 0;
    for (int i = 1; i<from.length; ++i)
      if (from[i]>from[result]) result = i;
    return result;
  }
  public static int minIndex(int[] from) {
    int result = 0;
    for (int i = 1; i<from.length; ++i)
      if (from[i]<from[result]) result = i;
    return result;
  }
  public static int minIndex(float[] from) {
    int result = 0;
    for (int i = 1; i<from.length; ++i)
      if (from[i]<from[result]) result = i;
    return result;
  }
  public static double maxValue(double[] from) {
    double result = from[0];
    for (int i = 1; i<from.length; ++i)
      if (from[i]>result) result = from[i];
    return result;
  }
  public static float maxValue(float[] ary) {
    return maxValue(ary,0,ary.length);
  }
  public static float maxValue(float[] ary, int from, int to) {
    float result = ary[from];
    for (int i = from+11; i<to; ++i)
      if (ary[i]>result) result = ary[i];
    return result;
  }
  public static float minValue(float[] from) {
    float result = from[0];
    for (int i = 1; i<from.length; ++i)
      if (from[i]<result) result = from[i];
    return result;
  }
  public static long maxValue(long[] from) {
    long result = from[0];
    for (int i = 1; i<from.length; ++i)
      if (from[i]>result) result = from[i];
    return result;
  }
  public static long minValue(long[] from) {
    long result = from[0];
    for (int i = 1; i<from.length; ++i)
      if (from[i]<result) result = from[i];
    return result;
  }

  // Find an element with linear search & return it's index, or -1
  public static <T> int find(T[] ts, T elem) {
    for( int i=0; i<ts.length; i++ )
      if( elem==ts[i] || elem.equals(ts[i]) )
        return i;
    return -1;
  }

  private static final DecimalFormat default_dformat = new DecimalFormat("0.#####");
  public static String pprint(double[][] arr){
    return pprint(arr,default_dformat);
  }
  // pretty print Matrix(2D array of doubles)
  public static String pprint(double[][] arr,DecimalFormat dformat) {
    int colDim = 0;
    for( double[] line : arr )
      colDim = Math.max(colDim, line.length);
    StringBuilder sb = new StringBuilder();
    int max_width = 0;
    int[] ilengths = new int[colDim];
    Arrays.fill(ilengths, -1);
    for( double[] line : arr ) {
      for( int c = 0; c < line.length; ++c ) {
        double d = line[c];
        String dStr = dformat.format(d);
        if( dStr.indexOf('.') == -1 ) dStr += ".0";
        ilengths[c] = Math.max(ilengths[c], dStr.indexOf('.'));
        int prefix = (d >= 0 ? 1 : 2);
        max_width = Math.max(dStr.length() + prefix, max_width);
      }
    }
    for( double[] line : arr ) {
      for( int c = 0; c < line.length; ++c ) {
        double d = line[c];
        String dStr = dformat.format(d);
        if( dStr.indexOf('.') == -1 ) dStr += ".0";
        for( int x = dStr.indexOf('.'); x < ilengths[c] + 1; ++x )
          sb.append(' ');
        sb.append(dStr);
        if( dStr.indexOf('.') == -1 ) sb.append('.');
        for( int i = dStr.length() - Math.max(0, dStr.indexOf('.')); i <= 5; ++i )
          sb.append('0');
      }
      sb.append("\n");
    }
    return sb.toString();
  }
  public static int[] unpackInts(long... longs) {
    int len      = 2*longs.length;
    int result[] = new int[len];
    int i = 0;
    for (long l : longs) {
      result[i++] = (int) (l & 0xffffffffL);
      result[i++] = (int) (l>>32);
    }
    return result;
  }

  private static void swap(long[] a, int i, int change) {
    long helper = a[i];
    a[i] = a[change];
    a[change] = helper;
  }

  public static void shuffleArray(long[] a, long seed) {
    int n = a.length;
    Random random = getDeterRNG(seed);
    random.nextInt();
    for (int i = 0; i < n; i++) {
      int change = i + random.nextInt(n - i);
      swap(a, i, change);
    }
  }


  /** Returns number of strings which represents a number. */
  public static int numInts(String... a) {
    int cnt = 0;
    for(String s : a) if (isInt(s)) cnt++;
    return cnt;
  }

  public static boolean isInt(String s) {
    int i = s.charAt(0)=='-' ? 1 : 0;
    for(; i<s.length();i++) if (!Character.isDigit(s.charAt(i))) return false;
    return true;
  }

  public static int[] toInt(String[] a, int off, int len) {
    int[] res = new int[len];
    for(int i=0; i<len; i++) res[i] = Integer.valueOf(a[off+i]);
    return res;
  }

  /** Clever union of String arrays.
   *
   * For union of numeric arrays (strings represent integers) it is expecting numeric ordering.
   * For pure string domains it is expecting lexicographical ordering.
   * For mixed domains it always expects lexicographical ordering since such a domain were produce
   * by a parser which sort string with Array.sort().
   *
   * PRECONDITION - string domain was sorted by Array.sort(String[]), integer domain by Array.sort(int[]) and switched to Strings !!!
   *
   * @param a a set of strings
   * @param b a set of strings
   * @return union of arrays
   */
  public static String[] domainUnion(String[] a, String[] b) {
    int cIinA = numInts(a);
    int cIinB = numInts(b);
    // Trivial case - all strings or ints, sorted
    if (cIinA==0 && cIinB==0   // only strings
            || cIinA==a.length && cIinB==b.length ) // only integers
      return union(a, b, cIinA==0);
    // Be little bit clever here: sort string representing numbers first and append
    // a,b were sorted by Array.sort() but can contain some numbers.
    // So sort numbers in numeric way, and then string in lexicographical order
    int[] ai = toInt(a, 0, cIinA); Arrays.sort(ai); // extract int part but sort it in numeric order
    int[] bi = toInt(b, 0, cIinB); Arrays.sort(bi);
    String[] ri = toString(union(ai,bi)); // integer part
    String[] si = union(a,b,cIinA,a.length-cIinA,cIinB,b.length-cIinB,true);
    return join(ri, si);
  }

  /** Union of given String arrays.
   *
   * The method expects ordering of domains in given order (lexicographical, numeric)
   *
   * @param a first array
   * @param b second array
   * @param lexo - true if domains are sorted in lexicographical order or false for numeric domains
   * @return union of values in given arrays.
   *
   * precondition lexo ? a,b are lexicographically sorted : a,b are sorted numerically
   * precondition a!=null &amp;&amp; b!=null
   */
  public static String[] union(String[] a, String[] b, boolean lexo) {
    assert a!=null && b!=null : "Union expect non-null input!";
    return union(a, b, 0, a.length, 0, b.length, lexo);
  }
  public static String[] union(String[] a, String[] b, int aoff, int alen, int boff, int blen, boolean lexo) {
    assert a!=null && b!=null : "Union expect non-null input!";
    String[] r = new String[alen+blen];
    int ia = aoff, ib = boff, i = 0;
    while (ia < aoff+alen && ib < boff+blen) {
      int c = lexo ? a[ia].compareTo(b[ib]) : Integer.valueOf(a[ia]).compareTo(Integer.valueOf(b[ib]));
      if ( c < 0) r[i++] = a[ia++];
      else if (c == 0) { r[i++] = a[ia++]; ib++; }
      else r[i++] = b[ib++];
    }
    if (ia < aoff+alen) while (ia<aoff+alen) r[i++] = a[ia++];
    if (ib < boff+blen) while (ib<boff+blen) r[i++] = b[ib++];
    return Arrays.copyOf(r, i);
  }
  /** Returns a union of given sorted arrays. */
  public static int[] union(int[] a, int[] b) {
    assert a!=null && b!=null : "Union expect non-null input!";
    int[] r = new int[a.length+b.length];
    int ia = 0, ib = 0, i = 0;
    while (ia < a.length && ib < b.length) {
      int c = a[ia]-b[ib];
      if ( c < 0) r[i++] = a[ia++];
      else if (c == 0) { r[i++] = a[ia++]; ib++; }
      else r[i++] = b[ib++];
    }
    if (ia < a.length) while (ia<a.length) r[i++] = a[ia++];
    if (ib < b.length) while (ib<b.length) r[i++] = b[ib++];
    return Arrays.copyOf(r, i);
  }

  public static float [] join(float[] a, float[] b) {
    float[] res = Arrays.copyOf(a, a.length+b.length);
    System.arraycopy(b, 0, res, a.length, b.length);
    return res;
  }
  public static <T> T[] join(T[] a, T[] b) {
    T[] res = Arrays.copyOf(a, a.length+b.length);
    System.arraycopy(b, 0, res, a.length, b.length);
    return res;
  }

  public static final boolean hasNaNsOrInfs(double [] ary){
    for(double d:ary)
      if(Double.isNaN(d) || Double.isInfinite(d))
        return true;
    return false;
  }

  public static final boolean hasNaNsOrInfs(float [] ary){
    for(float d:ary)
      if(Double.isNaN(d) || Double.isInfinite(d))
        return true;
    return false;
  }
  /** Generates sequence (start, stop) of integers: (start, start+1, ...., stop-1) */
  static public int[] seq(int start, int stop) {
    assert start<stop;
    int len = stop-start;
    int[] res = new int[len];
    for(int i=start; i<stop;i++) res[i-start] = i;
    return res;
  }

  // warning: Non-Symmetric! Returns all elements in a that are not in b (but NOT the other way around)
  static public int[] difference(int a[], int b[]) {
    if (a == null) return new int[]{};
    if (b == null) return a.clone();
    int[] r = new int[a.length];
    int cnt = 0;
    for (int i=0; i<a.length; i++) {
      if (!contains(b, a[i])) r[cnt++] = a[i];
    }
    return Arrays.copyOf(r, cnt);
  }

  // warning: Non-Symmetric! Returns all elements in a that are not in b (but NOT the other way around)
  static public String[] difference(String a[], String b[]) {
    if (a == null) return new String[]{};
    if (b == null) return a.clone();
    String[] r = new String[a.length];
    int cnt = 0;
    for (int i=0; i<a.length; i++) {
      if (!contains(b, a[i])) r[cnt++] = a[i];
    }
    return Arrays.copyOf(r, cnt);
  }

  static public double[][] append( double[][] a, double[][] b ) {
    if( a==null ) return b;
    if( b==null ) return a;
    if( a.length==0 ) return b;
    if( b.length==0 ) return a;
    assert a[0].length==b[0].length;
    double[][] c = Arrays.copyOf(a,a.length+b.length);
    System.arraycopy(b,0,c,a.length,b.length);
    return c;
  }

  static public double[] append( double[] a, double[] b ) {
    if( a==null ) return b;
    if( b==null ) return a;
    if( a.length==0 ) return b;
    if( b.length==0 ) return a;
    double[] c = Arrays.copyOf(a,a.length+b.length);
    System.arraycopy(b,0,c,a.length,b.length);
    return c;
  }
  static public String[] append( String[] a, String[] b ) {
    if( a==null ) return b;
    if( b==null ) return a;
    if( a.length==0 ) return b;
    if( b.length==0 ) return a;
    String[] c = Arrays.copyOf(a,a.length+b.length);
    System.arraycopy(b,0,c,a.length,b.length);
    return c;
  }

  static public String[] prepend(String[] ary, String s) {
    if (ary==null) return new String[] { s };
    String[] nary = new String[ary.length+1];
    nary[0] = s;
    System.arraycopy(ary,0,nary,1,ary.length);
    return nary;
  }
}
