H2O.ModelOutput = (_, _model) ->
  _isExpanded = signal no
  
  _inputParameters = map _model.parameters, (parameter) ->
    label: parameter.label
    value: parameter.actual_value
    help: parameter.help
    isModified: parameter.default_value is parameter.actual_value

  toggle = ->
    _isExpanded not _isExpanded()

  key: _model.key
  inputParameters: _inputParameters
  isExpanded: _isExpanded
  toggle: toggle
  template: 'flow-model-output'

