jobOutputStatusColors = 
  failed: '#d9534f'
  done: '#ccc' #'#5cb85c'
  running: '#f0ad4e'

getJobOutputStatusColor = (status) ->
  # CREATED   Job was created
  # RUNNING   Job is running
  # CANCELLED Job was cancelled by user
  # FAILED    Job crashed, error message/exception is available
  # DONE      Job was successfully finished
  switch status
    when 'DONE'
      jobOutputStatusColors.done
    when 'CREATED', 'RUNNING'
      jobOutputStatusColors.running
    else # 'CANCELLED', 'FAILED'
      jobOutputStatusColors.failed

getJobProgressPercent = (progress) ->
  "#{Math.ceil 100 * progress}%"

H2O.JobOutput = (_, _job) ->
  _isBusy = signal no
  _isLive = signal no

  _key = _job.key.name
  _description = _job.description
  _destinationKey = _job.dest.name
  _runTime = signal null
  _progress = signal null
  _status = signal null
  _statusColor = signal null
  _exception = signal null
  _kind = signal null

  isJobRunning = (job) ->
    job.status is 'CREATED' or job.status is 'RUNNING'

  updateJob = (job) ->
    _runTime job.msec
    _progress getJobProgressPercent job.progress
    _status job.status
    _statusColor getJobOutputStatusColor job.status
    _exception job.exception

  toggleRefresh = ->
    _isLive not _isLive()

  refresh = ->
    _isBusy yes
    _.requestJob _key, (error, job) ->
      _isBusy no
      if error
        _exception Flow.Exception 'Error fetching jobs', error
        _isLive no
      else
        updateJob job
        if isJobRunning job
          delay refresh, 1000 if _isLive()
        else
          toggleRefresh()

  act _isLive, (isLive) ->
    refresh() if isLive

  inspect = ->
    switch _kind()
      when 'frame'
        _.insertAndExecuteCell 'cs', "getFrame #{stringify _destinationKey}" 
      when 'model'
        _.insertAndExecuteCell 'cs', "getModel #{stringify _destinationKey}" 


  initialize = (job) ->
    updateJob job
    toggleRefresh if isJobRunning job

    _.requestInspect _destinationKey, (error, result) ->
      unless error
        _kind result.kind
      return

  initialize _job

  key: _key
  description: _description
  destinationKey: _destinationKey
  kind: _kind
  runTime: _runTime
  progress: _progress
  status: _status
  statusColor: _statusColor
  exception: _exception
  isLive: _isLive
  toggleRefresh: toggleRefresh
  inspect: inspect
  template: 'flow-job-output'

