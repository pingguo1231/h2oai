package hex.schemas;

import hex.deeplearning.DeepLearning;
import hex.deeplearning.DeepLearningModel;
import water.H2O;
import water.api.Handler;

public class DeepLearningHandler extends Handler<DeepLearning, DeepLearningV2> {
  @Override protected int min_ver() { return 2; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  public DeepLearningHandler() {}

  // TODO: move this into a new ModelBuilderHandler superclass
  // TODO: also add a score method in the new ModelBuilderHandler superclass
  public DeepLearningV2 train(int version, DeepLearning builder) {
    DeepLearningModel.DeepLearningParameters parms = builder._parms;
    assert parms != null; /* impl._job = */
    builder.train();
    DeepLearningV2 schema = schema(version); // TODO: superclass!
    schema.parameters = new DeepLearningV2.DeepLearningParametersV2();
    schema.job = builder._key;
    return schema;
  }
  @Override protected DeepLearningV2 schema(int version) { return new DeepLearningV2(); }
  @Override public void compute2() { throw H2O.fail(); }
}
