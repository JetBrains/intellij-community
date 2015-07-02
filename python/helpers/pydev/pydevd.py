#IMPORTANT: pydevd_constants must be the 1st thing defined because it'll keep a reference to the original sys._getframe
from __future__ import nested_scopes # Jython 2.1 support

import pydev_monkey_qt
from pydevd_utils import save_main_module

pydev_monkey_qt.patch_qt()

import traceback

from pydevd_frame_utils import add_exception_to_frame
import pydev_imports
from pydevd_breakpoints import * #@UnusedWildImport
import fix_getpass
from pydevd_comm import  CMD_CHANGE_VARIABLE, \
                         CMD_EVALUATE_EXPRESSION, \
                         CMD_EXEC_EXPRESSION, \
                         CMD_GET_COMPLETIONS, \
                         CMD_GET_FRAME, \
                         CMD_GET_VARIABLE, \
                         CMD_GET_ARRAY, \
                         CMD_LIST_THREADS, \
                         CMD_REMOVE_BREAK, \
                         CMD_RUN, \
                         CMD_SET_BREAK, \
                         CMD_SET_NEXT_STATEMENT,\
                         CMD_STEP_INTO, \
                         CMD_STEP_OVER, \
                         CMD_STEP_RETURN, \
                         CMD_STEP_INTO_MY_CODE, \
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
                         InternalGetArray, \
                         InternalTerminateThread, \
                         InternalRunThread, \
                         InternalStepThread, \
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
                         InternalSetNextStatementThread, \
                         ReloadCodeCommand, \
    CMD_SET_PY_EXCEPTION, \
                         CMD_IGNORE_THROWN_EXCEPTION_AT,\
                         InternalGetBreakpointException, \
                         InternalSendCurrExceptionTrace,\
                         InternalSendCurrExceptionTraceProceeded,\
                         CMD_ENABLE_DONT_TRACE, \
                         CMD_GET_FILE_CONTENTS,\
                         CMD_SET_PROPERTY_TRACE, CMD_RUN_CUSTOM_OPERATION,\
                         InternalRunCustomOperation, CMD_EVALUATE_CONSOLE_EXPRESSION, InternalEvaluateConsoleExpression,\
                         InternalConsoleGetCompletions

from pydevd_file_utils import NormFileToServer, GetFilenameAndBase
import pydevd_file_utils
import pydevd_vars
import pydevd_vm_type
import pydevd_tracing
import pydevd_io
from pydevd_additional_thread_info import PyDBAdditionalThreadInfo
from pydevd_custom_frames import CustomFramesContainer, CustomFramesContainerInit
import pydevd_dont_trace
import pydevd_traceproperty

from _pydev_imps import _pydev_time as time, _pydev_thread

import _pydev_threading as threading

import os
import atexit

SUPPORT_PLUGINS = not IS_JYTH_LESS25
PluginManager = None
if SUPPORT_PLUGINS:
    from pydevd_plugin_utils import PluginManager

if IS_PY3K:
    import pkgutil
else:
    from _pydev_imps import _pydev_pkgutil_old as pkgutil

threadingEnumerate = threading.enumerate
threadingCurrentThread = threading.currentThread

try:
    'dummy'.encode('utf-8') # Added because otherwise Jython 2.2.1 wasn't finding the encoding (if it wasn't loaded in the main thread).
except:
    pass

LIB_FILE = 0
PYDEV_FILE = 1

DONT_TRACE = {
              # commonly used things from the stdlib that we don't want to trace
              'Queue.py':LIB_FILE,
              'queue.py':LIB_FILE,
              'socket.py':LIB_FILE,
              'weakref.py':LIB_FILE,
              '_weakrefset.py':LIB_FILE,
              'linecache.py':LIB_FILE,
              'threading.py':LIB_FILE,

              # thirs party libs that we don't want to trace
              '_pydev_pluginbase.py':PYDEV_FILE,
              '_pydev_pkgutil_old.py':PYDEV_FILE,
              '_pydev_uuid_old.py':PYDEV_FILE,

              #things from pydev that we don't want to trace
              '_pydev_execfile.py':PYDEV_FILE,
              '_pydev_jython_execfile.py':PYDEV_FILE,
              '_pydev_threading':PYDEV_FILE,
              '_pydev_Queue':PYDEV_FILE,
              'django_debug.py':PYDEV_FILE,
              'jinja2_debug.py':PYDEV_FILE,
              'pydev_log.py':PYDEV_FILE,
              'pydev_monkey.py':PYDEV_FILE,
              'pydev_monkey_qt.py':PYDEV_FILE,
              'pydevd.py':PYDEV_FILE,
              'pydevd_additional_thread_info.py':PYDEV_FILE,
              'pydevd_breakpoints.py':PYDEV_FILE,
              'pydevd_comm.py':PYDEV_FILE,
              'pydevd_console.py':PYDEV_FILE,
              'pydevd_constants.py':PYDEV_FILE,
              'pydevd_custom_frames.py':PYDEV_FILE,
              'pydevd_dont_trace.py':PYDEV_FILE,
              'pydevd_exec.py':PYDEV_FILE,
              'pydevd_exec2.py':PYDEV_FILE,
              'pydevd_file_utils.py':PYDEV_FILE,
              'pydevd_frame.py':PYDEV_FILE,
              'pydevd_import_class.py':PYDEV_FILE,
              'pydevd_io.py':PYDEV_FILE,
              'pydevd_psyco_stub.py':PYDEV_FILE,
              'pydevd_referrers.py':PYDEV_FILE,
              'pydevd_reload.py':PYDEV_FILE,
              'pydevd_resolver.py':PYDEV_FILE,
              'pydevd_save_locals.py':PYDEV_FILE,
              'pydevd_signature.py':PYDEV_FILE,
              'pydevd_stackless.py':PYDEV_FILE,
              'pydevd_traceproperty.py':PYDEV_FILE,
              'pydevd_tracing.py':PYDEV_FILE,
              'pydevd_utils.py':PYDEV_FILE,
              'pydevd_vars.py':PYDEV_FILE,
              'pydevd_vm_type.py':PYDEV_FILE,
              'pydevd_xml.py':PYDEV_FILE,
            }

if IS_PY3K:
    # if we try to trace io.py it seems it can get halted (see http://bugs.python.org/issue4716)
    DONT_TRACE['io.py'] = LIB_FILE

    # Don't trace common encodings too
    DONT_TRACE['cp1252.py'] = LIB_FILE
    DONT_TRACE['utf_8.py'] = LIB_FILE


connected = False
bufferStdOutToServer = False
bufferStdErrToServer = False
remote = False

from _pydev_filesystem_encoding import getfilesystemencoding
file_system_encoding = getfilesystemencoding()


# Hack for https://sw-brainwy.rhcloud.com/tracker/PyDev/363 (i.e.: calling isAlive() can throw AssertionError under some circumstances)
# It is required to debug threads started by start_new_thread in Python 3.4
_temp = threading.Thread()
if hasattr(_temp, '_is_stopped'): # Python 3.4 has this
    def isThreadAlive(t):
        try:
            return not t._is_stopped
        except:
            return t.isAlive()
    
elif hasattr(_temp, '_Thread__stopped'): # Python 2.7 has this
    def isThreadAlive(t):
        try:
            return not t._Thread__stopped
        except:
            return t.isAlive()
    
else: # Haven't checked all other versions, so, let's use the regular isAlive call in this case.
    def isThreadAlive(t):
        return t.isAlive()
del _temp

#=======================================================================================================================
# PyDBCommandThread
#=======================================================================================================================
class PyDBCommandThread(PyDBDaemonThread):

    def __init__(self, pyDb):
        PyDBDaemonThread.__init__(self)
        self._py_db_command_thread_event = pyDb._py_db_command_thread_event
        self.pyDb = pyDb
        self.setName('pydevd.CommandThread')

    def OnRun(self):
        for i in xrange(1, 10):
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
                self._py_db_command_thread_event.clear()
                self._py_db_command_thread_event.wait(0.5)
        except:
            pydev_log.debug(sys.exc_info()[0])

            #only got this error in interpreter shutdown
            #PydevdLog(0, 'Finishing debug communication...(3)')


def killAllPydevThreads():
    threads = DictKeys(PyDBDaemonThread.created_pydb_daemon_threads)
    for t in threads:
        if hasattr(t, 'doKillPydevThread'):
            t.doKillPydevThread()


