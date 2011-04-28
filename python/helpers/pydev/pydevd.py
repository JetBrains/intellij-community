#IMPORTANT: pydevd_constants must be the 1st thing defined because it'll keep a reference to the original sys._getframe
from django_debug import DjangoLineBreakpoint
from pydevd_constants import * #@UnusedWildImport
from pydevd_breakpoints import * #@UnusedWildImport

from pydevd_comm import  CMD_CHANGE_VARIABLE, \
                         CMD_EVALUATE_EXPRESSION, \
                         CMD_EXEC_EXPRESSION, \
                         CMD_GET_COMPLETIONS, \
                         CMD_GET_FRAME, \
                         CMD_GET_VARIABLE, \
                         CMD_LIST_THREADS, \
                         CMD_REMOVE_BREAK, \
                         CMD_RUN, \
                         CMD_SET_BREAK, \
                         CMD_STEP_INTO, \
                         CMD_STEP_OVER, \
                         CMD_STEP_RETURN, \
                         CMD_THREAD_CREATE, \
                         CMD_THREAD_KILL, \
                         CMD_THREAD_RUN, \
                         CMD_THREAD_SUSPEND, \
                         CMD_RUN_TO_LINE, \
                         CMD_RELOAD_CODE, \
                         CMD_VERSION, \
                         CMD_CONSOLE_EXEC, \
                         CMD_ADD_EXCEPTION_BREAK, \
                         CMD_REMOVE_EXCEPTION_BREAK, \
                         CMD_LOAD_SOURCE, \
                         GetGlobalDebugger, \
                         InternalChangeVariable, \
                         InternalGetCompletions, \
                         InternalEvaluateExpression, \
                         InternalConsoleExec, \
                         InternalGetFrame, \
                         InternalGetVariable, \
                         InternalTerminateThread, \
                         NetCommand, \
                         NetCommandFactory, \
                         PyDBDaemonThread, \
                         PydevQueue, \
                         ReaderThread, \
                         SetGlobalDebugger, \
                         WriterThread, \
                         PydevdFindThreadById, \
                         PydevdLog, \
                         StartClient, \
                         StartServer

from pydevd_file_utils import NormFileToServer, GetFilenameAndBase
import pydevd_vars
from pydevd_vars import getAdditionalFramesContainer
import traceback
import pydevd_vm_type
import pydevd_tracing
import pydevd_io
from pydevd_additional_thread_info import PyDBAdditionalThreadInfo
import time

threadingEnumerate = threading.enumerate
threadingCurrentThread = threading.currentThread


DONT_TRACE = {
              #commonly used things from the stdlib that we don't want to trace
              'threading.py':1,
              'Queue.py':1,
              'socket.py':1,

              #things from pydev that we don't want to trace
              'pydevd_additional_thread_info.py':1,
              'pydevd_comm.py':1,
              'pydevd_constants.py':1,
              'pydevd_file_utils.py':1,
              'pydevd_frame.py':1,
              'pydevd_io.py':1 ,
              'pydevd_resolver.py':1 ,
              'pydevd_tracing.py':1 ,
              'pydevd_vars.py':1,
              'pydevd_vm_type.py':1,
              'pydevd.py':1 ,
              'pydevd_psyco_stub.py':1
              }

if IS_PY3K:
    #if we try to trace io.py it seems it can get halted (see http://bugs.python.org/issue4716)
    DONT_TRACE['io.py'] = 1


connected = False
bufferStdOutToServer = False
bufferStdErrToServer = False
remote = False

PyDBUseLocks = True


#=======================================================================================================================
# PyDBCommandThread
#=======================================================================================================================
class PyDBCommandThread(PyDBDaemonThread):

    def __init__(self, pyDb):
        PyDBDaemonThread.__init__(self)
        self.pyDb = pyDb
        self.setName('pydevd.CommandThread')

    def OnRun(self):
        time.sleep(5) #this one will only start later on (because otherwise we may not have any non-daemon threads

        run_traced = True

        if pydevd_vm_type.GetVmType() == pydevd_vm_type.PydevdVmType.JYTHON and sys.hexversion <= 0x020201f0:
            #don't run untraced threads if we're in jython 2.2.1 or lower
            #jython bug: if we start a thread and another thread changes the tracing facility
            #it affects other threads (it's not set only for the thread but globally) 
            #Bug: http://sourceforge.net/tracker/index.php?func=detail&aid=1870039&group_id=12867&atid=112867
            run_traced = False

        if run_traced:
            pydevd_tracing.SetTrace(None) # no debugging on this thread

        try:
            while not self.killReceived:
                try:
                    self.pyDb.processInternalCommands()
                except:
                    PydevdLog(0, 'Finishing debug communication...(2)')
                time.sleep(0.5)
        except:
            pass
            #only got this error in interpreter shutdown
            #PydevdLog(0, 'Finishing debug communication...(3)')

try:
    import thread
except ImportError:
    import _thread as thread #Py3K changed it.
_original_start_new_thread = thread.start_new_thread

#=======================================================================================================================
# NewThreadStartup
#=======================================================================================================================
class NewThreadStartup:

    def __init__(self, original_func, args, kwargs):
        self.original_func = original_func
        self.args = args
        self.kwargs = kwargs

    def __call__(self):
        global_debugger = GetGlobalDebugger()
        pydevd_tracing.SetTrace(global_debugger.trace_dispatch)
        self.original_func(*self.args, **self.kwargs)


