package water.parser;

import org.joda.time.DateTime;

public abstract class ParseTime {
  // Deduce if we are looking at a Date/Time value, or not.
  // If so, return time as msec since Jan 1, 1970 or Long.MIN_VALUE.

  // I tried java.util.SimpleDateFormat, but it just throws too many
  // exceptions, including ParseException, NumberFormatException, and
  // ArrayIndexOutOfBoundsException... and the Piece de resistance: a
  // ClassCastException deep in the SimpleDateFormat code:
  // "sun.util.calendar.Gregorian$Date cannot be cast to sun.util.calendar.JulianCalendar$Date"
  private static int digit( int x, int c ) {
    if( x < 0 || c < '0' || c > '9' ) return -1;
    return x*10+(c-'0');
  }

  // So I just brutally parse "dd-MMM-yy".
  private static final byte MMS[][][] = new byte[][][] {
    {"jan".getBytes(),"january"  .getBytes()},
    {"feb".getBytes(),"february" .getBytes()},
    {"mar".getBytes(),"march"    .getBytes()},
    {"apr".getBytes(),"april"    .getBytes()},
    {"may".getBytes(),"may"      .getBytes()},
    {"jun".getBytes(),"june"     .getBytes()},
    {"jul".getBytes(),"july"     .getBytes()},
    {"aug".getBytes(),"august"   .getBytes()},
    {"sep".getBytes(),"september".getBytes()},
    {"oct".getBytes(),"october"  .getBytes()},
    {"nov".getBytes(),"november" .getBytes()},
    {"dec".getBytes(),"december" .getBytes()}
  };
  
  // Time parse patterns
  public static final String TIME_PARSE[] = { "yyyy-MM-dd", "yyyy-MM-dd HH:mm:ss.SSS", "dd-MMM-yy" };

  // Returns:
  //  - not a time parse: Long.MIN_VALUE 
  //  - time parse via pattern X: time in msecs since Jan 1, 1970, shifted left by 1 byte, OR'd with X
  static long encodeTimePat(long tcode, int tpat ) { return (tcode<<8)|tpat; }
  static long decodeTime(long tcode ) { return tcode>>8; }
  static int  decodePat (long tcode ) { return ((int)tcode&0xFF); }
  public static long attemptTimeParse( ValueString str ) {
    try {
      long t0 = attemptTimeParse_01(str); // "yyyy-MM-dd" and that plus " HH:mm:ss.SSS"
      if( t0 != Long.MIN_VALUE ) return t0;
      long t2 = attemptTimeParse_2 (str); // "dd-MMM-yy"
      if( t2 != Long.MIN_VALUE ) return t2;
    } catch( org.joda.time.IllegalFieldValueException ie ) { }
    return Long.MIN_VALUE;
  }
  // So I just brutally parse "yyyy-MM-dd HH:mm:ss.SSS"
  private static long attemptTimeParse_01( ValueString str ) {
    final byte[] buf = str.get_buf();
    int i=str.get_off();
    final int end = i+str.get_length();
    while( i < end && buf[i] == ' ' ) i++;
    if   ( i < end && buf[i] == '"' ) i++;
    if( (end-i) != 10 && (end-i) < 19 ) return Long.MIN_VALUE;
    int yy=0, MM=0, dd=0, HH=0, mm=0, ss=0, SS=0;
    yy = digit(yy,buf[i++]);
    yy = digit(yy,buf[i++]);
    yy = digit(yy,buf[i++]);
    yy = digit(yy,buf[i++]);
    if( yy < 1970 ) return Long.MIN_VALUE;
    if( buf[i++] != '-' ) return Long.MIN_VALUE;
    MM = digit(MM,buf[i++]);
    MM = digit(MM,buf[i++]);
    if( MM < 1 || MM > 12 ) return Long.MIN_VALUE;
    if( buf[i++] != '-' ) return Long.MIN_VALUE;
    dd = digit(dd,buf[i++]);
    dd = digit(dd,buf[i++]);
    if( dd < 1 || dd > 31 ) return Long.MIN_VALUE;
    if( i==end )
      return encodeTimePat(new DateTime(yy,MM,dd,0,0,0).getMillis(),0);
    if( buf[i++] != ' ' ) return Long.MIN_VALUE;
    HH = digit(HH,buf[i++]);
    HH = digit(HH,buf[i++]);
    if( HH < 0 || HH > 23 ) return Long.MIN_VALUE;
    if( buf[i++] != ':' ) return Long.MIN_VALUE;
    mm = digit(mm,buf[i++]);
    mm = digit(mm,buf[i++]);
    if( mm < 0 || mm > 59 ) return Long.MIN_VALUE;
    if( buf[i++] != ':' ) return Long.MIN_VALUE;
    ss = digit(ss,buf[i++]);
    ss = digit(ss,buf[i++]);
    if( ss < 0 || ss > 59 ) return Long.MIN_VALUE;
    if( i<end && buf[i] == '.' ) {
      i++;
      if( i<end ) SS = digit(SS,buf[i++]);
      if( i<end ) SS = digit(SS,buf[i++]);
      if( i<end ) SS = digit(SS,buf[i++]);
      if( SS < 0 || SS > 999 ) return Long.MIN_VALUE;
    }
    if( i<end && buf[i] == '"' ) i++;
    if( i<end ) return Long.MIN_VALUE;
    return encodeTimePat(new DateTime(yy,MM,dd,HH,mm,ss).getMillis()+SS,1);
  }

