package water.util;

import java.io.File;
import java.util.ArrayList;
import org.apache.log4j.LogManager;
import org.apache.log4j.PropertyConfigurator;
import water.H2O;
import water.persist.Persist;

/** Log for H2O. 
 *
 *  OOME: when the VM is low on memory, OutOfMemoryError can be thrown in the
 *  logging framework while it is trying to print a message. In this case the
 *  first message that fails is recorded for later printout, and a number of
 *  messages can be discarded. The framework will attempt to print the recorded
 *  message later, and report the number of dropped messages, but this done in
 *  a best effort and lossy manner. Basically when an OOME occurs during
 *  logging, no guarantees are made about the messages.
 **/
abstract public class Log {

  private static org.apache.log4j.Logger _logger = null;

  static String LOG_DIR = null;

  static final int FATAL= 0;
  static final int ERRR = 1;
  static final int WARN = 2;
  static final int INFO = 3;
  static final int DEBUG= 4;
  static final int TRACE= 5;
  static final String[] LVLS = { "FATAL", "ERRR", "WARN", "INFO", "DEBUG", "TRACE" };
  static int _level=INFO;

  // Common pre-header
  private static String _preHeader;

  public static void init( String slvl ) {
    if( slvl != null ) {
      slvl = slvl.toLowerCase();
      if( slvl.startsWith("fatal") ) _level = FATAL;
      if( slvl.startsWith("err"  ) ) _level = ERRR;
      if( slvl.startsWith("warn" ) ) _level = WARN;
      if( slvl.startsWith("info" ) ) _level = INFO;
      if( slvl.startsWith("debug") ) _level = DEBUG;
      if( slvl.startsWith("trace") ) _level = TRACE;
    }
  }
  
  public static void trace( Object... objs ) { write(TRACE,objs); }
  public static void debug( Object... objs ) { write(DEBUG,objs); }
  public static void info ( Object... objs ) { write(INFO ,objs); }
  public static void warn ( Object... objs ) { write(WARN ,objs); }
  public static void err  ( Object... objs ) { write(ERRR ,objs); }
  public static void fatal( Object... objs ) { write(FATAL,objs); }

  public static void httpd( String msg ) {
    org.apache.log4j.Logger l = LogManager.getLogger(water.api.RequestServer.class);
    String s = "tid(" + Thread.currentThread().getId() + ") " + msg;
    l.info(s);
  }

  public static void info_no_stdout( String s ) { write0(INFO, false, s); }

  public static RuntimeException throwErr( Throwable e ) {
    err(e);                     // Log it
    throw e instanceof RuntimeException ? (RuntimeException)e : new RuntimeException(e); // Throw it
  }

  private static void write( int lvl, Object objs[] ) {
    boolean writeToStdout = (lvl <= _level);
    write0(lvl, writeToStdout, objs);
  }

  private static void write0( int lvl, boolean stdout, Object objs[] ) {
    StringBuilder sb = new StringBuilder();
    for( Object o : objs ) sb.append(o);
    String res = sb.toString();
    if( H2O.SELF_ADDRESS == null ) { // Oops, need to buffer until we can do a proper header
      INIT_MSGS.add(res);
      return;
    }
    if( INIT_MSGS != null ) {   // Ahh, dump any initial buffering
      String host = H2O.SELF_ADDRESS.getHostAddress();
      _preHeader = fixedLength(host + ":" + H2O.API_PORT + " ", 22) + fixedLength(H2O.PID + " ", 6);
      ArrayList<String> bufmsgs = INIT_MSGS;  INIT_MSGS = null;
      for( String s : bufmsgs ) write0(INFO, true, s);
    }
    write0(lvl, stdout, res);
  }

  private static void write0( int lvl, boolean stdout, String s ) {
    StringBuilder sb = new StringBuilder();
    String hdr = header(lvl);   // Common header for all lines
    write0(sb, hdr, s);

    // stdout first - in case log4j dies failing to init or write something
    if( stdout ) System.out.println(sb);

    // log something here
    org.apache.log4j.Logger l4j = _logger != null ? _logger : createLog4j();
    switch( lvl ) {
    case FATAL:l4j.fatal(sb); break;
    case ERRR: l4j.error(sb); break;
    case WARN: l4j.warn (sb); break;
    case INFO: l4j.info (sb); break;
    case DEBUG:l4j.debug(sb); break;
    case TRACE:l4j.trace(sb); break;
    default:
      l4j.error("Invalid log level requested");
      l4j.error(s);
    }
  }

