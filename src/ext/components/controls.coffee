{ defer, filter, find, flatten, forEach, groupBy, map, throttle, uniqueId } = require('lodash')
{ act, lift, react, signal, signals } = require("../../core/modules/dataflow")

columnLabelsFromFrame = (frame) ->
  columnLabels = map frame.columns, (column) ->
    missingPercent = 100 * column.missing_count / frame.rows

    type: if column.type is 'enum' then "enum(#{column.domain_cardinality})" else column.type
    value: column.label
    missingPercent: missingPercent
    missingLabel: if missingPercent is 0 then '' else "#{Math.round missingPercent}% NA"
  columnLabels


createControl = (kind, parameter) ->
  _hasError = signal no
  _hasWarning = signal no
  _hasInfo = signal no
  _message = signal ''
  _hasMessage = lift _message, (message) -> if message then yes else no
  _isVisible = signal yes
  _isGrided = signal no
  _isNotGrided = lift _isGrided, (value) -> not value

  kind: kind
  name: parameter.name
  label: parameter.label
  description: parameter.help
  isRequired: parameter.required
  hasError: _hasError
  hasWarning: _hasWarning
  hasInfo: _hasInfo
  message: _message
  hasMessage: _hasMessage
  isVisible: _isVisible
  isGridable: parameter.gridable
  isGrided: _isGrided
  isNotGrided: _isNotGrided


createTextboxControl = (parameter, type) ->
  isArrayValued = isInt = isReal = no

  switch type
    when 'byte[]', 'short[]', 'int[]', 'long[]'
      isArrayValued = yes
      isInt = yes
    when 'float[]', 'double[]'
      isArrayValued = yes
      isReal = yes
    when 'byte', 'short', 'int', 'long'
      isInt = yes
    when 'float', 'double'
      isReal = yes

  _text = signal if isArrayValued then (parameter.value ? []).join ', ' else (parameter.value ? '')

  _textGrided = signal _text() + ';'

  textToValue = (text) ->
    if isInt
      unless isNaN parsed = parseInt text, 10
        parsed
    else if isReal
      unless isNaN parsed = parseFloat text
        parsed
    else
      if text is '' then null else text

  textToValues = (text) ->
    if isArrayValued
      vals = []
      for value in text.split /\s*,\s*/g
        v = textToValue value
        if v?
          vals.push v
      vals
    else
      textToValue text

  _value = lift _text, textToValues

  _valueGrided = lift _textGrided, (text) ->
    values = []
    for part in "#{text}".split /\s*;\s*/g
      if token = part.trim()
        values.push textToValues token
    values

  control = createControl 'textbox', parameter
  control.text = _text
  control.textGrided = _textGrided
  control.value = _value
  control.valueGrided = _valueGrided
  control.isArrayValued = isArrayValued
  control


createGridableValues = (values, defaultValue) ->
  map values, (value) ->
    label: value
    value: signal true


createDropdownControl = (parameter) ->
  _value = signal parameter.value

  control = createControl 'dropdown', parameter
  control.values = signals parameter.values
  control.value = _value
  control.valueGrided = lift control.values, (values) ->
    createGridableValues values
  control


createCheckboxControl = (parameter) ->
  _value = signal parameter.value

  control = createControl 'checkbox', parameter
  control.clientId = do uniqueId
  control.value = _value
  control.valueGrided = [true, false]
  control


