Flow.ApplicationContext = (_) ->
  _.ready = do slots
  _.initialized = do slots
  _.open = do slot
  _.load = do slot
  _.saved = do slots
  _.loaded = do slots
  _.setDirty = do slots
  _.setPristine = do slots
  _.status = do slot
  _.trackEvent = do slot
  _.trackException = do slot
  _.selectCell = do slot
  _.insertCell = do slot
  _.insertAndExecuteCell = do slot
  _.showHelp = do slot
  _.showOutline = do slot
  _.showBrowser = do slot
  _.showClipboard = do slot
  _.saveClip = do slot
  _.growl = do slot
  _.confirm = do slot
  _.alert = do slot
  _.dialog = do slot