  private static long attemptTimeParse_2( ValueString str ) {
    final byte[] buf = str.get_buf();
    int i=str.get_off();
    final int end = i+str.get_length();
    while( i < end && buf[i] == ' ' ) i++;
    if   ( i < end && buf[i] == '"' ) i++;
    if( (end-i) < 8 ) return Long.MIN_VALUE;
    int yy=0, MM=0, dd=0;
    dd = digit(dd,buf[i++]);
    if( buf[i] != '-' ) dd = digit(dd,buf[i++]);
    if( dd < 1 || dd > 31 ) return Long.MIN_VALUE;
    if( buf[i++] != '-' ) return Long.MIN_VALUE;
    byte[]mm=null;
    OUTER: for( ; MM<MMS.length; MM++ ) {
      byte[][] mms = MMS[MM];
      INNER:
      for (byte[] mm1 : mms) {
        mm = mm1;
        if (mm == null) continue;
        if (i + mm.length >= end) continue INNER;
        for (int j = 0; j < mm.length; j++)
          if (mm[j] != Character.toLowerCase(buf[i + j]))
            continue INNER;
        if (buf[i + mm.length] == '-') break OUTER;
      }
    }
    if( MM == MMS.length ) return Long.MIN_VALUE; // No matching month
    i += mm.length;             // Skip month bytes
    MM++;                       // 1-based month
    if( buf[i++] != '-' ) return Long.MIN_VALUE;
    yy = digit(yy,buf[i++]);    // 2-digit year
    yy = digit(yy,buf[i++]);
    if( end-i>=2 && buf[i] != '"' ) {
      yy = digit(yy,buf[i++]);  // 4-digit year
      yy = digit(yy,buf[i++]);
    } else {
      yy += 2000;               // Y2K bug
    }
    if( i<end && buf[i] == '"' ) i++;
    if( i<end ) return Long.MIN_VALUE;
    return encodeTimePat(new DateTime(yy,MM,dd,0,0,0).getMillis(),2);
  }

  // --------------------------------
  // Parse XXXXXXXX-XXXX-XXXX and return an arbitrary long, or set str.off==-1
  // (and return Long.MIN_VALUE but this is a valid long return value).
  public static long attemptUUIDParse0( ValueString str ) {
    final byte[] buf = str.get_buf();
    int i=str.get_off();
    if( i+36>buf.length ) return badUUID(str);
    long lo=0;
    lo = get2(lo,buf,(i+=2)-2);
    lo = get2(lo,buf,(i+=2)-2);
    lo = get2(lo,buf,(i+=2)-2);
    lo = get2(lo,buf,(i+=2)-2);
    if( buf[i++]!='-' ) return badUUID(str);
    lo = get2(lo,buf,(i+=2)-2);
    lo = get2(lo,buf,(i+=2)-2);
    if( buf[i++]!='-' ) return badUUID(str);
    lo = get2(lo,buf,(i+=2)-2);
    return attemptUUIDParseLast(str,lo,buf,i);
  }
  // Parse -XXXX-XXXXXXXXXXXX and return an arbitrary long, or set str.off==-1
  // (and return Long.MIN_VALUE but this is a valid long return value).
  public static long attemptUUIDParse1( ValueString str ) {
    final byte[] buf = str.get_buf();
    int i=str.get_off();
    if( i== -1 ) return badUUID(str);
    long hi=0;
    if( buf[i++]!='-' ) return badUUID(str);
    hi = get2(hi,buf,(i+=2)-2);
    hi = get2(hi,buf,(i+=2)-2);
    if( buf[i++]!='-' ) return badUUID(str);
    hi = get2(hi,buf,(i+=2)-2);
    hi = get2(hi,buf,(i+=2)-2);
    hi = get2(hi,buf,(i+=2)-2);
    hi = get2(hi,buf,(i+=2)-2);
    hi = get2(hi,buf,(i+=2)-2);
    return attemptUUIDParseLast(str,hi,buf,i);
  }

  private static long attemptUUIDParseLast( ValueString str, long lo, byte[] buf, int i ) {
    // Can never equal MIN_VALUE since only parsed 14 of 16 digits, unless
    // failed parse already.
    if( lo == Long.MIN_VALUE ) return badUUID(str);
    // If the last 2 digits are 0x8000 and the first 14 are all 0's then might
    // legitimately parse MIN_VALUE, need to check for it special.
    str.setOff(i+2);            // Mark as parsed
    if( lo == 0x80000000000000L && buf[i]=='0' && buf[i+1]=='0' )
      return Long.MIN_VALUE;    // Valid MIN_VALUE parse
    // First 14 digits are a random scramble; will never equal MIN_VALUE result
    // unless we have a failed parse in the last 2 digits
    lo = get2(lo,buf,i);
    return (lo == Long.MIN_VALUE || // broken UUID already, OR
            // too many valid UUID digits
            (i+2< buf.length && hdigit(0,buf[i+2]) != Long.MIN_VALUE)) ? badUUID(str) : lo;
  }


  private static long get2( long x, byte[] buf, int i ) {
    if( x == Long.MIN_VALUE ) return x;
    x = hdigit(x,buf[i++]);
    x = hdigit(x,buf[i++]);
    return x;
  }

  private static long hdigit( long x, byte b ) {
    if( x == Long.MIN_VALUE ) return Long.MIN_VALUE;
    else if( b >= '0' && b <= '9' ) return (x<<4)+b-'0';
    else if( b >= 'A' && b <= 'F' ) return (x<<4)+b-'A'+10;
    else if( b >= 'a' && b <= 'f' ) return (x<<4)+b-'a'+10;
    else return Long.MIN_VALUE;
  }
  public static long badUUID( ValueString str ) {
    str.setOff(-1);
    return Long.MIN_VALUE; 
  }
}