createListControl = (parameter) ->
  MaxItemsPerPage = 25
  _searchTerm = signal ''
  _ignoreNATerm = signal ''

  _values = signals parameter.values

  _selectionCount = signal 0

  _isUpdatingSelectionCount = no
  blockSelectionUpdates = (f) ->
    _isUpdatingSelectionCount = yes
    f()
    _isUpdatingSelectionCount = no

  incrementSelectionCount = (amount) ->
    _selectionCount _selectionCount() + amount

  createEntry = (value) ->
    isSelected = signal no
    react isSelected, (isSelected) ->
      unless _isUpdatingSelectionCount
        if isSelected
          incrementSelectionCount 1
        else
          incrementSelectionCount -1
      return

    isSelected: isSelected
    value: if typeof value is 'string' then value else value.value
    type: value.type
    missingLabel: value.missingLabel
    missingPercent: value.missingPercent

  _entries = lift _values, (values) -> map values, createEntry
  _filteredItems = signal []
  _visibleItems = signal []
  _hasFilteredItems = lift _filteredItems, (entries) -> entries.length > 0
  _columnsFilterEnabled = signal parameter.type isnt 'enum[]'
  _navEnabled = signal parameter.type isnt 'enum[]'
  _currentPage = signal 0
  _maxPages = lift _filteredItems, (entries) -> Math.ceil entries.length / MaxItemsPerPage
  _canGoToPreviousPage = lift _currentPage, (index) -> index > 0
  _canGoToNextPage = lift _maxPages, _currentPage, (maxPages, index) -> index < maxPages - 1

  _searchCaption = lift _entries, _filteredItems, _selectionCount, _currentPage, _maxPages, (entries, filteredItems, selectionCount, currentPage, maxPages) ->
    caption = if maxPages is 0 then '' else "Showing page #{currentPage + 1} of #{maxPages}."
    if filteredItems.length isnt entries.length
      caption += " Filtered #{filteredItems.length} of #{entries.length}."
    if selectionCount isnt 0
      caption += " #{selectionCount} ignored."
    caption

  _lastUsedSearchTerm = null
  _lastUsedIgnoreNaTerm = null

  filterItems = (force=no) ->
    searchTerm = _searchTerm().trim()
    ignoreNATerm = _ignoreNATerm().trim()

    if force or searchTerm isnt _lastUsedSearchTerm or ignoreNATerm isnt _lastUsedIgnoreNaTerm
      filteredItems = []
      for entry, i in _entries()
        missingPercent = parseFloat ignoreNATerm
        hide = no
        if (searchTerm isnt '') and -1 is entry.value.toLowerCase().indexOf searchTerm.toLowerCase()
          hide = yes
        else if (not isNaN missingPercent) and (missingPercent isnt 0) and entry.missingPercent <= missingPercent
          hide = yes

        unless hide
          filteredItems.push entry

      _lastUsedSearchTerm = searchTerm
      _lastUsedIgnoreNaTerm = ignoreNATerm
      _currentPage 0
      _filteredItems filteredItems

    start = _currentPage() * MaxItemsPerPage
    _visibleItems _filteredItems().slice start, start + MaxItemsPerPage
    return

  changeSelection = (source, value) ->
    for entry in source
      entry.isSelected value
    return

  selectFiltered = ->
    entries = _filteredItems()
    blockSelectionUpdates -> changeSelection entries, yes
    _selectionCount entries.length

  deselectFiltered = ->
    blockSelectionUpdates -> changeSelection _filteredItems(), no
    _selectionCount 0

  goToPreviousPage = ->
    if _canGoToPreviousPage()
      _currentPage _currentPage() - 1
      filterItems()
    return

  goToNextPage = ->
    if _canGoToNextPage()
      _currentPage _currentPage() + 1
      filterItems()
    return

  control = createControl 'list', parameter
  act _entries, ->
      filterItems yes
      control.isVisible _entries().length > 0
  react _searchTerm, throttle filterItems, 500
  react _ignoreNATerm, throttle filterItems, 500

  control.values = _values
  control.entries = _visibleItems
  control.hasFilteredItems = _hasFilteredItems
  control.searchCaption = _searchCaption
  control.searchTerm = _searchTerm
  control.ignoreNATerm = _ignoreNATerm
  control.value = _entries
  control.selectFiltered = selectFiltered
  control.deselectFiltered = deselectFiltered
  control.navEnabled = _navEnabled
  control.previousLabel = 'Previous #{MaxItemsPerPage}'
  control.nextLabel = 'Next #{MaxItemsPerPage}'
  control.goToPreviousPage = goToPreviousPage
  control.goToNextPage = goToNextPage
  control.canGoToPreviousPage = _canGoToPreviousPage
  control.canGoToNextPage = _canGoToNextPage
  control.columnsFilterEnabled = _columnsFilterEnabled
  control


