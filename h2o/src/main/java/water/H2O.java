package water;

/**
* Start point for creating or joining an <code>H2O</code> Cloud.
*
* @author <a href="mailto:cliffc@0xdata.com"></a>
* @version 1.0
*/
public final class H2O {

  public static String VERSION = "(unknown)";
  public static long START_TIME_MILLIS = -1; // When did main() run

  // Start up an H2O Node and join any local Cloud
  public static volatile boolean IS_SYSTEM_RUNNING = false;
  public static void main( String[] args ) {
    if( IS_SYSTEM_RUNNING ) return;
    IS_SYSTEM_RUNNING = true;

    VERSION = getVersion();   // Pick this up from build-specific info.
    START_TIME_MILLIS = System.currentTimeMillis();

    printAndLogVersion();

  }

  /** If logging has not been setup yet, then Log.info will only print to
   *  stdout.  This allows for early processing of the '-version' option
   *  without unpacking the jar file and other startup stuff.  */
  public static void printAndLogVersion() {
    // Try to load a version
    //AbstractBuildVersion abv = getBuildVersion();
    //String build_branch = abv.branchName();
    //String build_hash = abv.lastCommitHash();
    //String build_describe = abv.describe();
    //String build_project_version = abv.projectVersion();
    //String build_by = abv.compiledBy();
    //String build_on = abv.compiledOn();
    //
    //Log.info ("----- H2O started -----");
    //Log.info ("Build git branch: " + build_branch);
    //Log.info ("Build git hash: " + build_hash);
    //Log.info ("Build git describe: " + build_describe);
    //Log.info ("Build project version: " + build_project_version);
    //Log.info ("Built by: '" + build_by + "'");
    //Log.info ("Built on: '" + build_on + "'");
    //
    Runtime runtime = Runtime.getRuntime();
    double ONE_GB = 1024 * 1024 * 1024;
    System.out.println/*Log.info*/ ("Java availableProcessors: " + runtime.availableProcessors());
    System.out.println/*Log.info*/ ("Java heap totalMemory: " + String.format("%.2f gb", runtime.totalMemory() / ONE_GB));
    System.out.println/*Log.info*/ ("Java heap maxMemory: " + String.format("%.2f gb", runtime.maxMemory() / ONE_GB));
    System.out.println/*Log.info*/ ("Java version: " + String.format("Java %s (from %s)", System.getProperty("java.version"), System.getProperty("java.vendor")));
    System.out.println/*Log.info*/ ("OS   version: " + String.format("%s %s (%s)", System.getProperty("os.name"), System.getProperty("os.version"), System.getProperty("os.arch")));
  }

  public static String getVersion() {
    String build_project_version = "(unknown)";
    //try {
    //  Class klass = Class.forName("water.BuildVersion");
    //  java.lang.reflect.Constructor constructor = klass.getConstructor();
    //  AbstractBuildVersion abv = (AbstractBuildVersion) constructor.newInstance();
    //  build_project_version = abv.projectVersion();
    //  // it exists on the classpath
    //} catch (Exception e) {
    //  // it does not exist on the classpath
    //}
    return build_project_version;
  }
}
