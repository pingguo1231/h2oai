Flow = if exports? then exports else @Flow = {}

# 
# TODO
#
# keyboard help dialog
# menu system
# tooltips on celltype flags


# CLI usage:
# help
# list frames
# list models, list models with compatible frames
# list jobs
# list scores
# list nodes
# ? import dataset
# ? browse files
# ? parse
# ? inspect dataset
# ? column summary
# ? histogram / box plot / top n / characteristics plots
# ? create model
# ? score model
# ? compare scorings
#   ? input / output comparison
#   ? parameter / threshold plots
# ? predictions

marked.setOptions
  smartypants: yes
  highlight: (code, lang) ->
    if window.hljs
      (window.hljs.highlightAuto code, [ lang ]).value
    else
      code

ko.bindingHandlers.cursorPosition =
  init: (element, valueAccessor, allBindings, viewModel, bindingContext) ->
    if arg = ko.unwrap valueAccessor()
      # Bit of a hack. Attaches a method to the bound object that returns the cursor position. Uses dwieeb/jquery-textrange.
      arg.read = -> $(element).textrange 'get', 'position'
    return

ko.bindingHandlers.autoResize =
  init: (element, valueAccessor, allBindings, viewModel, bindingContext) ->
    $el = $ element
      .on 'input', ->
        defer ->
          $el
            .css 'height', 'auto'
            .height element.scrollHeight
    return

# Like _.compose, but async. 
# Equivalent to caolan/async.waterfall()
async = (tasks) ->
  _tasks = slice tasks, 0

  next = (args, go) ->
    task = shift _tasks
    if task
      apply task, null, args.concat (error, results...) ->
        if error
          go error
        else
          next results, go
    else
      apply go, null, [ null ].concat args

  (args..., go) ->
    next args, go

deepClone = (obj) ->
  JSON.parse JSON.stringify obj

Flow.Application = (_) ->
  _view = Flow.Repl _
  Flow.DialogManager _
  
  context: _
  view: _view

Flow.ApplicationContext = (_) ->
  context$
    ready: do edges$

Flow.DialogManager = (_) ->

Flow.HtmlTag = (_, level) ->
  isCode: no
  render: (guid, input, go) ->
    go null,
      text: input.trim() or '(Untitled)'
      template: "flow-#{level}"

Flow.Raw = (_) ->
  isCode: no
  render: (guid, input, go) ->
    go null,
      text: input
      template: 'flow-raw'

Flow.Markdown = (_) ->
  isCode: no
  render: (guid, input, go) ->
    try
      html = marked input.trim() or '(No content)'
      go null,
        html: html
        template: 'flow-html'
    catch error
      go error

objectToHtmlTable = (obj) ->
  if obj is undefined
    '(undefined)'
  else if obj is null
    '(null)'
  else if isString obj
    if obj
      obj
    else
      '(Empty string)'
  else if isArray obj
    html = ''
    for value, i in obj
      html += "<tr><td>#{i + 1}</td><td>#{objectToHtmlTable value}</td></tr>"
    if html
      "<table class='table table-striped table-condensed'>#{html}</table>"
    else
      '(Empty array)'
  else if isObject obj
    html = ''
    for key, value of obj
      html += "<tr><td>#{key}</td><td>#{objectToHtmlTable value}</td></tr>"
    if html
      "<table class='table table-striped table-condensed'>#{html}</table>"
    else
      '(Empty object)'
  else
    obj