#=======================================================================================================================
# pydev_start_new_thread
#=======================================================================================================================
def pydev_start_new_thread(function, args, kwargs={}):
    '''
    We need to replace the original thread.start_new_thread with this function so that threads started through
    it and not through the threading module are properly traced.
    '''
    return _original_start_new_thread(NewThreadStartup(function, args, kwargs), ())

#=======================================================================================================================
# PyDB
#=======================================================================================================================
class PyDB:
    """ Main debugging class 
    Lots of stuff going on here:
    
    PyDB starts two threads on startup that connect to remote debugger (RDB)
    The threads continuously read & write commands to RDB.
    PyDB communicates with these threads through command queues.
       Every RDB command is processed by calling processNetCommand.
       Every PyDB net command is sent to the net by posting NetCommand to WriterThread queue
       
       Some commands need to be executed on the right thread (suspend/resume & friends)
       These are placed on the internal command queue.
    """

    RUNNING_THREAD_IDS = {} #this is a dict of thread ids pointing to thread ids. Whenever a command
                            #is passed to the java end that acknowledges that a thread was created,
                            #the thread id should be passed here -- and if at some time we do not find
                            #that thread alive anymore, we must remove it from this list and make
                            #the java side know that the thread was killed.

    def __init__(self):
        SetGlobalDebugger(self)
        pydevd_tracing.ReplaceSysSetTraceFunc()
        self.reader = None
        self.writer = None
        self.quitting = None
        self.cmdFactory = NetCommandFactory()
        self.cmdQueue = {}     # the hash of Queues. Key is thread id, value is thread
        self.breakpoints = {}
        self.django_breakpoints = {}
        self.additional_frames = getAdditionalFramesContainer()
        self.readyToRun = False
        self.lock = threading.RLock()
        self.internalQueueLock = threading.Lock()
        self._finishDebuggingSession = False
        self.force_post_mortem_stop = 0


    def FinishDebuggingSession(self):
        self._finishDebuggingSession = True

    def acquire(self):
        if PyDBUseLocks:
            self.lock.acquire()
        return True

    def release(self):
        if PyDBUseLocks:
            self.lock.release()
        return True

    def initializeNetwork(self, sock):
        try:
            sock.settimeout(None) # infinite, no timeouts from now on - jython does not have it
        except:
            pass
        self.writer = WriterThread(sock)
        self.reader = ReaderThread(sock)
        self.writer.start()
        self.reader.start()

        time.sleep(0.1) # give threads time to start        

    def connect(self, host, port):
        if host:
            s = StartClient(host, port)
        else:
            s = StartServer(port)

        self.initializeNetwork(s)


    def getInternalQueue(self, thread_id):
        """ returns intenal command queue for a given thread.
        if new queue is created, notify the RDB about it """
        try:
            return self.cmdQueue[thread_id]
        except KeyError:
            self.internalQueueLock.acquire()
            try:
                self.cmdQueue[thread_id] = PydevQueue.Queue()
                all_threads = threading.enumerate()
                cmd = None
                for t in all_threads:
                    if GetThreadId(t) == thread_id:
                        if not hasattr(t, 'additionalInfo'):
                            #see http://sourceforge.net/tracker/index.php?func=detail&aid=1955428&group_id=85796&atid=577329
                            #Let's create the additional info right away!
                            t.additionalInfo = PyDBAdditionalThreadInfo()

                        self.RUNNING_THREAD_IDS[thread_id] = t
                        cmd = self.cmdFactory.makeThreadCreatedMessage(t)
                        break

                if cmd:
                    PydevdLog(2, "found a new thread ", str(thread_id))
                    self.writer.addCommand(cmd)
                else:
                    PydevdLog(0, "could not find thread by id to register")
            finally:
                self.internalQueueLock.release()

        return self.cmdQueue[thread_id]


    def postInternalCommand(self, int_cmd, thread_id):
        """ if thread_id is *, post to all """
        if thread_id == "*":
            for k in self.cmdQueue.keys():
                self.cmdQueue[k].put(int_cmd)

        else:
            queue = self.getInternalQueue(thread_id)
            queue.put(int_cmd)

    def checkOutputRedirect(self):
        global bufferStdOutToServer
        global bufferStdErrToServer

        if bufferStdOutToServer:
                self.checkOutput(sys.stdoutBuf, 1) #@UndefinedVariable

        if bufferStdErrToServer:
                self.checkOutput(sys.stderrBuf, 2) #@UndefinedVariable

    def checkOutput(self, out, outCtx):
        '''Checks the output to see if we have to send some buffered output to the debug server
        
        @param out: sys.stdout or sys.stderr
        @param outCtx: the context indicating: 1=stdout and 2=stderr (to know the colors to write it)
        '''

        try:
            v = out.getvalue()

            if v:
                self.cmdFactory.makeIoMessage(v, outCtx, self)
        except:
            traceback.print_exc()


    def processInternalCommands(self):
        '''This function processes internal commands
        '''

        self.acquire()
        try:

            self.checkOutputRedirect()

            currThreadId = GetThreadId(threadingCurrentThread())
            threads = threadingEnumerate()
            foundNonPyDBDaemonThread = False
            foundThreads = {}

            for t in threads:
                tId = GetThreadId(t)
                if t.isAlive():
                    foundThreads[tId] = tId

                if not isinstance(t, PyDBDaemonThread):
                    foundNonPyDBDaemonThread = True
                    queue = self.getInternalQueue(GetThreadId(t))
                    cmdsToReadd = []    #some commands must be processed by the thread itself... if that's the case,
                                        #we will re-add the commands to the queue after executing.
                    try:
                        while True:
                            int_cmd = queue.get(False)
                            if int_cmd.canBeExecutedBy(currThreadId):
                                PydevdLog(2, "processing internal command ", str(int_cmd))
                                int_cmd.doIt(self)
                            else:
                                PydevdLog(2, "NOT processing internal command ", str(int_cmd))
                                cmdsToReadd.append(int_cmd)

                    except PydevQueue.Empty:
                        for int_cmd in cmdsToReadd:
                            queue.put(int_cmd)
                        # this is how we exit

            if not foundNonPyDBDaemonThread:
                self.FinishDebuggingSession()
                for t in threads:
                    if hasattr(t, 'doKill'):
                        t.doKill()

            for tId in self.RUNNING_THREAD_IDS.keys():
                try:
                    if not DictContains(foundThreads, tId):
                        self.processThreadNotAlive(tId)
                except:
                    sys.stderr.write('Error iterating through %s (%s) - %s\n' % (foundThreads, foundThreads.__class__, dir(foundThreads)))
                    sys.stderr.flush()
                    raise

        finally:
            self.release()

    def enable_tracing(self):
        #and enable the tracing for existing threads (because there may be frames being executed that
        #are currently untraced).
        threads = threadingEnumerate()
        for t in threads:
            if not t.getName().startswith('pydevd.'):
            #TODO: optimize so that we only actually add that tracing if it's in
            #the new breakpoint context.
                additionalInfo = getattr(t, 'additionalInfo', None)

                if additionalInfo is not None:
                    for frame in additionalInfo.IterFrames():
                        frame.f_trace = self.trace_dispatch
                        SetTraceForParents(frame, self.trace_dispatch)
                        del frame

    def processNetCommand(self, cmd_id, seq, text):
        '''Processes a command received from the Java side
        
        @param cmd_id: the id of the command
        @param seq: the sequence of the command
        @param text: the text received in the command
        
        @note: this method is run as a big switch... after doing some tests, it's not clear whether changing it for
        a dict id --> function call will have better performance result. A simple test with xrange(10000000) showed
        that the gains from having a fast access to what should be executed are lost because of the function call in
        a way that if we had 10 elements in the switch the if..elif are better -- but growing the number of choices
        makes the solution with the dispatch look better -- so, if this gets more than 20-25 choices at some time,
        it may be worth refactoring it (actually, reordering the ifs so that the ones used mostly come before 
        probably will give better performance).
        '''

        self.acquire()
        try:
            try:
                cmd = None
                if cmd_id == CMD_RUN:
                    self.readyToRun = True

                elif cmd_id == CMD_VERSION:
                    # response is version number
                    cmd = self.cmdFactory.makeVersionMessage(seq)

                elif cmd_id == CMD_LIST_THREADS:
                    # response is a list of threads
                    cmd = self.cmdFactory.makeListThreadsMessage(seq)

                elif cmd_id == CMD_THREAD_KILL:
                    int_cmd = InternalTerminateThread(text)
                    self.postInternalCommand(int_cmd, text)

                elif cmd_id == CMD_THREAD_SUSPEND:
                    t = PydevdFindThreadById(text)
                    if t:
                        additionalInfo = None
                        try:
                            additionalInfo = t.additionalInfo
                        except AttributeError:
                            pass #that's ok, no info currently set

                        if additionalInfo is not None:
                            for frame in additionalInfo.IterFrames():
                                frame.f_trace = self.trace_dispatch
                                SetTraceForParents(frame, self.trace_dispatch)
                                del frame

                        self.setSuspend(t, CMD_THREAD_SUSPEND)

                elif cmd_id == CMD_THREAD_RUN:
                    t = PydevdFindThreadById(text)
                    if t:
                        t.additionalInfo.pydev_step_cmd = None
                        t.additionalInfo.pydev_step_stop = None
                        t.additionalInfo.pydev_state = STATE_RUN

                elif cmd_id == CMD_STEP_INTO or cmd_id == CMD_STEP_OVER or cmd_id == CMD_STEP_RETURN:
                    #we received some command to make a single step
                    t = PydevdFindThreadById(text)
                    if t:
                        t.additionalInfo.pydev_step_cmd = cmd_id
                        t.additionalInfo.pydev_state = STATE_RUN

                elif cmd_id == CMD_RUN_TO_LINE:
                    #we received some command to make a single step
                    thread_id, line, func_name = text.split('\t', 2)
                    t = PydevdFindThreadById(thread_id)
                    if t:
                        t.additionalInfo.pydev_step_cmd = cmd_id
                        t.additionalInfo.pydev_next_line = int(line)
                        t.additionalInfo.pydev_func_name = func_name
                        t.additionalInfo.pydev_state = STATE_RUN


                elif cmd_id == CMD_RELOAD_CODE:
                    #we received some command to make a reload of a module
                    module_name = text.strip()
                    from pydevd_reload import xreload
                    if not DictContains(sys.modules, module_name):
                        if '.' in module_name:
                            new_module_name = module_name.split('.')[-1]
                            if DictContains(sys.modules, new_module_name):
                                module_name = new_module_name

                    if not DictContains(sys.modules, module_name):
                        sys.stderr.write('pydev debugger: Unable to find module to reload: "'+module_name+'".\n')
                        sys.stderr.write('pydev debugger: This usually means you are trying to reload the __main__ module (which cannot be reloaded).\n')
                        sys.stderr.flush()

                    else:
                        sys.stderr.write('pydev debugger: Reloading: '+module_name+'\n')
                        sys.stderr.flush()
                        xreload(sys.modules[module_name])


                elif cmd_id == CMD_CHANGE_VARIABLE:
                    #the text is: thread\tstackframe\tFRAME|GLOBAL\tattribute_to_change\tvalue_to_change
                    try:
                        thread_id, frame_id, scope, attr_and_value = text.split('\t', 3)

                        tab_index = attr_and_value.rindex('\t')
                        attr = attr_and_value[0:tab_index].replace('\t', '.')
                        value = attr_and_value[tab_index + 1:]
                        int_cmd = InternalChangeVariable(seq, thread_id, frame_id, scope, attr, value)
                        self.postInternalCommand(int_cmd, thread_id)

                    except:
                        traceback.print_exc()

                elif cmd_id == CMD_GET_VARIABLE:
                    #we received some command to get a variable
                    #the text is: thread_id\tframe_id\tFRAME|GLOBAL\tattributes*
                    try:
                        thread_id, frame_id, scopeattrs = text.split('\t', 2)

                        if scopeattrs.find('\t') != -1: # there are attributes beyond scope
                            scope, attrs = scopeattrs.split('\t', 1)
                        else:
                            scope, attrs = (scopeattrs, None)

                        int_cmd = InternalGetVariable(seq, thread_id, frame_id, scope, attrs)
                        self.postInternalCommand(int_cmd, thread_id)

                    except:
                        traceback.print_exc()

                elif cmd_id == CMD_GET_COMPLETIONS:
                    #we received some command to get a variable
                    #the text is: thread_id\tframe_id\tactivation token
                    try:
                        thread_id, frame_id, scope, act_tok = text.split('\t', 3)

                        int_cmd = InternalGetCompletions(seq, thread_id, frame_id, act_tok)
                        self.postInternalCommand(int_cmd, thread_id)

                    except:
                        traceback.print_exc()

                elif cmd_id == CMD_GET_FRAME:
                    thread_id, frame_id, scope = text.split('\t', 2)

                    int_cmd = InternalGetFrame(seq, thread_id, frame_id)
                    self.postInternalCommand(int_cmd, thread_id)

                elif cmd_id == CMD_SET_BREAK:
                    #func name: 'None': match anything. Empty: match global, specified: only method context.

                    #command to add some breakpoint.
                    # text is file\tline. Add to breakpoints dictionary
                    type, file, line, condition, expression = text.split('\t', 4)

                    if condition.startswith('**FUNC**'):
                        func_name, condition = condition.split('\t', 1)

                        #We must restore new lines and tabs as done in 
                        #AbstractDebugTarget.breakpointAdded
                        condition = condition.replace("@_@NEW_LINE_CHAR@_@", '\n').\
                            replace("@_@TAB_CHAR@_@", '\t').strip()

                        func_name = func_name[8:]
                    else:
                        func_name = 'None' #Match anything if not specified.


                    file = NormFileToServer(file)

                    if not os.path.exists(file):
                        sys.stderr.write('pydev debugger: warning: trying to add breakpoint'\
                            ' to file that does not exist: %s (will have no effect)\n' % (file,))
                        sys.stderr.flush()

                    line = int(line)

                    if len(condition) <= 0 or condition is None or condition == "None":
                        condition = None

                    if len(expression) <= 0 or expression is None or expression == "None":
                        expression = None

                    if type == 'python-line':
                        breakpoint = LineBreakpoint(type, True, condition, func_name, expression)
                        breakpoint.add(self.breakpoints, file, line, func_name)
                    elif type == 'django-line':
                        breakpoint = DjangoLineBreakpoint(type, file, line, True, condition, func_name, expression)
                        breakpoint.add(self.django_breakpoints, file, line, func_name)
                    else:
                        raise NameError(type)



                    self.enable_tracing()

                elif cmd_id == CMD_REMOVE_BREAK:
                    #command to remove some breakpoint
                    #text is file\tline. Remove from breakpoints dictionary
                    type, file, line = text.split('\t', 2)
                    file = NormFileToServer(file)
                    try:
                        line = int(line)
                    except ValueError:
                        pass

                    else:
                        try:
                            if type == 'django-line':
                                del self.django_breakpoints[file][line]
                            else:
                                del self.breakpoints[file][line] #remove the breakpoint in that line
                            if DEBUG_TRACE_BREAKPOINTS > 0:
                                sys.stderr.write('Removed breakpoint:%s\n' % (file,))
                                sys.stderr.flush()
                        except KeyError:
                            #ok, it's not there...
                            if DEBUG_TRACE_BREAKPOINTS > 0:
                                #Sometimes, when adding a breakpoint, it adds a remove command before (don't really know why)
                                sys.stderr.write("breakpoint not found: %s - %s\n" % (file, line))
                                sys.stderr.flush()

                elif cmd_id == CMD_EVALUATE_EXPRESSION or cmd_id == CMD_EXEC_EXPRESSION:
                    #command to evaluate the given expression
                    #text is: thread\tstackframe\tLOCAL\texpression
                    thread_id, frame_id, scope, expression, trim = text.split('\t', 4)
                    int_cmd = InternalEvaluateExpression(seq, thread_id, frame_id, expression,
                        cmd_id == CMD_EXEC_EXPRESSION, int(trim) == 1)
                    self.postInternalCommand(int_cmd, thread_id)

                elif cmd_id == CMD_CONSOLE_EXEC:
                    #command to exec expression in console, in case expression is only partially valid 'False' is returned
                    #text is: thread\tstackframe\tLOCAL\texpression

                    thread_id, frame_id, scope, expression = text.split('\t', 3)

                    int_cmd = InternalConsoleExec(seq, thread_id, frame_id, expression)
                    self.postInternalCommand(int_cmd, thread_id)

                elif cmd_id == CMD_ADD_EXCEPTION_BREAK:
                    global exception_set
                    exception, notify_always, notify_on_terminate = text.split('\t', 2)

                    is_notify_always = int(notify_always) == 1
                    is_notify_on_terminate = int(notify_on_terminate) == 1

                    exc_type = get_class(exception)

                    if exc_type is not None:
                        exception_set.add(ExceptionBreakpoint(exception, exc_type, is_notify_always, is_notify_on_terminate))

                    if is_notify_on_terminate:
                        update_exception_hook()
                    if is_notify_always:
                        global always_exception_set
                        always_exception_set.add(exc_type)
                        self.enable_tracing()

                elif cmd_id == CMD_REMOVE_EXCEPTION_BREAK:
                    exception = text
                    exc_type = get_class(exception)
                    if exc_type is not None:
                        try:
                            exception_set.remove(exc_type)
                            always_exception_set.remove(exc_type)
                        except:
                            pass
                    update_exception_hook()

                elif cmd_id == CMD_LOAD_SOURCE:
                    path = text
                    try:
                        f = open(path, 'r')
                        source = f.read()
                        self.cmdFactory.makeLoadSourceMessage(seq, source, self)
                    except:
                        return self.cmdFactory.makeErrorMessage(seq, GetExceptionTracebackStr())

                else:
                    #I have no idea what this is all about
                    cmd = self.cmdFactory.makeErrorMessage(seq, "unexpected command " + str(cmd_id))

                if cmd is not None:
                    self.writer.addCommand(cmd)
                    del cmd

            except Exception:
                traceback.print_exc()
                cmd = self.cmdFactory.makeErrorMessage(seq,
                    "Unexpected exception in processNetCommand.\nInitial params: %s" % ((cmd_id, seq, text),))

                self.writer.addCommand(cmd)
        finally:
            self.release()

    def processThreadNotAlive(self, threadId):
        """ if thread is not alive, cancel trace_dispatch processing """
        thread = self.RUNNING_THREAD_IDS.get(threadId, None)
        if thread is None:
            return

        del self.RUNNING_THREAD_IDS[threadId]
        wasNotified = thread.additionalInfo.pydev_notify_kill

        if not wasNotified:
            cmd = self.cmdFactory.makeThreadKilledMessage(threadId)
            self.writer.addCommand(cmd)
            thread.additionalInfo.pydev_notify_kill = True

    def setSuspend(self, thread, stop_reason):
        thread.additionalInfo.suspend_type = PYTHON_SUSPEND
        thread.additionalInfo.pydev_state = STATE_SUSPEND
        thread.stop_reason = stop_reason

    def doWaitSuspend(self, thread, frame, event, arg): #@UnusedVariable
        """ busy waits until the thread state changes to RUN 
        it expects thread's state as attributes of the thread.
        Upon running, processes any outstanding Stepping commands.
        """
        self.processInternalCommands()

        message = getattr(thread.additionalInfo, "message", None)

        cmd = self.cmdFactory.makeThreadSuspendMessage(GetThreadId(thread), frame, thread.stop_reason, message)
        self.writer.addCommand(cmd)

        info = thread.additionalInfo
        while info.pydev_state == STATE_SUSPEND and not self._finishDebuggingSession:
            self.processInternalCommands()
            time.sleep(0.2)

        #process any stepping instructions 
        if info.pydev_step_cmd == CMD_STEP_INTO:
            info.pydev_step_stop = None

        elif info.pydev_step_cmd == CMD_STEP_OVER:
            info.pydev_step_stop = frame
            if frame.f_trace is None:
                frame.f_trace = self.trace_dispatch
            SetTraceForParents(frame, self.trace_dispatch)

        elif info.pydev_step_cmd == CMD_RUN_TO_LINE:
            if frame.f_trace is None:
                frame.f_trace = self.trace_dispatch
            SetTraceForParents(frame, self.trace_dispatch)

            if event == 'line':
                #If we're already in the correct context, we have to stop it now, because we can act only on
                #line events -- if a return was the next statement it wouldn't work (so, we have this code
                #repeated at pydevd_frame). 
                stop = False
                curr_func_name = frame.f_code.co_name

                #global context is set with an empty name
                if curr_func_name in ('?', '<module>'):
                    curr_func_name = ''

                if curr_func_name == info.pydev_func_name:
                    line = info.pydev_next_line
                    if frame.f_lineno == line:
                        stop = True
                    else:
                        if frame.f_trace is None:
                            frame.f_trace = self.trace_dispatch
                        frame.f_lineno = line
                        frame.f_trace = None
                        stop = True
                if stop:
                    info.pydev_state = STATE_SUSPEND
                    self.doWaitSuspend(thread, frame, event, arg)
                    return


        elif info.pydev_step_cmd == CMD_STEP_RETURN:
            back_frame = frame.f_back
            if back_frame is not None:
                #steps back to the same frame (in a return call it will stop in the 'back frame' for the user)
                info.pydev_step_stop = frame
                if frame.f_trace is None:
                    frame.f_trace = self.trace_dispatch
                SetTraceForParents(frame, self.trace_dispatch)
            else:
                #No back frame?!? -- this happens in jython when we have some frame created from an awt event
                #(the previous frame would be the awt event, but this doesn't make part of 'jython', only 'java')
                #so, if we're doing a step return in this situation, it's the same as just making it run
                info.pydev_step_stop = None
                info.pydev_step_cmd = None
                info.pydev_state = STATE_RUN

        del frame
        cmd = self.cmdFactory.makeThreadRunMessage(GetThreadId(thread), info.pydev_step_cmd)
        self.writer.addCommand(cmd)



    def trace_dispatch(self, frame, event, arg):
        ''' This is the callback used when we enter some context in the debugger. 
        
        We also decorate the thread we are in with info about the debugging.
        The attributes added are:
            pydev_state
            pydev_step_stop
            pydev_step_cmd
            pydev_notify_kill 
        '''
        try:
            if self._finishDebuggingSession:
                #that was not working very well because jython gave some socket errors
                threads = threadingEnumerate()
                for t in threads:
                    if hasattr(t, 'doKill'):
                        t.doKill()
                return None

            filename, base = GetFilenameAndBase(frame)

            is_file_to_ignore = DictContains(DONT_TRACE, base) #we don't want to debug threading or anything related to pydevd

            if not self.force_post_mortem_stop: #If we're in post mortem mode, we might not have another chance to show that info!
                if is_file_to_ignore:
                    return None

            #print('trace_dispatch', base, frame.f_lineno, event, frame.f_code.co_name)
            try:
                #this shouldn't give an exception, but it could happen... (python bug)
                #see http://mail.python.org/pipermail/python-bugs-list/2007-June/038796.html
                #and related bug: http://bugs.python.org/issue1733757
                t = threadingCurrentThread()
            except:
                frame.f_trace = self.trace_dispatch
                return self.trace_dispatch

            try:
                additionalInfo = t.additionalInfo
            except:
                additionalInfo = t.additionalInfo = PyDBAdditionalThreadInfo()

            if self.force_post_mortem_stop: #If we're in post mortem mode, we might not have another chance to show that info!
                if additionalInfo.pydev_force_stop_at_exception:
                    self.force_post_mortem_stop -= 1
                    frame, frames_byid = additionalInfo.pydev_force_stop_at_exception
                    thread_id = GetThreadId(t)
                    used_id = pydev_vars.additional_frames_container.addAdditionalFrameById(thread_id, frames_byid)
                    try:
                        self.setSuspend(t, CMD_ADD_EXCEPTION_BREAK)
                        self.doWaitSuspend(t, frame, 'exception', None)
                    finally:
                        additionalInfo.pydev_force_stop_at_exception = None
                        pydev_vars.additional_frames_container.removeAdditionalFrameById(thread_id)

            # if thread is not alive, cancel trace_dispatch processing
            if not t.isAlive():
                self.processThreadNotAlive(GetThreadId(t))
                return None # suspend tracing

            if is_file_to_ignore:
                return None

            #each new frame...
            return additionalInfo.CreateDbFrame(self, filename, additionalInfo, t, frame).trace_dispatch(frame, event, arg)

        except SystemExit:
            return None

        except Exception:
            #Log it
            try:
                traceback.print_exc()
            except:
                pass
            return None

    if USE_PSYCO_OPTIMIZATION:
        try:
            import psyco
            trace_dispatch = psyco.proxy(trace_dispatch)
            processNetCommand = psyco.proxy(processNetCommand)
            processInternalCommands = psyco.proxy(processInternalCommands)
            doWaitSuspend = psyco.proxy(doWaitSuspend)
            getInternalQueue = psyco.proxy(getInternalQueue)
        except ImportError:
            if hasattr(sys, 'exc_clear'): #jython does not have it
                sys.exc_clear() #don't keep the traceback (let's keep it clear for when we go to the point of executing client code)

            if not IS_PY3K and not IS_PY27 and not IS_64_BITS and not sys.platform.startswith("java") and not sys.platform.startswith("cli"):
                sys.stderr.write("pydev debugger: warning: psyco not available for speedups (the debugger will still work correctly, but a bit slower)\n")
                sys.stderr.flush()


    def run(self, file, globals=None, locals=None):

        if globals is None:
            #patch provided by: Scott Schlesier - when script is run, it does not 
            #use globals from pydevd:
            #This will prevent the pydevd script from contaminating the namespace for the script to be debugged

            #pretend pydevd is not the main module, and
            #convince the file to be debugged that it was loaded as main
            sys.modules['pydevd'] = sys.modules['__main__']
            sys.modules['pydevd'].__name__ = 'pydevd'

            from imp import new_module
            m = new_module('__main__')
            sys.modules['__main__'] = m
            m.__file__ = file
            globals = m.__dict__

        if locals is None:
            locals = globals

        #Predefined (writable) attributes: __name__ is the module's name;
        #__doc__ is the module's documentation string, or None if unavailable;
        #__file__ is the pathname of the file from which the module was loaded, 
        #if it was loaded from a file. The __file__ attribute is not present for 
        #C modules that are statically linked into the interpreter; for extension modules 
        #loaded dynamically from a shared library, it is the pathname of the shared library file. 


        #I think this is an ugly hack, bug it works (seems to) for the bug that says that sys.path should be the same in
        #debug and run.
        if m.__file__.startswith(sys.path[0]):
            #print >> sys.stderr, 'Deleting: ', sys.path[0]
            del sys.path[0]

        #now, the local directory has to be added to the pythonpath
        #sys.path.insert(0, os.getcwd())
        #Changed: it's not the local directory, but the directory of the file launched
        #The file being run ust be in the pythonpath (even if it was not before)
        sys.path.insert(0, os.path.split(file)[0])

        # for completness, we'll register the pydevd.reader & pydevd.writer threads
        net = NetCommand(str(CMD_THREAD_CREATE), 0, '<xml><thread name="pydevd.reader" id="-1"/></xml>')
        self.writer.addCommand(net)
        net = NetCommand(str(CMD_THREAD_CREATE), 0, '<xml><thread name="pydevd.writer" id="-1"/></xml>')
        self.writer.addCommand(net)

        pydevd_tracing.SetTrace(self.trace_dispatch)
        try:
            #not available in jython!
            threading.settrace(self.trace_dispatch) # for all future threads
        except:
            pass

        try:
            thread.start_new_thread = pydev_start_new_thread
            thread.start_new = pydev_start_new_thread
        except:
            pass

        while not self.readyToRun:
            time.sleep(0.1) # busy wait until we receive run command

        PyDBCommandThread(debugger).start()

        if not IS_PY3K:
            execfile(file, globals, locals) #execute the script
        else:
            stream = open(file)
            try:
                encoding = None
                #Get encoding!
                for i in range(2):
                    line = stream.readline() #Should not raise an exception even if there are no more contents
                    #Must be a comment line
                    if line.strip().startswith('#'):
                        #Don't import re if there's no chance that there's an encoding in the line
                        if 'coding' in line:
                            import re
                            p = re.search(r"coding[:=]\s*([-\w.]+)", line)
                            if p:
                                encoding = p.group(1)
                                break
            finally:
                stream.close()

            if encoding:
                stream = open(file, encoding=encoding)
            else:
                stream = open(file)
            try:
                contents = stream.read()
            finally:
                stream.close()

            exec(compile(contents+"\n", file, 'exec'), globals, locals) #execute the script


