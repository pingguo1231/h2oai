H2O.AutoModelInput = (_, _go, opts={}) ->
  _trainingFrames = signal []
  _trainingFrame = signal null
  _validationFrames = signal []
  _validationFrame = signal null
  _hasTrainingFrame = lift _trainingFrame, (frame) -> if frame then yes else no
  _columns = signal []
  _column = signal null
  _canBuildModel = lift _trainingFrame, _column, (frame, column) -> frame and column

  # TODO loss
  defaultSeed = -1
  _seed = signal defaultSeed
  defaultMaxModels = 0
  _maxModels = signal ''
  defaultMaxRunTime = 3600
  _maxRuntimeSecs = signal defaultMaxRunTime
  _stoppingMetrics = signal []
  _stoppingMetric = signal null
  defaultStoppingRounds = 0
  _stoppingRounds = signal defaultStoppingRounds
  defaultStoppingTolerance = 0.001
  _stoppingTolerance = signal defaultStoppingTolerance

  buildModel = ->
    seed = defaultSeed
    unless isNaN parsed = parseInt _seed(), 10
      seed = parsed

    maxModels = defaultMaxModels
    unless isNaN parsed = parseInt _maxModels(), 10
      maxModels = parsed

    maxRuntimeSecs = defaultMaxRunTime
    unless isNaN parsed = parseInt _maxRuntimeSecs(), 10
      maxRuntimeSecs = parsed

    stoppingRounds = defaultStoppingRounds
    unless isNaN parsed = parseInt _stoppingRounds(), 10
      stoppingRounds = parsed

    stoppingTolerance = defaultStoppingTolerance
    unless isNaN parsed = parseFloat _stoppingTolerance()
      stoppingTolerance = parsed

    # TODO loss
    arg =
      training_frame: _trainingFrame()
      response_column: _column()
      validation_frame: _validationFrame()
      seed: seed
      max_models: maxModels
      max_runtime_secs: maxRuntimeSecs
      stopping_metric: _stoppingMetric()
      stopping_rounds: stoppingRounds
      stopping_tolerance: stoppingTolerance

    _.insertAndExecuteCell 'cs', "buildAutoModel #{JSON.stringify arg}"

  _.requestFrames (error, frames) ->
    unless error
      frames = (frame.frame_id.name for frame in frames when not frame.is_text)
      _trainingFrames frames
      _validationFrames frames
      if opts.training_frame
        _trainingFrame opts.training_frame
      if opts.validation_frame
        _validationFrame opts.validation_frame

      return
  
  findSchemaField = (schema, name) ->
    for field in schema.fields when field.schema_name is name
      return field

  _.requestSchema 'RandomDiscreteValueSearchCriteriaV99', (error, response) ->
    unless error
      schema = head response.schemas

      # TODO loss enum

      if field = findSchemaField schema, 'ScoreKeeperStoppingMetric'
        _stoppingMetrics field.values


  react _trainingFrame, (frame) ->
    if frame
      _.requestFrameSummaryWithoutData frame, (error, frame) ->
        unless error
          _columns (column.label for column in frame.columns)
          if opts.response_column
            _column opts.response_column
            delete opts.response_column #HACK
    else
      _columns []
  
  defer _go

  trainingFrames: _trainingFrames
  trainingFrame: _trainingFrame
  hasTrainingFrame: _hasTrainingFrame
  validationFrames: _validationFrames
  validationFrame: _validationFrame
  columns: _columns
  column: _column
  seed: _seed
  maxModels: _maxModels
  maxRuntimeSecs: _maxRuntimeSecs
  stoppingMetrics: _stoppingMetrics
  stoppingMetric: _stoppingMetric
  stoppingRounds: _stoppingRounds
  stoppingTolerance: _stoppingTolerance
  canBuildModel: _canBuildModel
  buildModel: buildModel
  template: 'flow-automodel-input'

