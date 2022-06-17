

public class H2OApp {
  public static void main( String[] args ) {
    
    // Fire up the H2O Cluster
    water.H2O.main(args);

    // Register menu items and service handlers for algos
    water.H2O.registerGET("/DeepLearning",hex.schemas.DeepLearningHandler.class,"compute2","Deep Learning","Model");

    // Done adding menu items; fire up web server
    water.H2O.finalizeRequest();
  }
}