def processCommandLine(argv):
    """ parses the arguments.
        removes our arguments from the command line """
    retVal = {}
    retVal['client'] = ''
    retVal['server'] = False
    retVal['port'] = 0
    retVal['file'] = ''
    i = 0
    del argv[0]
    while (i < len(argv)):
        if (argv[i] == '--port'):
            del argv[i]
            retVal['port'] = int(argv[i])
            del argv[i]
        elif (argv[i] == '--vm_type'):
            del argv[i]
            retVal['vm_type'] = argv[i]
            del argv[i]
        elif (argv[i] == '--client'):
            del argv[i]
            retVal['client'] = argv[i]
            del argv[i]
        elif (argv[i] == '--server'):
            del argv[i]
            retVal['server'] = True
        elif (argv[i] == '--file'):
            del argv[i]
            retVal['file'] = argv[i]
            i = len(argv) # pop out, file is our last argument
        elif (argv[i] == '--DEBUG_RECORD_SOCKET_READS'):
            del argv[i]
            retVal['DEBUG_RECORD_SOCKET_READS'] = True
        else:
            raise ValueError("unexpected option " + argv[i])
    return retVal

def usage(doExit=0):
    sys.stdout.write('Usage:\n')
    sys.stdout.write('pydevd.py --port=N [(--client hostname) | --server] --file executable [file_options]\n')
    if doExit:
        sys.exit(0)