Flow.H2O = (_) ->
  createResponse = (status, data, xhr) ->
    status: status, data: data, xhr: xhr

  handleResponse = (go, jqxhr) ->
    jqxhr
      .done (data, status, xhr) ->
        go null, createResponse status, data, xhr
      .fail (xhr, status, error) ->
        go createResponse status, xhr.responseJSON, xhr

  h2oGet = (path, go) ->
    handleResponse go, $.getJSON path

  h2oPost = (path, opts, go) ->
    handleResponse go, $.post path, opts

  processResponse = (go) ->
    (error, result) ->
      if error
        #TODO error logging / retries, etc.
        go error, result
      else
        if result.data.response?.status is 'error'
          go result.data.error, result.data
        else
          go error, result.data

  request = (path, go) ->
    h2oGet path, processResponse go

  post = (path, opts, go) ->
    h2oPost path, opts, processResponse go

  composePath = (path, opts) ->
    if opts
      params = mapWithKey opts, (v, k) -> "#{k}=#{v}"
      path + '?' + join params, '&'
    else
      path

  requestWithOpts = (path, opts, go) ->
    request (composePath path, opts), go

  requestJobs = (go) ->
    request '/Jobs.json', go

  requestJob = (key, go) ->
    #opts = key: encodeURIComponent key
    #requestWithOpts '/Job.json', opts, go
    request "/Jobs.json/#{encodeURIComponent key}", (error, result) ->
      if error
        go error, result
      else
        go error, result.jobs[0]

  link$ _.requestJobs, requestJobs
  link$ _.requestJob, requestJob

Flow.Routines = (_) ->
  _future = (f, args) ->
    self = (go) ->
      apply f, [null]
        .concat args
        .concat (error, result) ->
          if error
            self.error = error
            self.fulfilled = no
            self.rejected = yes
            go error if isFunction go
          else
            self.result = result
            self.fulfilled = yes
            self.rejected = no
            go null, result if isFunction go
          self.settled = yes
          self.pending = no

    self.method = f
    self.args = args
    self.fulfilled = no
    self.rejected = no
    self.settled = no
    self.pending = yes

    self.isFuture = yes

    self

  future = (f, args...) -> _future f, args

  renderJobs = (ft, results) ->

  renderJob = (ft, result) ->

  lookupRenderer = (method) ->
    switch method
      when jobs
        renderJobs
      when job
        renderJob
    
#  show = (arg) ->
#    if arg
#      if arg.isFuture
#        arg (error, result) ->
#          if error
#            error:
#              message: "Error evaluating future"
#              cause: error
#          else
#            renderer = lookupRenderer arg.method
#            if renderer
#              renderer arg, result
#            else
#    else
#      #XXX print usage
#      throw new Error "Illegal Argument: '#{arg}'"

  frames = (arg) ->

  frame = (arg) ->

  models = (arg) ->

  model = (arg) ->

  jobs = (arg) ->
    future _.requestJobs

  job = (arg) ->
    if isString arg
      future _.requestJob, arg
    else if isObject arg
      if arg.key?
        job arg.key
      else
        #XXX print usage
        throw new Error "Illegal Argument: '#{arg}'"
    else
      #XXX print usage
      throw new Error "Illegal Argument: '#{arg}'"

  jobs: jobs
  job: job

javascriptProgramTemplate = esprima.parse 'function foo(){ return a + b; }'
safetyWrapCoffeescript = (guid) ->
  (cs, go) ->
    lines = cs
      # normalize CR/LF
      .replace /[\n\r]/g, '\n'
      # split into lines
      .split '\n'

    # indent once
    block = map lines, (line) -> '  ' + line

    # enclose in execute-immediate closure
    block.unshift "context._results_['#{guid}'] = do ->"

    # join and proceed
    go null, join block, '\n'

compileCoffeescript = (cs, go) ->
  try
    go null, CoffeeScript.compile cs, bare: yes
  catch error
    go
      message: 'Error compiling coffee-script'
      cause: error

parseJavascript = (js, go) ->
  #XXX Test this - worst case scenario
  x = '''
    var foo = 6 * 7;
    var bar = 10;
    function foo() {
      foo = 20;
      bar = function(){};
      var foo = 10;
      for (var foo = 0; foo < 10; foo++) {
        var foo = foo++;
        foo = foo++;
      }
    }
  '''

  try
    go null, esprima.parse js
  catch error
    go
      message: 'Error parsing javascript expression'
      cause: error