#=======================================================================================================================
# CheckOutputThread
# Non-daemonic thread guaranties that all data is written even if program is finished
#=======================================================================================================================
class CheckOutputThread(PyDBDaemonThread):

    def __init__(self, pyDb):
        PyDBDaemonThread.__init__(self)
        self.pyDb = pyDb
        self.setName('pydevd.CheckAliveThread')
        self.daemon = False
        pyDb.output_checker = self

    def OnRun(self):
        if self.dontTraceMe:

            disable_tracing = True

            if pydevd_vm_type.GetVmType() == pydevd_vm_type.PydevdVmType.JYTHON and sys.hexversion <= 0x020201f0:
                # don't run untraced threads if we're in jython 2.2.1 or lower
                # jython bug: if we start a thread and another thread changes the tracing facility
                # it affects other threads (it's not set only for the thread but globally)
                # Bug: http://sourceforge.net/tracker/index.php?func=detail&aid=1870039&group_id=12867&atid=112867
                disable_tracing = False

            if disable_tracing:
                pydevd_tracing.SetTrace(None)  # no debugging on this thread

        while not self.killReceived:
            time.sleep(0.3)
            if not self.pyDb.haveAliveThreads() and self.pyDb.writer.empty() \
                    and not has_data_to_redirect():
                try:
                    pydev_log.debug("No alive threads, finishing debug session")
                    self.pyDb.FinishDebuggingSession()
                    killAllPydevThreads()
                except:
                    traceback.print_exc()

                self.killReceived = True

            self.pyDb.checkOutputRedirect()


    def doKillPydevThread(self):
        self.killReceived = True



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


    def __init__(self):
        SetGlobalDebugger(self)
        pydevd_tracing.ReplaceSysSetTraceFunc()
        self.reader = None
        self.writer = None
        self.output_checker = None
        self.quitting = None
        self.cmdFactory = NetCommandFactory()
        self._cmd_queue = {}  # the hash of Queues. Key is thread id, value is thread

        self.breakpoints = {}

        self.file_to_id_to_line_breakpoint = {}
        self.file_to_id_to_plugin_breakpoint = {}

        # Note: breakpoints dict should not be mutated: a copy should be created
        # and later it should be assigned back (to prevent concurrency issues).
        self.break_on_uncaught_exceptions = {}
        self.break_on_caught_exceptions = {}

        self.readyToRun = False
        self._main_lock = _pydev_thread.allocate_lock()
        self._lock_running_thread_ids = _pydev_thread.allocate_lock()
        self._py_db_command_thread_event = threading.Event()
        CustomFramesContainer._py_db_command_thread_event = self._py_db_command_thread_event
        self._finishDebuggingSession = False
        self._terminationEventSent = False
        self.signature_factory = None
        self.SetTrace = pydevd_tracing.SetTrace
        self.break_on_exceptions_thrown_in_same_context = False
        self.ignore_exceptions_thrown_in_lines_with_ignore_exception = True
        self.project_roots = None

        # Suspend debugger even if breakpoint condition raises an exception
        SUSPEND_ON_BREAKPOINT_EXCEPTION = True
        self.suspend_on_breakpoint_exception = SUSPEND_ON_BREAKPOINT_EXCEPTION

        # By default user can step into properties getter/setter/deleter methods
        self.disable_property_trace = False
        self.disable_property_getter_trace = False
        self.disable_property_setter_trace = False
        self.disable_property_deleter_trace = False

        #this is a dict of thread ids pointing to thread ids. Whenever a command is passed to the java end that
        #acknowledges that a thread was created, the thread id should be passed here -- and if at some time we do not
        #find that thread alive anymore, we must remove it from this list and make the java side know that the thread
        #was killed.
        self._running_thread_ids = {}
        self._set_breakpoints_with_id = False

        # This attribute holds the file-> lines which have an @IgnoreException.
        self.filename_to_lines_where_exceptions_are_ignored = {}

        #working with plugins (lazily initialized)
        self.plugin = None
        self.has_plugin_line_breaks = False
        self.has_plugin_exception_breaks = False

        # matplotlib support in debugger and debug console
        self.mpl_in_use = False
        self.mpl_hooks_in_debug_console = False
        self.mpl_modules_for_patching = {}
        
    def get_plugin_lazy_init(self):
        if self.plugin is None and SUPPORT_PLUGINS:
            self.plugin = PluginManager(self)
        return self.plugin

    def get_project_roots(self):
        if self.project_roots is None:
            roots = os.getenv('IDE_PROJECT_ROOTS', '').split(os.pathsep)
            pydev_log.debug("IDE_PROJECT_ROOTS %s\n" % roots)
            self.project_roots = roots

    def not_in_scope(self, filename):
        self.get_project_roots()
        filename = os.path.normcase(filename)
        for root in self.project_roots:
            root = os.path.normcase(root)
            if filename.startswith(root):
                return False
        return True

    def first_appearance_in_scope(self, trace):
        if trace is None or self.not_in_scope(trace.tb_frame.f_code.co_filename):
            return False
        else:
            trace = trace.tb_next
            while trace is not None:
                frame = trace.tb_frame
                if not self.not_in_scope(frame.f_code.co_filename):
                    return False
                trace = trace.tb_next
            return True

    def haveAliveThreads(self):
        for t in threadingEnumerate():
            if getattr(t, 'is_pydev_daemon_thread', False):
                #Important: Jython 2.5rc4 has a bug where a thread created with thread.start_new_thread won't be
                #set as a daemon thread, so, we also have to check for the 'is_pydev_daemon_thread' flag.
                #See: https://github.com/fabioz/PyDev.Debugger/issues/11
                continue

            if isinstance(t, PyDBDaemonThread):
                pydev_log.error_once(
                    'Error in debugger: Found PyDBDaemonThread not marked with is_pydev_daemon_thread=True.\n')

            if isThreadAlive(t):
                if not t.isDaemon() or hasattr(t, "__pydevd_main_thread"):
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
            sock.settimeout(None)  # infinite, no timeouts from now on - jython does not have it
        except:
            pass
        self.writer = WriterThread(sock)
        self.reader = ReaderThread(sock)
        self.writer.start()
        self.reader.start()

        time.sleep(0.1)  # give threads time to start

    def connect(self, host, port):
        if host:
            s = StartClient(host, port)
        else:
            s = StartServer(port)

        self.initializeNetwork(s)


    def getInternalQueue(self, thread_id):
        """ returns internal command queue for a given thread.
        if new queue is created, notify the RDB about it """
        if thread_id.startswith('__frame__'):
            thread_id = thread_id[thread_id.rfind('|') + 1:]
        try:
            return self._cmd_queue[thread_id]
        except KeyError:
            return self._cmd_queue.setdefault(thread_id, _queue.Queue()) #@UndefinedVariable


    def postInternalCommand(self, int_cmd, thread_id):
        """ if thread_id is *, post to all """
        if thread_id == "*":
            threads = threadingEnumerate()
            for t in threads:
                thread_id = GetThreadId(t)
                queue = self.getInternalQueue(thread_id)
                queue.put(int_cmd)

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


    def init_matplotlib_in_debug_console(self):
        # import hook and patches for matplotlib support in debug console
        from pydev_import_hook import import_hook_manager
        for module in DictKeys(self.mpl_modules_for_patching):
            import_hook_manager.add_module_name(module, DictPop(self.mpl_modules_for_patching, module))

    def init_matplotlib_support(self):
        # prepare debugger for integration with matplotlib GUI event loop
        from pydev_ipython.matplotlibtools import activate_matplotlib, activate_pylab, activate_pyplot, do_enable_gui
        # enable_gui_function in activate_matplotlib should be called in main thread. Unlike integrated console,
        # in the debug console we have no interpreter instance with exec_queue, but we run this code in the main
        # thread and can call it directly.
        class _MatplotlibHelper:
            _return_control_osc = False

        def return_control():
            # Some of the input hooks (e.g. Qt4Agg) check return control without doing
            # a single operation, so we don't return True on every
            # call when the debug hook is in place to allow the GUI to run
            _MatplotlibHelper._return_control_osc = not _MatplotlibHelper._return_control_osc
            return _MatplotlibHelper._return_control_osc

        from pydev_ipython.inputhook import set_return_control_callback
        set_return_control_callback(return_control)

        self.mpl_modules_for_patching = {"matplotlib": lambda: activate_matplotlib(do_enable_gui),
                            "matplotlib.pyplot": activate_pyplot,
                            "pylab": activate_pylab }


    def processInternalCommands(self):
        '''This function processes internal commands
        '''
        self._main_lock.acquire()
        try:

            self.checkOutputRedirect()

            curr_thread_id = GetThreadId(threadingCurrentThread())
            program_threads_alive = {}
            all_threads = threadingEnumerate()
            program_threads_dead = []
            self._lock_running_thread_ids.acquire()
            try:
                for t in all_threads:
                    thread_id = GetThreadId(t)

                    if getattr(t, 'is_pydev_daemon_thread', False):
                        pass # I.e.: skip the DummyThreads created from pydev daemon threads
                    elif isinstance(t, PyDBDaemonThread):
                        pydev_log.error_once('Error in debugger: Found PyDBDaemonThread not marked with is_pydev_daemon_thread=True.\n')

                    elif isThreadAlive(t):
                        program_threads_alive[thread_id] = t

                        if not DictContains(self._running_thread_ids, thread_id):
                            if not hasattr(t, 'additionalInfo'):
                                # see http://sourceforge.net/tracker/index.php?func=detail&aid=1955428&group_id=85796&atid=577329
                                # Let's create the additional info right away!
                                t.additionalInfo = PyDBAdditionalThreadInfo()
                            self._running_thread_ids[thread_id] = t
                            self.writer.addCommand(self.cmdFactory.makeThreadCreatedMessage(t))


                        queue = self.getInternalQueue(thread_id)
                        cmdsToReadd = []  # some commands must be processed by the thread itself... if that's the case,
                                            # we will re-add the commands to the queue after executing.
                        try:
                            while True:
                                int_cmd = queue.get(False)

                                if not self.mpl_hooks_in_debug_console and isinstance(int_cmd, InternalConsoleExec):
                                    # add import hooks for matplotlib patches if only debug console was started
                                    try:
                                        self.init_matplotlib_in_debug_console()
                                        self.mpl_in_use = True
                                    except:
                                        PydevdLog(2, "Matplotlib support in debug console failed", traceback.format_exc())
                                    finally:
                                        self.mpl_hooks_in_debug_console = True

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


    def setTracingForUntracedContexts(self, ignore_frame=None, overwrite_prev_trace=False):
        # Enable the tracing for existing threads (because there may be frames being executed that
        # are currently untraced).
        threads = threadingEnumerate()
        try:
            for t in threads:
                # TODO: optimize so that we only actually add that tracing if it's in
                # the new breakpoint context.
                additionalInfo = None
                try:
                    additionalInfo = t.additionalInfo
                except AttributeError:
                    pass  # that's ok, no info currently set

                if additionalInfo is not None:
                    for frame in additionalInfo.IterFrames():
                        if frame is not ignore_frame:
                            self.SetTraceForFrameAndParents(frame, overwrite_prev_trace=overwrite_prev_trace)
        finally:
            frame = None
            t = None
            threads = None
            additionalInfo = None


    def consolidate_breakpoints(self, file, id_to_breakpoint, breakpoints):
        break_dict = {}
        for breakpoint_id, pybreakpoint in DictIterItems(id_to_breakpoint):
            break_dict[pybreakpoint.line] = pybreakpoint

        breakpoints[file] = break_dict

    def add_break_on_exception(
        self,
        exception,
        notify_always,
        notify_on_terminate,
        notify_on_first_raise_only,
        ignore_libraries=False
        ):
        try:
            eb = ExceptionBreakpoint(
                exception,
                notify_always,
                notify_on_terminate,
                notify_on_first_raise_only,
                ignore_libraries
            )
        except ImportError:
            pydev_log.error("Error unable to add break on exception for: %s (exception could not be imported)\n" % (exception,))
            return None

        if eb.notify_on_terminate:
            cp = self.break_on_uncaught_exceptions.copy()
            cp[exception] = eb
            if DebugInfoHolder.DEBUG_TRACE_BREAKPOINTS > 0:
                pydev_log.error("Exceptions to hook on terminate: %s\n" % (cp,))
            self.break_on_uncaught_exceptions = cp

        if eb.notify_always:
            cp = self.break_on_caught_exceptions.copy()
            cp[exception] = eb
            if DebugInfoHolder.DEBUG_TRACE_BREAKPOINTS > 0:
                pydev_log.error("Exceptions to hook always: %s\n" % (cp,))
            self.break_on_caught_exceptions = cp

        return eb

    def update_after_exceptions_added(self, added):
        updated_on_caught = False
        updated_on_uncaught = False

        for eb in added:
            if not updated_on_uncaught and eb.notify_on_terminate:
                updated_on_uncaught = True
                update_exception_hook(self)

            if not updated_on_caught and eb.notify_always:
                updated_on_caught = True
                self.setTracingForUntracedContexts()


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
        #print(ID_TO_MEANING[str(cmd_id)], repr(text))

        self._main_lock.acquire()
        try:
            try:
                cmd = None
                if cmd_id == CMD_RUN:
                    self.readyToRun = True

                elif cmd_id == CMD_VERSION:
                    # response is version number
                    # ide_os should be 'WINDOWS' or 'UNIX'.
                    ide_os = 'WINDOWS'

                    # Breakpoints can be grouped by 'LINE' or by 'ID'.
                    breakpoints_by = 'LINE'

                    splitted = text.split('\t')
                    if len(splitted) == 1:
                        _local_version = splitted

                    elif len(splitted) == 2:
                        _local_version, ide_os = splitted

                    elif len(splitted) == 3:
                        _local_version, ide_os, breakpoints_by = splitted

                    if breakpoints_by == 'ID':
                        self._set_breakpoints_with_id = True
                    else:
                        self._set_breakpoints_with_id = False

                    pydevd_file_utils.set_ide_os(ide_os)

                    cmd = self.cmdFactory.makeVersionMessage(seq)

                elif cmd_id == CMD_LIST_THREADS:
                    # response is a list of threads
                    cmd = self.cmdFactory.makeListThreadsMessage(seq)

                elif cmd_id == CMD_THREAD_KILL:
                    int_cmd = InternalTerminateThread(text)
                    self.postInternalCommand(int_cmd, text)

                elif cmd_id == CMD_THREAD_SUSPEND:
                    # Yes, thread suspend is still done at this point, not through an internal command!
                    t = PydevdFindThreadById(text)
                    if t:
                        additionalInfo = None
                        try:
                            additionalInfo = t.additionalInfo
                        except AttributeError:
                            pass  # that's ok, no info currently set

                        if additionalInfo is not None:
                            for frame in additionalInfo.IterFrames():
                                self.SetTraceForFrameAndParents(frame)
                                del frame

                        self.setSuspend(t, CMD_THREAD_SUSPEND)
                    elif text.startswith('__frame__:'):
                        sys.stderr.write("Can't suspend tasklet: %s\n" % (text,))

                elif cmd_id == CMD_THREAD_RUN:
                    t = PydevdFindThreadById(text)
                    if t:
                        thread_id = GetThreadId(t)
                        int_cmd = InternalRunThread(thread_id)
                        self.postInternalCommand(int_cmd, thread_id)

                    elif text.startswith('__frame__:'):
                        sys.stderr.write("Can't make tasklet run: %s\n" % (text,))


                elif cmd_id == CMD_STEP_INTO or cmd_id == CMD_STEP_OVER or cmd_id == CMD_STEP_RETURN or \
                        cmd_id == CMD_STEP_INTO_MY_CODE:
                    # we received some command to make a single step
                    t = PydevdFindThreadById(text)
                    if t:
                        thread_id = GetThreadId(t)
                        int_cmd = InternalStepThread(thread_id, cmd_id)
                        self.postInternalCommand(int_cmd, thread_id)

                    elif text.startswith('__frame__:'):
                        sys.stderr.write("Can't make tasklet step command: %s\n" % (text,))


                elif cmd_id == CMD_RUN_TO_LINE or cmd_id == CMD_SET_NEXT_STATEMENT or cmd_id == CMD_SMART_STEP_INTO:
                    # we received some command to make a single step
                    thread_id, line, func_name = text.split('\t', 2)
                    t = PydevdFindThreadById(thread_id)
                    if t:
                        int_cmd = InternalSetNextStatementThread(thread_id, cmd_id, line, func_name)
                        self.postInternalCommand(int_cmd, thread_id)
                    elif thread_id.startswith('__frame__:'):
                        sys.stderr.write("Can't set next statement in tasklet: %s\n" % (thread_id,))


                elif cmd_id == CMD_RELOAD_CODE:
                    # we received some command to make a reload of a module
                    module_name = text.strip()

                    thread_id = '*'  # Any thread

                    # Note: not going for the main thread because in this case it'd only do the load
                    # when we stopped on a breakpoint.
                    # for tid, t in self._running_thread_ids.items(): #Iterate in copy
                    #    thread_name = t.getName()
                    #
                    #    print thread_name, GetThreadId(t)
                    #    #Note: if possible, try to reload on the main thread
                    #    if thread_name == 'MainThread':
                    #        thread_id = tid

                    int_cmd = ReloadCodeCommand(module_name, thread_id)
                    self.postInternalCommand(int_cmd, thread_id)


                elif cmd_id == CMD_CHANGE_VARIABLE:
                    # the text is: thread\tstackframe\tFRAME|GLOBAL\tattribute_to_change\tvalue_to_change
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
                    # we received some command to get a variable
                    # the text is: thread_id\tframe_id\tFRAME|GLOBAL\tattributes*
                    try:
                        thread_id, frame_id, scopeattrs = text.split('\t', 2)

                        if scopeattrs.find('\t') != -1:  # there are attributes beyond scope
                            scope, attrs = scopeattrs.split('\t', 1)
                        else:
                            scope, attrs = (scopeattrs, None)

                        int_cmd = InternalGetVariable(seq, thread_id, frame_id, scope, attrs)
                        self.postInternalCommand(int_cmd, thread_id)

                    except:
                        traceback.print_exc()

                elif cmd_id == CMD_GET_ARRAY:
                    # we received some command to get an array variable
                    # the text is: thread_id\tframe_id\tFRAME|GLOBAL\tname\ttemp\troffs\tcoffs\trows\tcols\tformat
                    try:
                        roffset, coffset, rows, cols, format, thread_id, frame_id, scopeattrs  = text.split('\t', 7)

                        if scopeattrs.find('\t') != -1:  # there are attributes beyond scope
                            scope, attrs = scopeattrs.split('\t', 1)
                        else:
                            scope, attrs = (scopeattrs, None)

                        int_cmd = InternalGetArray(seq, roffset, coffset, rows, cols, format, thread_id, frame_id, scope, attrs)
                        self.postInternalCommand(int_cmd, thread_id)

                    except:
                        traceback.print_exc()

                elif cmd_id == CMD_GET_COMPLETIONS:
                    # we received some command to get a variable
                    # the text is: thread_id\tframe_id\tactivation token
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
                    # func name: 'None': match anything. Empty: match global, specified: only method context.
                    # command to add some breakpoint.
                    # text is file\tline. Add to breakpoints dictionary
                    if self._set_breakpoints_with_id:
                        breakpoint_id, type, file, line, func_name, condition, expression = text.split('\t', 6)

                        breakpoint_id = int(breakpoint_id)
                        line = int(line)

                        # We must restore new lines and tabs as done in
                        # AbstractDebugTarget.breakpointAdded
                        condition = condition.replace("@_@NEW_LINE_CHAR@_@", '\n').\
                            replace("@_@TAB_CHAR@_@", '\t').strip()

                        expression = expression.replace("@_@NEW_LINE_CHAR@_@", '\n').\
                            replace("@_@TAB_CHAR@_@", '\t').strip()
                    else:
                        #Note: this else should be removed after PyCharm migrates to setting
                        #breakpoints by id (and ideally also provides func_name).
                        type, file, line, func_name, condition, expression = text.split('\t', 5)
                        # If we don't have an id given for each breakpoint, consider
                        # the id to be the line.
                        breakpoint_id = line = int(line)

                        condition = condition.replace("@_@NEW_LINE_CHAR@_@", '\n'). \
                            replace("@_@TAB_CHAR@_@", '\t').strip()

                        expression = expression.replace("@_@NEW_LINE_CHAR@_@", '\n'). \
                            replace("@_@TAB_CHAR@_@", '\t').strip()

                    if not IS_PY3K:  # In Python 3, the frame object will have unicode for the file, whereas on python 2 it has a byte-array encoded with the filesystem encoding.
                        file = file.encode(file_system_encoding)

                    file = NormFileToServer(file)

                    if not pydevd_file_utils.exists(file):
                        sys.stderr.write('pydev debugger: warning: trying to add breakpoint'\
                            ' to file that does not exist: %s (will have no effect)\n' % (file,))
                        sys.stderr.flush()


                    if len(condition) <= 0 or condition is None or condition == "None":
                        condition = None

                    if len(expression) <= 0 or expression is None or expression == "None":
                        expression = None

                    supported_type = False
                    if type == 'python-line':
                        breakpoint = LineBreakpoint(line, condition, func_name, expression)
                        breakpoints = self.breakpoints
                        file_to_id_to_breakpoint = self.file_to_id_to_line_breakpoint
                        supported_type = True
                    else:
                        result = None
                        plugin = self.get_plugin_lazy_init()
                        if plugin is not None:
                            result = plugin.add_breakpoint('add_line_breakpoint', self, type, file, line, condition, expression, func_name)
                        if result is not None:
                            supported_type = True
                            breakpoint, breakpoints = result
                            file_to_id_to_breakpoint = self.file_to_id_to_plugin_breakpoint
                        else:
                            supported_type = False

                    if not supported_type:
                        raise NameError(type)

                    if DebugInfoHolder.DEBUG_TRACE_BREAKPOINTS > 0:
                        pydev_log.debug('Added breakpoint:%s - line:%s - func_name:%s\n' % (file, line, func_name.encode('utf-8')))
                        sys.stderr.flush()

                    if DictContains(file_to_id_to_breakpoint, file):
                        id_to_pybreakpoint = file_to_id_to_breakpoint[file]
                    else:
                        id_to_pybreakpoint = file_to_id_to_breakpoint[file] = {}

                    id_to_pybreakpoint[breakpoint_id] = breakpoint
                    self.consolidate_breakpoints(file, id_to_pybreakpoint, breakpoints)
                    if self.plugin is not None:
                        self.has_plugin_line_breaks = self.plugin.has_line_breaks()

                    self.setTracingForUntracedContexts(overwrite_prev_trace=True)

                elif cmd_id == CMD_REMOVE_BREAK:
                    #command to remove some breakpoint
                    #text is type\file\tid. Remove from breakpoints dictionary
                    breakpoint_type, file, breakpoint_id = text.split('\t', 2)

                    if not IS_PY3K:  # In Python 3, the frame object will have unicode for the file, whereas on python 2 it has a byte-array encoded with the filesystem encoding.
                        file = file.encode(file_system_encoding)

                    file = NormFileToServer(file)

                    try:
                        breakpoint_id = int(breakpoint_id)
                    except ValueError:
                        pydev_log.error('Error removing breakpoint. Expected breakpoint_id to be an int. Found: %s' % (breakpoint_id,))

                    else:
                        file_to_id_to_breakpoint = None
                        if breakpoint_type == 'python-line':
                            breakpoints = self.breakpoints
                            file_to_id_to_breakpoint = self.file_to_id_to_line_breakpoint
                        elif self.get_plugin_lazy_init() is not None:
                            result = self.plugin.get_breakpoints(self, breakpoint_type)
                            if result is not None:
                                file_to_id_to_breakpoint = self.file_to_id_to_plugin_breakpoint
                                breakpoints = result

                        if file_to_id_to_breakpoint is None:
                            pydev_log.error('Error removing breakpoint. Cant handle breakpoint of type %s' % breakpoint_type)
                        else:
                            try:
                                id_to_pybreakpoint = file_to_id_to_breakpoint.get(file, {})
                                if DebugInfoHolder.DEBUG_TRACE_BREAKPOINTS > 0:
                                    existing = id_to_pybreakpoint[breakpoint_id]
                                    sys.stderr.write('Removed breakpoint:%s - line:%s - func_name:%s (id: %s)\n' % (
                                        file, existing.line, existing.func_name.encode('utf-8'), breakpoint_id))

                                del id_to_pybreakpoint[breakpoint_id]
                                self.consolidate_breakpoints(file, id_to_pybreakpoint, breakpoints)
                                if self.plugin is not None:
                                    self.has_plugin_line_breaks = self.plugin.has_line_breaks()

                            except KeyError:
                                pydev_log.error("Error removing breakpoint: Breakpoint id not found: %s id: %s. Available ids: %s\n" % (
                                    file, breakpoint_id, DictKeys(id_to_pybreakpoint)))


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

                elif cmd_id == CMD_SET_PY_EXCEPTION:
                    # Command which receives set of exceptions on which user wants to break the debugger
                    # text is: break_on_uncaught;break_on_caught;TypeError;ImportError;zipimport.ZipImportError;
                    # This API is optional and works 'in bulk' -- it's possible
                    # to get finer-grained control with CMD_ADD_EXCEPTION_BREAK/CMD_REMOVE_EXCEPTION_BREAK
                    # which allows setting caught/uncaught per exception.
                    #
                    splitted = text.split(';')
                    self.break_on_uncaught_exceptions = {}
                    self.break_on_caught_exceptions = {}
                    added = []
                    if len(splitted) >= 4:
                        if splitted[0] == 'true':
                            break_on_uncaught = True
                        else:
                            break_on_uncaught = False

                        if splitted[1] == 'true':
                            break_on_caught = True
                        else:
                            break_on_caught = False

                        if splitted[2] == 'true':
                            self.break_on_exceptions_thrown_in_same_context = True
                        else:
                            self.break_on_exceptions_thrown_in_same_context = False

                        if splitted[3] == 'true':
                            self.ignore_exceptions_thrown_in_lines_with_ignore_exception = True
                        else:
                            self.ignore_exceptions_thrown_in_lines_with_ignore_exception = False

                        for exception_type in splitted[4:]:
                            exception_type = exception_type.strip()
                            if not exception_type:
                                continue

                            exception_breakpoint = self.add_break_on_exception(
                                exception_type,
                                notify_always=break_on_caught,
                                notify_on_terminate=break_on_uncaught,
                                notify_on_first_raise_only=False,
                            )
                            if exception_breakpoint is None:
                                continue
                            added.append(exception_breakpoint)

                        self.update_after_exceptions_added(added)

                    else:
                        sys.stderr.write("Error when setting exception list. Received: %s\n" % (text,))

                elif cmd_id == CMD_GET_FILE_CONTENTS:

                    if not IS_PY3K:  # In Python 3, the frame object will have unicode for the file, whereas on python 2 it has a byte-array encoded with the filesystem encoding.
                        text = text.encode(file_system_encoding)

                    if os.path.exists(text):
                        f = open(text, 'r')
                        try:
                            source = f.read()
                        finally:
                            f.close()
                        cmd = self.cmdFactory.makeGetFileContents(seq, source)

                elif cmd_id == CMD_SET_PROPERTY_TRACE:
                    # Command which receives whether to trace property getter/setter/deleter
                    # text is feature_state(true/false);disable_getter/disable_setter/disable_deleter
                    if text != "":
                        splitted = text.split(';')
                        if len(splitted) >= 3:
                            if self.disable_property_trace is False and splitted[0] == 'true':
                                # Replacing property by custom property only when the debugger starts
                                pydevd_traceproperty.replace_builtin_property()
                                self.disable_property_trace = True
                            # Enable/Disable tracing of the property getter
                            if splitted[1] == 'true':
                                self.disable_property_getter_trace = True
                            else:
                                self.disable_property_getter_trace = False
                            # Enable/Disable tracing of the property setter
                            if splitted[2] == 'true':
                                self.disable_property_setter_trace = True
                            else:
                                self.disable_property_setter_trace = False
                            # Enable/Disable tracing of the property deleter
                            if splitted[3] == 'true':
                                self.disable_property_deleter_trace = True
                            else:
                                self.disable_property_deleter_trace = False
                    else:
                        # User hasn't configured any settings for property tracing
                        pass

                elif cmd_id == CMD_ADD_EXCEPTION_BREAK:
                    if text.find('\t') != -1:
                        exception, notify_always, notify_on_terminate, ignore_libraries = text.split('\t', 3)
                    else:
                        exception, notify_always, notify_on_terminate, ignore_libraries = text, 0, 0, 0

                    if exception.find('-') != -1:
                        type, exception = exception.split('-')
                    else:
                        type = 'python'

                    if type == 'python':
                        if int(notify_always) == 1:
                            pydev_log.warn("Deprecated parameter: 'notify always' policy removed in PyCharm\n")
                        exception_breakpoint = self.add_break_on_exception(
                            exception,
                            notify_always=int(notify_always) > 0,
                            notify_on_terminate = int(notify_on_terminate) == 1,
                            notify_on_first_raise_only=int(notify_always) == 2,
                            ignore_libraries=int(ignore_libraries) > 0
                        )

                        if exception_breakpoint is not None:
                            self.update_after_exceptions_added([exception_breakpoint])
                    else:
                        supported_type = False
                        plugin = self.get_plugin_lazy_init()
                        if plugin is not None:
                            supported_type = plugin.add_breakpoint('add_exception_breakpoint', self, type, exception)

                        if supported_type:
                            self.has_plugin_exception_breaks = self.plugin.has_exception_breaks()
                        else:
                            raise NameError(type)



                elif cmd_id == CMD_REMOVE_EXCEPTION_BREAK:
                    exception = text
                    if exception.find('-') != -1:
                        type, exception = exception.split('-')
                    else:
                        type = 'python'

                    if type == 'python':
                        try:
                            cp = self.break_on_uncaught_exceptions.copy()
                            DictPop(cp, exception, None)
                            self.break_on_uncaught_exceptions = cp

                            cp = self.break_on_caught_exceptions.copy()
                            DictPop(cp, exception, None)
                            self.break_on_caught_exceptions = cp
                        except:
                            pydev_log.debug("Error while removing exception %s"%sys.exc_info()[0])
                        update_exception_hook(self)
                    else:
                        supported_type = False
                        
                        # I.e.: no need to initialize lazy (if we didn't have it in the first place, we can't remove
                        # anything from it anyways).
                        plugin = self.plugin 
                        if plugin is not None:
                            supported_type = plugin.remove_exception_breakpoint(self, type, exception)

                        if supported_type:
                            self.has_plugin_exception_breaks = self.plugin.has_exception_breaks()
                        else:
                            raise NameError(type)

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
                    plugin = self.get_plugin_lazy_init()
                    if plugin is not None:
                        plugin.add_breakpoint('add_exception_breakpoint', self, 'django', exception)
                        self.has_plugin_exception_breaks = self.plugin.has_exception_breaks()


                elif cmd_id == CMD_REMOVE_DJANGO_EXCEPTION_BREAK:
                    exception = text

                    # I.e.: no need to initialize lazy (if we didn't have it in the first place, we can't remove
                    # anything from it anyways).
                    plugin = self.plugin
                    if plugin is not None:
                        plugin.remove_exception_breakpoint(self, 'django', exception)
                        self.has_plugin_exception_breaks = self.plugin.has_exception_breaks()

                elif cmd_id == CMD_EVALUATE_CONSOLE_EXPRESSION:
                    # Command which takes care for the debug console communication
                    if text != "":
                        thread_id, frame_id, console_command = text.split('\t', 2)
                        console_command, line = console_command.split('\t')
                        if console_command == 'EVALUATE':
                            int_cmd = InternalEvaluateConsoleExpression(seq, thread_id, frame_id, line)
                        elif console_command == 'GET_COMPLETIONS':
                            int_cmd = InternalConsoleGetCompletions(seq, thread_id, frame_id, line)
                        self.postInternalCommand(int_cmd, thread_id)

                elif cmd_id == CMD_RUN_CUSTOM_OPERATION:
                    # Command which runs a custom operation
                    if text != "":
                        try:
                            location, custom = text.split('||', 1)
                        except:
                            sys.stderr.write('Custom operation now needs a || separator. Found: %s\n' % (text,))
                            raise

                        thread_id, frame_id, scopeattrs = location.split('\t', 2)

                        if scopeattrs.find('\t') != -1:  # there are attributes beyond scope
                            scope, attrs = scopeattrs.split('\t', 1)
                        else:
                            scope, attrs = (scopeattrs, None)

                        # : style: EXECFILE or EXEC
                        # : encoded_code_or_file: file to execute or code
                        # : fname: name of function to be executed in the resulting namespace
                        style, encoded_code_or_file, fnname = custom.split('\t', 3)
                        int_cmd = InternalRunCustomOperation(seq, thread_id, frame_id, scope, attrs,
                                                             style, encoded_code_or_file, fnname)
                        self.postInternalCommand(int_cmd, thread_id)

                elif cmd_id == CMD_IGNORE_THROWN_EXCEPTION_AT:
                    if text:
                        replace = 'REPLACE:'  # Not all 3.x versions support u'REPLACE:', so, doing workaround.
                        if not IS_PY3K:
                            replace = unicode(replace)

                        if text.startswith(replace):
                            text = text[8:]
                            self.filename_to_lines_where_exceptions_are_ignored.clear()

                        if text:
                            for line in text.split('||'):  # Can be bulk-created (one in each line)
                                filename, line_number = line.split('|')
                                if not IS_PY3K:
                                    filename = filename.encode(file_system_encoding)

                                filename = NormFileToServer(filename)

                                if os.path.exists(filename):
                                    lines_ignored = self.filename_to_lines_where_exceptions_are_ignored.get(filename)
                                    if lines_ignored is None:
                                        lines_ignored = self.filename_to_lines_where_exceptions_are_ignored[filename] = {}
                                    lines_ignored[int(line_number)] = 1
                                else:
                                    sys.stderr.write('pydev debugger: warning: trying to ignore exception thrown'\
                                        ' on file that does not exist: %s (will have no effect)\n' % (filename,))

                elif cmd_id == CMD_ENABLE_DONT_TRACE:
                    if text:
                        true_str = 'true'  # Not all 3.x versions support u'str', so, doing workaround.
                        if not IS_PY3K:
                            true_str = unicode(true_str)

                        mode = text.strip() == true_str
                        pydevd_dont_trace.trace_filter(mode)

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

        # If conditional breakpoint raises any exception during evaluation send details to Java
        if stop_reason == CMD_SET_BREAK and self.suspend_on_breakpoint_exception:
            self.sendBreakpointConditionException(thread)


    def sendBreakpointConditionException(self, thread):
        """If conditional breakpoint raises an exception during evaluation
        send exception details to java
        """
        thread_id = GetThreadId(thread)
        conditional_breakpoint_exception_tuple = thread.additionalInfo.conditional_breakpoint_exception
        # conditional_breakpoint_exception_tuple - should contain 2 values (exception_type, stacktrace)
        if conditional_breakpoint_exception_tuple and len(conditional_breakpoint_exception_tuple) == 2:
            exc_type, stacktrace = conditional_breakpoint_exception_tuple
            int_cmd = InternalGetBreakpointException(thread_id, exc_type, stacktrace)
            # Reset the conditional_breakpoint_exception details to None
            thread.additionalInfo.conditional_breakpoint_exception = None
            self.postInternalCommand(int_cmd, thread_id)


    def sendCaughtExceptionStack(self, thread, arg, curr_frame_id):
        """Sends details on the exception which was caught (and where we stopped) to the java side.

        arg is: exception type, description, traceback object
        """
        thread_id = GetThreadId(thread)
        int_cmd = InternalSendCurrExceptionTrace(thread_id, arg, curr_frame_id)
        self.postInternalCommand(int_cmd, thread_id)


    def sendCaughtExceptionStackProceeded(self, thread):
        """Sends that some thread was resumed and is no longer showing an exception trace.
        """
        thread_id = GetThreadId(thread)
        int_cmd = InternalSendCurrExceptionTraceProceeded(thread_id)
        self.postInternalCommand(int_cmd, thread_id)
        self.processInternalCommands()


    def doWaitSuspend(self, thread, frame, event, arg): #@UnusedVariable
        """ busy waits until the thread state changes to RUN
        it expects thread's state as attributes of the thread.
        Upon running, processes any outstanding Stepping commands.
        """
        self.processInternalCommands()

        message = getattr(thread.additionalInfo, "message", None)

        cmd = self.cmdFactory.makeThreadSuspendMessage(GetThreadId(thread), frame, thread.stop_reason, message)
        self.writer.addCommand(cmd)

        CustomFramesContainer.custom_frames_lock.acquire()
        try:
            from_this_thread = []

            for frame_id, custom_frame in DictIterItems(CustomFramesContainer.custom_frames):
                if custom_frame.thread_id == thread.ident:
                    # print >> sys.stderr, 'Frame created: ', frame_id
                    self.writer.addCommand(self.cmdFactory.makeCustomFrameCreatedMessage(frame_id, custom_frame.name))
                    self.writer.addCommand(self.cmdFactory.makeThreadSuspendMessage(frame_id, custom_frame.frame, CMD_THREAD_SUSPEND, ""))

                from_this_thread.append(frame_id)

        finally:
            CustomFramesContainer.custom_frames_lock.release()

        imported = False
        info = thread.additionalInfo

        if info.pydev_state == STATE_SUSPEND and not self._finishDebuggingSession:
            # before every stop check if matplotlib modules were imported inside script code
            if len(self.mpl_modules_for_patching) > 0:
                for module in DictKeys(self.mpl_modules_for_patching):
                    if module in sys.modules:
                        activate_function = DictPop(self.mpl_modules_for_patching, module)
                        activate_function()
                        self.mpl_in_use = True

        while info.pydev_state == STATE_SUSPEND and not self._finishDebuggingSession:
            if self.mpl_in_use:
                # call input hooks if only matplotlib is in use
                try:
                    if not imported:
                        from pydev_ipython.inputhook import get_inputhook
                        imported = True
                    inputhook = get_inputhook()
                    if inputhook:
                        inputhook()
                except:
                    pass

            self.processInternalCommands()
            time.sleep(0.01)

        # process any stepping instructions
        if info.pydev_step_cmd == CMD_STEP_INTO or info.pydev_step_cmd == CMD_STEP_INTO_MY_CODE:
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
                # steps back to the same frame (in a return call it will stop in the 'back frame' for the user)
                info.pydev_step_stop = frame
                self.SetTraceForFrameAndParents(frame)
            else:
                # No back frame?!? -- this happens in jython when we have some frame created from an awt event
                # (the previous frame would be the awt event, but this doesn't make part of 'jython', only 'java')
                # so, if we're doing a step return in this situation, it's the same as just making it run
                info.pydev_step_stop = None
                info.pydev_step_cmd = None
                info.pydev_state = STATE_RUN

        del frame
        cmd = self.cmdFactory.makeThreadRunMessage(GetThreadId(thread), info.pydev_step_cmd)
        self.writer.addCommand(cmd)

        CustomFramesContainer.custom_frames_lock.acquire()
        try:
            # The ones that remained on last_running must now be removed.
            for frame_id in from_this_thread:
                # print >> sys.stderr, 'Removing created frame: ', frame_id
                self.writer.addCommand(self.cmdFactory.makeThreadKilledMessage(frame_id))

        finally:
            CustomFramesContainer.custom_frames_lock.release()

    def handle_post_mortem_stop(self, additionalInfo, t):
        pydev_log.debug("We are stopping in post-mortem\n")
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
                try:
                    if self.output_checker is None:
                        killAllPydevThreads()
                except:
                    traceback.print_exc()
                self._terminationEventSent = True
                return None

            filename, base = GetFilenameAndBase(frame)

            is_file_to_ignore = DictContains(DONT_TRACE, base) #we don't want to debug threading or anything related to pydevd

            #print('trace_dispatch', base, frame.f_lineno, event, frame.f_code.co_name, is_file_to_ignore)
            if is_file_to_ignore:
                if DONT_TRACE[base] == LIB_FILE:
                    if self.not_in_scope(filename):
                        return None
                else:
                    return None

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
                    if 'trace_dispatch' == f.f_code.co_name:
                        _fname, bs = GetFilenameAndBase(f)
                        if bs == 'pydevd_frame.py':
                            return None  #we don't wan't to trace code invoked from pydevd_frame.trace_dispatch
                    f = f.f_back

            # if thread is not alive, cancel trace_dispatch processing
            if not isThreadAlive(t):
                self.processThreadNotAlive(GetThreadId(t))
                return None  # suspend tracing

            # each new frame...
            return additionalInfo.CreateDbFrame((self, filename, additionalInfo, t, frame)).trace_dispatch(frame, event, arg)

        except SystemExit:
            return None

        except Exception:
            # Log it
            try:
                if traceback is not None:
                    # This can actually happen during the interpreter shutdown in Python 2.7
                    traceback.print_exc()
            except:
                # Error logging? We're really in the interpreter shutdown...
                # (https://github.com/fabioz/PyDev.Debugger/issues/8) 
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
            if hasattr(sys, 'exc_clear'):  # jython does not have it
                sys.exc_clear()  # don't keep the traceback (let's keep it clear for when we go to the point of executing client code)

            if not IS_PY3K and not IS_PY27 and not IS_64_BITS and not sys.platform.startswith("java") and not sys.platform.startswith("cli"):
                sys.stderr.write("pydev debugger: warning: psyco not available for speedups (the debugger will still work correctly, but a bit slower)\n")
                sys.stderr.flush()



    def SetTraceForFrameAndParents(self, frame, also_add_to_passed_frame=True, overwrite_prev_trace=False, dispatch_func=None):
        if dispatch_func is None:
            dispatch_func = self.trace_dispatch

        if also_add_to_passed_frame:
            self.update_trace(frame, dispatch_func, overwrite_prev_trace)

        frame = frame.f_back
        while frame:
            self.update_trace(frame, dispatch_func, overwrite_prev_trace)

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

    def prepareToRun(self):
        ''' Shared code to prepare debugging by installing traces and registering threads '''
        self.patch_threads()
        pydevd_tracing.SetTrace(self.trace_dispatch)


        PyDBCommandThread(self).start()
        if self.signature_factory is not None:
            # we need all data to be sent to IDE even after program finishes
            CheckOutputThread(self).start()


    def patch_threads(self):
        try:
            # not available in jython!
            threading.settrace(self.trace_dispatch)  # for all future threads
        except:
            pass

        from pydev_monkey import patch_thread_modules
        patch_thread_modules()

    def get_fullname(self, mod_name):
        try:
            loader = pkgutil.get_loader(mod_name)
        except:
            return None
        if loader is not None:
            for attr in ("get_filename", "_get_filename"):
                meth = getattr(loader, attr, None)
                if meth is not None:
                    return meth(mod_name)
        return None

    def run(self, file, globals=None, locals=None, module=False, set_trace=True):
        if module:
            filename = self.get_fullname(file)
            if filename is None:
                sys.stderr.write("No module named %s\n" % file)
                return
            else:
                file = filename

        if os.path.isdir(file):
            new_target = os.path.join(file, '__main__.py')
            if os.path.isfile(new_target):
                file = new_target

        if globals is None:
            m = save_main_module(file, 'pydevd')
            globals = m.__dict__
            try:
                globals['__builtins__'] = __builtins__
            except NameError:
                pass  # Not there on Jython...

        if locals is None:
            locals = globals

        if set_trace:
            # Predefined (writable) attributes: __name__ is the module's name;
            # __doc__ is the module's documentation string, or None if unavailable;
            # __file__ is the pathname of the file from which the module was loaded,
            # if it was loaded from a file. The __file__ attribute is not present for
            # C modules that are statically linked into the interpreter; for extension modules
            # loaded dynamically from a shared library, it is the pathname of the shared library file.


            # I think this is an ugly hack, bug it works (seems to) for the bug that says that sys.path should be the same in
            # debug and run.
            if m.__file__.startswith(sys.path[0]):
                # print >> sys.stderr, 'Deleting: ', sys.path[0]
                del sys.path[0]

            # now, the local directory has to be added to the pythonpath
            # sys.path.insert(0, os.getcwd())
            # Changed: it's not the local directory, but the directory of the file launched
            # The file being run ust be in the pythonpath (even if it was not before)
            sys.path.insert(0, os.path.split(file)[0])

            self.prepareToRun()

            while not self.readyToRun:
                time.sleep(0.1)  # busy wait until we receive run command

        try:
            self.init_matplotlib_support()
        except:
            sys.stderr.write("Matplotlib support in debugger failed\n")
            traceback.print_exc()

        pydev_imports.execfile(file, globals, locals)  # execute the script

    def exiting(self):
        sys.stdout.flush()
        sys.stderr.flush()
        self.checkOutputRedirect()
        cmd = self.cmdFactory.makeExitMessage()
        self.writer.addCommand(cmd)

    def wait_for_commands(self, globals):
        thread = threading.currentThread()
        import pydevd_frame_utils
        frame = pydevd_frame_utils.Frame(None, -1, pydevd_frame_utils.FCode("Console",
                                                                            os.path.abspath(os.path.dirname(__file__))), globals, globals)
        thread_id = GetThreadId(thread)
        import pydevd_vars
        pydevd_vars.addAdditionalFrameById(thread_id, {id(frame): frame})

        cmd = self.cmdFactory.makeShowConsoleMessage(thread_id, frame)
        self.writer.addCommand(cmd)

        while True:
            self.processInternalCommands()
            time.sleep(0.01)

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
    setup['multiproc'] = False #Used by PyCharm (reuses connection: ssh tunneling)
    setup['multiprocess'] = False # Used by PyDev (creates new connection to ide)
    setup['save-signatures'] = False
    setup['print-in-debugger-startup'] = False
    setup['cmd-line'] = False
    setup['module'] = False
    i = 0
    del argv[0]
    while (i < len(argv)):
        if argv[i] == '--port':
            del argv[i]
            setup['port'] = int(argv[i])
            del argv[i]
        elif argv[i] == '--vm_type':
            del argv[i]
            setup['vm_type'] = argv[i]
            del argv[i]
        elif argv[i] == '--client':
            del argv[i]
            setup['client'] = argv[i]
            del argv[i]
        elif argv[i] == '--server':
            del argv[i]
            setup['server'] = True
        elif argv[i] == '--file':
            del argv[i]
            setup['file'] = argv[i]
            i = len(argv) # pop out, file is our last argument
        elif argv[i] == '--DEBUG_RECORD_SOCKET_READS':
            del argv[i]
            setup['DEBUG_RECORD_SOCKET_READS'] = True
        elif argv[i] == '--DEBUG':
            del argv[i]
            set_debug(setup)
        elif argv[i] == '--multiproc':
            del argv[i]
            setup['multiproc'] = True
        elif argv[i] == '--multiprocess':
            del argv[i]
            setup['multiprocess'] = True
        elif argv[i] == '--save-signatures':
            del argv[i]
            setup['save-signatures'] = True
        elif argv[i] == '--print-in-debugger-startup':
            del argv[i]
            setup['print-in-debugger-startup'] = True
        elif (argv[i] == '--cmd-line'):
            del argv[i]
            setup['cmd-line'] = True
        elif (argv[i] == '--module'):
            del argv[i]
            setup['module'] = True
        else:
            raise ValueError("unexpected option " + argv[i])
    return setup