def SetTraceForParents(frame, dispatch_func):
    frame = frame.f_back
    while frame:
        if frame.f_trace is None:
            frame.f_trace = dispatch_func

        frame = frame.f_back
    del frame

def exit_hook():
    GetGlobalDebugger().checkOutputRedirect()

def settrace(host='localhost', stdoutToServer=False, stderrToServer=False, port=5678, suspend=True, trace_only_current_thread=False):
    '''Sets the tracing function with the pydev debug function and initializes needed facilities.
    
    @param host: the user may specify another host, if the debug server is not in the same machine
    @param stdoutToServer: when this is true, the stdout is passed to the debug server
    @param stderrToServer: when this is true, the stderr is passed to the debug server
        so that they are printed in its console and not in this process console.
    @param port: specifies which port to use for communicating with the server (note that the server must be started 
        in the same port). @note: currently it's hard-coded at 5678 in the client
    @param suspend: whether a breakpoint should be emulated as soon as this function is called. 
    @param trace_only_current_thread: determines if only the current thread will be traced or all future threads will also have the tracing enabled.
    '''

    global remote
    global connected
    global bufferStdOutToServer
    global bufferStdErrToServer

    remote = True

    if not connected :
        connected = True
        bufferStdOutToServer = stdoutToServer
        bufferStdErrToServer = stderrToServer

        pydevd_vm_type.SetupType()

        debugger = PyDB()
        debugger.connect(host, port)

        net = NetCommand(str(CMD_THREAD_CREATE), 0, '<xml><thread name="pydevd.reader" id="-1"/></xml>')
        debugger.writer.addCommand(net)
        net = NetCommand(str(CMD_THREAD_CREATE), 0, '<xml><thread name="pydevd.writer" id="-1"/></xml>')
        debugger.writer.addCommand(net)

        if bufferStdOutToServer:
            sys.stdoutBuf = pydevd_io.IOBuf()
            sys.stdout = pydevd_io.IORedirector(sys.stdout, sys.stdoutBuf) #@UndefinedVariable

        if bufferStdErrToServer:
            sys.stderrBuf = pydevd_io.IOBuf()
            sys.stderr = pydevd_io.IORedirector(sys.stderr, sys.stderrBuf) #@UndefinedVariable

        SetTraceForParents(GetFrame(), debugger.trace_dispatch)

        t = threadingCurrentThread()
        try:
            additionalInfo = t.additionalInfo
        except AttributeError:
            additionalInfo = PyDBAdditionalThreadInfo()
            t.additionalInfo = additionalInfo

        while not debugger.readyToRun:
            time.sleep(0.1) # busy wait until we receive run command

        if suspend:
            debugger.setSuspend(t, CMD_SET_BREAK)

        #note that we do that through pydevd_tracing.SetTrace so that the tracing
        #is not warned to the user!
        pydevd_tracing.SetTrace(debugger.trace_dispatch)

        if not trace_only_current_thread:
            #Trace future threads?
            try:
                #not available in jython!
                threading.settrace(debugger.trace_dispatch) # for all future threads
            except:
                pass

            try:
                thread.start_new_thread = pydev_start_new_thread
                thread.start_new = pydev_start_new_thread
            except:
                pass

        #sys.exitfunc = exit_hook

        PyDBCommandThread(debugger).start()

    else:
        #ok, we're already in debug mode, with all set, so, let's just set the break
        debugger = GetGlobalDebugger()

        SetTraceForParents(GetFrame(), debugger.trace_dispatch)

        t = threadingCurrentThread()
        try:
            additionalInfo = t.additionalInfo
        except AttributeError:
            additionalInfo = PyDBAdditionalThreadInfo()
            t.additionalInfo = additionalInfo

        pydevd_tracing.SetTrace(debugger.trace_dispatch)

        if not trace_only_current_thread:
            #Trace future threads?
            try:
                #not available in jython!
                threading.settrace(debugger.trace_dispatch) # for all future threads
            except:
                pass

            try:
                thread.start_new_thread = pydev_start_new_thread
                thread.start_new = pydev_start_new_thread
            except:
                pass

        if suspend:
            debugger.setSuspend(t, CMD_SET_BREAK)