createModelsControl = (_, parameter) ->
  _models = signal []
  _frames = signal []
  _selectedFrame = signal null
  _checkAllModels = signal false

  _.requestFrames (error, frames) ->
    unless error
      _frames (frame.frame_id.name for frame in frames)

  createModelItem = (modelKey) ->
    _isSelected = signal no

    value: modelKey
    isSelected: _isSelected

  createModelItems = (error, frame) ->
    _models map frame.compatible_models, createModelItem

  _isCheckingAll = no
  lift _checkAllModels, (checkAll) ->
    _isCheckingAll = yes
    for view in _models()
      view.isSelected checkAll
    _isCheckingAll = no
    return

  selectFiltered = ->
    entries = _models()
    blockSelectionUpdates -> changeSelection entries, yes

  deselectFiltered = ->
    entries = _models()
    blockSelectionUpdates -> changeSelection entries, no

  lift _selectedFrame, (frameKey) ->
    if frameKey
      _.requestFrame frameKey, createModelItems, find_compatible_models: yes

  control = createControl 'models', parameter
  control.clientId = do uniqueId
  control.frames = _frames
  control.selectedFrame = _selectedFrame
  control.checkAllModels = _checkAllModels
  control.value = _models
  control.defaultValue = []
  control


createStringPairsControl = (parameter) ->
  _pairs = signal []
  _columns = signal []

  react _columns, () ->
    _pairs []

  pairEquals = (pair, leftValue, rightValue) ->
      return (pair.leftColumn() == leftValue and pair.rightColumn() == rightValue) or (pair.rightColumn() == leftValue and pair.leftColumn() == rightValue)

  pairExists = (leftValue, rightValue) ->
    samePairs = (pair for pair in _pairs() when pairEquals(pair, leftValue, rightValue))
    return samePairs.length != 0

  _stringPair = (leftValue, rightValue) ->
    _leftColumn = signal leftValue
    _rightColumn = signal rightValue
    _id = signal uniqueId()

    leftColumn: _leftColumn
    rightColumn: _rightColumn
    id: _id
    remove: ->
      _pairs (entry for entry in _pairs() when entry.id() != _id())

  _pairConstructor = ->
    _leftColumn = signal ''
    _leftColumns = signal _columns()
    _leftSelected = signal no

    _rightColumn = signal ''
    _rightColumns = signal []

    _calculateRightColumns = ->
      _rightColumns (entry for entry in _leftColumns() when entry != _leftColumn() and not pairExists(_leftColumn(), entry))

    react _leftColumn, (leftColumn) ->
      if leftColumn
        _calculateRightColumns()
        _leftSelected yes
      else
        _rightColumns []
        _leftSelected no

    react _pairs, () ->
      _calculateRightColumns()

    leftColumn: _leftColumn
    leftColumns: _leftColumns
    leftSelected: _leftSelected
    rightColumn: _rightColumn
    rightColumns: _rightColumns
    create: ->
      if not _rightColumn() or not _leftColumn() or pairExists(_leftColumn(), _rightColumn())
        return
      new_entries = _pairs()
      new_entries.push _stringPair(_leftColumn(), _rightColumn())
      _pairs new_entries

  _pairToValue = (pairs) ->
    result = []
    for pair in pairs
      result.push {a: pair.leftColumn(), b: pair.rightColumn()}
    return result

  _value = lift _pairs, _pairToValue

  control = createControl 'stringpairs', parameter
  control.value = _value
  control.newPair = _pairConstructor
  control.pairs = _pairs
  control.columns = _columns
  control


