package water.parser;

import water.DKV;
import water.H2O;
import water.Iced;
import water.Key;

import java.util.HashSet;

/**
* Configuration and base guesser for a parse;
*/
public final class ParseSetup extends Iced {
  static final byte AUTO_SEP = -1;
  Key[] _srcs;                      // Source Keys being parsed
  int _checkHeader;                 // 1st row: 0: guess, +1 header, -1 data
  // Whether or not single-quotes quote a field.  E.g. how do we parse:
  // raw data:  123,'Mally,456,O'Mally
  // singleQuotes==True  ==> 2 columns: 123  and  Mally,456,OMally
  // singleQuotes==False ==> 4 columns: 123  and  'Mally  and  456  and  O'Mally
  boolean _singleQuotes;

  String _hexName;                  // Cleaned up result Key suggested name
  ParserType _pType;                // CSV, XLS, XSLX, SVMLight, Auto
  byte _sep;          // Field separator, usually comma ',' or TAB or space ' '
  int _ncols;         // Columns to parse
  String[] _columnNames;
  String[][] _data;           // First few rows of parsed/tokenized data
  boolean _isValid;           // The initial parse is sane
  String[] _errors;           // Errors in this parse setup
  long _invalidLines; // Number of broken/invalid lines found

  public ParseSetup(boolean isValid, long invalidLines, String[] errors, ParserType t, byte sep, int ncols, boolean singleQuotes, String[] columnNames, String[][] data, int checkHeader) {
    _isValid = isValid;
    _invalidLines = invalidLines;
    _errors = errors;
    _pType = t;
    _sep = sep;
    _ncols = ncols;
    _singleQuotes = singleQuotes;
    _columnNames = columnNames;
    _data = data;
    _checkHeader = checkHeader;
  }

  // Invalid setup based on a prior valid one
  ParseSetup(ParseSetup ps, String err) {
    this(false, ps._invalidLines, new String[]{err}, ps._pType, ps._sep, ps._ncols, ps._singleQuotes, ps._columnNames, ps._data, ps._checkHeader);
  }

  // Called from Nano request server with a set of Keys, produce a suitable parser setup guess.
  public ParseSetup() {
  }

  final boolean hasHeaders() { return _columnNames != null; }

  public Parser parser() {
    switch( _pType ) {
      case CSV:      return new      CsvParser(this);
      case XLS:      return new      XlsParser(this);
      case SVMLight: return new SVMLightParser(this);
    }
    throw H2O.fail();
  }

  // Set of duplicated column names
  HashSet<String> checkDupColumnNames() {
    HashSet<String> conflictingNames = new HashSet<>();
    if( _columnNames==null ) return conflictingNames;
    HashSet<String> uniqueNames = new HashSet<>();
    for( String n : _columnNames )
      (uniqueNames.contains(n) ? conflictingNames : uniqueNames).add(n);
    return conflictingNames;
  }

  @Override public String toString() { return _pType.toString( _ncols, _sep ); }

  static boolean allStrings(String [] line){
    ValueString str = new ValueString();
    for( String s : line ) {
      try {
        Double.parseDouble(s);
        return false;       // Number in 1st row guesses: No Column Header
      } catch (NumberFormatException e) { /*Pass - determining if number is possible*/ }
      if( ParseTime.attemptTimeParse(str.setTo(s)) != Long.MIN_VALUE ) return false;
      ParseTime.attemptUUIDParse0(str.setTo(s));
      ParseTime.attemptUUIDParse1(str);
      if( str.get_off() != -1 ) return false; // Valid UUID parse
    }
    return true;
  }
  // simple heuristic to determine if we have headers:
  // return true iff the first line is all strings and second line has at least one number
  static boolean hasHeader(String[] l1, String[] l2) {
    return allStrings(l1) && !allStrings(l2);
  }

  // Guess everything from a single pile-o-bits.  Used in tests, or in initial
  // parser inspections when the user has not told us anything about separators
  // or headers.
  public static ParseSetup guessSetup( byte[] bits, boolean singleQuotes, int checkHeader ) { return guessSetup(bits, ParserType.AUTO, AUTO_SEP, -1, singleQuotes, checkHeader, null); }

  private static final ParserType guessTypeOrder[] = {ParserType.XLS,ParserType.XLSX,ParserType.SVMLight,ParserType.CSV};
  public static ParseSetup guessSetup( byte[] bits, ParserType pType, byte sep, int ncols, boolean singleQuotes, int checkHeader, String[] columnNames ) {
    switch( pType ) {
      case CSV:      return      CsvParser.CSVguessSetup(bits,sep,ncols,singleQuotes,checkHeader,columnNames);
      case SVMLight: return SVMLightParser.   guessSetup(bits);
      case XLS:      return      XlsParser.   guessSetup(bits);
      case AUTO:
        for( ParserType pType2 : guessTypeOrder ) {
          try {
            ParseSetup ps = guessSetup(bits,pType2,sep,ncols,singleQuotes,checkHeader,columnNames);
            if( ps != null && ps._isValid ) return ps;
          } catch( Throwable ignore ) { /*ignore failed parse attempt*/ }
        }
    }
    return new ParseSetup( false, 0, new String[]{"Cannot determine file type"}, pType, sep, ncols, singleQuotes, columnNames, null, checkHeader );
  }

  // Guess a local setup that is compatible to the given global (this) setup.
  // If they are not compatible, there will be _errors set.
  ParseSetup guessSetup( byte[] bits, int checkHeader ) {
    assert _isValid;
    ParseSetup ps = guessSetup(bits, _singleQuotes, checkHeader);
    if( !ps._isValid ) return ps; // Already invalid
    if( _pType != ps._pType ||
            (_pType == ParserType.CSV && (_sep != ps._sep || _ncols != ps._ncols)) )
      return new ParseSetup(ps,"Conflicting file layouts, expecting: "+this+" but found "+ps+"\n");
    return ps;
  }

  protected static String hex( String n ) {
    // blahblahblah/myName.ext ==> myName
    // blahblahblah/myName.csv.ext ==> myName
    int sep = n.lastIndexOf(java.io.File.separatorChar);
    if( sep > 0 ) n = n.substring(sep+1);
    int dot = n.lastIndexOf('.');
    if( dot > 0 ) n = n.substring(0, dot);
    int dot2 = n.lastIndexOf('.');
    if( dot2 > 0 ) n = n.substring(0, dot2);
    // "2012_somedata" ==> "X2012_somedata"
    if( !Character.isJavaIdentifierStart(n.charAt(0)) ) n = "X"+n;
    // "human%Percent" ==> "human_Percent"
    char[] cs = n.toCharArray();
    for( int i=1; i<cs.length; i++ )
      if( !Character.isJavaIdentifierPart(cs[i]) )
        cs[i] = '_';
    // "myName" ==> "myName.hex"
    n = new String(cs);
    int i = 0;
    String res = n + ".hex";
    Key k = Key.make(res);
    // Renumber to handle dup names
    while(DKV.get(k) != null)
      k = Key.make(res = n + ++i + ".hex");
    return res;
  }
} // ParseSetup state class