if __name__ == '__main__':
    sys.stderr.write("pydev debugger: starting\n")
    # parse the command line. --file is our last argument that is required
    try:
        setup = processCommandLine(sys.argv)
    except ValueError:
        traceback.print_exc()
        usage(1)

    #as to get here all our imports are already resolved, the psyco module can be
    #changed and we'll still get the speedups in the debugger, as those functions 
    #are already compiled at this time.
    try:
        import psyco
    except ImportError:
        if hasattr(sys, 'exc_clear'): #jython does not have it
            sys.exc_clear() #don't keep the traceback -- clients don't want to see it
        pass #that's ok, no need to mock psyco if it's not available anyways
    else:
        #if it's available, let's change it for a stub (pydev already made use of it)
        import pydevd_psyco_stub
        sys.modules['psyco'] = pydevd_psyco_stub


    PydevdLog(2, "Executing file ", setup['file'])
    PydevdLog(2, "arguments:", str(sys.argv))

    pydevd_vm_type.SetupType(setup.get('vm_type', None))

    DebugInfoHolder.DEBUG_RECORD_SOCKET_READS = setup.get('DEBUG_RECORD_SOCKET_READS', False)

    debugger = PyDB()
    debugger.connect(setup['client'], setup['port'])

    connected = True #Mark that we're connected when started from inside eclipse.

    debugger.run(setup['file'], None, None)