  private static void write0( StringBuilder sb, String hdr, String s ) {
    if( s.contains("\n") ) {
      for( String s2 : s.split("\n") ) { write0(sb,hdr,s2); sb.append("\n"); }
      sb.setLength(sb.length()-1);
    } else {
      sb.append(hdr).append(s);
    }
  }

  // Build a header for all lines in a single message
  private static String header( int lvl ) {
    String nowString = Timer.nowAsLogString();
    String s = nowString +" "+_preHeader+" "+
      fixedLength(Thread.currentThread().getName() + " ", 10)+
      LVLS[lvl]+": ";
    return s;
  }

  // A little bit of startup buffering
  private static ArrayList<String> INIT_MSGS = new ArrayList<String>();

  /**
   * @return This is what should be used when doing Download All Logs.
   */
  public static String getLogDir() throws Exception {
    if (LOG_DIR == null) {
      throw new Exception("LOG_DIR not yet defined");
    }

    return LOG_DIR;
  }

  /**
   * @return The common prefix for all of the different log files for this process.
   */
  public static String getLogPathFileNameStem() throws Exception {
    if (H2O.SELF_ADDRESS == null) {
      throw new Exception("H2O.SELF_ADDRESS not yet defined");
    }

    String ip = H2O.SELF_ADDRESS.getHostAddress();
    int port = H2O.API_PORT;
    String portString = Integer.toString(port);
    String logFileName =
            getLogDir() + File.separator +
                    "h2o_" + ip + "_" + portString;
    return logFileName;
  }

  /**
   * @return This is what shows up in the Web UI when clicking on show log file.
   */
  public static String getLogPathFileName() throws Exception {
    return getLogPathFileNameStem() + "-2-debug.log";
  }

