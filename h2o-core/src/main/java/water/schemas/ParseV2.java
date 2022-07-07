package water.schemas;

import java.io.File;
import java.util.Arrays;
import water.*;
import water.api.Parse;
import water.util.DocGen.HTML;

public class ParseV2 extends Schema<Parse,ParseV2> {

  // Input fields
  @API(help="Final hex key name",validation="/*this input is required*/")
  Key hex;

  @API(help="Source keys",validation="/*this input is required*/",dependsOn={"hex"})
  Key[] srcs;

  @API(help="Delete input key after parse")
  boolean delete_on_done;

  @API(help="Block until the parse completes (as opposed to returning early and requiring polling")
  boolean blocking;

  // Output fields
  @API(help="Job Key")
  Key job;

  //==========================
  // Customer adapters Go Here

  // Version&Schema-specific filling into the handler
  @Override public ParseV2 fillInto( Parse h ) {
    h._hex = hex;
    h._srcs = srcs;
    h._delete_on_done = delete_on_done;
    h._blocking = blocking;
    return this;
  }

  // Version&Schema-specific filling from the handler
  @Override public ParseV2 fillFrom( Parse h ) {
    job = h._job;
    return this;
  }

  //==========================

  @Override public HTML writeHTML_impl( HTML ab ) {
    ab.title("Parse");
    String url = InspectV1.link(hex);
    return ab.href("hex",url,url);
  }

  // Helper so ImportV1 can link to ParseV2
  public static String link(String[] keys) {
    return "Parse?hex="+hex(keys[0])+"&srcs="+Arrays.toString(keys);
  }

  private static String hex( String n ) {
    // blahblahblah/myName.ext ==> myName
    int sep = n.lastIndexOf(File.separatorChar);
    if( sep > 0 ) n = n.substring(sep+1);
    int dot = n.lastIndexOf('.');
    if( dot > 0 ) n = n.substring(0, dot);
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
}
