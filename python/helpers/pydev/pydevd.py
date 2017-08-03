'''
Entry point module (keep at root):

This module starts the debugger.
'''
from __future__ import nested_scopes  # Jython 2.1 support

import atexit
import os
import sys
import traceback

from _pydevd_bundle.pydevd_constants import IS_JYTH_LESS25, IS_PY3K, IS_PY34_OR_GREATER, get_thread_id, dict_keys, dict_contains, \
    dict_iter_items, DebugInfoHolder, PYTHON_SUSPEND, STATE_SUSPEND, STATE_RUN, get_frame, xrange, \
    clear_cached_thread_id, INTERACTIVE_MODE_AVAILABLE
from _pydev_bundle import fix_getpass
from _pydev_bundle import pydev_imports, pydev_log
from _pydev_bundle._pydev_filesystem_encoding import getfilesystemencoding
from _pydev_bundle.pydev_is_thread_alive import is_thread_alive
from _pydev_imps._pydev_saved_modules import threading
from _pydev_imps._pydev_saved_modules import time
from _pydev_imps._pydev_saved_modules import thread
from _pydevd_bundle import pydevd_io, pydevd_vm_type, pydevd_tracing
from _pydevd_bundle import pydevd_utils
from _pydevd_bundle import pydevd_vars
from _pydevd_bundle.pydevd_additional_thread_info import PyDBAdditionalThreadInfo
from _pydevd_bundle.pydevd_breakpoints import ExceptionBreakpoint, update_exception_hook
from _pydevd_bundle.pydevd_comm import CMD_SET_BREAK, CMD_SET_NEXT_STATEMENT, CMD_STEP_INTO, CMD_STEP_OVER, \
    CMD_STEP_RETURN, CMD_STEP_INTO_MY_CODE, CMD_THREAD_SUSPEND, CMD_RUN_TO_LINE, \
    CMD_ADD_EXCEPTION_BREAK, CMD_SMART_STEP_INTO, InternalConsoleExec, NetCommandFactory, \
    PyDBDaemonThread, _queue, ReaderThread, GetGlobalDebugger, get_global_debugger, \
    set_global_debugger, WriterThread, pydevd_find_thread_by_id, pydevd_log, \
    start_client, start_server, InternalGetBreakpointException, InternalSendCurrExceptionTrace, \
    InternalSendCurrExceptionTraceProceeded
from _pydevd_bundle.pydevd_custom_frames import CustomFramesContainer, custom_frames_container_init
from _pydevd_bundle.pydevd_frame_utils import add_exception_to_frame
from _pydevd_bundle.pydevd_kill_all_pydevd_threads import kill_all_pydev_threads
from _pydevd_bundle.pydevd_trace_dispatch import trace_dispatch as _trace_dispatch, global_cache_skips, global_cache_frame_skips, show_tracing_warning
from _pydevd_frame_eval.pydevd_frame_eval_main import frame_eval_func, stop_frame_eval, enable_cache_frames_without_breaks, \
    dummy_trace_dispatch, show_frame_eval_warning
from _pydevd_bundle.pydevd_utils import save_main_module
from pydevd_concurrency_analyser.pydevd_concurrency_logger import ThreadingLogger, AsyncioLogger, send_message, cur_time
from pydevd_concurrency_analyser.pydevd_thread_wrappers import wrap_threads


__version_info__ = (1, 0, 0)
__version_info_str__ = []
for v in __version_info__:
    __version_info_str__.append(str(v))

__version__ = '.'.join(__version_info_str__)

#IMPORTANT: pydevd_constants must be the 1st thing defined because it'll keep a reference to the original sys._getframe







SUPPORT_PLUGINS = not IS_JYTH_LESS25
PluginManager = None
if SUPPORT_PLUGINS:
    from _pydevd_bundle.pydevd_plugin_utils import PluginManager


threadingEnumerate = threading.enumerate
threadingCurrentThread = threading.currentThread

try:
    'dummy'.encode('utf-8') # Added because otherwise Jython 2.2.1 wasn't finding the encoding (if it wasn't loaded in the main thread).
except:
    pass


connected = False
bufferStdOutToServer = False
bufferStdErrToServer = False
remote = False
forked = False

file_system_encoding = getfilesystemencoding()


#=======================================================================================================================
# PyDBCommandThread
#=======================================================================================================================
class PyDBCommandThread(PyDBDaemonThread):

    def __init__(self, py_db):
        PyDBDaemonThread.__init__(self)
        self._py_db_command_thread_event = py_db._py_db_command_thread_event
        self.py_db = py_db
        self.setName('pydevd.CommandThread')

    def _on_run(self):
        for i in xrange(1, 10):
            time.sleep(0.5) #this one will only start later on (because otherwise we may not have any non-daemon threads
            if self.killReceived:
                return

        if self.pydev_do_not_trace:
            self.py_db.SetTrace(None) # no debugging on this thread

        try:
            while not self.killReceived:
                try:
                    self.py_db.process_internal_commands()
                except:
                    pydevd_log(0, 'Finishing debug communication...(2)')
                self._py_db_command_thread_event.clear()
                self._py_db_command_thread_event.wait(0.5)
        except:
            pydev_log.debug(sys.exc_info()[0])

            #only got this error in interpreter shutdown
            #pydevd_log(0, 'Finishing debug communication...(3)')



