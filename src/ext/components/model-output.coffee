H2O.ModelOutput = (_, _model) ->
  _isExpanded = signal no
  
  _inputParameters = map _model.parameters, (parameter) ->
    label: parameter.label
    value: parameter.actual_value
    help: parameter.help
    isModified: parameter.default_value is parameter.actual_value

  toggle = ->
    _isExpanded not _isExpanded()

  cloneModel = ->
    # _.insertAndExecuteCell 'cs', 'assist buildModel, 
    alert 'Not implemented'

  predict = ->
    _.insertAndExecuteCell 'cs', "predict #{stringify _model.key.name}"

  inspect = ->
    _.insertAndExecuteCell 'cs', "inspect getModel #{stringify _model.key.name}"

  key: _model.key
  algo: _model.algo
  inputParameters: _inputParameters
  isExpanded: _isExpanded
  toggle: toggle
  cloneModel: cloneModel
  predict: predict
  inspect: inspect
  template: 'flow-model-output'