identifyDeclarations = (node) ->
  return null unless node

  switch node.type
    when 'VariableDeclaration'
      return (name: declaration.id.name, type: 'var', object:'context' for declaration in node.declarations when declaration.type is 'VariableDeclarator' and declaration.id.type is 'Identifier')
        
    when 'FunctionDeclaration'
      #
      # XXX Not sure about the semantics here.
      #
      if node.id.type is 'Identifier'
        return [ name: node.id.name, type: 'function', object: 'context' ]
    when 'ForStatement'
      return identifyDeclarations node.init
    when 'ForInStatement', 'ForOfStatement'
      return identifyDeclarations node.left
  return null

parseDeclarations = (block) ->
  identifiers = []
  for node in block.body
    if declarations = identifyDeclarations node
      for declaration in declarations
        identifiers.push declaration
  indexBy identifiers, (identifier) -> identifier.name

isRedeclared = (stack, identifier) ->
  i = stack.length
  while i--
    return true if stack[i][identifier] 
  false

traverseJavascript = (parent, key, node, f) ->
  if isArray node
    i = node.length
    while i--
      child = node[i]
      if child isnt null and isObject child
        traverseJavascript node, i, child, f
        f node, i, child
  else 
    for own i, child of node
      if child isnt null and isObject child
        traverseJavascript node, i, child, f
        f node, i, child
  return

deleteAstNode = (parent, i) ->
  if _.isArray parent
    parent.splice i, 1
  else if isObject parent
    delete parent[i]

rewriteJavascript = (_context) ->
  (program, go) ->
    topLevelDeclarations = parseDeclarations program.body[0].expression.right.callee.body
    for name of _context when name isnt '_routines_' and name isnt '_results_'
      topLevelDeclarations[name] =
        type: 'var'
        name: name
        object: 'context'
    
    try
      traverseJavascript null, null, program, (parent, i, node) ->
        # remove hoisted vars		
        if node.type is 'VariableDeclaration'		
          declarations = node.declarations.filter (declaration) ->		
            declaration.type is 'VariableDeclarator' and declaration.id.type is 'Identifier' and topLevelDeclarations[declaration.id.name]		
          if declarations.length is 0
            # purge this node so that escodegen doesn't fail		
            deleteAstNode parent, i		
          else		
            # replace with cleaned-up declarations
            node.declarations = declarations

        #
        # XXX Nasty hack - re-implement
        #
        # bail out if imported identifiers are used as function params
        else if node.type is 'FunctionExpression' or node.type is 'FunctionDeclaration'
          for param in node.params when param.type is 'MemberExpression'
            throw new Error "Function has a formal parameter name-clash with identifier '#{param.object.name}'. Correct this and try again." 

        # replace identifier with qualified name
        else if node.type is 'Identifier'
          return if parent.type is 'VariableDeclarator' and i is 'id' # ignore var declarations
          return if i is 'property' # ignore members
          return unless identifier = topLevelDeclarations[node.name]

          parent[i] =
            type: 'MemberExpression'
            computed: no
            object:
              type: 'Identifier'
              name: identifier.object
            property:
              type: 'Identifier'
              name: identifier.name
    catch error
      return go
        message: 'Error rewriting javascript'
        cause: error

    go null, program

generateJavascript = (program, go) ->
  try
    go null, escodegen.generate program
  catch error
    return go
      message: 'Error generating javascript'
      cause: error

compileJavascript = (js, go) ->
  debug js
  try
    closure = new Function 'context', js
    go null, closure
  catch error
    go
      message: 'Error compiling javascript'
      cause: error

executeJavascript = (context) ->
  (closure, go) ->
    try
      go null, closure context
    catch error
      go
        message: 'Error executing javascript'
        cause: error

Flow.Coffeescript = (_, _context) ->
  _routineNames = keys _context
  render: (guid, input, go) ->
    tasks = [
      safetyWrapCoffeescript guid
      compileCoffeescript
      parseJavascript
      rewriteJavascript _context
      generateJavascript
      compileJavascript
      executeJavascript _context
    ]
    (async tasks) input, (error, result) ->
      if error
        go error
      else
        debug _context
        go null,
          text: _context._results_[guid]
          template: 'flow-raw'

