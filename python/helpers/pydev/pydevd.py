#IMPORTANT: pydevd_constants must be the 1st thing defined because it'll keep a reference to the original sys._getframe
from django_debug import DjangoLineBreakpoint
from pydevd_signature import SignatureFactory
from pydevd_frame import add_exception_to_frame
from pydevd_constants import * #@UnusedWildImport
import pydev_imports
from pydevd_breakpoints import * #@UnusedWildImport
import fix_getpass

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
                         CMD_SET_NEXT_STATEMENT,\
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
                         CMD_ADD_DJANGO_EXCEPTION_BREAK, \
                         CMD_REMOVE_DJANGO_EXCEPTION_BREAK, \
                         CMD_SMART_STEP_INTO,\
    InternalChangeVariable, \
                         InternalGetCompletions, \
                         InternalEvaluateExpression, \
                         InternalConsoleExec, \
                         InternalGetFrame, \
                         InternalGetVariable, \
                         InternalTerminateThread, \
                         InternalRunThread, \
                         InternalStepThread, \
                         NetCommand, \
                         NetCommandFactory, \
                         PyDBDaemonThread, \
                         _queue, \
                         ReaderThread, \
                         SetGlobalDebugger, \
                         WriterThread, \
                         PydevdFindThreadById, \
                         PydevdLog, \
                         StartClient, \
                         StartServer, \
                         InternalSetNextStatementThread

from pydevd_file_utils import NormFileToServer, GetFilenameAndBase
import pydevd_file_utils
import pydevd_vars
import traceback
import pydevd_vm_type
import pydevd_tracing
import pydevd_io
import pydev_monkey
from pydevd_additional_thread_info import PyDBAdditionalThreadInfo

if USE_LIB_COPY:
    import _pydev_time as time
    import _pydev_threading as threading
else:
    import time
    import threading

import os


threadingEnumerate = threading.enumerate
threadingCurrentThread = threading.currentThread