def usage(doExit=0):
    sys.stdout.write('Usage:\n')
    sys.stdout.write('pydevd.py --port=N [(--client hostname) | --server] --file executable [file_options]\n')
    if doExit:
        sys.exit(0)


def initStdoutRedirect():
    if not getattr(sys, 'stdoutBuf', None):
        sys.stdoutBuf = pydevd_io.IOBuf()
        sys.stdout_original = sys.stdout
        sys.stdout = pydevd_io.IORedirector(sys.stdout, sys.stdoutBuf) #@UndefinedVariable

def initStderrRedirect():
    if not getattr(sys, 'stderrBuf', None):
        sys.stderrBuf = pydevd_io.IOBuf()
        sys.stderr_original = sys.stderr
        sys.stderr = pydevd_io.IORedirector(sys.stderr, sys.stderrBuf) #@UndefinedVariable


def has_data_to_redirect():
    if getattr(sys, 'stdoutBuf', None):
        if not sys.stdoutBuf.empty():
            return True
    if getattr(sys, 'stderrBuf', None):
        if not sys.stderrBuf.empty():
            return True

    return False

#=======================================================================================================================
# settrace
#=======================================================================================================================
def settrace(
    host=None,
    stdoutToServer=False,
    stderrToServer=False,
    port=5678,
    suspend=True,
    trace_only_current_thread=False,
    overwrite_prev_trace=False,
    patch_multiprocessing=False,
    ):
    '''Sets the tracing function with the pydev debug function and initializes needed facilities.

    @param host: the user may specify another host, if the debug server is not in the same machine (default is the local
        host)

    @param stdoutToServer: when this is true, the stdout is passed to the debug server

    @param stderrToServer: when this is true, the stderr is passed to the debug server
        so that they are printed in its console and not in this process console.

    @param port: specifies which port to use for communicating with the server (note that the server must be started
        in the same port). @note: currently it's hard-coded at 5678 in the client

    @param suspend: whether a breakpoint should be emulated as soon as this function is called.

    @param trace_only_current_thread: determines if only the current thread will be traced or all current and future
        threads will also have the tracing enabled.

    @param overwrite_prev_trace: if True we'll reset the frame.f_trace of frames which are already being traced

    @param patch_multiprocessing: if True we'll patch the functions which create new processes so that launched
        processes are debugged.
    '''
    _set_trace_lock.acquire()
    try:
        _locked_settrace(
            host,
            stdoutToServer,
            stderrToServer,
            port,
            suspend,
            trace_only_current_thread,
            overwrite_prev_trace,
            patch_multiprocessing,
        )
    finally:
        _set_trace_lock.release()



