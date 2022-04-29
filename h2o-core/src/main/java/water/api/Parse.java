package water.api;

import java.util.*;
import water.*;
import water.util.RString;

public class Parse extends Handler {
  // Inputs
  private Key _hex; // Key holding final value after job is removed
  private Key[] _srcs;          // Source keys
  private boolean _delete_on_done = true;
  private boolean _blocking = true;

  // Output
  private Key _job; // Boolean read-only value; exists==>running, not-exists==>canceled/removed

  // MAPPING from URI ==> POJO
  // 
  // THIS IS THE WRONG ARCHITECTURE....  either this call should be auto-gened
  // (auto-gen moves parms into local fields & complains on errors) or 
  // it needs to move into an explicit Schema somehow.
  //@Override public Response checkArguments(Properties parms) {
  //  for( Enumeration<String> e = (Enumeration<String>)parms.propertyNames(); e.hasMoreElements(); ) {
  //    String prop = e.nextElement();
  //    if( false ) {
  //    } else if( prop.equals("hex") ) { 
  //      _hex = Key.make(parms.getProperty(prop));
  //    } else if( prop.equals("srcs") ) {
  //      String srcs = parms.getProperty(prop);
  //      if( !srcs.startsWith("[") || !srcs.endsWith("]") ) return throwIAE("Bad 'srcs' param: "+srcs);
  //      srcs = srcs.substring(1,srcs.length()-1);
  //      String ss[] = srcs.split(",");
  //      Key keys[] = new Key[ss.length];
  //      for( int i=0; i<ss.length; i++ )
  //        if( DKV.get(keys[i] = Key.make(ss[i])) == null )
  //          return throwIAE("Missing srcs key "+keys[i]);
  //      _srcs = keys;
  //    } else {
  //      return throwIAE("unknown parameter: "+prop);
  //    }
  //  }
  //  if( _hex == null ) return throwIAE("Missing 'hex'");
  //  if( _srcs == null || _srcs.length==0 ) return throwIAE("Missing 'srcs'");
  //  return null;                // Happy happy
  //}


  @Override protected Response serve(RequestServer server, String uri, String method, Properties parms, RequestType type) {
    water.parser.ParseDataset2.parse(_hex,_srcs);
    throw H2O.unimpl();
    //return new Response("<a href=/2/Inspect.html?src="+_hex+">Parse done!</a>");
    //return new Response("<a href=/2/DeepLearning.html?src="+_hex+">Parse done!</a>");
  }

  //public static String link(String k, String content) {
  //  RString rs = new RString("<a href='Parse2.query?source_key=%key'>%content</a>");
  //  rs.replace("key", k.toString());
  //  rs.replace("content", content);
  //  return rs.toString();
  //}

}