Flow.Repl = (_) ->
  _cells = nodes$ []
  _selectedCell = null
  _selectedCellIndex = -1
  _clipboardCell = null
  _lastDeletedCell = null
  _context = 
    _results_: {}
    _routines_: Flow.Routines _

  _renderers =
    h1: -> Flow.HtmlTag _, 'h1'
    h2: -> Flow.HtmlTag _, 'h2'
    h3: -> Flow.HtmlTag _, 'h3'
    h4: -> Flow.HtmlTag _, 'h4'
    h5: -> Flow.HtmlTag _, 'h5'
    h6: -> Flow.HtmlTag _, 'h6'
    md: -> Flow.Markdown _
    cs: -> Flow.Coffeescript _, _context
    raw: -> Flow.Raw _

  countLines = (text) ->
    newlineCount = 1
    for character in text when character is '\n'
      newlineCount++
    newlineCount

  checkConsistency = ->
    for cell, i in _cells()
      unless cell
        error "index #{i} is empty"
    return

  selectCell = (target) ->
    return if _selectedCell is target
    _selectedCell.isSelected no if _selectedCell
    _selectedCell = target
    _selectedCell.isSelected yes
    _selectedCellIndex = _cells.indexOf _selectedCell
    checkConsistency()
    return

  createCell = (type='cs', input='') ->
    _guid = uniqueId()
    _type = node$ type
    _renderer = lift$ _type, (type) -> _renderers[type]()
    _isSelected = node$ no
    _isActive = node$ no
    _hasError = node$ no
    _isBusy = node$ no
    _isReady = lift$ _isBusy, (isBusy) -> not isBusy
    _hasInput = node$ yes
    _input = node$ input
    _output = node$ null
    _hasOutput = lift$ _output, (output) -> if output? then yes else no
    _lineCount = lift$ _input, countLines

    # This is a shim.
    # The ko 'cursorPosition' custom binding attaches a read() method to this.
    _cursorPosition = {}

    # select and display input when activated
    apply$ _isActive, (isActive) ->
      if isActive
        selectCell self
        _hasInput yes
        _output null if _renderer().isCode is no
      return

    # deactivate when deselected
    apply$ _isSelected, (isSelected) ->
      _isActive no unless isSelected

    # tied to mouse-clicks on the cell
    select = -> selectCell self

    # tied to mouse-double-clicks on html content
    activate = -> _isActive yes

    execute = (go) ->
      input = _input().trim()
      return unless input
      renderer = _renderer()
      _isBusy yes
      renderer.render _guid, input, (error, result) ->
        if error
          _hasError yes
          if error.cause?
            _output
              error: error
              template: 'flow-error'
          else
            _output
              text: JSON.stringify error, null, 2
              template: 'flow-raw'
        else
          _hasError no
          _output result
          _hasInput renderer.isCode isnt no

        _isBusy no

      _isActive no
      go() if go

    self =
      guid: _guid
      type: _type
      isSelected: _isSelected
      isActive: _isActive
      hasError: _hasError
      isBusy: _isBusy
      isReady: _isReady
      input: _input
      hasInput: _hasInput
      output: _output
      hasOutput: _hasOutput
      lineCount: _lineCount
      select: select
      activate: activate
      execute: execute
      _cursorPosition: _cursorPosition
      cursorPosition: -> _cursorPosition.read()
      template: 'flow-cell'

  cloneCell = (cell) ->
    createCell cell.type(), cell.input()

  switchToCommandMode = ->
    _selectedCell.isActive no

  switchToEditMode = ->
    _selectedCell.isActive yes
    no

  convertCellToCode = -> _selectedCell.type 'cs'

  convertCellToHeading = (level) -> -> _selectedCell.type "h#{level}"

  convertCellToMarkdown = -> _selectedCell.type 'md'

  convertCellToRaw = -> _selectedCell.type 'raw'

  copyCell = ->
    _clipboardCell = cloneCell _selectedCell

  cutCell = ->
    _clipboardCell = _selectedCell
    removeCell()

  deleteCell = ->
    _lastDeletedCell = _selectedCell
    removeCell()

  removeCell = ->
    cells = _cells()
    if cells.length > 1
      if _selectedCellIndex is cells.length - 1
        #TODO call dispose() on this cell
        splice _cells, _selectedCellIndex, 1
        selectCell cells[_selectedCellIndex - 1]
      else
        #TODO call dispose() on this cell
        splice _cells, _selectedCellIndex, 1
        selectCell cells[_selectedCellIndex]
    return
    
  insertCell = (index, cell) ->
    splice _cells, index, 0, cell
    selectCell cell
    cell

  insertCellAbove = ->
    insertCell _selectedCellIndex, createCell 'cs'

  insertCellBelow = ->
    insertCell _selectedCellIndex + 1, createCell 'cs'

  moveCellDown = ->
    cells = _cells()
    unless _selectedCellIndex is cells.length - 1
      splice _cells, _selectedCellIndex, 1
      _selectedCellIndex++
      splice _cells, _selectedCellIndex, 0, _selectedCell
    return

  moveCellUp = ->
    unless _selectedCellIndex is 0
      cells = _cells()
      splice _cells, _selectedCellIndex, 1
      _selectedCellIndex--
      splice _cells, _selectedCellIndex, 0, _selectedCell
    return

  mergeCellBelow = ->
    cells = _cells()
    unless _selectedCellIndex is cells.length - 1
      nextCell = cells[_selectedCellIndex + 1]
      if _selectedCell.type() is nextCell.type()
        nextCell.input _selectedCell.input() + '\n' + nextCell.input()
        removeCell()
    return

  splitCell = ->
    if _selectedCell.isActive()
      input = _selectedCell.input()
      if input.length > 1
        cursorPosition = _selectedCell.cursorPosition()
        if 0 < cursorPosition < input.length - 1
          left = substr input, 0, cursorPosition
          right = substr input, cursorPosition
          _selectedCell.input left
          insertCell _selectedCellIndex + 1, createCell 'cs', right
          _selectedCell.isActive yes
    return

  pasteCellAbove = ->
    insertCell _selectedCellIndex, _clipboardCell if _clipboardCell

  pasteCellBelow = ->
    insertCell _selectedCellIndex + 1, _clipboardCell if _clipboardCell

  undoLastDelete = ->
    insertCell _selectedCellIndex + 1, _lastDeletedCell if _lastDeletedCell
    _lastDeletedCell = null

  runCell = ->
    _selectedCell.execute()
    no

  runCellAndInsertBelow = ->
    _selectedCell.execute -> insertCellBelow()
    no

  #TODO ipython has inconsistent behavior here. seems to be doing runCellAndInsertBelow if executed on the lowermost cell.
  runCellAndSelectBelow = ->
    _selectedCell.execute -> selectNextCell()
    no

  saveFlow = ->
    debug 'saveFlow'
    no

  selectNextCell = ->
    cells = _cells()
    unless _selectedCellIndex is cells.length - 1
      selectCell cells[_selectedCellIndex + 1]
    return

  selectPreviousCell = ->
    unless _selectedCellIndex is 0
      cells = _cells()
      selectCell cells[_selectedCellIndex - 1]
    return

  displayHelp = -> debug 'displayHelp'

  # (From IPython Notebook keyboard shortcuts dialog)
  # The IPython Notebook has two different keyboard input modes. Edit mode allows you to type code/text into a cell and is indicated by a green cell border. Command mode binds the keyboard to notebook level actions and is indicated by a grey cell border.
  # 
  # Command Mode (press Esc to enable)
  # 
  normalModeKeyboardShortcuts = [
    [ 'enter', 'edit mode', switchToEditMode ]
    #[ 'shift+enter', 'run cell, select below', runCellAndSelectBelow ]
    #[ 'ctrl+enter', 'run cell', runCell ]
    #[ 'alt+enter', 'run cell, insert below', runCellAndInsertBelow ]
    [ 'y', 'to code', convertCellToCode ]
    [ 'm', 'to markdown', convertCellToMarkdown ]
    [ 'r', 'to raw', convertCellToRaw ]
    [ '1', 'to heading 1', convertCellToHeading 1 ]
    [ '2', 'to heading 2', convertCellToHeading 2 ]
    [ '3', 'to heading 3', convertCellToHeading 3 ]
    [ '4', 'to heading 4', convertCellToHeading 4 ]
    [ '5', 'to heading 5', convertCellToHeading 5 ]
    [ '6', 'to heading 6', convertCellToHeading 6 ]
    [ 'up', 'select previous cell', selectPreviousCell ]
    [ 'down', 'select next cell', selectNextCell ]
    [ 'k', 'select previous cell', selectPreviousCell ]
    [ 'j', 'select next cell', selectNextCell ]
    [ 'ctrl+k', 'move cell up', moveCellUp ]
    [ 'ctrl+j', 'move cell down', moveCellDown ]
    [ 'a', 'insert cell above', insertCellAbove ]
    [ 'b', 'insert cell below', insertCellBelow ]
    [ 'x', 'cut cell', cutCell ]
    [ 'c', 'copy cell', copyCell ]
    [ 'shift+v', 'paste cell above', pasteCellAbove ]
    [ 'v', 'paste cell below', pasteCellBelow ]
    [ 'z', 'undo last delete', undoLastDelete ]
    [ 'd d', 'delete cell (press twice)', deleteCell ]
    [ 'shift+m', 'merge cell below', mergeCellBelow ]
    [ 's', 'save notebook', saveFlow ]
    #[ 'mod+s', 'save notebook', saveFlow ]
    # [ 'l', 'toggle line numbers' ]
    # [ 'o', 'toggle output' ]
    # [ 'shift+o', 'toggle output scrolling' ]
    # [ 'q', 'close pager' ]
    [ 'h', 'keyboard shortcuts', displayHelp ]
    # [ 'i', 'interrupt kernel (press twice)' ]
    # [ '0', 'restart kernel (press twice)' ]
  ] 

  # 
  # Edit Mode (press Enter to enable) 
  # 
  editModeKeyboardShortcuts = [
    # Tab : code completion or indent
    # Shift-Tab : tooltip
    # Cmd-] : indent
    # Cmd-[ : dedent
    # Cmd-a : select all
    # Cmd-z : undo
    # Cmd-Shift-z : redo
    # Cmd-y : redo
    # Cmd-Up : go to cell start
    # Cmd-Down : go to cell end
    # Opt-Left : go one word left
    # Opt-Right : go one word right
    # Opt-Backspace : del word before
    # Opt-Delete : del word after
    [ 'esc', 'command mode', switchToCommandMode ]
    [ 'ctrl+m', 'command mode', switchToCommandMode ]
    [ 'shift+enter', 'run cell, select below', runCellAndSelectBelow ]
    [ 'ctrl+enter', 'run cell', runCell ]
    [ 'alt+enter', 'run cell, insert below', runCellAndInsertBelow ]
    [ 'ctrl+shift+-', 'split cell', splitCell ]
    [ 'mod+s', 'save notebook', saveFlow ]
  ]

  setupKeyboardHandling = (mode) ->
    for [ shortcut, caption, f ] in normalModeKeyboardShortcuts
      Mousetrap.bind shortcut, f

    for [ shortcut, caption, f ] in editModeKeyboardShortcuts
      Mousetrap.bindGlobal shortcut, f
    return

  initialize = ->
    setupKeyboardHandling 'normal'
    cell = createCell 'cs'
    push _cells, cell
    selectCell cell

  initialize()

  cells: _cells
  template: (view) -> view.template

$ ->
  window.flow = flow = Flow.Application do Flow.ApplicationContext
  ko.applyBindings flow
  flow.context.ready()
  