DONT_TRACE = {
              #commonly used things from the stdlib that we don't want to trace
              'threading.py':1,
              'Queue.py':1,
              'queue.py':1,
              'socket.py':1,

              #things from pydev that we don't want to trace
              'pydevd_additional_thread_info.py':1,
              'pydevd_comm.py':1,
              'pydevd_constants.py':1,
              'pydevd_exec.py':1,
              'pydevd_exec2.py':1,
              'pydevd_file_utils.py':1,
              'pydevd_frame.py':1,
              'pydevd_io.py':1 ,
              'pydevd_resolver.py':1 ,
              'pydevd_tracing.py':1 ,
              'pydevd_signature.py':1,
              'pydevd_utils.py':1,
              'pydevd_vars.py':1,
              'pydevd_vm_type.py':1,
              'pydevd.py':1 ,
              'pydevd_psyco_stub.py':1,
              '_pydev_execfile.py':1,
              '_pydev_jython_execfile.py':1
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
        for i in range(1, 10):
            time.sleep(0.5) #this one will only start later on (because otherwise we may not have any non-daemon threads
            if self.killReceived:
                return

        if self.dontTraceMe:
            self.pyDb.SetTrace(None) # no debugging on this thread

        try:
            while not self.killReceived:
                try:
                    self.pyDb.processInternalCommands()
                except:
                    PydevdLog(0, 'Finishing debug communication...(2)')
                time.sleep(0.5)
        except:
            pydev_log.debug(sys.exc_info()[0])

            #only got this error in interpreter shutdown
            #PydevdLog(0, 'Finishing debug communication...(3)')


def killAllPydevThreads():
    threads = threadingEnumerate()
    for t in threads:
        if hasattr(t, 'doKillPydevThread'):
            t.doKillPydevThread()
    

#=======================================================================================================================
# PyDBCheckAliveThread
#=======================================================================================================================
class PyDBCheckAliveThread(PyDBDaemonThread):

    def __init__(self, pyDb):
        PyDBDaemonThread.__init__(self)
        self.pyDb = pyDb
        self.setDaemon(False)
        self.setName('pydevd.CheckAliveThread')

    def OnRun(self):
            if self.dontTraceMe:
                self.pyDb.SetTrace(None) # no debugging on this thread
            while not self.killReceived:
                if not self.pyDb.haveAliveThreads():
                    try:
                        pydev_log.debug("No alive threads, finishing debug session")
                        self.pyDb.FinishDebuggingSession()
                        killAllPydevThreads()
                    except:
                        traceback.print_exc()

                    self.stop()
                    self.killReceived = True
                    return

                time.sleep(0.3)

    def doKillPydevThread(self):
        pass

if USE_LIB_COPY:
    import _pydev_thread as thread
else:
    try:
        import thread
    except ImportError:
        import _thread as thread #Py3K changed it.

_original_start_new_thread = thread.start_new_thread

if getattr(thread, '_original_start_new_thread', None) is None:
    thread._original_start_new_thread = thread.start_new_thread

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
        global_debugger.SetTrace(global_debugger.trace_dispatch)
        self.original_func(*self.args, **self.kwargs)

thread.NewThreadStartup = NewThreadStartup

#=======================================================================================================================
# pydev_start_new_thread
#=======================================================================================================================
def _pydev_start_new_thread(function, args, kwargs={}):
    '''
    We need to replace the original thread.start_new_thread with this function so that threads started through
    it and not through the threading module are properly traced.
    '''
    if USE_LIB_COPY:
        import _pydev_thread as thread
    else:
        try:
            import thread
        except ImportError:
            import _thread as thread #Py3K changed it.

    return thread._original_start_new_thread(thread.NewThreadStartup(function, args, kwargs), ())

class PydevStartNewThread(object):
    def __get__(self, obj, type=None):
        return self

    def __call__(self, function, args, kwargs={}):
        return _pydev_start_new_thread(function, args, kwargs)

pydev_start_new_thread = PydevStartNewThread()

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
        self._cmd_queue = {}     # the hash of Queues. Key is thread id, value is thread
        self.breakpoints = {}
        self.django_breakpoints = {}
        self.exception_set = {}
        self.always_exception_set = set()
        self.django_exception_break = {}
        self.readyToRun = False
        self._main_lock = threading.Lock()
        self._lock_running_thread_ids = threading.Lock()
        self._finishDebuggingSession = False
        self._terminationEventSent = False
        self.force_post_mortem_stop = 0
        self.signature_factory = None
        self.SetTrace = pydevd_tracing.SetTrace

        #this is a dict of thread ids pointing to thread ids. Whenever a command is passed to the java end that
        #acknowledges that a thread was created, the thread id should be passed here -- and if at some time we do not
        #find that thread alive anymore, we must remove it from this list and make the java side know that the thread
        #was killed.
        self._running_thread_ids = {}

    def haveAliveThreads(self):
        for t in threadingEnumerate():
            if not isinstance(t, PyDBDaemonThread) and t.isAlive() and not t.isDaemon():
                return True

        return False

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
        """ returns internal command queue for a given thread.
        if new queue is created, notify the RDB about it """
        try:
            return self._cmd_queue[thread_id]
        except KeyError:
            return self._cmd_queue.setdefault(thread_id, _queue.Queue()) #@UndefinedVariable


    def postInternalCommand(self, int_cmd, thread_id):
        """ if thread_id is *, post to all """
        if thread_id == "*":
            for k in self._cmd_queue.keys():
                self._cmd_queue[k].put(int_cmd)

        else:
            queue = self.getInternalQueue(thread_id)
            queue.put(int_cmd)

    def checkOutputRedirect(self):
        global bufferStdOutToServer
        global bufferStdErrToServer

        if bufferStdOutToServer:
                initStdoutRedirect()
                self.checkOutput(sys.stdoutBuf, 1) #@UndefinedVariable

        if bufferStdErrToServer:
                initStderrRedirect()
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
        curr_thread_id = GetThreadId(threadingCurrentThread())
        program_threads_alive = {}
        all_threads = threadingEnumerate()
        program_threads_dead = []


        self._main_lock.acquire()
        try:

            self.checkOutputRedirect()

            self._lock_running_thread_ids.acquire()
            try:
                for t in all_threads:
                    thread_id = GetThreadId(t)

                    if not isinstance(t, PyDBDaemonThread) and t.isAlive():
                        program_threads_alive[thread_id] = t

                        if not DictContains(self._running_thread_ids, thread_id):
                            if not hasattr(t, 'additionalInfo'):
                                #see http://sourceforge.net/tracker/index.php?func=detail&aid=1955428&group_id=85796&atid=577329
                                #Let's create the additional info right away!
                                t.additionalInfo = PyDBAdditionalThreadInfo()
                            self._running_thread_ids[thread_id] = t
                            self.writer.addCommand(self.cmdFactory.makeThreadCreatedMessage(t))


                        queue = self.getInternalQueue(thread_id)
                        cmdsToReadd = []    #some commands must be processed by the thread itself... if that's the case,
                                            #we will re-add the commands to the queue after executing.
                        try:
                            while True:
                                int_cmd = queue.get(False)
                                if int_cmd.canBeExecutedBy(curr_thread_id):
                                    PydevdLog(2, "processing internal command ", str(int_cmd))
                                    int_cmd.doIt(self)
                                else:
                                    PydevdLog(2, "NOT processing internal command ", str(int_cmd))
                                    cmdsToReadd.append(int_cmd)

                        except _queue.Empty: #@UndefinedVariable
                            for int_cmd in cmdsToReadd:
                                queue.put(int_cmd)
                            # this is how we exit


                thread_ids = list(self._running_thread_ids.keys())
                for tId in thread_ids:
                    if not DictContains(program_threads_alive, tId):
                        program_threads_dead.append(tId)
            finally:
                self._lock_running_thread_ids.release()

            for tId in program_threads_dead:
                try:
                    self.processThreadNotAlive(tId)
                except:
                    sys.stderr.write('Error iterating through %s (%s) - %s\n' % (
                        program_threads_alive, program_threads_alive.__class__, dir(program_threads_alive)))
                    raise


            if len(program_threads_alive) == 0:
                self.FinishDebuggingSession()
                for t in all_threads:
                    if hasattr(t, 'doKillPydevThread'):
                        t.doKillPydevThread()

        finally:
            self._main_lock.release()


    def setTracingForUntracedContexts(self):
        #Enable the tracing for existing threads (because there may be frames being executed that
        #are currently untraced).
        threads = threadingEnumerate()
        for t in threads:
            if not t.getName().startswith('pydevd.'):
                #TODO: optimize so that we only actually add that tracing if it's in
                #the new breakpoint context.
                additionalInfo = None
                try:
                    additionalInfo = t.additionalInfo
                except AttributeError:
                    pass #that's ok, no info currently set

                if additionalInfo is not None:
                    for frame in additionalInfo.IterFrames():
                        self.SetTraceForFrameAndParents(frame)
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

        self._main_lock.acquire()
        try:
            try:
                cmd = None
                if cmd_id == CMD_RUN:
                    self.readyToRun = True

                elif cmd_id == CMD_VERSION:
                    # response is version number
                    local_version, pycharm_os = text.split('\t', 1)

                    pydevd_file_utils.set_pycharm_os(pycharm_os)

                    cmd = self.cmdFactory.makeVersionMessage(seq)

                elif cmd_id == CMD_LIST_THREADS:
                    # response is a list of threads
                    cmd = self.cmdFactory.makeListThreadsMessage(seq)

                elif cmd_id == CMD_THREAD_KILL:
                    int_cmd = InternalTerminateThread(text)
                    self.postInternalCommand(int_cmd, text)

                elif cmd_id == CMD_THREAD_SUSPEND:
                    #Yes, thread suspend is still done at this point, not through an internal command!
                    t = PydevdFindThreadById(text)
                    if t:
                        additionalInfo = None
                        try:
                            additionalInfo = t.additionalInfo
                        except AttributeError:
                            pass #that's ok, no info currently set

                        if additionalInfo is not None:
                            for frame in additionalInfo.IterFrames():
                                self.SetTraceForFrameAndParents(frame)
                                del frame

                        self.setSuspend(t, CMD_THREAD_SUSPEND)

                elif cmd_id == CMD_THREAD_RUN:
                    t = PydevdFindThreadById(text)
                    if t:
                        thread_id = GetThreadId(t)
                        int_cmd = InternalRunThread(thread_id)
                        self.postInternalCommand(int_cmd, thread_id)

                elif cmd_id == CMD_STEP_INTO or cmd_id == CMD_STEP_OVER or cmd_id == CMD_STEP_RETURN:
                    #we received some command to make a single step
                    t = PydevdFindThreadById(text)
                    if t:
                        thread_id = GetThreadId(t)
                        int_cmd = InternalStepThread(thread_id, cmd_id)
                        self.postInternalCommand(int_cmd, thread_id)

                elif cmd_id == CMD_RUN_TO_LINE or cmd_id == CMD_SET_NEXT_STATEMENT or cmd_id == CMD_SMART_STEP_INTO:
                    #we received some command to make a single step
                    thread_id, line, func_name = text.split('\t', 2)
                    t = PydevdFindThreadById(thread_id)
                    if t:
                        int_cmd = InternalSetNextStatementThread(thread_id, cmd_id, line, func_name)
                        self.postInternalCommand(int_cmd, thread_id)


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

                    if not pydevd_file_utils.exists(file):
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

                    self.setTracingForUntracedContexts()

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
                        found = False
                        try:
                            if type == 'django-line':
                                del self.django_breakpoints[file][line]
                            elif type == 'python-line':
                                del self.breakpoints[file][line] #remove the breakpoint in that line
                            else:
                                try:
                                    del self.django_breakpoints[file][line]
                                    found = True
                                except:
                                    pass
                                try:
                                    del self.breakpoints[file][line] #remove the breakpoint in that line
                                    found = True
                                except:
                                    pass

                            if DebugInfoHolder.DEBUG_TRACE_BREAKPOINTS > 0:
                                sys.stderr.write('Removed breakpoint:%s - %s\n' % (file, line))
                                sys.stderr.flush()
                        except KeyError:
                            found = False

                        if not found:
                            #ok, it's not there...
                            if DebugInfoHolder.DEBUG_TRACE_BREAKPOINTS > 0:
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
                    exception, notify_always, notify_on_terminate = text.split('\t', 2)

                    eb = ExceptionBreakpoint(exception, notify_always, notify_on_terminate)

                    self.exception_set[exception] = eb

                    if eb.notify_on_terminate:
                        update_exception_hook(self)
                    if DebugInfoHolder.DEBUG_TRACE_BREAKPOINTS > 0:
                        pydev_log.error("Exceptions to hook on terminate: %s\n" % (self.exception_set,))

                    if eb.notify_always:
                        self.always_exception_set.add(exception)
                        if DebugInfoHolder.DEBUG_TRACE_BREAKPOINTS > 0:
                            pydev_log.error("Exceptions to hook always: %s\n" % (self.always_exception_set,))
                        self.setTracingForUntracedContexts()

                elif cmd_id == CMD_REMOVE_EXCEPTION_BREAK:
                    exception = text
                    try:
                        del self.exception_set[exception]
                        self.always_exception_set.remove(exception)
                    except:
                        pass
                    update_exception_hook(self)

                elif cmd_id == CMD_LOAD_SOURCE:
                    path = text
                    try:
                        f = open(path, 'r')
                        source = f.read()
                        self.cmdFactory.makeLoadSourceMessage(seq, source, self)
                    except:
                        return self.cmdFactory.makeErrorMessage(seq, pydevd_tracing.GetExceptionTracebackStr())

                elif cmd_id == CMD_ADD_DJANGO_EXCEPTION_BREAK:
                    exception = text

                    self.django_exception_break[exception] = True
                    self.setTracingForUntracedContexts()

                elif cmd_id == CMD_REMOVE_DJANGO_EXCEPTION_BREAK:
                    exception = text

                    try:
                        del self.django_exception_break[exception]
                    except :
                        pass

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
            self._main_lock.release()

    def processThreadNotAlive(self, threadId):
        """ if thread is not alive, cancel trace_dispatch processing """
        self._lock_running_thread_ids.acquire()
        try:
            thread = self._running_thread_ids.pop(threadId, None)
            if thread is None:
                return

            wasNotified = thread.additionalInfo.pydev_notify_kill
            if not wasNotified:
                thread.additionalInfo.pydev_notify_kill = True

        finally:
            self._lock_running_thread_ids.release()

        cmd = self.cmdFactory.makeThreadKilledMessage(threadId)
        self.writer.addCommand(cmd)


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
            time.sleep(0.01)

        #process any stepping instructions
        if info.pydev_step_cmd == CMD_STEP_INTO:
            info.pydev_step_stop = None
            info.pydev_smart_step_stop = None

        elif info.pydev_step_cmd == CMD_STEP_OVER:
            info.pydev_step_stop = frame
            info.pydev_smart_step_stop = None
            self.SetTraceForFrameAndParents(frame)

        elif info.pydev_step_cmd == CMD_SMART_STEP_INTO:
            self.SetTraceForFrameAndParents(frame)
            info.pydev_step_stop = None
            info.pydev_smart_step_stop = frame

        elif info.pydev_step_cmd == CMD_RUN_TO_LINE or info.pydev_step_cmd == CMD_SET_NEXT_STATEMENT :
            self.SetTraceForFrameAndParents(frame)

            if event == 'line' or event == 'exception':
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
                    else :
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
                self.SetTraceForFrameAndParents(frame)
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


    def handle_post_mortem_stop(self, additionalInfo, t):
        pydev_log.debug("We are stopping in post-mortem\n")
        self.force_post_mortem_stop -= 1
        frame, frames_byid = additionalInfo.pydev_force_stop_at_exception
        thread_id = GetThreadId(t)
        pydevd_vars.addAdditionalFrameById(thread_id, frames_byid)
        try:
            try:
                add_exception_to_frame(frame, additionalInfo.exception)
                self.setSuspend(t, CMD_ADD_EXCEPTION_BREAK)
                self.doWaitSuspend(t, frame, 'exception', None)
            except:
                pydev_log.error("We've got an error while stopping in post-mortem: %s\n"%sys.exc_info()[0])
        finally:
            additionalInfo.pydev_force_stop_at_exception = None
            pydevd_vars.removeAdditionalFrameById(thread_id)

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
            if self._finishDebuggingSession and not self._terminationEventSent:
                #that was not working very well because jython gave some socket errors
                t = threadingCurrentThread()
                try:
                    threads = threadingEnumerate()
                    for t in threads:
                        if hasattr(t, 'doKillPydevThread'):
                            t.doKillPydevThread()
                except:
                    traceback.print_exc()
                self._terminationEventSent = True
                return None

            filename, base = GetFilenameAndBase(frame)

            is_file_to_ignore = DictContains(DONT_TRACE, base) #we don't want to debug threading or anything related to pydevd

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
                if additionalInfo is None:
                    raise AttributeError()
            except:
                t.additionalInfo = PyDBAdditionalThreadInfo()
                additionalInfo = t.additionalInfo

            if additionalInfo is None:
                return None

            if additionalInfo.is_tracing:
                f = frame
                while f is not None:
                    fname, bs = GetFilenameAndBase(f)
                    if bs == 'pydevd_frame.py':
                        if 'trace_dispatch' == f.f_code.co_name:
                            return None  #we don't wan't to trace code invoked from pydevd_frame.trace_dispatch
                    f = f.f_back

            # if thread is not alive, cancel trace_dispatch processing
            if not t.isAlive():
                self.processThreadNotAlive(GetThreadId(t))
                return None # suspend tracing

            if is_file_to_ignore:
                return None

            #each new frame...
            return additionalInfo.CreateDbFrame((self, filename, additionalInfo, t, frame)).trace_dispatch(frame, event, arg)

        except SystemExit:
            return None

        except TypeError:
            return None

        except Exception:
            #Log it
            if traceback is not None:
                #This can actually happen during the interpreter shutdown in Python 2.7
                traceback.print_exc()
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



    def SetTraceForFrameAndParents(self, frame, also_add_to_passed_frame=True, overwrite_prev=False):
        dispatch_func = self.trace_dispatch

        if also_add_to_passed_frame:
            self.update_trace(frame, dispatch_func, overwrite_prev)

        frame = frame.f_back
        while frame:
            self.update_trace(frame, dispatch_func, overwrite_prev)

            frame = frame.f_back
        del frame

    def update_trace(self, frame, dispatch_func, overwrite_prev):
        if frame.f_trace is None:
          frame.f_trace = dispatch_func
        else:
          if overwrite_prev:
              frame.f_trace = dispatch_func
          else:
              try:
                  #If it's the trace_exception, go back to the frame trace dispatch!
                  if frame.f_trace.im_func.__name__ == 'trace_exception':
                      frame.f_trace = frame.f_trace.im_self.trace_dispatch
              except AttributeError:
                  pass
              frame = frame.f_back
        del frame



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
            try:
                globals['__builtins__'] = __builtins__
            except NameError:
                pass #Not there on Jython...

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
        PyDBCheckAliveThread(debugger).start()

        if pydevd_vm_type.GetVmType() == pydevd_vm_type.PydevdVmType.JYTHON and sys.version_info[1] == 5 and sys.version_info[2] >= 3:
            from _pydev_jython_execfile import jython_execfile
            jython_execfile(sys.argv)
        else:
            pydev_imports.execfile(file, globals, locals) #execute the script

    def exiting(self):
        sys.stdout.flush()
        sys.stderr.flush()
        self.checkOutputRedirect()
        cmd = self.cmdFactory.makeExitMessage()
        self.writer.addCommand(cmd)

def set_debug(setup):
    setup['DEBUG_RECORD_SOCKET_READS'] = True
    setup['DEBUG_TRACE_BREAKPOINTS'] = 1
    setup['DEBUG_TRACE_LEVEL'] = 3


def processCommandLine(argv):
    """ parses the arguments.
        removes our arguments from the command line """
    setup = {}
    setup['client'] = ''
    setup['server'] = False
    setup['port'] = 0
    setup['file'] = ''
    setup['multiproc'] = False
    setup['save-signatures'] = False
    i = 0
    del argv[0]
    while (i < len(argv)):
        if (argv[i] == '--port'):
            del argv[i]
            setup['port'] = int(argv[i])
            del argv[i]
        elif (argv[i] == '--vm_type'):
            del argv[i]
            setup['vm_type'] = argv[i]
            del argv[i]
        elif (argv[i] == '--client'):
            del argv[i]
            setup['client'] = argv[i]
            del argv[i]
        elif (argv[i] == '--server'):
            del argv[i]
            setup['server'] = True
        elif (argv[i] == '--file'):
            del argv[i]
            setup['file'] = argv[i]
            i = len(argv) # pop out, file is our last argument
        elif (argv[i] == '--DEBUG_RECORD_SOCKET_READS'):
            del argv[i]
            setup['DEBUG_RECORD_SOCKET_READS'] = True
        elif (argv[i] == '--DEBUG'):
            del argv[i]
            set_debug(setup)
        elif (argv[i] == '--multiproc'):
            del argv[i]
            setup['multiproc'] = True
        elif (argv[i] == '--save-signatures'):
            del argv[i]
            setup['save-signatures'] = True
        else:
            raise ValueError("unexpected option " + argv[i])
    return setup

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
    debugger = GetGlobalDebugger()
    debugger.exiting()

def initStdoutRedirect():
    if not getattr(sys, 'stdoutBuf', None):
        sys.stdoutBuf = pydevd_io.IOBuf()
        sys.stdout = pydevd_io.IORedirector(sys.stdout, sys.stdoutBuf) #@UndefinedVariable

def initStderrRedirect():
    if not getattr(sys, 'stderrBuf', None):
        sys.stderrBuf = pydevd_io.IOBuf()
        sys.stderr = pydevd_io.IORedirector(sys.stderr, sys.stderrBuf) #@UndefinedVariable

def settrace(host='localhost', stdoutToServer=False, stderrToServer=False, port=5678, suspend=True, trace_only_current_thread=False, overwrite_prev_trace=False):
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
    _set_trace_lock.acquire()
    try:
        _locked_settrace(host, stdoutToServer, stderrToServer, port, suspend, trace_only_current_thread, overwrite_prev_trace)
    finally:
        _set_trace_lock.release()

_set_trace_lock = threading.Lock()

def _locked_settrace(host, stdoutToServer, stderrToServer, port, suspend, trace_only_current_thread, overwrite_prev_trace):
    if host is None:
        import pydev_localhost
        host = pydev_localhost.get_localhost()

    global connected
    global bufferStdOutToServer
    global bufferStdErrToServer
    global remote

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
            initStdoutRedirect()

        if bufferStdErrToServer:
            initStderrRedirect()

        debugger.SetTraceForFrameAndParents(GetFrame(), False, overwrite_prev=overwrite_prev_trace)

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

        sys.exitfunc = exit_hook

        PyDBCommandThread(debugger).start()
        PyDBCheckAliveThread(debugger).start()

    else:
        #ok, we're already in debug mode, with all set, so, let's just set the break
        debugger = GetGlobalDebugger()

        debugger.SetTraceForFrameAndParents(GetFrame(), False)

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

def stoptrace():
    global connected
    if connected:
        pydevd_tracing.RestoreSysSetTraceFunc()
        sys.settrace(None)
        try:
            #not available in jython!
            threading.settrace(None) # for all future threads
        except:
            pass
        
        try:
            thread.start_new_thread = _original_start_new_thread
            thread.start_new = _original_start_new_thread
        except:
            pass
    
        debugger = GetGlobalDebugger()
        
        if debugger:
            debugger.trace_dispatch = None
    
            debugger.SetTraceForFrameAndParents(GetFrame(), False)
        
            debugger.exiting()
        
            killAllPydevThreads()  
        
        connected = False

class Dispatcher(object):
    def __init__(self):
        self.port = None

    def connect(self, host, port):
        self.host  = host
        self.port = port
        self.client = StartClient(self.host, self.port)
        self.reader = DispatchReader(self)
        self.reader.dontTraceMe = False #we run reader in the same thread so we don't want to loose tracing
        self.reader.run()

    def close(self):
        try:
            self.reader.doKillPydevThread()
        except :
            pass

class DispatchReader(ReaderThread):
    def __init__(self, dispatcher):
        self.dispatcher = dispatcher
        ReaderThread.__init__(self, self.dispatcher.client)

    def handleExcept(self):
        ReaderThread.handleExcept(self)

    def processCommand(self, cmd_id, seq, text):
        if cmd_id == 99:
            self.dispatcher.port = int(text)
            self.killReceived = True


def dispatch():
    argv = sys.original_argv[:]
    setup = processCommandLine(argv)
    host = setup['client']
    port = setup['port']
    dispatcher = Dispatcher()
    try:
        dispatcher.connect(host, port)
        port = dispatcher.port
    finally:
        dispatcher.close()
    return host, port


def settrace_forked():
    host, port = dispatch()

    import pydevd_tracing
    pydevd_tracing.RestoreSysSetTraceFunc()

    if port is not None:
        global connected
        connected = False
        settrace(host, port=port, suspend=False, overwrite_prev_trace=True)
#=======================================================================================================================
# main
#=======================================================================================================================
if __name__ == '__main__':
    # parse the command line. --file is our last argument that is required
    try:
        sys.original_argv = sys.argv[:]
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

    fix_getpass.fixGetpass()


    pydev_log.debug("Executing file %s" % setup['file'])
    pydev_log.debug("arguments: %s"% str(sys.argv))


    pydevd_vm_type.SetupType(setup.get('vm_type', None))

    if os.getenv('PYCHARM_DEBUG'):
        set_debug(setup)

    DebugInfoHolder.DEBUG_RECORD_SOCKET_READS = setup.get('DEBUG_RECORD_SOCKET_READS', False)
    DebugInfoHolder.DEBUG_TRACE_BREAKPOINTS = setup.get('DEBUG_TRACE_BREAKPOINTS', -1)
    DebugInfoHolder.DEBUG_TRACE_LEVEL = setup.get('DEBUG_TRACE_LEVEL', -1)

    port = setup['port']
    host = setup['client']

    if setup['multiproc']:
        pydev_log.debug("Started in multiproc mode\n")

        dispatcher = Dispatcher()
        try:
            dispatcher.connect(host, port)
            if dispatcher.port is not None:
                port = dispatcher.port
                pydev_log.debug("Received port %d\n" %port)
                pydev_log.info("pydev debugger: process %d is connecting\n"% os.getpid())

                try:
                    pydev_monkey.patch_new_process_functions()
                except:
                    pydev_log.error("Error patching process functions\n")
                    traceback.print_exc()
            else:
                pydev_log.error("pydev debugger: couldn't get port for new debug process\n")
        finally:
            dispatcher.close()
    else:
        pydev_log.info("pydev debugger: starting\n")

        try:
            pydev_monkey.patch_new_process_functions_with_warning()
        except:
            pydev_log.error("Error patching process functions\n")
            traceback.print_exc()


    debugger = PyDB()

    if setup['save-signatures']:
        if pydevd_vm_type.GetVmType() == pydevd_vm_type.PydevdVmType.JYTHON:
            sys.stderr.write("Collecting run-time type information is not supported for Jython\n")
        else:
            debugger.signature_factory = SignatureFactory()

    debugger.connect(host, port)

    connected = True #Mark that we're connected when started from inside ide.

    debugger.run(setup['file'], None, None)