_set_trace_lock = _pydev_thread.allocate_lock()

def _locked_settrace(
    host,
    stdoutToServer,
    stderrToServer,
    port,
    suspend,
    trace_only_current_thread,
    overwrite_prev_trace,
    patch_multiprocessing,
    ):
    if patch_multiprocessing:
        try:
            import pydev_monkey #Jython 2.1 can't use it...
        except:
            pass
        else:
            pydev_monkey.patch_new_process_functions()

    if host is None:
        import pydev_localhost
        host = pydev_localhost.get_localhost()

    global connected
    global bufferStdOutToServer
    global bufferStdErrToServer

    if not connected :
        pydevd_vm_type.SetupType()

        debugger = PyDB()
        debugger.connect(host, port)  # Note: connect can raise error.

        # Mark connected only if it actually succeeded.
        connected = True
        bufferStdOutToServer = stdoutToServer
        bufferStdErrToServer = stderrToServer

        if bufferStdOutToServer:
            initStdoutRedirect()

        if bufferStdErrToServer:
            initStderrRedirect()

        debugger.SetTraceForFrameAndParents(GetFrame(), False, overwrite_prev_trace=overwrite_prev_trace)


        CustomFramesContainer.custom_frames_lock.acquire()
        try:
            for _frameId, custom_frame in DictIterItems(CustomFramesContainer.custom_frames):
                debugger.SetTraceForFrameAndParents(custom_frame.frame, False)
        finally:
            CustomFramesContainer.custom_frames_lock.release()


        t = threadingCurrentThread()
        try:
            additionalInfo = t.additionalInfo
        except AttributeError:
            additionalInfo = PyDBAdditionalThreadInfo()
            t.additionalInfo = additionalInfo

        while not debugger.readyToRun:
            time.sleep(0.1)  # busy wait until we receive run command

        # note that we do that through pydevd_tracing.SetTrace so that the tracing
        # is not warned to the user!
        pydevd_tracing.SetTrace(debugger.trace_dispatch)

        if not trace_only_current_thread:
            # Trace future threads?
            debugger.patch_threads()

            # As this is the first connection, also set tracing for any untraced threads
            debugger.setTracingForUntracedContexts(ignore_frame=GetFrame(), overwrite_prev_trace=overwrite_prev_trace)

        # Stop the tracing as the last thing before the actual shutdown for a clean exit.
        atexit.register(stoptrace)

        PyDBCommandThread(debugger).start()
        CheckOutputThread(debugger).start()

        #Suspend as the last thing after all tracing is in place.
        if suspend:
            debugger.setSuspend(t, CMD_THREAD_SUSPEND)


    else:
        # ok, we're already in debug mode, with all set, so, let's just set the break
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
            # Trace future threads?
            debugger.patch_threads()


        if suspend:
            debugger.setSuspend(t, CMD_THREAD_SUSPEND)


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

        from pydev_monkey import undo_patch_thread_modules
        undo_patch_thread_modules()
 
        debugger = GetGlobalDebugger()
 
        if debugger:
  
            debugger.SetTraceForFrameAndParents(
                GetFrame(), also_add_to_passed_frame=True, overwrite_prev_trace=True, dispatch_func=lambda *args:None)
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

    def OnRun(self):
        dummy_thread = threading.currentThread()
        dummy_thread.is_pydev_daemon_thread = False
        return ReaderThread.OnRun(self)
        
    def handleExcept(self):
        ReaderThread.handleExcept(self)

    def processCommand(self, cmd_id, seq, text):
        if cmd_id == 99:
            self.dispatcher.port = int(text)
            self.killReceived = True


