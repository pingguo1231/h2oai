Flow.Browser = (_) ->
  _docs = signals []
  _hasDocs = lift _docs, (docs) -> docs.length > 0

  createDocView = ([ type, id, doc ]) ->
    _title = signal doc.title
    _date = signal new Date doc.modifiedDate
    _fromNow = lift _date, Flow.Util.fromNow

    load = ->
      _.loadNotebook id, doc

    purge = ->
      _.requestDeleteObject type, id, (error) ->
        if error
        else
          _docs.remove self

    self =
      title: _title
      doc: doc
      date: _date
      fromNow: _fromNow
      load: load
      purge: purge

  storeNotebook = (id, doc, go) ->
    if id
      _.requestPutObject 'notebook', id, doc, (error) ->
        if error
          go error
        else
          go null, id
    else
      id = uuid()
      _.requestPutObject 'notebook', id, doc, (error) ->
        if error
          go error
        else
          _docs.push createDocView [ 'notebook', id, doc ]
          go null, id

  loadNotebooks = ->
    _.requestObjects 'notebook', (error, objs) ->
      if error
        debug error
      else
        #XXX sort
        _docs map objs, createDocView

  link _.ready, ->
    loadNotebooks()

  link _.storeNotebook, storeNotebook

  docs: _docs
  hasDocs: _hasDocs
  loadNotebooks: loadNotebooks