#=======================================================================================================================
# CheckOutputThread
# Non-daemonic thread guaranties that all data is written even if program is finished
#=======================================================================================================================
class CheckOutputThread(PyDBDaemonThread):

    def __init__(self, py_db):
        PyDBDaemonThread.__init__(self)
        self.py_db = py_db
        self.setName('pydevd.CheckAliveThread')
        self.daemon = False
        py_db.output_checker = self

    def _on_run(self):
        if self.pydev_do_not_trace:

            disable_tracing = True

            if pydevd_vm_type.get_vm_type() == pydevd_vm_type.PydevdVmType.JYTHON and sys.hexversion <= 0x020201f0:
                # don't run untraced threads if we're in jython 2.2.1 or lower
                # jython bug: if we start a thread and another thread changes the tracing facility
                # it affects other threads (it's not set only for the thread but globally)
                # Bug: http://sourceforge.net/tracker/index.php?func=detail&aid=1870039&group_id=12867&atid=112867
                disable_tracing = False

            if disable_tracing:
                pydevd_tracing.SetTrace(None)  # no debugging on this thread

        while not self.killReceived:
            time.sleep(0.3)
            if not self.py_db.has_threads_alive() and self.py_db.writer.empty() \
                    and not has_data_to_redirect():
                try:
                    pydev_log.debug("No alive threads, finishing debug session")
                    self.py_db.finish_debugging_session()
                    kill_all_pydev_threads()
                except:
                    traceback.print_exc()

                self.killReceived = True

            self.py_db.check_output_redirect()


    def do_kill_pydev_thread(self):
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
       Every RDB command is processed by calling process_net_command.
       Every PyDB net command is sent to the net by posting NetCommand to WriterThread queue

       Some commands need to be executed on the right thread (suspend/resume & friends)
       These are placed on the internal command queue.
    """


    def __init__(self):
        set_global_debugger(self)
        pydevd_tracing.replace_sys_set_trace_func()
        self.reader = None
        self.writer = None
        self.output_checker = None
        self.quitting = None
        self.cmd_factory = NetCommandFactory()
        self._cmd_queue = {}  # the hash of Queues. Key is thread id, value is thread

        self.breakpoints = {}

        self.file_to_id_to_line_breakpoint = {}
        self.file_to_id_to_plugin_breakpoint = {}

        # Note: breakpoints dict should not be mutated: a copy should be created
        # and later it should be assigned back (to prevent concurrency issues).
        self.break_on_uncaught_exceptions = {}
        self.break_on_caught_exceptions = {}

        self.ready_to_run = False
        self._main_lock = thread.allocate_lock()
        self._lock_running_thread_ids = thread.allocate_lock()
        self._py_db_command_thread_event = threading.Event()
        CustomFramesContainer._py_db_command_thread_event = self._py_db_command_thread_event
        self._finish_debugging_session = False
        self._termination_event_set = False
        self.signature_factory = None
        self.SetTrace = pydevd_tracing.SetTrace
        self.break_on_exceptions_thrown_in_same_context = False
        self.ignore_exceptions_thrown_in_lines_with_ignore_exception = True

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
        self.thread_analyser = None
        self.asyncio_analyser = None

        # matplotlib support in debugger and debug console
        self.mpl_in_use = False
        self.mpl_hooks_in_debug_console = False
        self.mpl_modules_for_patching = {}

        self._filename_to_not_in_scope = {}
        self.first_breakpoint_reached = False
        self.is_filter_enabled = pydevd_utils.is_filter_enabled()
        self.is_filter_libraries = pydevd_utils.is_filter_libraries()
        self.show_return_values = False
        self.remove_return_values_flag = False

        # this flag disables frame evaluation even if it's available
        self.do_not_use_frame_eval = False

    def get_plugin_lazy_init(self):
        if self.plugin is None and SUPPORT_PLUGINS:
            self.plugin = PluginManager(self)
        return self.plugin

    def not_in_scope(self, filename):
        return pydevd_utils.not_in_project_roots(filename)

    def is_ignored_by_filters(self, filename):
        return pydevd_utils.is_ignored_by_filter(filename)

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

    def has_threads_alive(self):
        for t in threadingEnumerate():
            if getattr(t, 'is_pydev_daemon_thread', False):
                #Important: Jython 2.5rc4 has a bug where a thread created with thread.start_new_thread won't be
                #set as a daemon thread, so, we also have to check for the 'is_pydev_daemon_thread' flag.
                #See: https://github.com/fabioz/PyDev.Debugger/issues/11
                continue

            if isinstance(t, PyDBDaemonThread):
                pydev_log.error_once(
                        'Error in debugger: Found PyDBDaemonThread not marked with is_pydev_daemon_thread=True.\n')

            if is_thread_alive(t):
                if not t.isDaemon() or hasattr(t, "__pydevd_main_thread"):
                    return True

        return False

    def finish_debugging_session(self):
        self._finish_debugging_session = True


    def initialize_network(self, sock):
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
            s = start_client(host, port)
        else:
            s = start_server(port)

        self.initialize_network(s)


    def get_internal_queue(self, thread_id):
        """ returns internal command queue for a given thread.
        if new queue is created, notify the RDB about it """
        if thread_id.startswith('__frame__'):
            thread_id = thread_id[thread_id.rfind('|') + 1:]
        try:
            return self._cmd_queue[thread_id]
        except KeyError:
            return self._cmd_queue.setdefault(thread_id, _queue.Queue()) #@UndefinedVariable


    def post_internal_command(self, int_cmd, thread_id):
        """ if thread_id is *, post to all """
        if thread_id == "*":
            threads = threadingEnumerate()
            for t in threads:
                thread_id = get_thread_id(t)
                queue = self.get_internal_queue(thread_id)
                queue.put(int_cmd)

        else:
            queue = self.get_internal_queue(thread_id)
            queue.put(int_cmd)

    def check_output_redirect(self):
        global bufferStdOutToServer
        global bufferStdErrToServer

        if bufferStdOutToServer:
            init_stdout_redirect()
            self.check_output(sys.stdoutBuf, 1) #@UndefinedVariable

        if bufferStdErrToServer:
            init_stderr_redirect()
            self.check_output(sys.stderrBuf, 2) #@UndefinedVariable

    def check_output(self, out, outCtx):
        '''Checks the output to see if we have to send some buffered output to the debug server

        @param out: sys.stdout or sys.stderr
        @param outCtx: the context indicating: 1=stdout and 2=stderr (to know the colors to write it)
        '''

        try:
            v = out.getvalue()

            if v:
                self.cmd_factory.make_io_message(v, outCtx, self)
        except:
            traceback.print_exc()


    def init_matplotlib_in_debug_console(self):
        # import hook and patches for matplotlib support in debug console
        from _pydev_bundle.pydev_import_hook import import_hook_manager
        for module in dict_keys(self.mpl_modules_for_patching):
            import_hook_manager.add_module_name(module, self.mpl_modules_for_patching.pop(module))

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

    def _activate_mpl_if_needed(self):
        if len(self.mpl_modules_for_patching) > 0:
            for module in dict_keys(self.mpl_modules_for_patching):
                if module in sys.modules:
                    activate_function = self.mpl_modules_for_patching.pop(module)
                    activate_function()
                    self.mpl_in_use = True

    def _call_mpl_hook(self):
        try:
            from pydev_ipython.inputhook import get_inputhook
            inputhook = get_inputhook()
            if inputhook:
                inputhook()
        except:
            pass

    def suspend_all_other_threads(self, thread_suspended_at_bp):
        all_threads = threadingEnumerate()
        for t in all_threads:
            if getattr(t, 'is_pydev_daemon_thread', False):
                pass # I.e.: skip the DummyThreads created from pydev daemon threads
            elif hasattr(t, 'pydev_do_not_trace'):
                pass  # skip some other threads, i.e. ipython history saving thread from debug console
            else:
                if t is thread_suspended_at_bp:
                    continue
                additional_info = None
                try:
                    additional_info = t.additional_info
                except AttributeError:
                    pass  # that's ok, no info currently set

                if additional_info is not None:
                    for frame in additional_info.iter_frames(t):
                        self.set_trace_for_frame_and_parents(frame, overwrite_prev_trace=True)
                        del frame

                    self.set_suspend(t, CMD_THREAD_SUSPEND)
                else:
                    sys.stderr.write("Can't suspend thread: %s\n" % (t,))

    def process_internal_commands(self):
        '''This function processes internal commands
        '''
        self._main_lock.acquire()
        try:

            self.check_output_redirect()

            curr_thread_id = get_thread_id(threadingCurrentThread())
            program_threads_alive = {}
            all_threads = threadingEnumerate()
            program_threads_dead = []
            self._lock_running_thread_ids.acquire()
            try:
                for t in all_threads:
                    if getattr(t, 'is_pydev_daemon_thread', False):
                        pass # I.e.: skip the DummyThreads created from pydev daemon threads
                    elif isinstance(t, PyDBDaemonThread):
                        pydev_log.error_once('Error in debugger: Found PyDBDaemonThread not marked with is_pydev_daemon_thread=True.\n')

                    elif is_thread_alive(t):
                        if not self._running_thread_ids:
                            # Fix multiprocessing debug with breakpoints in both main and child processes
                            # (https://youtrack.jetbrains.com/issue/PY-17092) When the new process is created, the main
                            # thread in the new process already has the attribute 'pydevd_id', so the new thread doesn't
                            # get new id with its process number and the debugger loses access to both threads.
                            # Therefore we should update thread_id for every main thread in the new process.

                            # TODO: Investigate: should we do this for all threads in threading.enumerate()?
                            # (i.e.: if a fork happens on Linux, this seems likely).
                            old_thread_id = get_thread_id(t)

                            clear_cached_thread_id(t)
                            clear_cached_thread_id(threadingCurrentThread())

                            thread_id = get_thread_id(t)
                            curr_thread_id = get_thread_id(threadingCurrentThread())
                            if pydevd_vars.has_additional_frames_by_id(old_thread_id):
                                frames_by_id = pydevd_vars.get_additional_frames_by_id(old_thread_id)
                                pydevd_vars.add_additional_frame_by_id(thread_id, frames_by_id)
                        else:
                            thread_id = get_thread_id(t)
                        program_threads_alive[thread_id] = t

                        if not dict_contains(self._running_thread_ids, thread_id):
                            if not hasattr(t, 'additional_info'):
                                # see http://sourceforge.net/tracker/index.php?func=detail&aid=1955428&group_id=85796&atid=577329
                                # Let's create the additional info right away!
                                t.additional_info = PyDBAdditionalThreadInfo()
                            self._running_thread_ids[thread_id] = t
                            self.writer.add_command(self.cmd_factory.make_thread_created_message(t))


                        queue = self.get_internal_queue(thread_id)
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
                                        pydevd_log(2, "Matplotlib support in debug console failed", traceback.format_exc())
                                    self.mpl_hooks_in_debug_console = True

                                if int_cmd.can_be_executed_by(curr_thread_id):
                                    pydevd_log(2, "processing internal command ", str(int_cmd))
                                    int_cmd.do_it(self)
                                else:
                                    pydevd_log(2, "NOT processing internal command ", str(int_cmd))
                                    cmdsToReadd.append(int_cmd)


                        except _queue.Empty: #@UndefinedVariable
                            for int_cmd in cmdsToReadd:
                                queue.put(int_cmd)
                                # this is how we exit


                thread_ids = list(self._running_thread_ids.keys())
                for tId in thread_ids:
                    if not dict_contains(program_threads_alive, tId):
                        program_threads_dead.append(tId)
            finally:
                self._lock_running_thread_ids.release()

            for tId in program_threads_dead:
                try:
                    self._process_thread_not_alive(tId)
                except:
                    sys.stderr.write('Error iterating through %s (%s) - %s\n' % (
                        program_threads_alive, program_threads_alive.__class__, dir(program_threads_alive)))
                    raise


            if len(program_threads_alive) == 0:
                self.finish_debugging_session()
                for t in all_threads:
                    if hasattr(t, 'do_kill_pydev_thread'):
                        t.do_kill_pydev_thread()

        finally:
            self._main_lock.release()

    def disable_tracing_while_running_if_frame_eval(self):
        pydevd_tracing.settrace_while_running_if_frame_eval(self, self.dummy_trace_dispatch)

    def enable_tracing_in_frames_while_running_if_frame_eval(self):
        pydevd_tracing.settrace_while_running_if_frame_eval(self, self.trace_dispatch)

    def set_tracing_for_untraced_contexts_if_not_frame_eval(self, ignore_frame=None, overwrite_prev_trace=False):
        if self.frame_eval_func is not None:
            return
        self.set_tracing_for_untraced_contexts(ignore_frame, overwrite_prev_trace)

    def set_tracing_for_untraced_contexts(self, ignore_frame=None, overwrite_prev_trace=False):
        # Enable the tracing for existing threads (because there may be frames being executed that
        # are currently untraced).
        if self.frame_eval_func is not None:
            return
        threads = threadingEnumerate()
        try:
            for t in threads:
                if getattr(t, 'is_pydev_daemon_thread', False):
                    continue

                # TODO: optimize so that we only actually add that tracing if it's in
                # the new breakpoint context.
                additional_info = None
                try:
                    additional_info = t.additional_info
                except AttributeError:
                    pass  # that's ok, no info currently set

                if additional_info is not None:
                    for frame in additional_info.iter_frames(t):
                        if frame is not ignore_frame:
                            self.set_trace_for_frame_and_parents(frame, overwrite_prev_trace=overwrite_prev_trace)
        finally:
            frame = None
            t = None
            threads = None
            additional_info = None


    def consolidate_breakpoints(self, file, id_to_breakpoint, breakpoints):
        break_dict = {}
        for breakpoint_id, pybreakpoint in dict_iter_items(id_to_breakpoint):
            break_dict[pybreakpoint.line] = pybreakpoint

        breakpoints[file] = break_dict
        global_cache_skips.clear()
        global_cache_frame_skips.clear()

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
                self.set_tracing_for_untraced_contexts_if_not_frame_eval()

    def _process_thread_not_alive(self, threadId):
        """ if thread is not alive, cancel trace_dispatch processing """
        self._lock_running_thread_ids.acquire()
        try:
            thread = self._running_thread_ids.pop(threadId, None)
            if thread is None:
                return

            wasNotified = thread.additional_info.pydev_notify_kill
            if not wasNotified:
                thread.additional_info.pydev_notify_kill = True

        finally:
            self._lock_running_thread_ids.release()

        cmd = self.cmd_factory.make_thread_killed_message(threadId)
        self.writer.add_command(cmd)


    def set_suspend(self, thread, stop_reason):
        thread.additional_info.suspend_type = PYTHON_SUSPEND
        thread.additional_info.pydev_state = STATE_SUSPEND
        thread.stop_reason = stop_reason

        # If conditional breakpoint raises any exception during evaluation send details to Java
        if stop_reason == CMD_SET_BREAK and self.suspend_on_breakpoint_exception:
            self._send_breakpoint_condition_exception(thread)


    def _send_breakpoint_condition_exception(self, thread):
        """If conditional breakpoint raises an exception during evaluation
        send exception details to java
        """
        thread_id = get_thread_id(thread)
        conditional_breakpoint_exception_tuple = thread.additional_info.conditional_breakpoint_exception
        # conditional_breakpoint_exception_tuple - should contain 2 values (exception_type, stacktrace)
        if conditional_breakpoint_exception_tuple and len(conditional_breakpoint_exception_tuple) == 2:
            exc_type, stacktrace = conditional_breakpoint_exception_tuple
            int_cmd = InternalGetBreakpointException(thread_id, exc_type, stacktrace)
            # Reset the conditional_breakpoint_exception details to None
            thread.additional_info.conditional_breakpoint_exception = None
            self.post_internal_command(int_cmd, thread_id)


    def send_caught_exception_stack(self, thread, arg, curr_frame_id):
        """Sends details on the exception which was caught (and where we stopped) to the java side.

        arg is: exception type, description, traceback object
        """
        thread_id = get_thread_id(thread)
        int_cmd = InternalSendCurrExceptionTrace(thread_id, arg, curr_frame_id)
        self.post_internal_command(int_cmd, thread_id)


    def send_caught_exception_stack_proceeded(self, thread):
        """Sends that some thread was resumed and is no longer showing an exception trace.
        """
        thread_id = get_thread_id(thread)
        int_cmd = InternalSendCurrExceptionTraceProceeded(thread_id)
        self.post_internal_command(int_cmd, thread_id)
        self.process_internal_commands()


    def send_process_created_message(self):
        """Sends a message that a new process has been created.
        """
        cmd = self.cmd_factory.make_process_created_message()
        self.writer.add_command(cmd)


    def do_wait_suspend(self, thread, frame, event, arg, suspend_type="trace"): #@UnusedVariable
        """ busy waits until the thread state changes to RUN
        it expects thread's state as attributes of the thread.
        Upon running, processes any outstanding Stepping commands.
        """
        self.process_internal_commands()

        message = thread.additional_info.pydev_message

        cmd = self.cmd_factory.make_thread_suspend_message(get_thread_id(thread), frame, thread.stop_reason, message, suspend_type)
        self.writer.add_command(cmd)

        CustomFramesContainer.custom_frames_lock.acquire()  # @UndefinedVariable
        try:
            from_this_thread = []

            for frame_id, custom_frame in dict_iter_items(CustomFramesContainer.custom_frames):
                if custom_frame.thread_id == thread.ident:
                    # print >> sys.stderr, 'Frame created: ', frame_id
                    self.writer.add_command(self.cmd_factory.make_custom_frame_created_message(frame_id, custom_frame.name))
                    self.writer.add_command(self.cmd_factory.make_thread_suspend_message(frame_id, custom_frame.frame, CMD_THREAD_SUSPEND, "", suspend_type))

                from_this_thread.append(frame_id)

        finally:
            CustomFramesContainer.custom_frames_lock.release()  # @UndefinedVariable

        info = thread.additional_info

        if info.pydev_state == STATE_SUSPEND and not self._finish_debugging_session:
            # before every stop check if matplotlib modules were imported inside script code
            self._activate_mpl_if_needed()

        while info.pydev_state == STATE_SUSPEND and not self._finish_debugging_session:
            if self.mpl_in_use:
                # call input hooks if only matplotlib is in use
                self._call_mpl_hook()

            self.process_internal_commands()
            time.sleep(0.01)

        # process any stepping instructions
        if info.pydev_step_cmd == CMD_STEP_INTO or info.pydev_step_cmd == CMD_STEP_INTO_MY_CODE:
            info.pydev_step_stop = None
            info.pydev_smart_step_stop = None

        elif info.pydev_step_cmd == CMD_STEP_OVER:
            info.pydev_step_stop = frame
            info.pydev_smart_step_stop = None
            self.set_trace_for_frame_and_parents(frame)

        elif info.pydev_step_cmd == CMD_SMART_STEP_INTO:
            self.set_trace_for_frame_and_parents(frame)
            info.pydev_step_stop = None
            info.pydev_smart_step_stop = frame

        elif info.pydev_step_cmd == CMD_RUN_TO_LINE or info.pydev_step_cmd == CMD_SET_NEXT_STATEMENT :
            self.set_trace_for_frame_and_parents(frame)

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
                    self.do_wait_suspend(thread, frame, event, arg, "trace")
                    return


        elif info.pydev_step_cmd == CMD_STEP_RETURN:
            back_frame = frame.f_back
            if back_frame is not None:
                # steps back to the same frame (in a return call it will stop in the 'back frame' for the user)
                info.pydev_step_stop = frame
                self.set_trace_for_frame_and_parents(frame)
            else:
                # No back frame?!? -- this happens in jython when we have some frame created from an awt event
                # (the previous frame would be the awt event, but this doesn't make part of 'jython', only 'java')
                # so, if we're doing a step return in this situation, it's the same as just making it run
                info.pydev_step_stop = None
                info.pydev_step_cmd = -1
                info.pydev_state = STATE_RUN

        if self.frame_eval_func is not None and info.pydev_state == STATE_RUN:
            if info.pydev_step_cmd == -1:
                if not self.do_not_use_frame_eval:
                    self.SetTrace(self.dummy_trace_dispatch)
                    self.set_trace_for_frame_and_parents(frame, overwrite_prev_trace=True, dispatch_func=dummy_trace_dispatch)
            else:
                self.set_trace_for_frame_and_parents(frame, overwrite_prev_trace=True)
                # enable old tracing function for stepping
                self.SetTrace(self.trace_dispatch)

        del frame
        cmd = self.cmd_factory.make_thread_run_message(get_thread_id(thread), info.pydev_step_cmd)
        self.writer.add_command(cmd)

        CustomFramesContainer.custom_frames_lock.acquire()  # @UndefinedVariable
        try:
            # The ones that remained on last_running must now be removed.
            for frame_id in from_this_thread:
                # print >> sys.stderr, 'Removing created frame: ', frame_id
                self.writer.add_command(self.cmd_factory.make_thread_killed_message(frame_id))

        finally:
            CustomFramesContainer.custom_frames_lock.release()  # @UndefinedVariable

    def handle_post_mortem_stop(self, thread, frame, frames_byid, exception):
        pydev_log.debug("We are stopping in post-mortem\n")
        thread_id = get_thread_id(thread)
        pydevd_vars.add_additional_frame_by_id(thread_id, frames_byid)
        try:
            try:
                add_exception_to_frame(frame, exception)
                self.set_suspend(thread, CMD_ADD_EXCEPTION_BREAK)
                self.do_wait_suspend(thread, frame, 'exception', None, "trace")
            except:
                pydev_log.error("We've got an error while stopping in post-mortem: %s\n"%sys.exc_info()[0])
        finally:
            pydevd_vars.remove_additional_frame_by_id(thread_id)


    def set_trace_for_frame_and_parents(self, frame, also_add_to_passed_frame=True, overwrite_prev_trace=False, dispatch_func=None):
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

    def prepare_to_run(self):
        ''' Shared code to prepare debugging by installing traces and registering threads '''
        if self.signature_factory is not None or self.thread_analyser is not None:
            # we need all data to be sent to IDE even after program finishes
            CheckOutputThread(self).start()
            # turn off frame evaluation for concurrency visualization
            self.frame_eval_func = None

        self.patch_threads()
        pydevd_tracing.SetTrace(self.trace_dispatch, self.frame_eval_func, self.dummy_trace_dispatch)
        # There is no need to set tracing function if frame evaluation is available. Moreover, there is no need to patch thread
        # functions, because frame evaluation function is set to all threads by default.

        PyDBCommandThread(self).start()

        if show_tracing_warning or show_frame_eval_warning:
            cmd = self.cmd_factory.make_show_cython_warning_message()
            self.writer.add_command(cmd)


    def patch_threads(self):
        try:
            # not available in jython!
            import threading
            threading.settrace(self.trace_dispatch)  # for all future threads
        except:
            pass

        from _pydev_bundle.pydev_monkey import patch_thread_modules
        patch_thread_modules()

    def get_fullname(self, mod_name):
        if IS_PY3K:
            import pkgutil
        else:
            from _pydev_imps import _pydev_pkgutil_old as pkgutil
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

    def run(self, file, globals=None, locals=None, is_module=False, set_trace=True):
        module_name = None
        if is_module:
            module_name = file
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

            if not is_module:
                # now, the local directory has to be added to the pythonpath
                # sys.path.insert(0, os.getcwd())
                # Changed: it's not the local directory, but the directory of the file launched
                # The file being run must be in the pythonpath (even if it was not before)
                sys.path.insert(0, os.path.split(file)[0])

            while not self.ready_to_run:
                time.sleep(0.1)  # busy wait until we receive run command

            if self.break_on_caught_exceptions or (self.plugin and self.plugin.has_exception_breaks()) or self.signature_factory:
                # disable frame evaluation if there are exception breakpoints with 'On raise' activation policy
                # or if there are plugin exception breakpoints or if collecting run-time types is enabled
                self.frame_eval_func = None

            # call prepare_to_run when we already have all information about breakpoints
            self.prepare_to_run()

        if self.thread_analyser is not None:
            wrap_threads()
            t = threadingCurrentThread()
            self.thread_analyser.set_start_time(cur_time())
            send_message("threading_event", 0, t.getName(), get_thread_id(t), "thread", "start", file, 1, None, parent=get_thread_id(t))

        if self.asyncio_analyser is not None:
            # we don't have main thread in asyncio graph, so we should add a fake event
            send_message("asyncio_event", 0, "Task", "Task", "thread", "stop", file, 1, frame=None, parent=None)

        try:
            if INTERACTIVE_MODE_AVAILABLE:
                self.init_matplotlib_support()
        except:
            sys.stderr.write("Matplotlib support in debugger failed\n")
            traceback.print_exc()

        if hasattr(sys, 'exc_clear'):
            # we should clean exception information in Python 2, before user's code execution
            sys.exc_clear()

        if not is_module:
            pydev_imports.execfile(file, globals, locals)  # execute the script
        else:
            # Run with the -m switch
            import runpy
            if hasattr(runpy, '_run_module_as_main'):
                # Newer versions of Python actually use this when the -m switch is used.
                runpy._run_module_as_main(module_name, alter_argv=False)
            else:
                runpy.run_module(module_name)
        return globals

    def exiting(self):
        sys.stdout.flush()
        sys.stderr.flush()
        self.check_output_redirect()
        cmd = self.cmd_factory.make_exit_message()
        self.writer.add_command(cmd)

    def wait_for_commands(self, globals):
        self._activate_mpl_if_needed()

        thread = threading.currentThread()
        from _pydevd_bundle import pydevd_frame_utils
        frame = pydevd_frame_utils.Frame(None, -1, pydevd_frame_utils.FCode("Console",
                                                                            os.path.abspath(os.path.dirname(__file__))), globals, globals)
        thread_id = get_thread_id(thread)
        from _pydevd_bundle import pydevd_vars
        pydevd_vars.add_additional_frame_by_id(thread_id, {id(frame): frame})

        cmd = self.cmd_factory.make_show_console_message(thread_id, frame)
        self.writer.add_command(cmd)

        while True:
            if self.mpl_in_use:
                # call input hooks if only matplotlib is in use
                self._call_mpl_hook()
            self.process_internal_commands()
            time.sleep(0.01)

    trace_dispatch = _trace_dispatch
    frame_eval_func = frame_eval_func
    dummy_trace_dispatch = dummy_trace_dispatch
    enable_cache_frames_without_breaks = enable_cache_frames_without_breaks

def set_debug(setup):
    setup['DEBUG_RECORD_SOCKET_READS'] = True
    setup['DEBUG_TRACE_BREAKPOINTS'] = 1
    setup['DEBUG_TRACE_LEVEL'] = 3


def enable_qt_support(qt_support_mode):
    from _pydev_bundle import pydev_monkey_qt
    pydev_monkey_qt.patch_qt(qt_support_mode)


def usage(doExit=0):
    sys.stdout.write('Usage:\n')
    sys.stdout.write('pydevd.py --port N [(--client hostname) | --server] --file executable [file_options]\n')
    if doExit:
        sys.exit(0)


def init_stdout_redirect():
    if not getattr(sys, 'stdoutBuf', None):
        sys.stdoutBuf = pydevd_io.IOBuf()
        sys.stdout_original = sys.stdout
        sys.stdout = pydevd_io.IORedirector(sys.stdout, sys.stdoutBuf) #@UndefinedVariable

def init_stderr_redirect():
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



_set_trace_lock = thread.allocate_lock()

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
            from _pydev_bundle import pydev_monkey
        except:
            pass
        else:
            pydev_monkey.patch_new_process_functions()

    global connected
    global bufferStdOutToServer
    global bufferStdErrToServer

    if not connected:
        pydevd_vm_type.setup_type()

        if SetupHolder.setup is None:
            setup = {
                'client': host,  # dispatch expects client to be set to the host address when server is False
                'server': False,
                'port': int(port),
                'multiprocess': patch_multiprocessing,
            }
            SetupHolder.setup = setup

        debugger = PyDB()
        debugger.connect(host, port)  # Note: connect can raise error.

        # Mark connected only if it actually succeeded.
        connected = True
        bufferStdOutToServer = stdoutToServer
        bufferStdErrToServer = stderrToServer

        if bufferStdOutToServer:
            init_stdout_redirect()

        if bufferStdErrToServer:
            init_stderr_redirect()

        patch_stdin(debugger)
        debugger.set_trace_for_frame_and_parents(get_frame(), False, overwrite_prev_trace=overwrite_prev_trace)


        CustomFramesContainer.custom_frames_lock.acquire()  # @UndefinedVariable
        try:
            for _frameId, custom_frame in dict_iter_items(CustomFramesContainer.custom_frames):
                debugger.set_trace_for_frame_and_parents(custom_frame.frame, False)
        finally:
            CustomFramesContainer.custom_frames_lock.release()  # @UndefinedVariable


        t = threadingCurrentThread()
        try:
            additional_info = t.additional_info
        except AttributeError:
            additional_info = PyDBAdditionalThreadInfo()
            t.additional_info = additional_info

        while not debugger.ready_to_run:
            time.sleep(0.1)  # busy wait until we receive run command

        global forked
        frame_eval_for_tracing = debugger.frame_eval_func
        if frame_eval_func is not None and not forked:
            # Disable frame evaluation for Remote Debug Server
            frame_eval_for_tracing = None

        # note that we do that through pydevd_tracing.SetTrace so that the tracing
        # is not warned to the user!
        pydevd_tracing.SetTrace(debugger.trace_dispatch, frame_eval_for_tracing, debugger.dummy_trace_dispatch)

        if not trace_only_current_thread:
            # Trace future threads?
            debugger.patch_threads()

            # As this is the first connection, also set tracing for any untraced threads
            debugger.set_tracing_for_untraced_contexts(ignore_frame=get_frame(), overwrite_prev_trace=overwrite_prev_trace)

        # Stop the tracing as the last thing before the actual shutdown for a clean exit.
        atexit.register(stoptrace)

        PyDBCommandThread(debugger).start()
        CheckOutputThread(debugger).start()

        #Suspend as the last thing after all tracing is in place.
        if suspend:
            debugger.set_suspend(t, CMD_THREAD_SUSPEND)


    else:
        # ok, we're already in debug mode, with all set, so, let's just set the break
        debugger = get_global_debugger()

        debugger.set_trace_for_frame_and_parents(get_frame(), False)

        t = threadingCurrentThread()
        try:
            additional_info = t.additional_info
        except AttributeError:
            additional_info = PyDBAdditionalThreadInfo()
            t.additional_info = additional_info

        pydevd_tracing.SetTrace(debugger.trace_dispatch, debugger.frame_eval_func, debugger.dummy_trace_dispatch)

        if not trace_only_current_thread:
            # Trace future threads?
            debugger.patch_threads()


        if suspend:
            debugger.set_suspend(t, CMD_THREAD_SUSPEND)


def stoptrace():
    global connected
    if connected:
        pydevd_tracing.restore_sys_set_trace_func()
        sys.settrace(None)
        try:
            #not available in jython!
            threading.settrace(None) # for all future threads
        except:
            pass

        from _pydev_bundle.pydev_monkey import undo_patch_thread_modules
        undo_patch_thread_modules()

        debugger = get_global_debugger()

        if debugger:

            debugger.set_trace_for_frame_and_parents(
                    get_frame(), also_add_to_passed_frame=True, overwrite_prev_trace=True, dispatch_func=lambda *args:None)
            debugger.exiting()

            kill_all_pydev_threads()

        connected = False

class Dispatcher(object):
    def __init__(self):
        self.port = None

    def connect(self, host, port):
        self.host  = host
        self.port = port
        self.client = start_client(self.host, self.port)
        self.reader = DispatchReader(self)
        self.reader.pydev_do_not_trace = False #we run reader in the same thread so we don't want to loose tracing
        self.reader.run()

    def close(self):
        try:
            self.reader.do_kill_pydev_thread()
        except :
            pass

class DispatchReader(ReaderThread):
    def __init__(self, dispatcher):
        self.dispatcher = dispatcher
        ReaderThread.__init__(self, self.dispatcher.client)

    def _on_run(self):
        dummy_thread = threading.currentThread()
        dummy_thread.is_pydev_daemon_thread = False
        return ReaderThread._on_run(self)

    def handle_except(self):
        ReaderThread.handle_except(self)

    def process_command(self, cmd_id, seq, text):
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

    from _pydevd_bundle import pydevd_tracing
    pydevd_tracing.restore_sys_set_trace_func()

    if port is not None:
        global connected
        connected = False
        global forked
        forked = True

        custom_frames_container_init()

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


def apply_debugger_options(setup_options):
    """

    :type setup_options: dict[str, bool]
    """
    default_options = {'save-signatures': False, 'qt-support': ''}
    default_options.update(setup_options)
    setup_options = default_options

    debugger = GetGlobalDebugger()
    if setup_options['save-signatures']:
        if pydevd_vm_type.get_vm_type() == pydevd_vm_type.PydevdVmType.JYTHON:
            sys.stderr.write("Collecting run-time type information is not supported for Jython\n")
        else:
            # Only import it if we're going to use it!
            from _pydevd_bundle.pydevd_signature import SignatureFactory
            debugger.signature_factory = SignatureFactory()

    if setup_options['qt-support']:
        enable_qt_support(setup_options['qt-support'])


def patch_stdin(debugger):
    from _pydev_bundle.pydev_console_utils import DebugConsoleStdIn
    orig_stdin = sys.stdin
    sys.stdin = DebugConsoleStdIn(debugger, orig_stdin)


#=======================================================================================================================
# main
#=======================================================================================================================
if __name__ == '__main__':

    # parse the command line. --file is our last argument that is required
    try:
        from _pydevd_bundle.pydevd_command_line_handling import process_command_line
        setup = process_command_line(sys.argv)
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

    fix_getpass.fix_getpass()

    pydev_log.debug("Executing file %s" % setup['file'])
    pydev_log.debug("arguments: %s"% str(sys.argv))


    pydevd_vm_type.setup_type(setup.get('vm_type', None))

    if os.getenv('PYCHARM_DEBUG') == 'True' or os.getenv('PYDEV_DEBUG') == 'True':
        set_debug(setup)

    DebugInfoHolder.DEBUG_RECORD_SOCKET_READS = setup.get('DEBUG_RECORD_SOCKET_READS', DebugInfoHolder.DEBUG_RECORD_SOCKET_READS)
    DebugInfoHolder.DEBUG_TRACE_BREAKPOINTS = setup.get('DEBUG_TRACE_BREAKPOINTS', DebugInfoHolder.DEBUG_TRACE_BREAKPOINTS)
    DebugInfoHolder.DEBUG_TRACE_LEVEL = setup.get('DEBUG_TRACE_LEVEL', DebugInfoHolder.DEBUG_TRACE_LEVEL)

    port = setup['port']
    host = setup['client']
    f = setup['file']
    fix_app_engine_debug = False

    debugger = PyDB()

    try:
        from _pydev_bundle import pydev_monkey
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
        from _pydevd_bundle import pydevd_stackless
        pydevd_stackless.patch_stackless()
    except:
        # It's ok not having stackless there...
        try:
            sys.exc_clear()  # the exception information should be cleaned in Python 2
        except:
            pass

    is_module = setup['module']
    patch_stdin(debugger)

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
        if setup['save-threading']:
            debugger.thread_analyser = ThreadingLogger()
        if setup['save-asyncio']:
            if IS_PY34_OR_GREATER:
                debugger.asyncio_analyser = AsyncioLogger()

        apply_debugger_options(setup)

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