DISPATCH_APPROACH_NEW_CONNECTION = 1 # Used by PyDev
DISPATCH_APPROACH_EXISTING_CONNECTION = 2 # Used by PyCharm
DISPATCH_APPROACH = DISPATCH_APPROACH_NEW_CONNECTION

def dispatch():
    setup = SetupHolder.setup
    host = setup['client']
    port = setup['port']
    if DISPATCH_APPROACH == DISPATCH_APPROACH_EXISTING_CONNECTION:
        dispatcher = Dispatcher()
        try:
            dispatcher.connect(host, port)
            port = dispatcher.port
        finally:
            dispatcher.close()
    return host, port


def settrace_forked():
    '''
    When creating a fork from a process in the debugger, we need to reset the whole debugger environment!
    '''
    host, port = dispatch()

    import pydevd_tracing
    pydevd_tracing.RestoreSysSetTraceFunc()

    if port is not None:
        global connected
        connected = False

        CustomFramesContainerInit()

        settrace(
            host,
            port=port,
            suspend=False,
            trace_only_current_thread=False,
            overwrite_prev_trace=True,
            patch_multiprocessing=True,
            )

#=======================================================================================================================
# SetupHolder
#=======================================================================================================================
class SetupHolder:

    setup = None


#=======================================================================================================================
# main
#=======================================================================================================================
if __name__ == '__main__':
    
    # parse the command line. --file is our last argument that is required
    try:
        sys.original_argv = sys.argv[:]
        setup = processCommandLine(sys.argv)
        SetupHolder.setup = setup
    except ValueError:
        traceback.print_exc()
        usage(1)

    if setup['print-in-debugger-startup']:
        try:
            pid = ' (pid: %s)' % os.getpid()
        except:
            pid = ''
        sys.stderr.write("pydev debugger: starting%s\n" % pid)

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
    f = setup['file']
    fix_app_engine_debug = False


    try:
        import pydev_monkey
    except:
        pass #Not usable on jython 2.1
    else:
        if setup['multiprocess']: # PyDev
            pydev_monkey.patch_new_process_functions()

        elif setup['multiproc']: # PyCharm
            pydev_log.debug("Started in multiproc mode\n")
            # Note: we're not inside method, so, no need for 'global'
            DISPATCH_APPROACH = DISPATCH_APPROACH_EXISTING_CONNECTION

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

            # Only do this patching if we're not running with multiprocess turned on.
            if f.find('dev_appserver.py') != -1:
                if os.path.basename(f).startswith('dev_appserver.py'):
                    appserver_dir = os.path.dirname(f)
                    version_file = os.path.join(appserver_dir, 'VERSION')
                    if os.path.exists(version_file):
                        try:
                            stream = open(version_file, 'r')
                            try:
                                for line in stream.read().splitlines():
                                    line = line.strip()
                                    if line.startswith('release:'):
                                        line = line[8:].strip()
                                        version = line.replace('"', '')
                                        version = version.split('.')
                                        if int(version[0]) > 1:
                                            fix_app_engine_debug = True

                                        elif int(version[0]) == 1:
                                            if int(version[1]) >= 7:
                                                # Only fix from 1.7 onwards
                                                fix_app_engine_debug = True
                                        break
                            finally:
                                stream.close()
                        except:
                            traceback.print_exc()

    try:
        # In the default run (i.e.: run directly on debug mode), we try to patch stackless as soon as possible
        # on a run where we have a remote debug, we may have to be more careful because patching stackless means
        # that if the user already had a stackless.set_schedule_callback installed, he'd loose it and would need
        # to call it again (because stackless provides no way of getting the last function which was registered
        # in set_schedule_callback).
        #
        # So, ideally, if there's an application using stackless and the application wants to use the remote debugger
        # and benefit from stackless debugging, the application itself must call:
        #
        # import pydevd_stackless
        # pydevd_stackless.patch_stackless()
        #
        # itself to be able to benefit from seeing the tasklets created before the remote debugger is attached.
        import pydevd_stackless
        pydevd_stackless.patch_stackless()
    except:
        pass  # It's ok not having stackless there...

    debugger = PyDB()
    is_module = setup['module']

    if fix_app_engine_debug:
        sys.stderr.write("pydev debugger: google app engine integration enabled\n")
        curr_dir = os.path.dirname(__file__)
        app_engine_startup_file = os.path.join(curr_dir, 'pydev_app_engine_debug_startup.py')

        sys.argv.insert(1, '--python_startup_script=' + app_engine_startup_file)
        import json
        setup['pydevd'] = __file__
        sys.argv.insert(2, '--python_startup_args=%s' % json.dumps(setup),)
        sys.argv.insert(3, '--automatic_restart=no')
        sys.argv.insert(4, '--max_module_instances=1')

        # Run the dev_appserver
        debugger.run(setup['file'], None, None, is_module, set_trace=False)
    else:
        # as to get here all our imports are already resolved, the psyco module can be
        # changed and we'll still get the speedups in the debugger, as those functions
        # are already compiled at this time.
        try:
            import psyco
        except ImportError:
            if hasattr(sys, 'exc_clear'):  # jython does not have it
                sys.exc_clear()  # don't keep the traceback -- clients don't want to see it
            pass  # that's ok, no need to mock psyco if it's not available anyways
        else:
            # if it's available, let's change it for a stub (pydev already made use of it)
            import pydevd_psyco_stub
            sys.modules['psyco'] = pydevd_psyco_stub

        if setup['save-signatures']:
            if pydevd_vm_type.GetVmType() == pydevd_vm_type.PydevdVmType.JYTHON:
                sys.stderr.write("Collecting run-time type information is not supported for Jython\n")
            else:
                # Only import it if we're going to use it!
                from pydevd_signature import SignatureFactory
                debugger.signature_factory = SignatureFactory()

        try:
            debugger.connect(host, port)
        except:
            sys.stderr.write("Could not connect to %s: %s\n" % (host, port))
            traceback.print_exc()
            sys.exit(1)

        connected = True  # Mark that we're connected when started from inside ide.

        globals = debugger.run(setup['file'], None, None, is_module)

        if setup['cmd-line']:
            debugger.wait_for_commands(globals)


