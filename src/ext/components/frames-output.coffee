H2O.FramesOutput = (_, _go, _frames) ->
  _frameViews = signal []
  _checkAllFrames = signal no
  _hasSelectedFrames = signal no

  _isCheckingAll = no
  react _checkAllFrames, (checkAll) ->
    _isCheckingAll = yes
    for view in _frameViews()
      view.isChecked checkAll
    _hasSelectedFrames checkAll
    _isCheckingAll = no
    return

  createFrameView = (frame) ->
    _isChecked = signal no

    react _isChecked, ->
      return if _isCheckingAll
      checkedViews = (view for view in _frameViews() when view.isChecked())
      _hasSelectedFrames checkedViews.length > 0

    columnLabels = head (map frame.columns, (column) -> column.label), 15
    description = 'Columns: ' + (columnLabels.join ', ') + if frame.columns.length > columnLabels.length then "... (#{frame.columns.length - columnLabels.length} more columns)" else ''

    view = ->
      if frame.is_text
        #TODO handle this properly. frames-output.jade currently does not allow viewing
        _.insertAndExecuteCell 'cs', "setupParse [ #{stringify frame.key.name } ]"
      else
        _.insertAndExecuteCell 'cs', "getFrame #{stringify frame.key.name}"

    predict = ->
      _.insertAndExecuteCell 'cs', "predict frame: #{stringify frame.key.name}"

    inspect = ->
      _.insertAndExecuteCell 'cs', "inspect getFrame #{stringify frame.key.name}"

    createModel = ->
      _.insertAndExecuteCell 'cs', "assist buildModel, null, training_frame: #{stringify frame.key.name}"


    key: frame.key.name
    isChecked: _isChecked
    description: description
    size: Flow.Util.formatBytes frame.byte_size
    rowCount: frame.rows
    columnCount: frame.columns.length
    isText: frame.is_text
    view: view
    predict: predict
    inspect: inspect
    createModel: createModel

  importFiles = ->
    _.insertAndExecuteCell 'cs', 'importFiles'

  collectSelectedKeys = ->
    for view in _frameViews() when view.isChecked()
      view.key

  predictOnFrames = ->
    _.insertAndExecuteCell 'cs', "predict frames: #{stringify collectSelectedKeys()}"

  deleteFrames = ->
    _.confirm 'Are you sure you want to delete these frames?', { acceptCaption: 'Delete Frames', declineCaption: 'Cancel' }, (accept) ->
      if accept
        _.insertAndExecuteCell 'cs', "deleteFrames #{stringify collectSelectedKeys()}"
    


  _frameViews map _frames, createFrameView

  defer _go

  frameViews: _frameViews
  hasFrames: _frames.length > 0
  importFiles: importFiles
  predictOnFrames: predictOnFrames
  deleteFrames: deleteFrames
  hasSelectedFrames: _hasSelectedFrames
  checkAllFrames: _checkAllFrames
  template: 'flow-frames-output'

