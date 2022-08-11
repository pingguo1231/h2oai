Steam.FrameListView = (_) ->
  _predicate = node$ type: 'all'
  _items = do nodes$
  _hasItems = lift$ _items, (items) -> items.length > 0

  _canClearPredicate = lift$ _predicate, (predicate) -> predicate.type isnt 'all'
  _predicateCaption = lift$ _predicate, (predicate) ->
    switch predicate.type
      when 'all'
        'Showing\nall datasets'
      when 'compatibleWithModel'
        "Showing datasets compatible with\n#{predicate.modelKey}"
      else
        throw new Error 'Invalid predicate type'

  displayItem = (item) ->
    if item
      _.displayFrame item.data
    else
      _.displayEmpty()

  displayActiveItem = ->
    displayItem find _items(), (item) -> item.isActive()

  activateAndDisplayItem = (item) ->
    for other in _items()
      if other is item
        other.isActive yes
      else
        other.isActive no

    displayItem item

  createItem = (frame) ->
    #TODO replace with type checking
    console.assert isArray frame.column_names
    self =
      data: frame
      title: frame.key
      caption: (describeCount frame.column_names.length, 'column') + ': ' + join (head frame.column_names, 5), ', '
      timestamp: frame.creation_epoch_time_millis
      display: -> activateAndDisplayItem self
      isActive: node$ no
  
  displayFrames = (frames) ->
    _items items = map frames, createItem
    activateAndDisplayItem head items
    _.framesLoaded()

  loadFrames = (predicate) ->
    console.assert isDefined predicate
    switch predicate.type
      when 'all'
        _.requestFramesAndCompatibleModels (error, data) ->
          if error
            #TODO handle errors
          else
            displayFrames data.frames

      when 'compatibleWithModel'
        #FIXME Need an api call to get "frames and compatible models for all frames compatible with a model"
        _.requestModelAndCompatibleFrames predicate.modelKey, (error, data) ->
          if error
            #TODO handle errors
          else
            compatibleFramesByKey = indexBy (head data.models).compatible_frames, (frame) -> frame.key
            _.requestFramesAndCompatibleModels (error, data) ->
              if error
                #TODO handle errors
              else
                compatibleFrames = filter data.frames, (frame) -> if compatibleFramesByKey[frame.key] then yes else no
                # PP-74 hide raw frames from list
                nonRawFrames = filter compatibleFrames, (frame) -> not frame.is_raw_frame
                displayFrames nonRawFrames

    _predicate predicate
    return

  clearPredicate = -> loadFrames type: 'all'

  link$ _.loadFrames, (predicate) ->
    if predicate
      loadFrames predicate
    else
      displayActiveItem()

  items: _items
  predicateCaption: _predicateCaption
  clearPredicate: clearPredicate
  canClearPredicate: _canClearPredicate
  hasItems: _hasItems
  template: 'frame-list-view'

