package water.api;

import hex.Model;
import hex.ModelMetrics;

/**
 * Base Schema for individual instances of ModelMetrics objects.
 */
public abstract class ModelMetricsBase extends Schema<ModelMetrics, ModelMetricsBase> {
  // InOut fields
  @API(help="The model used for this scoring run.")
  public ModelSchema model;

  @API(help="The checksum for the model used for this scoring run.")
  public long model_checksum;

  @API(help="The frame used for this scoring run.")
  public FrameV2 frame; // TODO: should use a base class!

  @API(help="The checksum for the frame used for this scoring run.")
  public long frame_checksum;

  // Output fields
  @API(help="The category (e.g., Clustering) for the model used for this scoring run.")
  public Model.ModelCategory model_category ;

  @API(help="The duration in mS for this scoring run.")
  public long duration_in_ms;

  @API(help="The time in mS since the epoch for the start of this scoring run.")
  public long scoring_time;

  @API(help="The AUC object for this scoring run.")
  public AUCBase auc;

  @API(help="The ConfusionMatrix object for this scoring run.")
  public ConfusionMatrixBase cm;

  @API(help="Predictions Frame.")
  public FrameV2 predictions;

  // Non-version-specific filling into the impl
  @Override public ModelMetrics createImpl() {
    ModelMetrics m = new ModelMetrics(this.model.createImpl(),
                                      this.frame.createImpl(),
                                      this.duration_in_ms,
                                      this.scoring_time,
                                      this.auc.createImpl(),
                                      null
                                      /* TODO: choose either ConfusionMatrix or ConfusionMatrix2! this.cm.createImpl()*/);
    return m;
  }

  @Override public ModelMetricsBase fillFromImpl(ModelMetrics modelMetrics) {
    // TODO: this is failing in PojoUtils with an IllegalAccessException.  Why?  Different class loaders?
    // PojoUtils.copyProperties(this, m, PojoUtils.FieldNaming.CONSISTENT);

    // TODO: fix PojoUtils.copyProperties so we don't need the boilerplate here:
    // For the Model we want the key and the parameters, but no output.
    Model model = modelMetrics.model.get();
    this.model = model.schema().fillFromImpl(model);
    this.model.output = null;
    this.model_checksum = modelMetrics.model_checksum;
    this.frame = new FrameV2(modelMetrics.frame);
    this.frame_checksum = modelMetrics.frame_checksum;
    this.model_category = modelMetrics.model_category;
    this.duration_in_ms = modelMetrics.duration_in_ms;
    this.scoring_time = modelMetrics.scoring_time;
    this.auc = new AUCV3().fillFromImpl(modelMetrics.auc); // TODO: shouldn't be version-specific
    // TODO: need to have only one Iced CM class. . .
    // this.cm = new ConfusionMatrixV3().fillFromImpl(modelMetrics.cm);  // TODO: shouldn't be version-specific

    return this;
  }

  public static ModelMetricsBase schema(int version) {
    return new ModelMetricsV3();
  }
}