  private static void setLog4jProperties(String logDirParent, java.util.Properties p) throws Exception {
    LOG_DIR = logDirParent + File.separator + "h2ologs";
    String logPathFileName = getLogPathFileNameStem();

    // H2O-wide logging
    p.setProperty("log4j.rootLogger", "TRACE, R1, R2, R3, R4, R5, R6");

    p.setProperty("log4j.appender.R1",                          "org.apache.log4j.RollingFileAppender");
    p.setProperty("log4j.appender.R1.Threshold",                "TRACE");
    p.setProperty("log4j.appender.R1.File",                     logPathFileName + "-1-trace.log");
    p.setProperty("log4j.appender.R1.MaxFileSize",              "1MB");
    p.setProperty("log4j.appender.R1.MaxBackupIndex",           "3");
    p.setProperty("log4j.appender.R1.layout",                   "org.apache.log4j.PatternLayout");
    p.setProperty("log4j.appender.R1.layout.ConversionPattern", "%m%n");

    p.setProperty("log4j.appender.R2",                          "org.apache.log4j.RollingFileAppender");
    p.setProperty("log4j.appender.R2.Threshold",                "DEBUG");
    p.setProperty("log4j.appender.R2.File",                     logPathFileName + "-2-debug.log");
    p.setProperty("log4j.appender.R2.MaxFileSize",              "3MB");
    p.setProperty("log4j.appender.R2.MaxBackupIndex",           "3");
    p.setProperty("log4j.appender.R2.layout",                   "org.apache.log4j.PatternLayout");
    p.setProperty("log4j.appender.R2.layout.ConversionPattern", "%m%n");

    p.setProperty("log4j.appender.R3",                          "org.apache.log4j.RollingFileAppender");
    p.setProperty("log4j.appender.R3.Threshold",                "INFO");
    p.setProperty("log4j.appender.R3.File",                     logPathFileName + "-3-info.log");
    p.setProperty("log4j.appender.R3.MaxFileSize",              "2MB");
    p.setProperty("log4j.appender.R3.MaxBackupIndex",           "3");
    p.setProperty("log4j.appender.R3.layout",                   "org.apache.log4j.PatternLayout");
    p.setProperty("log4j.appender.R3.layout.ConversionPattern", "%m%n");

    p.setProperty("log4j.appender.R4",                          "org.apache.log4j.RollingFileAppender");
    p.setProperty("log4j.appender.R4.Threshold",                "WARN");
    p.setProperty("log4j.appender.R4.File",                     logPathFileName + "-4-warn.log");
    p.setProperty("log4j.appender.R4.MaxFileSize",              "256KB");
    p.setProperty("log4j.appender.R4.MaxBackupIndex",           "3");
    p.setProperty("log4j.appender.R4.layout",                   "org.apache.log4j.PatternLayout");
    p.setProperty("log4j.appender.R4.layout.ConversionPattern", "%m%n");

    p.setProperty("log4j.appender.R5",                          "org.apache.log4j.RollingFileAppender");
    p.setProperty("log4j.appender.R5.Threshold",                "ERROR");
    p.setProperty("log4j.appender.R5.File",                     logPathFileName + "-5-error.log");
    p.setProperty("log4j.appender.R5.MaxFileSize",              "256KB");
    p.setProperty("log4j.appender.R5.MaxBackupIndex",           "3");
    p.setProperty("log4j.appender.R5.layout",                   "org.apache.log4j.PatternLayout");
    p.setProperty("log4j.appender.R5.layout.ConversionPattern", "%m%n");

    p.setProperty("log4j.appender.R6",                          "org.apache.log4j.RollingFileAppender");
    p.setProperty("log4j.appender.R6.Threshold",                "FATAL");
    p.setProperty("log4j.appender.R6.File",                     logPathFileName + "-6-fatal.log");
    p.setProperty("log4j.appender.R6.MaxFileSize",              "256KB");
    p.setProperty("log4j.appender.R6.MaxBackupIndex",           "3");
    p.setProperty("log4j.appender.R6.layout",                   "org.apache.log4j.PatternLayout");
    p.setProperty("log4j.appender.R6.layout.ConversionPattern", "%m%n");

    // HTTPD logging
    p.setProperty("log4j.logger.water.api.RequestServer",       "TRACE, HTTPD");

    p.setProperty("log4j.appender.HTTPD",                       "org.apache.log4j.RollingFileAppender");
    p.setProperty("log4j.appender.HTTPD.Threshold",             "TRACE");
    p.setProperty("log4j.appender.HTTPD.File",                  logPathFileName + "-httpd.log");
    p.setProperty("log4j.appender.HTTPD.MaxFileSize",           "1MB");
    p.setProperty("log4j.appender.HTTPD.MaxBackupIndex",        "3");
    p.setProperty("log4j.appender.HTTPD.layout",                "org.apache.log4j.PatternLayout");
    p.setProperty("log4j.appender.HTTPD.layout.ConversionPattern", "%d{ISO8601} %m%n");

    // Turn down the logging for some class hierarchies.
    p.setProperty("log4j.logger.org.apache.http",               "WARN");
    p.setProperty("log4j.logger.com.amazonaws",                 "WARN");
    p.setProperty("log4j.logger.org.apache.hadoop",             "WARN");
    p.setProperty("log4j.logger.org.jets3t.service",            "WARN");

    // See the following document for information about the pattern layout.
    // http://logging.apache.org/log4j/1.2/apidocs/org/apache/log4j/PatternLayout.html
    //
    //  Uncomment this line to find the source of unwanted messages.
    //     p.setProperty("log4j.appender.R1.layout.ConversionPattern", "%p %C %m%n");
  }

  private static synchronized org.apache.log4j.Logger createLog4j() {
    if( _logger != null ) return _logger; // Test again under lock

    // Use ice folder if local, or default
    File dir = new File( H2O.ICE_ROOT.getScheme() == null || Persist.Schemes.FILE.equals(H2O.ICE_ROOT.getScheme())
                         ? H2O.ICE_ROOT.getPath()
                         : H2O.DEFAULT_ICE_ROOT());
      
    // If a log4j properties file was specified on the command-line, use it.
    // Otherwise, create some default properties on the fly.
    String log4jProperties = System.getProperty ("log4j.configuration");
    if (log4jProperties != null) {
      PropertyConfigurator.configure(log4jProperties);
    } else {
      java.util.Properties p = new java.util.Properties();
      try {
        setLog4jProperties(dir.toString(), p);
      }
      catch (Exception e) {
        System.err.println("ERROR: failed in createLog4j, exiting now.");
        e.printStackTrace();
        H2O.exit(1);
      }
      PropertyConfigurator.configure(p);
    }
    
    return (_logger = LogManager.getLogger(Log.class.getName()));
  }

  static String fixedLength(String s, int length) {
    String r = padRight(s, length);
    if( r.length() > length ) {
      int a = Math.max(r.length() - length + 1, 0);
      int b = Math.max(a, r.length());
      r = "#" + r.substring(a, b);
    }
    return r;
  }
  
  static String padRight(String stringToPad, int size) {
    StringBuilder strb = new StringBuilder(stringToPad);
    while( strb.length() < size )
      if( strb.length() < size ) strb.append(' ');
    return strb.toString();
  }
  
}