createMonotoneContraintsControl = (opts, valueEncoder, parameter) ->
  _keyValues = signal []
  _columns = signal []

  react _columns, () ->
    _keyValues []

  _keyValueObject = (key, value) ->
    _key = signal key
    _value = signal value
    _id = signal uniqueId()

    key: _key
    value: _value
    id: _id
    encodedValue: ->
      valueEncoder _value()
    remove: ->
      _keyValues (entry for entry in _keyValues() when entry.id() != _id())

  _keyValueConstructor = ->
    _key = signal ''
    _keyOpts = signal _columns()
    _keySelected = signal no

    _value = signal ''
    _valueOpts = signal []

    react _key, (value) ->
      if value
        _keySelected yes
        _valueOpts opts
      else
        _keySelected no
        _valueOpts []

    _keyValueExists = (checkedKey) ->
      sameKeys = (keyValue for keyValue in _keyValues() when keyValue.key() == checkedKey)
      return sameKeys.length != 0

    react _keyValues, (_) ->
      _keyOpts (key for key in _keyOpts() when not _keyValueExists(key))
      _key null

    key: _key
    keyOpts: _keyOpts
    keySelected: _keySelected
    value: _value
    valueOpts: _valueOpts
    create: ->
      if not _key() or not _value() or _keyValueExists(_key())
        return
      new_entries = _keyValues()
      new_entries.push _keyValueObject(_key(), _value())
      _keyValues new_entries

  _keyValuesToValue = (keyValues) ->
    result = []
    keyValues.forEach (keyValue) ->
      result.push {key: keyValue.key(), value: keyValue.encodedValue()}
    result

  control = createControl 'keyvalues', parameter
  control.value = lift _keyValues, _keyValuesToValue
  control.columns = _columns
  control.keyValues = _keyValues
  control.newKeyValue = _keyValueConstructor
  control


createControlFromParameter = (_, parameter) ->
  switch parameter.type
    when 'enum', 'Key<Frame>', 'VecSpecifier'
      createDropdownControl parameter
    when 'string[]', 'enum[]'
      createListControl parameter
    when 'boolean'
      createCheckboxControl parameter
    when 'Key<Model>', 'string', 'byte', 'short', 'int', 'long', 'float', 'double', 'byte[]', 'short[]', 'int[]', 'long[]', 'float[]', 'double[]'
      createTextboxControl parameter, parameter.type
    when 'Key<Model>[]'
      createModelsControl _, parameter
    when 'StringPair[]'
      createStringPairsControl parameter
    when 'KeyValue[]'
      if parameter.name is 'monotone_constraints'
        increasing = 'Increasing'
        decreasing = 'Decreasing'
        valueEncoder = (value) ->
          switch value
            when increasing
              return 1
            when decreasing
              return -1
            else
              console.error "Unknown value #{_value()} to encode."
              return 0
        createMonotoneContraintsControl [increasing, decreasing], valueEncoder, parameter
    else
      console.error 'Invalid field', JSON.stringify parameter, null, 2
      null


ControlGroups = (_, _parameters) ->
  _parametersByLevel = groupBy _parameters, (parameter) -> parameter.level
  _controlGroups = map [ 'critical', 'secondary', 'expert' ], (type) ->
    controls = map _parametersByLevel[type], (p) -> createControlFromParameter _, p
    controls = filter controls, (a) -> if a then yes else no
    controls

  _findControl = (name) ->
    find (flatten _controlGroups), (c) -> c.name is name

  _createForm =  ->
    form = []
    [critical, secondary, expert] = _controlGroups
    labels =  ['Parameters', 'Advanced', 'Expert']
    for controls, i in _controlGroups
      if controls.length
        gridEnabled = controls.some (c) -> c.isGridable
        form.push kind: 'group', title: labels[i], grided: gridEnabled
        form.push control for control in controls
    form

  _readControlValue = (control) ->
    value = if control.isGrided() and control.valueGrided then control.valueGrided() else control.value()
    switch control.kind
      when 'dropdown'
        selected = if value then value else control.defaultValue
        if Array.isArray selected
          selected = (item.label for item in selected when item.value())
        selected
      when 'list', 'models'
        selected = control.defaultValue
        if value.length
          selected = (entry.value for entry in value when entry.isSelected())
        selected
      else
       value

  _validateControl = (control, validations, checkForErrors=no) ->
    if validations
      for validation in validations
        if validation.message_type is 'TRACE'
          control.isVisible no
        else
          control.isVisible yes
          if checkForErrors
            switch validation.message_type
              when 'INFO'
                control.hasInfo yes
                control.message validation.message
              when 'WARN'
                control.hasWarning yes
                control.message validation.message
              when 'ERRR'
                control.hasError yes
                control.message validation.message
    else
      control.isVisible yes
      control.hasInfo no
      control.hasWarning no
      control.hasError no
      control.message ''

  createForm: _createForm
  findControl: _findControl
  readControlValue: _readControlValue
  validateControl: _validateControl
  list: _controlGroups


module.exports =
  ControlGroups: ControlGroups
  columnLabelsFromFrame: columnLabelsFromFrame
