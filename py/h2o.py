import sys, os, getpass, logging, time, inspect, requests, json
import h2o_util
from h2o_util import log, log_rest
import h2o_print as h2p

class H2O(object):
    # static (class) variables
    ipaddr_from_cmd_line = None
    debugger = False
    json_url_history = []
    python_test_name = inspect.stack()[1][1]
    verbose = False


    # TODO: ensure that all of this is really necessary:
    def __init__(self,
                 use_this_ip_addr=None, port=54321, capture_output=True,
                 use_debugger=None, classpath=None,
                 use_hdfs=False, use_maprfs=False,
                 # hdfs_version="cdh4", hdfs_name_node="192.168.1.151",
                 # hdfs_version="cdh3", hdfs_name_node="192.168.1.176",
                 hdfs_version=None, hdfs_name_node=None, hdfs_config=None,
                 aws_credentials=None,
                 use_flatfile=False, java_heap_GB=None, java_heap_MB=None, java_extra_args=None,
                 use_home_for_ice=False, node_id=None, username=None,
                 random_udp_drop=False,
                 redirect_import_folder_to_s3_path=None,
                 redirect_import_folder_to_s3n_path=None,
                 disable_h2o_log=False,
                 enable_benchmark_log=False,
                 h2o_remote_buckets_root=None,
                 delete_keys_at_teardown=False,
                 cloud_name=None,
    ):

        if use_hdfs:
            # see if we can touch a 0xdata machine
            try:
                # long timeout in ec2...bad
                a = requests.get('http://192.168.1.176:80', timeout=1)
                hdfs_0xdata_visible = True
            except:
                hdfs_0xdata_visible = False

            # different defaults, depending on where we're running
            if hdfs_name_node is None:
                if hdfs_0xdata_visible:
                    hdfs_name_node = "192.168.1.176"
                else: # ec2
                    hdfs_name_node = "10.78.14.235:9000"

            if hdfs_version is None:
                if hdfs_0xdata_visible:
                    hdfs_version = "cdh3"
                else: # ec2
                    hdfs_version = "0.20.2"

        self.redirect_import_folder_to_s3_path = redirect_import_folder_to_s3_path
        self.redirect_import_folder_to_s3n_path = redirect_import_folder_to_s3n_path

        self.aws_credentials = aws_credentials
        self.port = port
        # None is legal for self.addr.
        # means we won't give an ip to the jar when we start.
        # Or we can say use use_this_ip_addr=127.0.0.1, or the known address
        # if use_this_addr is None, use 127.0.0.1 for urls and json
        # Command line arg 'ipaddr_from_cmd_line' dominates:
        if H2O.ipaddr_from_cmd_line:
            self.addr = H2O.ipaddr_from_cmd_line
        else:
            self.addr = use_this_ip_addr

        if self.addr is not None:
            self.http_addr = self.addr
        else:
            self.http_addr = get_ip_address()

        # command line should always dominate for enabling
        if H2O.debugger: use_debugger = True
        self.use_debugger = use_debugger

        self.classpath = classpath
        self.capture_output = capture_output

        self.use_hdfs = use_hdfs
        self.use_maprfs = use_maprfs
        self.hdfs_name_node = hdfs_name_node
        self.hdfs_version = hdfs_version
        self.hdfs_config = hdfs_config

        self.use_flatfile = use_flatfile
        self.java_heap_GB = java_heap_GB
        self.java_heap_MB = java_heap_MB
        self.java_extra_args = java_extra_args

        self.use_home_for_ice = use_home_for_ice
        self.node_id = node_id

        if username:
            self.username = username
        else:
            self.username = getpass.getuser()

        # don't want multiple reports from tearDown and tearDownClass
        # have nodes[0] remember (0 always exists)
        self.sandbox_error_was_reported = False
        self.sandbox_ignore_errors = False

        self.random_udp_drop = random_udp_drop
        self.disable_h2o_log = disable_h2o_log

        # this dumps stats from tests, and perf stats while polling to benchmark.log
        self.enable_benchmark_log = enable_benchmark_log
        self.h2o_remote_buckets_root = h2o_remote_buckets_root
        self.delete_keys_at_teardown = delete_keys_at_teardown

        if cloud_name:
            self.cloud_name = cloud_name
        else:
            self.cloud_name = 'pytest-%s-%s' % (getpass.getuser(), os.getpid())


    ''' 
    Printable string representation of an H2O node object. 
    '''
    def __str__(self):
        return '%s - http://%s:%d/' % (type(self), self.http_addr, self.port)


    # TODO: UGH, move this.
    @staticmethod
    def verboseprint(*args, **kwargs):
        if H2O.verbose:
            for x in args: # so you don't have to create a single string
                print x,
            for x in kwargs: # so you don't have to create a single string
                print x,
            print
            sys.stdout.flush()


    def __url(self, loc, port=None):
        # always use the new api port
        if port is None: port = self.port
        if loc.startswith('/'):
            delim = ''
        else:
            delim = '/'
        u = 'http://%s:%d%s%s' % (self.http_addr, port, delim, loc)
        return u


    '''
    Make a REST request to the h2o server and if succesful return a dict containing the JSON result.
    '''
    def __do_json_request(self, jsonRequest=None, fullUrl=None, timeout=10, params=None, postData=None, returnFast=False,
                          cmd='get', extraComment=None, ignoreH2oError=False, noExtraErrorCheck=False, **kwargs):
        H2O.verboseprint("__do_json_request, timeout: " + str(timeout))
        # if url param is used, use it as full url. otherwise crate from the jsonRequest
        if fullUrl:
            url = fullUrl
        else:
            url = self.__url(jsonRequest)

        # remove any params that are 'None'
        # need to copy dictionary, since can't delete while iterating
        if params is not None:
            params2 = params.copy()
            for k in params2:
                if params2[k] is None:
                    del params[k]
            paramsStr = '?' + '&'.join(['%s=%s' % (k, v) for (k, v) in params.items()])
        else:
            paramsStr = ''

        if extraComment:
            log('Start ' + url + paramsStr, comment=extraComment)
        else:
            log('Start ' + url + paramsStr)

        log_rest("")
        log_rest("----------------------------------------------------------------------\n")
        if extraComment:
            log_rest("# Extra comment info about this request: " + extraComment)
        if cmd == 'get':
            log_rest("GET")
        else:
            log_rest("POST")
        log_rest(url + paramsStr)

        # file get passed thru kwargs here
        try:
            if 'post' == cmd:
                # NOTE == cmd: for now, since we don't have deserialization from JSON in h2o-dev, we use form-encoded POST.
                # This is temporary.
                # 
                # This following does application/json (aka, posting JSON in the body):
                # r = requests.post(url, timeout=timeout, params=params, data=json.dumps(postData), **kwargs)
                # 
                # This does form-encoded, which doesn't allow POST of nested structures
                r = requests.post(url, timeout=timeout, params=params, data=postData, **kwargs)
            elif 'delete' == cmd:
                r = requests.delete(url, timeout=timeout, params=params, **kwargs)                
            elif 'get' == cmd:
                r = requests.get(url, timeout=timeout, params=params, **kwargs)
            else:
                raise ValueError("Unknown HTTP command (expected 'get', 'post' or 'delete'): " + cmd)

        except Exception, e:
            # rethrow the exception after we've checked for stack trace from h2o
            # out of memory errors maybe don't show up right away? so we should wait for h2o
            # to get it out to h2o stdout. We don't want to rely on cloud teardown to check
            # because there's no delay, and we don't want to delay all cloud teardowns by waiting.
            # (this is new/experimental)
            exc_info = sys.exc_info()
            # use this to ignore the initial connection errors during build cloud when h2o is coming up
            if not noExtraErrorCheck: 
                h2p.red_print(
                    "ERROR: got exception on %s to h2o. \nGoing to check sandbox, then rethrow.." % (url + paramsStr))
                time.sleep(2)
                H2O.check_sandbox_for_errors(python_test_name=H2O.python_test_name);
            log_rest("")
            log_rest("EXCEPTION CAUGHT DOING REQUEST: " + str(e.message))
            raise exc_info[1], None, exc_info[2]

            H2O.verboseprint("r: " + repr(r))

        if 200 != r.status_code:
            print "JSON call returned non-200 status: ", url
            print "r.status_code: " + str(r.status_code)
            print "r.headers: " + repr(r.headers)
            print "r.text: " + r.text

        log_rest("")
        try:
            if r is None:
                log_rest("r is None")
            else:
                log_rest("HTTP status code: " + str(r.status_code))
                if hasattr(r, 'text'):
                    if r.text is None:
                        log_rest("r.text is None")
                    else:
                        log_rest(r.text)
                else:
                    log_rest("r does not have attr text")
        except Exception, e:
            # Paranoid exception catch.  
            # Ignore logging exceptions in the case that the above error checking isn't sufficient.
            print "Caught exception from result logging: ", e, "; result: ", repr(r)

        # fatal if no response
        if not r:
            raise Exception("Maybe bad url? no r in __do_json_request in %s:" % inspect.stack()[1][3])

        # this is used to open a browser on results, or to redo the operation in the browser
        # we don't' have that may urls flying around, so let's keep them all
        H2O.json_url_history.append(r.url)
        # if r.json():
        #     raise Exception("Maybe bad url? no r.json in __do_json_request in %s:" % inspect.stack()[1][3])

        rjson = None
        if returnFast:
            return
        try:
            rjson = r.json()
        except:
            print h2o_util.dump_json(r.text)
            if not isinstance(r, (list, dict)):
                raise Exception("h2o json responses should always be lists or dicts, see previous for text")

            raise Exception("Could not decode any json from the request.")

        # TODO
        # TODO
        # TODO
        # TODO: we should really only look in the response object.  This check
        # prevents us from having a field called "error" (e.g., for a scoring result).
        for e in ['error', 'Error', 'errors', 'Errors']:
            # error can be null (python None). This happens in exec2
            if e in rjson and rjson[e]:
                H2O.verboseprint("rjson:" + h2o_util.dump_json(rjson))
                emsg = 'rjson %s in %s: %s' % (e, inspect.stack()[1][3], rjson[e])
                if ignoreH2oError:
                    # well, we print it..so not totally ignore. test can look at rjson returned
                    print emsg
                else:
                    print emsg
                    raise Exception(emsg)

        for w in ['warning', 'Warning', 'warnings', 'Warnings']:
            # warning can be null (python None).
            if w in rjson and rjson[w]:
                H2O.verboseprint(dump_json(rjson))
                print 'rjson %s in %s: %s' % (w, inspect.stack()[1][3], rjson[w])

        return rjson
        # end of __do_json_request


    ''' 
    Check the output for errors.  Note: false positives are possible; a whitelist is available.
    '''
    @staticmethod
    def check_sandbox_for_errors(cloudShutdownIsError=False, sandboxIgnoreErrors=False, python_test_name=''):
        # TODO: nothing right now
        return
        # dont' have both tearDown and tearDownClass report the same found error
        # only need the first
        if nodes and nodes[0].sandbox_error_report(): # gets current state
            return

        # Can build a cloud that ignores all sandbox things that normally fatal the test
        # Kludge, test will set this directly if it wants, rather than thru build_cloud parameter.
        # we need the sandbox_ignore_errors, for the test teardown_cloud..the state disappears!
        ignore = sandboxIgnoreErrors or (nodes and nodes[0].sandbox_ignore_errors)
        errorFound = h2o_sandbox.check_sandbox_for_errors(
            LOG_DIR=LOG_DIR,
            sandboxIgnoreErrors=ignore,
            cloudShutdownIsError=cloudShutdownIsError,
            python_test_name=python_test_name)

        if errorFound and nodes:
            nodes[0].sandbox_error_report(True) # sets


    ###################
    # REST API ACCESSORS
    # TODO: remove .json

    '''
    Fetch all the jobs or a single job from the /Jobs endpoint.
    '''
    def jobs(self, job_key=None, timeoutSecs=10, **kwargs):
        params_dict = {
            'job_key': job_key
        }
        h2o_util.check_params_update_kwargs(params_dict, kwargs, 'jobs', True)
        result = self.__do_json_request('2/Jobs.json', timeout=timeoutSecs, params=params_dict)
        return result


    '''
    Poll a single job from the /Jobs endpoint until it is "status": "DONE" or "CANCELLED" or "FAILED" or we time out.
    '''
    # TODO: add delays, etc.
    def poll_job(self, job_key, timeoutSecs=10, retryDelaySecs=0.5, **kwargs):
        params_dict = {
        }
        h2o_util.check_params_update_kwargs(params_dict, kwargs, 'poll_job', True)

        start_time = time.time()
        while True:
            H2O.verboseprint('Polling for job: ' + job_key + '. . .')
            result = self.__do_json_request('2/Jobs.json/' + job_key, timeout=timeoutSecs, params=params_dict)
            
            if result['jobs'][0]['status'] == 'DONE' or result['jobs'][0]['status'] == 'CANCELLED' or result['jobs'][0]['status'] == 'FAILED':
                H2O.verboseprint('Job ' + result['jobs'][0]['status'] + ': ' + job_key + '.')
                return result

            if time.time() - start_time > timeoutSecs:
                H2O.verboseprint('Job: ' + job_key + ' timed out in: ' + timeoutSecs + '.')
                return None

            time.sleep(retryDelaySecs)


    ''' 
    Import a file or files into h2o.  The 'file' parameter accepts a directory or a single file.
    192.168.0.37:54323/ImportFiles.html?file=%2Fhome%2F0xdiag%2Fdatasets
    '''
    def import_files(self, path, timeoutSecs=180):
        a = self.__do_json_request('2/ImportFiles.json',
            timeout=timeoutSecs,
            params={"path": path}
        )
        H2O.verboseprint("\nimport_files result:", h2o_util.dump_json(a))
        return a


    '''
    Parse an imported raw file or files into a Frame.
    '''
    def parse(self, key, key2=None,
              timeoutSecs=300, retryDelaySecs=0.2, initialDelaySecs=None, pollTimeoutSecs=180,
              noise=None, benchmarkLogging=None, noPoll=False, **kwargs):

        #
        # Call ParseSetup?srcs=[keys] . . .
        #

        if benchmarkLogging:
            cloudPerfH2O.get_log_save(initOnly=True)

        # TODO: multiple keys
        parse_setup_params = {
            'srcs': "[" + key + "]"
        }
        # h2o_util.check_params_update_kwargs(params_dict, kwargs, 'parse_setup', print_params=True)
        setup_result = self.__do_json_request(jsonRequest="ParseSetup.json", timeout=timeoutSecs, params=parse_setup_params)
        H2O.verboseprint("ParseSetup result:", h2o_util.dump_json(setup_result))

        # 
        # and then Parse?srcs=<keys list> and params from the ParseSetup result
        # Parse?srcs=[nfs://Users/rpeck/Source/h2o2/smalldata/logreg/prostate.csv]&hex=prostate.hex&pType=CSV&sep=44&ncols=9&checkHeader=0&singleQuotes=false&columnNames=[ID,%20CAPSULE,%20AGE,%20RACE,%20DPROS,%20DCAPS,%20PSA,%20VOL,%20GLEASON]
        #

        first = True
        ascii_column_names = '['
        for s in setup_result['columnNames']:
            if not first: ascii_column_names += ', '
            ascii_column_names += str(s)
            first  = False
        ascii_column_names += ']'

        parse_params = {
            'srcs': "[" + setup_result['srcs'][0]['name'] + "]", # TODO: cons up the whole list
            'hex': setup_result['hexName'],
            'pType': setup_result['pType'],
            'sep': setup_result['sep'],
            'ncols': setup_result['ncols'],
            'checkHeader': setup_result['checkHeader'],
            'singleQuotes': setup_result['singleQuotes'],
            'columnNames': ascii_column_names,
        }
        print "parse_params: ", parse_params
        h2o_util.check_params_update_kwargs(parse_params, kwargs, 'parse', print_params=True)

        parse_result = self.__do_json_request(jsonRequest="Parse.json", timeout=timeoutSecs, params=parse_params, **kwargs)
        H2O.verboseprint("Parse result:", h2o_util.dump_json(parse_result))

        job_key = parse_result['job']['name']

        # TODO: dislike having different shapes for noPoll and poll
        if noPoll:
            return this.jobs(job_key)

        job_json = self.poll_job(job_key, timeoutSecs=timeoutSecs)

        if job_json:
            dest_key = job_json['jobs'][0]['dest']['name']
            return self.frames(dest_key)

        return None


    # TODO: remove .json
    '''
    Return a single Frame or all of the Frames in the h2o cluster.  The
    frames are contained in a list called "frames" at the top level of the
    result.  Currently the list is unordered.
    TODO:
    When find_compatible_models is implemented then the top level 
    dict will also contain a "models" list.
    '''
    def frames(self, key=None, timeoutSecs=10, **kwargs):
        params_dict = {
            'find_compatible_models': 0,
        }
        h2o_util.check_params_update_kwargs(params_dict, kwargs, 'frames', True)
        
        if key:
            result = self.__do_json_request('3/Frames.json/' + key, timeout=timeoutSecs, params=params_dict)
        else:
            result = self.__do_json_request('3/Frames.json', timeout=timeoutSecs, params=params_dict)
        return result


    # TODO: remove .json
    '''
    Return the columns for a single Frame in the h2o cluster.  
    '''
    def columns(self, key, timeoutSecs=10, **kwargs):
        params_dict = { }
        h2o_util.check_params_update_kwargs(params_dict, kwargs, 'columns', True)
        
        result = self.__do_json_request('3/Frames.json/' + key + '/columns', timeout=timeoutSecs, params=params_dict)
        return result


    # TODO: remove .json
    '''
    Return a single column for a single Frame in the h2o cluster.  
    '''
    def column(self, key, column, timeoutSecs=10, **kwargs):
        params_dict = { }
        h2o_util.check_params_update_kwargs(params_dict, kwargs, 'column', True)
        
        result = self.__do_json_request('3/Frames.json/' + key + '/columns/' + column, timeout=timeoutSecs, params=params_dict)
        return result


    # TODO: remove .json
    '''
    Return the summary for a single column for a single Frame in the h2o cluster.  
    '''
    def summary(self, key, column, timeoutSecs=10, **kwargs):
        params_dict = { }
        h2o_util.check_params_update_kwargs(params_dict, kwargs, 'summary', True)
        
        result = self.__do_json_request('3/Frames.json/' + key + '/columns/' + column + '/summary', timeout=timeoutSecs, params=params_dict)
        return result


    '''
    Delete a frame on the h2o cluster, given its key.
    '''
    def delete_frame(self, key, ignoreMissingKey=True, timeoutSecs=60, **kwargs):
        assert key is not None, '"key" parameter is null'

        result = self.__do_json_request('/3/Frames.json/' + key, cmd='delete', timeout=timeoutSecs)

        # TODO: look for what?
        if not ignoreMissingKey and 'f00b4r' in result:
            raise ValueError('Frame key not found: ' + key)

        return result


    '''
    Delete all frames on the h2o cluster.
    '''
    def delete_frames(self, timeoutSecs=60, **kwargs):
        parameters = { }
        result = self.__do_json_request('/3/Frames.json', cmd='delete', timeout=timeoutSecs)

        return result


    # TODO: remove .json
    '''
    Return a model builder or all of the model builders known to the
    h2o cluster.  The model builders are contained in a dictionary
    called "model_builders" at the top level of the result.  The
    dictionary maps algorithm names to parameters lists.  Each of the
    parameters contains all the metdata required by a client to
    present a model building interface to the user.
    '''
    def model_builders(self, algo=None, timeoutSecs=10, **kwargs):
        params_dict = {
        }
        h2o_util.check_params_update_kwargs(params_dict, kwargs, 'model_builders', True)

        if algo:
            result = self.__do_json_request('2/ModelBuilders.json/' + algo, timeout=timeoutSecs, params=params_dict)
        else:
            result = self.__do_json_request('2/ModelBuilders.json', timeout=timeoutSecs, params=params_dict)
        return result


    '''
    Check a dictionary of model builder parameters on the h2o cluster using the given algorithm and model parameters.
    '''
    def validate_model_parameters(self, algo, training_frame, parameters, timeoutSecs=60, **kwargs):
        assert algo is not None, '"algo" parameter is null'
        assert training_frame is not None, '"training_frame" parameter is null'
        assert parameters is not None, '"parameters" parameter is null'

        model_builders = self.model_builders(timeoutSecs=timeoutSecs)
        assert model_builders is not None, "/ModelBuilders REST call failed"
        assert algo in model_builders['model_builders']
        builder = model_builders['model_builders'][algo]
        
        # TODO: test this assert, I don't think this is working. . .
        frames = self.frames(key=training_frame)
        assert frames is not None, "/Frames/{0} REST call failed".format(training_frame)
        assert frames['frames'][0]['key']['name'] == training_frame, "/Frames/{0} returned Frame {1} rather than Frame {2}".format(training_frame, frames['frames'][0]['key']['name'], training_frame)

        # TODO: add parameter existence checks
        # TODO: add parameter value validation
        parameters['training_frame'] = training_frame

        # TODO: add parameter existence checks
        # TODO: add parameter value validation
        result = self.__do_json_request('/2/ModelBuilders.json/' + algo + "/parameters", cmd='post', timeout=timeoutSecs, postData=parameters, ignoreH2oError=True, noExtraErrorCheck=True)

        H2O.verboseprint("model parameters validation: " + repr(result))
        return result


    '''
    Build a model on the h2o cluster using the given algorithm, training 
    Frame and model parameters.
    '''
    def build_model(self, algo, training_frame, parameters, destination_key = None, timeoutSecs=60, asynchronous=False, **kwargs):
        assert algo is not None, '"algo" parameter is null'
        assert training_frame is not None, '"training_frame" parameter is null'
        assert parameters is not None, '"parameters" parameter is null'

        model_builders = self.model_builders(timeoutSecs=timeoutSecs)
        assert model_builders is not None, "/ModelBuilders REST call failed"
        assert algo in model_builders['model_builders']
        builder = model_builders['model_builders'][algo]
        
        # TODO: test this assert, I don't think this is working. . .
        frames = self.frames(key=training_frame)
        assert frames is not None, "/Frames/{0} REST call failed".format(training_frame)
        assert frames['frames'][0]['key']['name'] == training_frame, "/Frames/{0} returned Frame {1} rather than Frame {2}".format(training_frame, frames['frames'][0]['key']['name'], training_frame)

        # TODO: add parameter existence checks
        # TODO: add parameter value validation
        parameters['training_frame'] = training_frame

        if destination_key is not None:
            parameters['destination_key'] = destination_key
        result = self.__do_json_request('/2/ModelBuilders.json/' + algo, cmd='post', timeout=timeoutSecs, postData=parameters)

        if asynchronous:
            return result
        elif 'validation_error_count' in result:
            # parameters validation failure
            # TODO: add schema_type and schema_version into all the schemas to make this clean to check
            return result
        else:
            job = result['jobs'][0]
            job_key = job['key']['name']
            H2O.verboseprint("model building job_key: " + repr(job_key))
            job_json = self.poll_job(job_key, timeoutSecs=timeoutSecs)
            return job_json


    '''
    Score a model on the h2o cluster on the given Frame and return only the model metrics. 
    '''
    def compute_model_metrics(self, model, frame, timeoutSecs=60, **kwargs):
        assert model is not None, '"model" parameter is null'
        assert frame is not None, '"frame" parameter is null'

        models = self.models(key=model, timeoutSecs=timeoutSecs)
        assert models is not None, "/Models REST call failed"
        assert models['models'][0]['key'] == model, "/Models/{0} returned Model {1} rather than Model {2}".format(model, models['models'][0]['key']['name'], model)

        # TODO: test this assert, I don't think this is working. . .
        frames = self.frames(key=frame)
        assert frames is not None, "/Frames/{0} REST call failed".format(frame)
        assert frames['frames'][0]['key']['name'] == frame, "/Frames/{0} returned Frame {1} rather than Frame {2}".format(frame, frames['frames'][0]['key']['name'], frame)

        result = self.__do_json_request('/3/ModelMetrics.json/models/' + model + '/frames/' + frame, cmd='post', timeout=timeoutSecs)

        mm = result['model_metrics'][0]
        H2O.verboseprint("model metrics: " + repr(mm))
        return mm


    def predict(self, model, frame, timeoutSecs=60, **kwargs):
        assert model is not None, '"model" parameter is null'
        assert frame is not None, '"frame" parameter is null'

        models = self.models(key=model, timeoutSecs=timeoutSecs)
        assert models is not None, "/Models REST call failed"
        assert models['models'][0]['key'] == model, "/Models/{0} returned Model {1} rather than Model {2}".format(model, models['models'][0]['key']['name'], model)

        # TODO: test this assert, I don't think this is working. . .
        frames = self.frames(key=frame)
        assert frames is not None, "/Frames/{0} REST call failed".format(frame)
        assert frames['frames'][0]['key']['name'] == frame, "/Frames/{0} returned Frame {1} rather than Frame {2}".format(frame, frames['frames'][0]['key']['name'], frame)

        result = self.__do_json_request('/3/Predictions.json/models/' + model + '/frames/' + frame, cmd='post', timeout=timeoutSecs)
        return result


    '''
    ModelMetrics list. 
    '''
    def model_metrics(self, timeoutSecs=60, **kwargs):
        result = self.__do_json_request('/3/ModelMetrics.json', cmd='get', timeout=timeoutSecs)

        return result


    # TODO: remove .json
    '''
    Return all of the models in the h2o cluster, or a single model given its key.  
    The models are contained in a list called "models" at the top level of the
    result.  Currently the list is unordered.
    TODO:
    When find_compatible_frames is implemented then the top level 
    dict will also contain a "frames" list.
    '''
    def models(self, key=None, timeoutSecs=10, **kwargs):
        params_dict = {
            'find_compatible_frames': False
        }
        h2o_util.check_params_update_kwargs(params_dict, kwargs, 'models', True)

        if key:
            result = self.__do_json_request('3/Models.json/' + key, timeout=timeoutSecs, params=params_dict)
        else:
            result = self.__do_json_request('3/Models.json', timeout=timeoutSecs, params=params_dict)
        return result


    '''
    Delete a model on the h2o cluster, given its key.
    '''
    def delete_model(self, key, ignoreMissingKey=True, timeoutSecs=60, **kwargs):
        assert key is not None, '"key" parameter is null'

        result = self.__do_json_request('/3/Models.json/' + key, cmd='delete', timeout=timeoutSecs)

        # TODO: look for what?
        if not ignoreMissingKey and 'f00b4r' in result:
            raise ValueError('Model key not found: ' + key)

        return result


    '''
    Delete all models on the h2o cluster.
    '''
    def delete_models(self, timeoutSecs=60, **kwargs):
        parameters = { }
        result = self.__do_json_request('/3/Models.json', cmd='delete', timeout=timeoutSecs)

        return result


