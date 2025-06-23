"""
Entry point module (keep at root):

This module starts the debugger.
"""
import os
import sys
from contextlib import contextmanager
import weakref

if sys.version_info[:2] < (2, 6):
    raise RuntimeError('The PyDev.Debugger requires Python 2.6 onwards to be run. If you need to use an older Python version, use an older version of the debugger.')

# allow the debugger to work in isolated mode Python
here = os.path.dirname(os.path.abspath(__file__))
if here not in sys.path:
    sys.path.insert(0, here)

from _pydevd_bundle.pydevd_collect_try_except_info import collect_return_info

import itertools
import atexit
import traceback
from functools import partial
from collections import defaultdict
from socket import SHUT_RDWR

from _pydevd_bundle.pydevd_constants import (
    IS_PYCHARM,
    IS_PY34_OR_GREATER,
    IS_PY36_OR_GREATER,
    IS_PY2,
    IS_CPYTHON,
    PYTHON_SUSPEND,
    STATE_SUSPEND,
    STATE_RUN,
    INTERACTIVE_MODE_AVAILABLE,
    SHOW_DEBUG_INFO_ENV,
    NULL,
    NO_FTRACE,
    GOTO_HAS_RESPONSE,
    USE_LOW_IMPACT_MONITORING,
    HALT_VARIABLE_RESOLVE_THREADS_ON_STEP_RESUME,
    IGNORE_BASENAMES_STARTING_WITH,
    get_thread_id,
    get_current_thread_id,
    get_frame,
    dict_keys,
    dict_iter_items,
    DebugInfoHolder,
    xrange,
    clear_cached_thread_id,
    dummy_excepthook,
    ForkSafeLock,
    )
from _pydevd_bundle.pydevd_comm_constants import CMD_STEP_INTO_COROUTINE
from _pydev_bundle import fix_getpass, pydev_imports, pydev_log
from _pydev_bundle._pydev_filesystem_encoding import getfilesystemencoding
from _pydev_bundle.pydev_is_thread_alive import is_thread_alive
from _pydev_imps._pydev_saved_modules import threading, time, thread
from _pydevd_bundle import pydevd_io, pydevd_vm_type, pydevd_frame_utils, pydevd_utils, pydevd_vars
import pydevd_tracing
from _pydev_bundle.pydev_override import overrides
from _pydevd_bundle.pydevd_breakpoints import (ExceptionBreakpoint,set_fallback_excepthook, disable_excepthook)
from _pydevd_bundle.pydevd_comm import (
    CMD_SET_BREAK,
    CMD_SET_NEXT_STATEMENT,
    CMD_STEP_INTO,
    CMD_STEP_OVER,
    CMD_STEP_RETURN,
    CMD_STEP_INTO_MY_CODE,
    CMD_THREAD_SUSPEND,
    CMD_RUN_TO_LINE,
    CMD_ADD_EXCEPTION_BREAK,
    CMD_SMART_STEP_INTO,
    InternalConsoleExec,
    InternalGetBreakpointException,
    InternalSendCurrExceptionTrace,
    InternalSendCurrExceptionTraceProceeded,
    NetCommandFactory,
    PyDBDaemonThread,
    ReaderThread,
    WriterThread,
    _queue,
    GetGlobalDebugger,
    CommunicationRole,
    get_global_debugger,
    set_global_debugger,
    pydevd_log,
    start_client,
    start_server,
    create_server_socket)
from _pydevd_bundle.pydevd_custom_frames import CustomFramesContainer, custom_frames_container_init
from _pydevd_bundle.pydevd_frame_utils import add_exception_to_frame, remove_exception_from_frame
from _pydevd_bundle.pydevd_trace_dispatch import trace_dispatch as _trace_dispatch, show_tracing_warning
from _pydevd_frame_eval.pydevd_frame_eval_main import (frame_eval_func, clear_thread_local_info, dummy_trace_dispatch, show_frame_eval_warning)
from _pydevd_bundle.pydevd_pep_669_tracing_wrapper import (enable_pep669_monitoring, restart_events, disable_pep669_monitoring)
from _pydevd_bundle.pydevd_additional_thread_info import set_additional_thread_info
from _pydevd_bundle.pydevd_utils import (save_main_module, is_current_thread_main_thread, kill_thread, import_attr_from_module)
from pydevd_concurrency_analyser.pydevd_concurrency_logger import ThreadingLogger, AsyncioLogger, send_message, cur_time
from pydevd_concurrency_analyser.pydevd_thread_wrappers import wrap_threads, wrap_asyncio
from pydevd_file_utils import get_fullname, rPath, get_package_dir, basename, get_abs_path_real_path_and_base_from_frame, NORM_PATHS_AND_BASE_CONTAINER
import pydev_ipython  # @UnusedImport
from _pydevd_bundle.pydevd_dont_trace_files import DONT_TRACE, DONT_TRACE_DIRS, PYDEV_FILE, LIB_FILE, LIB_FILES_IN_DONT_TRACE_DIRS
from _pydevd_bundle.pydevd_asyncio_provider import get_apply
from _pydev_bundle._pydev_saved_modules import ThreadingEvent
from _pydevd_bundle.pydevd_thread_lifecycle import mark_thread_suspended, suspend_all_threads
from _pydevd_bundle.pydevd_timeout import TimeoutTracker

get_file_type = DONT_TRACE.get
suspend_threads_lock = ForkSafeLock()

__version_info__ = (1, 4, 0)
__version_info_str__ = []
for v in __version_info__:
    __version_info_str__.append(str(v))

__version__ = '.'.join(__version_info_str__)

# IMPORTANT: pydevd_constants must be the 1st thing defined because it'll keep a reference to the original sys._getframe


def install_breakpointhook(pydevd_breakpointhook=None):
    if pydevd_breakpointhook is None:
        from _pydevd_bundle.pydevd_breakpointhook import breakpointhook
        pydevd_breakpointhook = breakpointhook
    if sys.version_info >= (3, 7):
        # There are some choices on how to provide the breakpoint hook. Namely, we can provide a
        # PYTHONBREAKPOINT which provides the import path for a method to be executed or we
        # can override sys.breakpointhook.
        # pydevd overrides sys.breakpointhook instead of providing an environment variable because
        # it's possible that the debugger starts the user program but is not available in the
        # PYTHONPATH (and would thus fail to be imported if PYTHONBREAKPOINT was set to pydevd.settrace).
        # Note that the implementation still takes PYTHONBREAKPOINT in account (so, if it was provided
        # by someone else, it'd still work).
        sys.breakpointhook = pydevd_breakpointhook


# Install the breakpoint hook at import time.
install_breakpointhook()

from _pydevd_bundle.pydevd_plugin_utils import PluginManager

threadingEnumerate = threading.enumerate
threadingCurrentThread = threading.current_thread

original_excepthook = sys.__excepthook__

try:
    'dummy'.encode('utf-8') # Added because otherwise Jython 2.2.1 wasn't finding the encoding (if it wasn't loaded in the main thread).
except:
    pass

buffer_stdout_to_server = False
buffer_stderr_to_server = False
remote = False
forked = False

file_system_encoding = getfilesystemencoding()
log_exception = traceback.print_exc

_CACHE_FILE_TYPE = {}
TIMEOUT_SLOW = 0.2
TIMEOUT_FAST = 1.0 / 50


# =======================================================================================================================
# PyDBCommandThread
# =======================================================================================================================
class PyDBCommandThread(PyDBDaemonThread):
    def __init__(self, py_db):
        PyDBDaemonThread.__init__(self, py_db)
        self._py_db_command_thread_event = py_db._py_db_command_thread_event
        self.name = 'pydevd.CommandThread'

    @overrides(PyDBDaemonThread._on_run)
    def _on_run(self):
        # Delay a bit this initialization to wait for the main program to start.
        self._py_db_command_thread_event.wait(TIMEOUT_SLOW)

        if self._kill_received:
            return

        try:
            while not self._kill_received:
                try:
                    self.py_db.process_internal_commands()
                except:
                    pydevd_log(0, 'Finishing debug communication...(2)')
                self._py_db_command_thread_event.clear()
                self._py_db_command_thread_event.wait(TIMEOUT_SLOW)
        except:
            try:
                pydev_log.debug(sys.exc_info()[0])
            except:
                pass
            # only got this error in interpreter shutdown
            # pydevd_log(0, 'Finishing debug communication...(3)')

    def do_kill_pydev_thread(self):
        PyDBDaemonThread.do_kill_pydev_thread(self)
        # Set flag so that it can exit before the usual timeout.
        self._py_db_command_thread_event.set()


# =======================================================================================================================
# CheckOutputThread
# Non-daemon thread: guarantees that all data is written even if program is finished
# =======================================================================================================================
class CheckAliveThread(PyDBDaemonThread):
    def __init__(self, py_db):
        PyDBDaemonThread.__init__(self, py_db)
        self.name = 'pydevd.CheckAliveThread'
        self.daemon = False
        self._wait_event = ThreadingEvent()

    @overrides(PyDBDaemonThread._on_run)
    def _on_run(self):
        py_db = self.py_db

        def can_exit():
            with py_db._main_lock:
                # Note: it's important to get the lock besides checking that it's empty (this
                # means that we're not in the middle of some command processing).
                writer = py_db.writer
                writer_empty = writer is not None and writer.empty()

            return (not py_db.has_threads_alive() or py_db.check_alive_thread) and writer_empty

        try:
            while not self._kill_received:
                self._wait_event.wait(TIMEOUT_SLOW)
                if can_exit():
                    break

                py_db.check_output_redirect()

            if can_exit():
                pydev_log.debug("No threads alive, finishing debug session")
                py_db.dispose_and_kill_all_pydevd_threads()
        except:
            log_exception()

    def join(self, timeout=None):
        # If someone tries to join this thread, mark it to be killed.
        # This is the case for CherryPy when auto-reload is turned on.
        self.do_kill_pydev_thread()
        PyDBDaemonThread.join(self, timeout=timeout)

    @overrides(PyDBDaemonThread.do_kill_pydev_thread)
    def do_kill_pydev_thread(self):
        PyDBDaemonThread.do_kill_pydev_thread(self)
        # Set flag so that it can exit before the usual timeout.
        self._wait_event.set()


class TrackedLock(object):
    """
    The lock that tracks if it has been acquired by the current thread
    """
    def __init__(self):
        self._lock = thread.allocate_lock()
        # thread-local storage
        self._tls = threading.local()
        self._tls.is_lock_acquired = False

    def acquire(self):
        self._lock.acquire()
        self._tls.is_lock_acquired = True

    def release(self):
        self._lock.release()
        self._tls.is_lock_acquired = False

    def __enter__(self):
        self.acquire()

    def __exit__(self, exc_type, exc_val, exc_tb):
        self.release()

    def is_acquired_by_current_thread(self):
        return self._tls.is_lock_acquired


class AbstractSingleNotificationBehavior(object):
    """
    The basic usage should be:

    # Increment the request time for the suspend.
    single_notification_behavior.increment_suspend_time()

    # Notify that this is a pause request (when a pause, not a breakpoint).
    single_notification_behavior.on_pause()

    # Mark threads to be suspended.
    set_suspend(...)

    # On do_wait_suspend, use notify_thread_suspended:
    def do_wait_suspend(...):
        with single_notification_behavior.notify_thread_suspended(thread_id):
            ...
    """

    __slots__ = [
        '_last_resume_notification_time',
        '_last_suspend_notification_time',
        '_lock',
        '_next_request_time',
        '_suspend_time_request',
        '_suspended_thread_id_to_thread',
        '_pause_requested',
        '_py_db',
    ]

    NOTIFY_OF_PAUSE_TIMEOUT = .5

    def __init__(self, py_db):
        self._py_db = weakref.ref(py_db)
        self._next_request_time = partial(next, itertools.count())
        self._last_suspend_notification_time = -1
        self._last_resume_notification_time = -1
        self._suspend_time_request = self._next_request_time()
        self._lock = thread.allocate_lock()
        self._suspended_thread_id_to_thread = {}
        self._pause_requested = False

    def send_suspend_notification(self, thread_id, thread, stop_reason):
        raise AssertionError('abstract: subclasses must override.')

    def send_resume_notification(self, thread_id):
        raise AssertionError('abstract: subclasses must override.')

    def increment_suspend_time(self):
        with self._lock:
            self._suspend_time_request = self._next_request_time()

    def on_pause(self):
        # Upon a pause, we should force sending new suspend notifications
        # if no notification is sent after some time and there's some thread already stopped.
        with self._lock:
            self._pause_requested = True
            global_suspend_time = self._suspend_time_request
        py_db = self._py_db()
        if py_db is not None:
            py_db.timeout_tracker.call_on_timeout(
                self.NOTIFY_OF_PAUSE_TIMEOUT,
                self._notify_after_timeout,
                kwargs={"global_suspend_time": global_suspend_time}
            )

    def _notify_after_timeout(self, global_suspend_time):
        time.sleep(self.NOTIFY_OF_PAUSE_TIMEOUT)
        with self._lock:
            if self._suspended_thread_id_to_thread:
                if global_suspend_time > self._last_suspend_notification_time:
                    self._last_suspend_notification_time = global_suspend_time
                    # Notify about any thread which is currently suspended.
                    pydev_log.info("Sending suspend notification after timeout.")
                    thread_id, thread = next(iter(self._suspended_thread_id_to_thread.items()))
                    self.send_suspend_notification(thread_id, thread, CMD_THREAD_SUSPEND)

    def on_thread_suspend(self, thread_id, thread, stop_reason):
        with self._lock:
            pause_requested = self._pause_requested
            if pause_requested:
                # When a suspend notification is sent, reset the pause flag.
                self._pause_requested = False

            self._suspended_thread_id_to_thread[thread_id] = thread

            # CMD_THREAD_SUSPEND should always be a side-effect of a break, so, only
            # issue for a CMD_THREAD_SUSPEND if a pause is pending.
            if stop_reason != CMD_THREAD_SUSPEND or pause_requested:
                if self._suspend_time_request > self._last_suspend_notification_time:
                    pydev_log.info("Sending suspend notification.")
                    self._last_suspend_notification_time = self._suspend_time_request
                    self.send_suspend_notification(thread_id, thread, stop_reason)
                else:
                    pydev_log.info(
                        "Suspend not sent (it was already sent). Last suspend {sus} <= Last resume {res}".format(
                            sus=self._suspend_time_request,
                            res=self._last_resume_notification_time
                        )
                    )
            else:
                pydev_log.info("Suspend not sent because stop reason is thread suspend and pause was not requested.")

    def on_thread_resume(self, thread_id, thread):
        # on resume (step, continue all):
        with self._lock:
            self._suspended_thread_id_to_thread.pop(thread_id)
            if self._last_resume_notification_time < self._last_suspend_notification_time:
                pydev_log.info("Sending resume notification.")
                self._last_resume_notification_time = self._last_suspend_notification_time
                self.send_resume_notification(thread_id)
            else:
                pydev_log.info(
                    "Resume not sent (it was already sent). Last resume {res} >= Last suspend {sus}".format(
                        res=self._last_resume_notification_time,
                        sus=self._last_suspend_notification_time
                    )
                )

    @contextmanager
    def notify_thread_suspended(self, thread_id, thread, stop_reason):
        self.on_thread_suspend(thread_id, thread, stop_reason)
        try:
            yield  # At this point the thread must be actually suspended.
        finally:
            self.on_thread_resume(thread_id, thread)


class ThreadsSuspendedSingleNotification(AbstractSingleNotificationBehavior):
    __slots__ = AbstractSingleNotificationBehavior.__slots__ + [
        "multi_threads_single_notification", "_callbacks", "_callbacks_lock"]

    def __init__(self, py_db):
        AbstractSingleNotificationBehavior.__init__(self, py_db)
        # If True, pydevd will send a single notification when all threads are suspended/resumed.
        self.multi_threads_single_notification = False
        self._callbacks_lock = threading.Lock()
        self._callbacks = []

    def add_on_resumed_callback(self, callback):
        with self._callbacks_lock:
            self._callbacks.append(callback)

    @overrides(AbstractSingleNotificationBehavior.send_resume_notification)
    def send_resume_notification(self, thread_id):
        py_db = self._py_db()
        if py_db is not None:
            py_db.writer.add_command(py_db.cmd_factory.make_thread_resume_single_notification(thread_id))

            with self._callbacks_lock:
                callbacks = self._callbacks
                self._callbacks = []

            for callback in callbacks:
                callback()

    @overrides(AbstractSingleNotificationBehavior.send_suspend_notification)
    def send_suspend_notification(self, thread_id, thread, stop_reason):
        py_db = self._py_db()
        if py_db is not None:
            py_db.writer.add_command(py_db.cmd_factory.make_thread_suspend_single_notification(thread_id, stop_reason))

    @overrides(AbstractSingleNotificationBehavior.notify_thread_suspended)
    @contextmanager
    def notify_thread_suspended(self, thread_id, stop_reason):
        if self.multi_threads_single_notification:
            pydev_log.debug("Thread suspend mode: single notification")
            with AbstractSingleNotificationBehavior.notify_thread_suspended(self, thread_id, thread, stop_reason):
                yield
        else:
            pydev_log.debug("Thread suspend mode: NOT single notification")
            yield


# =======================================================================================================================
# PyDB
# =======================================================================================================================
class PyDB(object):
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

    def __init__(self, set_as_global=True):
        if set_as_global:
            pydevd_tracing.replace_sys_set_trace_func()

        self.reader = None
        self.writer = None
        self.created_pydb_daemon_threads = {}
        self._waiting_for_connection_thread = None
        self.check_alive_thread = None
        self.py_db_command_thread = None
        self.quitting = None
        self.cmd_factory = NetCommandFactory()
        self._cmd_queue = defaultdict(_queue.Queue)  # Key is thread id or '*', value is Queue
        self._thread_events = defaultdict(ThreadingEvent)  # Key is thread id or '*', value is Event
        self.timeout_tracker = TimeoutTracker(self)

        self.breakpoints = {}

        self.__user_type_renderers = {}

        # mtime to be raised when breakpoints change
        self.mtime = 0

        self.file_to_id_to_line_breakpoint = {}
        self.file_to_id_to_plugin_breakpoint = {}

        # Note: breakpoints dict should not be mutated: a copy should be created
        # and later it should be assigned back (to prevent concurrency issues).
        self.break_on_uncaught_exceptions = {}
        self.break_on_caught_exceptions = {}

        self.ready_to_run = False
        self._main_lock = TrackedLock()
        self._lock_running_thread_ids = thread.allocate_lock()
        self._py_db_command_thread_event = ThreadingEvent()
        if set_as_global:
            CustomFramesContainer._py_db_command_thread_event = self._py_db_command_thread_event

        self.pydb_disposed = False
        self._wait_for_threads_to_finish_called = False
        self._wait_for_threads_to_finish_called_lock = thread.allocate_lock()
        self._wait_for_threads_to_finish_called_event = ThreadingEvent()

        self.terminate_requested = False
        self._disposed_lock = thread.allocate_lock()
        self.signature_factory = None
        self.SetTrace = pydevd_tracing.SetTrace
        self.skip_on_exceptions_thrown_in_same_context = False
        self.ignore_exceptions_thrown_in_lines_with_ignore_exception = True

        # Suspend debugger even if breakpoint condition raises an exception.
        # May be changed with CMD_PYDEVD_JSON_CONFIG.
        self.skip_suspend_on_breakpoint_exception = ()  # By default suspend on any Exception.
        self.skip_print_breakpoint_exception = ()  # By default print on any Exception.

        # By default user can step into properties getter/setter/deleter methods
        self.disable_property_trace = False
        self.disable_property_getter_trace = False
        self.disable_property_setter_trace = False
        self.disable_property_deleter_trace = False

        # this is a dict of thread ids pointing to thread ids. Whenever a command is passed to the java end that
        # acknowledges that a thread was created, the thread id should be passed here -- and if at some time we do not
        # find that thread alive anymore, we must remove it from this list and make the java side know that the thread
        # was killed.
        self._running_thread_ids = {}
        self._enable_thread_notifications = False
        self._set_breakpoints_with_id = False

        # This attribute holds the file-> lines which have an @IgnoreException.
        self.filename_to_lines_where_exceptions_are_ignored = {}

        # working with plugins (lazily initialized)
        self.plugin = None
        self.has_plugin_line_breaks = False
        self.has_plugin_exception_breaks = False
        self.thread_analyser = None
        self.asyncio_analyser = None

        # The GUI event loop that's going to run.
        # Possible values:
        # matplotlib - Whatever GUI backend matplotlib is using.
        # 'wx'/'qt'/'none'/... - GUI toolkits that have bulitin support. See pydevd_ipython/inputhook.py:24.
        # Other - A custom function that'll be imported and run.
        self._gui_event_loop = "matplotlib"
        self._installed_gui_support = False
        self.gui_in_use = False

        # GUI event loop support in debugger
        self.activate_gui_function = None

        # matplotlib support in debugger and debug console
        self.mpl_hooks_in_debug_console = False
        self.mpl_modules_for_patching = {}

        self._filename_to_not_in_scope = {}
        self.first_breakpoint_reached = False
        self._is_libraries_filter_enabled = pydevd_utils.is_filter_libraries()
        self.is_files_filter_enabled = pydevd_utils.is_filter_enabled()

        self.show_return_values = False
        self.remove_return_values_flag = False
        self.redirect_output = False

        # this flag disables frame evaluation even if it's available
        self.use_frame_eval = True
        self.stop_on_start = False

        # If True, pydevd will send a single notification when all threads are suspended/resumed.
        self._threads_suspended_single_notification = ThreadsSuspendedSingleNotification(self)

        self._local_thread_trace_func = threading.local()

        self._server_socket_ready_event = threading.Event()

        self.frame_eval_func = frame_eval_func
        self.dummy_trace_dispatch = dummy_trace_dispatch

        try:
            self.threading_get_ident = threading.get_ident  # Python 3
            self.threading_active = threading._active
        except:
            try:
                self.threading_get_ident = threading._get_ident  # Python 2 noqa
                self.threading_active = threading._active
            except:
                self.threading_get_ident = None  # Jython
                self.threading_active = None

        self._dont_trace_get_file_type = DONT_TRACE.get
        self._dont_trace_dirs_get_file_type = DONT_TRACE_DIRS.get
        self.PYDEV_FILE = PYDEV_FILE
        self.LIB_FILE = LIB_FILE

        self._in_project_scope_cache = {}
        self._exclude_by_filter_cache = {}
        self._apply_filter_cache = {}
        self._ignore_system_exit_codes = set()

        # sequence id of `CMD_PROCESS_CREATED` command -> threading.Event
        self.process_created_msg_received_events = dict()
        # the role PyDB plays in the communication with IDE
        self.communication_role = None

        self.collect_return_info = collect_return_info

        # If True, pydevd will stop on assertion errors in tests.
        self.stop_on_failed_tests = False

        # If True, pydevd finished all work and only waits check_alive_thread
        self.wait_output_checker_thread = False

        self._exception_breakpoints_change_callbacks = set()

        self.is_pep669_monitoring_enabled = False

        if USE_LOW_IMPACT_MONITORING:
            from _pydevd_bundle.pydevd_pep_669_tracing_wrapper import (
                global_cache_skips, global_cache_frame_skips)
        else:
            from _pydevd_bundle.pydevd_trace_dispatch import (
                global_cache_skips, global_cache_frame_skips)

        self._global_cache_skips = global_cache_skips
        self._global_cache_frame_skips = global_cache_frame_skips

        self.value_resolve_thread_list = []

        if set_as_global:
            # All debugger fields need to be initialized first. If they aren't,
            # there can be instances in a multithreaded environment where the debugger
            # is accessible but the attempts to access its fields may fail.
            set_global_debugger(self)

        # Stop the tracing as the last thing before the actual shutdown for a clean exit.
        atexit.register(stoptrace)

    def wait_for_ready_to_run(self):
        while not self.ready_to_run:
            # busy wait until we receive run command
            self.process_internal_commands()
            self._py_db_command_thread_event.clear()
            self._py_db_command_thread_event.wait(TIMEOUT_FAST)

    def _internal_get_file_type(self, abs_real_path_and_basename):
        basename = abs_real_path_and_basename[-1]
        if basename.startswith(IGNORE_BASENAMES_STARTING_WITH) or \
                abs_real_path_and_basename[0].startswith(IGNORE_BASENAMES_STARTING_WITH):
            # Note: these are the files that are completely ignored (they aren't shown to the user
            # as user nor library code as it's usually just noise in the frame stack).
            return self.PYDEV_FILE
        file_type = self._dont_trace_get_file_type(basename)
        if file_type is not None:
            return file_type

        if basename.startswith("__init__.py") or basename in LIB_FILES_IN_DONT_TRACE_DIRS:
            # i.e.: ignore the __init__ files inside pydevd (the other
            # files are ignored just by their name).
            abs_path = abs_real_path_and_basename[0]
            i = max(abs_path.rfind("/"), abs_path.rfind("\\"))
            if i:
                abs_path = abs_path[0:i]
                i = max(abs_path.rfind("/"), abs_path.rfind("\\"))
                if i:
                    dirname = abs_path[i + 1:]
                    # At this point, something as:
                    # "my_path\_pydev_runfiles\__init__.py"
                    # is now  "_pydev_runfiles".
                    return self._dont_trace_dirs_get_file_type(dirname)
        return None

    def get_file_type(self, frame, abs_real_path_and_basename=None, _cache_file_type=_CACHE_FILE_TYPE):
        """
        :param abs_real_path_and_basename:
            The result from get_abs_path_real_path_and_base_from_file or
            get_abs_path_real_path_and_base_from_frame.

        :return
            _pydevd_bundle.pydevd_dont_trace_files.PYDEV_FILE:
                If it's a file internal to the debugger which shouldn't be
                traced nor shown to the user.

            _pydevd_bundle.pydevd_dont_trace_files.LIB_FILE:
                If it's a file in a library which shouldn't be traced.

            None:
                If it's a regular user file which should be traced.
        """
        if abs_real_path_and_basename is None:
            try:
                # Make fast path faster!
                abs_real_path_and_basename = NORM_PATHS_AND_BASE_CONTAINER[frame.f_code.co_filename]
            except:
                abs_real_path_and_basename = get_abs_path_real_path_and_base_from_frame(frame)

        # Note 1: we have to take into account that we may have files as '<string>', and that in
        # this case the cache key can't rely only on the filename. With the current cache, there's
        # still a potential miss if 2 functions which have exactly the same content are compiled
        # with '<string>', but in practice as we only separate the one from python -c from the rest
        # this shouldn't be a problem in practice.

        # Note 2: firstlineno added to make misses faster in the first comparison.

        # Note 3: this cache key is repeated in pydevd_frame_evaluator.pyx:get_func_code_info (for
        # speedups).
        if isinstance(frame.f_code, pydevd_frame_utils.FCode):
            return get_file_type(abs_real_path_and_basename[-1])

        cache_key = (frame.f_code.co_firstlineno, abs_real_path_and_basename[0], frame.f_code)
        try:
            return _cache_file_type[cache_key]
        except:
            if abs_real_path_and_basename[0] == "<string>":
                f = frame.f_back
                while f is not None:
                    if (self.get_file_type(f) != self.PYDEV_FILE and
                            basename(f.f_code.co_filename) not in ("runpy.py","<string>",)):
                        # We found some back frame that's not internal, which means we must consider
                        # this a library file.
                        # This is done because we only want to trace files as <string> if they don't
                        # have any back frame (which is the case for python -c ...), for all other
                        # cases we don't want to trace them because we can't show the source to the
                        # user (at least for now...).

                        # Note that we return as a LIB_FILE and not PYDEV_FILE because we still want
                        # to show it in the stack.
                        _cache_file_type[cache_key] = LIB_FILE
                        return LIB_FILE

                    f = f.f_back
                else:
                    # This is a top-level file (used in python -c), so, trace it as usual... we
                    # still won't be able to show the sources, but some tests require this to work.
                    _cache_file_type[cache_key] = None
                    return None

            file_type = self._internal_get_file_type(abs_real_path_and_basename)

            _cache_file_type[cache_key] = file_type
            return file_type

    def get_cache_file_type(self, _cache=_CACHE_FILE_TYPE):  # i.e.: Make it local.
        return _cache

    def get_thread_local_trace_func(self):
        try:
            thread_trace_func = self._local_thread_trace_func.thread_trace_func
        except AttributeError:
            thread_trace_func = self.trace_dispatch
        return thread_trace_func

    def enable_tracing(self, thread_trace_func=None, apply_to_all_threads=False):
        """
        Enables tracing.

        If in regular mode (tracing), will set the tracing function to the tracing
        function for this thread -- by default it's `PyDB.trace_dispatch`, but after
        `PyDB.enable_tracing` is called with a `thread_trace_func`, the given function will
        be the default for the given thread.
        """
        set_fallback_excepthook()
        if USE_LOW_IMPACT_MONITORING:
            enable_pep669_monitoring()
            return

        if self.frame_eval_func is not None:
            self.frame_eval_func()
            pydevd_tracing.SetTrace(self.dummy_trace_dispatch)

            if IS_CPYTHON and apply_to_all_threads:
                pydevd_tracing.set_trace_to_threads(self.dummy_trace_dispatch)
            return

        if apply_to_all_threads:
            # If applying to all threads, don't use the local thread trace function.
            assert thread_trace_func is not None
        else:
            if thread_trace_func is None:
                thread_trace_func = self.get_thread_local_trace_func()
            else:
                self._local_thread_trace_func.thread_trace_func = thread_trace_func

        pydevd_tracing.SetTrace(thread_trace_func)
        if IS_CPYTHON and apply_to_all_threads:
            pydevd_tracing.set_trace_to_threads(thread_trace_func)

    def disable_tracing(self):
        if USE_LOW_IMPACT_MONITORING:
            disable_pep669_monitoring(all_threads=False)
        else:
            pydevd_tracing.SetTrace(None)

    def maybe_kill_active_value_resolve_threads(self):
        if HALT_VARIABLE_RESOLVE_THREADS_ON_STEP_RESUME:
            for t in self.value_resolve_thread_list:
                kill_thread(t)
            self.value_resolve_thread_list = []

    def on_breakpoints_changed(self, removed=False):
        """
        When breakpoints change, we have to re-evaluate all the assumptions we've made so far.
        """
        if not self.ready_to_run:
            # No need to do anything if we're still not running.
            return

        self.mtime += 1
        if not removed:
            # When removing breakpoints we can leave tracing as was, but if a breakpoint was added
            # we have to reset the tracing for the existing functions to be re-evaluated.
            self.set_tracing_for_untraced_contexts()

    def set_tracing_for_untraced_contexts(self, breakpoints_changed=False):
        # Enable the tracing for existing threads (because there may be frames being executed that
        # are currently untraced).

        if IS_CPYTHON:
            # Note: use sys._current_frames instead of threading.enumerate() because this way
            # we also see C/C++ threads, not only the ones visible to the threading module.
            tid_to_frame = sys._current_frames()

            ignore_thread_ids = set(
                t.ident for t in threadingEnumerate()
                if getattr(t, 'is_pydev_daemon_thread', False) or
                getattr(t, 'pydev_do_not_trace', False)
            )

            for thread_id, frame in tid_to_frame.items():
                if thread_id not in ignore_thread_ids:
                    self.set_trace_for_frame_and_parents(frame)
        else:
            try:
                threads = threadingEnumerate()
                for t in threads:
                    if getattr(t, "is_pydev_daemon_thread", False) or getattr(t, "pydev_do_not_trace", False):
                        continue

                    additional_info = set_additional_thread_info(t)
                    frame = additional_info.get_topmost_frame(t)
                    try:
                        if frame is not None:
                            self.set_trace_for_frame_and_parents(frame)
                    finally:
                        frame = None
            finally:
                frame = None
                t = None
                threads = None
                additional_info = None

    @property
    def multi_threads_single_notification(self):
        return self._threads_suspended_single_notification.multi_threads_single_notification

    @multi_threads_single_notification.setter
    def multi_threads_single_notification(self, notify):
        self._threads_suspended_single_notification.multi_threads_single_notification = notify

    def get_plugin_lazy_init(self):
        if self.plugin is None:
            self.plugin = PluginManager(self)
        return self.plugin

    def in_project_scope(self, filename):
        return pydevd_utils.in_project_roots(filename)

    def is_ignored_by_filters(self, filename):
        return pydevd_utils.is_ignored_by_filter(filename)

    def is_exception_trace_in_project_scope(self, trace):
        return pydevd_utils.is_exception_trace_in_project_scope(trace)

    def is_top_level_trace_in_project_scope(self, trace):
        return pydevd_utils.is_top_level_trace_in_project_scope(trace)

    def is_test_item_or_set_up_caller(self, frame):
        return pydevd_utils.is_test_item_or_set_up_caller(frame)

    def set_unit_tests_debugging_mode(self):
        self.stop_on_failed_tests = True

    def has_threads_alive(self):
        for t in pydevd_utils.get_non_pydevd_threads():
            if isinstance(t, PyDBDaemonThread):
                pydev_log.error_once('Error in debugger: Found PyDBDaemonThread not marked with is_pydev_daemon_thread=True.\n')

            if is_thread_alive(t):
                if not t.daemon or hasattr(t, "__pydevd_main_thread"):
                    return True

        return False

    def initialize_network(self, sock, terminate_on_socket_close=True):
        try:
            sock.settimeout(None)  # infinite, no timeouts from now on - jython does not have it
        except:
            pass

        curr_reader = getattr(self, "reader", None)
        curr_writer = getattr(self, "writer", None)

        if curr_reader:
            curr_reader.do_kill_pydev_thread()
        if curr_writer:
            curr_writer.do_kill_pydev_thread()

        self.writer = WriterThread(sock, self, terminate_on_socket_close=terminate_on_socket_close)
        self.reader = ReaderThread(sock, self, terminate_on_socket_close=terminate_on_socket_close)
        self.writer.start()
        self.reader.start()

        time.sleep(0.1)  # give threads time to start

    def connect(self, host, port):
        if host:
            self.communication_role = CommunicationRole.CLIENT
            s = start_client(host, port)
        else:
            self.communication_role = CommunicationRole.SERVER
            s = start_server(port)

        self.initialize_network(s)

    def create_wait_for_connection_thread(self):
        if self._waiting_for_connection_thread is not None:
            raise AssertionError("There is already another thread waiting for a connection.")

        self._server_socket_ready_event.clear()
        self._waiting_for_connection_thread = self._WaitForConnectionThread(self)
        self._waiting_for_connection_thread.start()

    def set_server_socket_ready(self):
        self._server_socket_ready_event.set()

    class _WaitForConnectionThread(PyDBDaemonThread):
        def __init__(self, py_db):
            PyDBDaemonThread.__init__(self, py_db)
            self._server_socket = None

        def run(self):
            host = SetupHolder.setup["client"]
            port = SetupHolder.setup["port"]

            self._server_socket = create_server_socket(host=host, port=port)
            self.py_db._server_socket_name = self._server_socket.getsockname()
            self.py_db.set_server_socket_ready()

            while not self._kill_received:
                try:
                    s = self._server_socket
                    if s is None:
                        return

                    s.listen(1)
                    new_socket, _addr = s.accept()
                    if self._kill_received:
                        pydev_log.info("Connection (from wait_for_attach) accepted but ignored as kill was already received.")
                        return

                    pydev_log.info("Connection (from wait_for_attach) accepted.")
                    reader = getattr(self.py_db, "reader", None)
                    if reader is not None:
                        self.py_db.set_enable_thread_notifications(False)
                        lst = [self.py_db.file_to_id_to_line_breakpoint,
                               self.py_db.file_to_id_to_plugin_breakpoint,
                               self.py_db.breakpoints
                               ]
                        for file_to_id_to_breakpoint in lst:
                            if file_to_id_to_breakpoint:
                                file_to_id_to_breakpoint.clear()
                        self.py_db.on_disconnect()

                        self.py_db.on_breakpoints_changed(removed=True)

                    self.py_db.initialize_network(new_socket, terminate_on_socket_close=False)

                except:
                    if DebugInfoHolder.DEBUG_TRACE_LEVEL > 0:
                        pydev_log.debug("Exiting _WaitForConnectionThread: %s\n" % port)

        def do_kill_pydev_thread(self):
            PyDBDaemonThread.do_kill_pydev_thread(self)
            s = self._server_socket
            if s is not None:
                try:
                    s.close()
                except:
                    pass
                self._server_socket = None

    def get_internal_queue_and_event(self, thread_id):
        """ returns internal command queue for a given thread.
        if new queue is created, notify the RDB about it """
        if thread_id.startswith('__frame__'):
            thread_id = thread_id[thread_id.rfind('|') + 1:]
        return self._cmd_queue[thread_id], self._thread_events[thread_id]

    def post_internal_command(self, int_cmd, thread_id):
        """ if thread_id is *, post to the '*' queue"""
        queue, event = self.get_internal_queue_and_event(thread_id)
        queue.put(int_cmd)
        if thread_id == "*":
            self._py_db_command_thread_event.set()
        else:
            event.set()

    def enable_output_redirection(self, redirect_stdout, redirect_stderr):
        global buffer_stdout_to_server
        global buffer_stderr_to_server

        buffer_stdout_to_server = redirect_stdout
        buffer_stderr_to_server = redirect_stderr
        self.redirect_output = redirect_stdout or redirect_stderr
        if buffer_stdout_to_server:
            init_stdout_redirect()
        if buffer_stderr_to_server:
            init_stderr_redirect()

    def check_output_redirect(self):
        global buffer_stdout_to_server
        global buffer_stderr_to_server

        if buffer_stdout_to_server:
            init_stdout_redirect()

        if buffer_stderr_to_server:
            init_stderr_redirect()

    def init_gui_in_debug_console(self):
        # import hook and patches for matplotlib support in debug console
        from _pydev_bundle.pydev_import_hook import import_hook_manager
        if is_current_thread_main_thread():
            for module in dict_keys(self.mpl_modules_for_patching):
                import_hook_manager.add_module_name(module, self.mpl_modules_for_patching.pop(module))

    def init_gui_support(self):
        if self._installed_gui_support:
            return
        self._installed_gui_support = True

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

        from pydev_ipython.inputhook import set_return_control_callback, enable_gui
        set_return_control_callback(return_control)

        if self._gui_event_loop == "matplotlib":
            # prepare debugger for integration with matplotlib GUI event loop
            from pydev_ipython.matplotlibtools import activate_matplotlib, activate_pylab, activate_pyplot, do_enable_gui
            self.mpl_modules_for_patching = {
                "matplotlib": lambda: activate_matplotlib(do_enable_gui),
                "matplotlib.pyplot": activate_pyplot,
                "pylab": activate_pylab}
        else:
            self.activate_gui_function = enable_gui

    def _activate_gui_if_needed(self):
        if self.gui_in_use:
            return

        if len(self.mpl_modules_for_patching) > 0:
            if is_current_thread_main_thread():
                for module in dict_keys(self.mpl_modules_for_patching):
                    if module in sys.modules:
                        activate_function = self.mpl_modules_for_patching.pop(module)
                        if activate_function is not None:
                            activate_function()
                        self.gui_in_use = True

        if self.activate_gui_function:
            if is_current_thread_main_thread():  # Only call enable_gui in the main thread.
                try:
                    # First try to activate builtin GUI event loops.
                    self.activate_gui_function(self._gui_event_loop)
                    self.activate_gui_function = None
                    self.gui_in_use = True
                except ValueError:
                    # The user requested a custom GUI event loop, try to import it.
                    from pydev_ipython.inputhook import set_inputhook

                    try:
                        inputhook_function = import_attr_from_module(
                            self._gui_event_loop)
                        set_inputhook(inputhook_function)
                        self.gui_in_use = True
                    except Exception as e:
                        pydev_log.debug("Cannot activate custom GUI event loop {}: {}".format(self._gui_event_loop, e))
                    finally:
                        self.activate_gui_function = None

    def _call_input_hook(self):
        try:
            from pydev_ipython.inputhook import get_inputhook
            inputhook = get_inputhook()
            if inputhook:
                inputhook()
        except:
            pass

    def notify_thread_created(self, thread_id, thread, use_lock=True):
        if self.writer is None:
            # Protect about threads being created before the communication structure is in place
            # (note that they will appear later on anyways as pydevd does reconcile live/dead threads
            # when processing internal commands, albeit it may take longer and in general this should
            # not be usual as it's expected that the debugger is live before other threads are created).
            return

        with self._lock_running_thread_ids if use_lock else NULL:
            if thread_id in self._running_thread_ids:
                return

            additional_info = set_additional_thread_info(thread)
            if additional_info.pydev_notify_kill:
                # After we notify it should be killed, make sure we don't notify it's alive (on a racing condition
                # this could happen as we may notify before the thread is stopped internally).
                return

            self._running_thread_ids[thread_id] = thread

        self.writer.add_command(self.cmd_factory.make_thread_created_message(thread))

    def notify_thread_not_alive(self, thread_id, use_lock=True):
        """ if thread is not alive, cancel trace_dispatch processing """
        if self.writer is None:
            return

        with self._lock_running_thread_ids if use_lock else NULL:
            if not self._enable_thread_notifications:
                return

            thread = self._running_thread_ids.pop(thread_id, None)
            if thread is None:
                return

            set_additional_thread_info(thread)
            was_notified = thread.additional_info.pydev_notify_kill
            if not was_notified:
                thread.additional_info.pydev_notify_kill = True

        self.writer.add_command(self.cmd_factory.make_thread_killed_message(thread_id))

    def set_enable_thread_notifications(self, enable):
        with self._lock_running_thread_ids:
            if self._enable_thread_notifications != enable:
                self._enable_thread_notifications = enable

                if enable:
                    # As it was previously disabled, we have to notify about existing threads again
                    # (so, clear the cache related to that).
                    self._running_thread_ids = {}

    def process_internal_commands(self, process_thread_ids=None):
        """
        This function processes internal commands
        """
        ready_to_run = self.ready_to_run

        dispose = False
        with self._main_lock:
            program_threads_alive = {}
            if ready_to_run:
                self.check_output_redirect()

                all_threads = threadingEnumerate()
                program_threads_dead = []
                with self._lock_running_thread_ids:
                    reset_cache = not self._running_thread_ids

                    for t in all_threads:
                        if getattr(t, 'is_pydev_daemon_thread', False):
                            pass  # I.e.: skip the DummyThreads created from pydev daemon threads
                        elif isinstance(t, PyDBDaemonThread):
                            pydev_log.error_once('Error in debugger: Found PyDBDaemonThread not marked with is_pydev_daemon_thread=True.\n')

                        elif is_thread_alive(t):
                            if reset_cache:
                                # Fix multiprocessing debug with breakpoints in both main and child processes
                                # (https://youtrack.jetbrains.com/issue/PY-17092) When the new process is created, the main
                                # thread in the new process already has the attribute 'pydevd_id', so the new thread doesn't
                                # get new id with its process number and the debugger loses access to both threads.
                                # Therefore we should update thread_id for every main thread in the new process.
                                clear_cached_thread_id(t)

                            thread_id = get_thread_id(t)
                            program_threads_alive[thread_id] = t

                            self.notify_thread_created(thread_id, t, use_lock=False)

                    # Compute and notify about threads which are no longer alive.
                    thread_ids = list(self._running_thread_ids.keys())
                    for thread_id in thread_ids:
                        if thread_id not in program_threads_alive:
                            program_threads_dead.append(thread_id)

                    for thread_id in program_threads_dead:
                        self.notify_thread_not_alive(thread_id, use_lock=False)

            cmds_to_execute = []

            # Without self._lock_running_thread_ids
            if len(program_threads_alive) == 0 and ready_to_run:
                dispose = True
            else:
                # Actually process the commands now (make sure we don't have a lock for _lock_running_thread_ids
                # acquired at this point as it could lead to a deadlock if some command evaluated tried to
                # create a thread and wait for it -- which would try to notify about it getting that lock).
                curr_thread_id = get_current_thread_id(threadingCurrentThread())
                if process_thread_ids is None:
                    if ready_to_run:
                        process_thread_ids = (curr_thread_id, "*")
                    else:
                        process_thread_ids = ("*",)

                for thread_id in process_thread_ids:
                    queue, _event = self.get_internal_queue_and_event(thread_id)

                    # some commands must be processed by the thread itself... if that's the case,
                    # we will re-add the commands to the queue after executing.
                    cmds_to_add_back = []

                    try:
                        while True:
                            int_cmd = queue.get(False)
                            try:
                                if not self.mpl_hooks_in_debug_console and isinstance(int_cmd, InternalConsoleExec):
                                    # add import hooks for matplotlib patches if only debug console was started
                                    try:
                                        self.init_gui_in_debug_console()
                                        self.gui_in_use = True
                                    except:
                                        pydevd_log(2,"Matplotlib support in debug console failed", traceback.format_exc())
                                    self.mpl_hooks_in_debug_console = True

                                if int_cmd.can_be_executed_by(curr_thread_id):
                                    pydevd_log(2, "processing internal command ", str(int_cmd)), cmds_to_execute.append(int_cmd)
                                else:
                                    pydevd_log(2, "NOT processing internal command ", str(int_cmd))
                                    cmds_to_add_back.append(int_cmd)
                            except:
                                log_exception()
                                raise

                    except _queue.Empty:  # @UndefinedVariable
                        # this is how we exit
                        for int_cmd in cmds_to_add_back:
                            queue.put(int_cmd)

        if dispose:
            self.dispose_and_kill_all_pydevd_threads()
        else:
            # Actually execute the commands without the main lock!
            for internal_cmd in cmds_to_execute:
                pydev_log.debug("processing internal command: %s" % internal_cmd)
                try:
                    internal_cmd.do_it(self)
                except:
                    pydev_log.error("Error processing internal command.")

    def consolidate_breakpoints(self, canonical_normalized_filename, id_to_breakpoint, file_to_line_to_breakpoints):
        break_dict = {}
        for breakpoint_id, pybreakpoint in dict_iter_items(id_to_breakpoint):
            break_dict[pybreakpoint.line] = pybreakpoint

        file_to_line_to_breakpoints[canonical_normalized_filename] = break_dict
        self.clear_skip_caches()

    def clear_skip_caches(self):
        self._global_cache_skips.clear()
        self._global_cache_frame_skips.clear()
        if USE_LOW_IMPACT_MONITORING:
            restart_events()

    def add_break_on_exception(
            self,
            exception,
            condition,
            expression,
            notify_on_handled_exceptions,
            notify_on_unhandled_exceptions,
            notify_on_first_raise_only,
            ignore_libraries=False
    ):
        try:
            eb = ExceptionBreakpoint(
                exception,
                condition,
                expression,
                notify_on_handled_exceptions,
                notify_on_unhandled_exceptions,
                notify_on_first_raise_only,
                ignore_libraries
            )
        except ImportError:
            pydev_log.error("Error unable to add break on exception for: %s (exception could not be imported)\n" % (exception,))
            return None

        if eb.notify_on_unhandled_exceptions:
            cp = self.break_on_uncaught_exceptions.copy()
            cp[exception] = eb
            if DebugInfoHolder.DEBUG_TRACE_BREAKPOINTS > 0:
                pydev_log.error("Exceptions to hook on terminate: %s\n" % (cp,))
            self.break_on_uncaught_exceptions = cp

        if eb.notify_on_handled_exceptions:
            cp = self.break_on_caught_exceptions.copy()
            cp[exception] = eb
            if DebugInfoHolder.DEBUG_TRACE_BREAKPOINTS > 0:
                pydev_log.error("Exceptions to hook always: %s\n" % (cp,))
            self.break_on_caught_exceptions = cp

        pydev_log.debug("Added exception breakpoint: %r" % eb)

        self._notify_exception_breakpoints_change(eb)

        return eb

    def add_exception_breakpoints_change_callback(self, callback):
        self._exception_breakpoints_change_callbacks.add(callback)

    def remove_exception_breakpoints_change_callback(self, callback):
        self._exception_breakpoints_change_callbacks.remove(callback)

    def _notify_exception_breakpoints_change(self, eb):
        for callback in self._exception_breakpoints_change_callbacks:
            callback(self, eb)

    def set_user_type_renderers(self, renderers):
        self.__user_type_renderers = renderers

    def get_user_type_renderers(self):
        return self.__user_type_renderers

    def set_suspend(self, thread, stop_reason, suspend_other_threads=False, is_pause=False):
        """
        :param thread:
            The thread which should be suspended.

        :param stop_reason:
            Reason why the thread was suspended.

        :param suspend_other_threads:
            Whether to force other threads to be suspended (i.e.: when hitting a breakpoint
            with a suspend all threads policy).

        :param is_pause:
            If this is a pause to suspend all threads, any thread can be considered as the 'main'
            thread paused.
        """
        self._threads_suspended_single_notification.increment_suspend_time()
        if is_pause:
            self._threads_suspended_single_notification.on_pause()

        with suspend_threads_lock:
            info = mark_thread_suspended(thread, stop_reason)
            if not suspend_other_threads and self.multi_threads_single_notification:
                # In the mode which gives a single notification when all threads are
                # stopped, stop all threads whenever a set_suspend is issued.
                suspend_other_threads = True
            if suspend_other_threads:
                # Suspend all except the current one (which we're currently suspending already).
                suspend_all_threads(self, except_thread=thread)

        if is_pause:
            # Must set tracing after setting the state to suspend.
            frame = info.get_topmost_frame(thread)
            if frame is not None:
                try:
                    self.set_trace_for_frame_and_parents(frame)
                finally:
                    frame = None

        # If conditional breakpoint raises any exception during evaluation send the details to the client.
        if stop_reason == CMD_SET_BREAK and info.conditional_breakpoint_exception is not None:
            conditional_breakpoint_exception_tuple = info.conditional_breakpoint_exception
            info.conditional_breakpoint_exception = None
            self._send_breakpoint_condition_exception(thread, conditional_breakpoint_exception_tuple)

    def _send_breakpoint_condition_exception(self, thread, conditional_breakpoint_exception_tuple):
        """If conditional breakpoint raises an exception during evaluation
        send exception details to java
        """
        thread_id = get_thread_id(thread)
        # conditional_breakpoint_exception_tuple - should contain 2 values (exception_type, stacktrace)
        if conditional_breakpoint_exception_tuple and len(conditional_breakpoint_exception_tuple) == 2:
            exc_type, stacktrace = conditional_breakpoint_exception_tuple
            int_cmd = InternalGetBreakpointException(thread_id, exc_type, stacktrace)
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
        """Sends a message that a new process has been created."""
        if self.writer is None or self.cmd_factory is None:
            return
        cmd = self.cmd_factory.make_process_created_message()
        self.writer.add_command(cmd)

    def send_process_will_be_substituted(self):
        """When `PyDB` works in server mode this method sends a message that a
        new process is going to be created. After that it waits for the
        response from the IDE to be sure that the IDE received this message.
        Waiting for the response is required because the current process might
        become substituted before it actually sends the message and the IDE
        will not try to connect to `PyDB` in this case.

        When `PyDB` works in client mode this method does nothing because the
        substituted process will try to connect to the IDE itself.
        """
        if self.communication_role == CommunicationRole.SERVER:
            if self._main_lock.is_acquired_by_current_thread():
                # if `_main_lock` is acquired by the current thread then `event.wait()` would stuck
                # because the corresponding call of `event.set()` is made under the same `_main_lock`
                pydev_log.debug("Skip sending process substitution notification\n")
                return

            cmd = self.cmd_factory.make_process_created_message()
            # register event before putting command to the message queue
            event = threading.Event()
            self.process_created_msg_received_events[cmd.seq] = event
            self.writer.add_command(cmd)
            event.wait()

    def set_next_statement(self, frame, event, func_name, next_line):
        stop = False
        response_msg = ""
        old_line = frame.f_lineno
        if event == 'line' or event == 'exception':
            # If we're already in the correct context, we have to stop it now, because we can act only on
            # line events -- if a return was the next statement it wouldn't work (so, we have this code
            # repeated at pydevd_frame).

            curr_func_name = frame.f_code.co_name

            # global context is set with an empty name
            if curr_func_name in ('?', '<module>'):
                curr_func_name = ''

            if func_name == '*' or curr_func_name == func_name:
                line = next_line
                frame.f_trace = self.trace_dispatch
                frame.f_lineno = line
                stop = True
            else:
                response_msg = "jump is available only within the bottom frame"
        return stop, old_line, response_msg

    def cancel_async_evaluation(self, thread_id, frame_id):
        with self._main_lock:
            try:
                all_threads = threadingEnumerate()
                for t in all_threads:
                    if getattr(t, 'is_pydev_daemon_thread', False) and \
                    hasattr(t, 'cancel_event') and \
                    hasattr(t, 'thread_id') and \
                    t.thread_id == thread_id and \
                    t.frame_id == frame_id:
                        t.cancel_event.set()
            except:
                log_exception()

    def do_wait_suspend(self, thread, frame, event, arg, send_suspend_message=True, is_unhandled_exception=False):  # @UnusedVariable
        """ busy waits until the thread state changes to RUN
        it expects thread's state as attributes of the thread.
        Upon running, processes any outstanding Stepping commands.

        :param is_unhandled_exception:
            If True we should use the line of the exception instead of the current line in the frame
            as the paused location on the top-level frame (exception info must be passed on 'arg').
        """
        self.process_internal_commands()
        thread_stack_str = ''  # @UnusedVariable -- this is here so that `make_get_thread_stack_message`
        # can retrieve it later.

        thread_id = get_current_thread_id(thread)
        stop_reason = thread.stop_reason
        suspend_type = thread.additional_info.trace_suspend_type

        if send_suspend_message:
            # Send the suspend message
            message = thread.additional_info.pydev_message
            thread.additional_info.trace_suspend_type = 'trace'  # Reset to trace mode for next call.
            frame_to_lineno = {}
            if is_unhandled_exception:
                # arg must be the exception info (tuple(exc_type, exc, traceback))
                tb = arg[2]
                while tb is not None:
                    frame_to_lineno[tb.tb_frame] = tb.tb_lineno
                    tb = tb.tb_next
            cmd = self.cmd_factory.make_thread_suspend_message(thread_id, frame, stop_reason, message, suspend_type, frame_to_lineno=frame_to_lineno)
            frame_to_lineno.clear()
            thread_stack_str = cmd.thread_stack_str  # @UnusedVariable -- `make_get_thread_stack_message` uses it later.
            self.writer.add_command(cmd)

        with CustomFramesContainer.custom_frames_lock:  # @UndefinedVariable
            from_this_thread = []

            for frame_id, custom_frame in dict_iter_items(CustomFramesContainer.custom_frames):
                if custom_frame.thread_id == thread.ident:
                    # print >> sys.stderr, 'Frame created: ', frame_id
                    self.writer.add_command(self.cmd_factory.make_custom_frame_created_message(frame_id, custom_frame.name))
                    self.writer.add_command(self.cmd_factory.make_thread_suspend_message(frame_id, custom_frame.frame, CMD_THREAD_SUSPEND, "", suspend_type))

                from_this_thread.append(frame_id)

        with self._threads_suspended_single_notification.notify_thread_suspended(thread_id, stop_reason):
            self._do_wait_suspend(thread, frame, event, arg, suspend_type, from_this_thread)

    def _do_wait_suspend(self, thread, frame, event, arg, suspend_type, from_this_thread):
        info = thread.additional_info
        with self._main_lock:
            activate_gui = info.pydev_state == STATE_SUSPEND and not self.pydb_disposed
        in_main_thread = is_current_thread_main_thread()
        if activate_gui and in_main_thread:
            # before every stop check if matplotlib modules were imported inside script code
            # or some GUI event loop needs to be activated
            self._activate_gui_if_needed()

        curr_thread_id = get_current_thread_id(threadingCurrentThread())
        queue, notify_event = self.get_internal_queue_and_event(curr_thread_id)

        wait_timeout = TIMEOUT_SLOW
        while True:
            with self._main_lock:  # Use lock to check if suspended state changed
                if info.pydev_state != STATE_SUSPEND or (self.pydb_disposed and not self.terminate_requested):
                    # Note: we can't exit here if terminate was requested while a breakpoint was hit.
                    break

            if in_main_thread and self.gui_in_use:
                wait_timeout = TIMEOUT_FAST
                # call input hooks if only GUI is in use
                self._call_input_hook()

            # No longer process commands for '*' at this point, just the
            # ones related to this thread.
            try:
                internal_cmd = queue.get(False)
            except _queue.Empty:
                pass
            else:
                if internal_cmd.can_be_executed_by(curr_thread_id):
                    pydev_log.debug("processing internal command: %s" % internal_cmd)
                    try:
                        internal_cmd.do_it(self)
                    except:
                        pydev_log.error("Error processing internal command.")
                else:
                    # This shouldn't really happen...
                    pydev_log.info(
                        "NOT processing internal command: %s " % internal_cmd)
                    queue.put(internal_cmd)
                    wait_timeout = TIMEOUT_FAST

            try:
                notify_event.wait(wait_timeout)
            except KeyboardInterrupt:
                pass

            notify_event.clear()

        self.cancel_async_evaluation(get_current_thread_id(thread), str(id(frame)))

        # process any stepping instructions
        if info.pydev_step_cmd in (CMD_STEP_INTO, CMD_STEP_INTO_MY_CODE):
            
            if not isinstance(frame.f_code, pydevd_frame_utils.FCode) and frame.f_code.co_flags & 0x80:  # CO_COROUTINE = 0x80
                # When in a coroutine we switch to CMD_STEP_INTO_COROUTINE.
                info.pydev_step_cmd = CMD_STEP_INTO_COROUTINE
                info.pydev_step_stop = frame
            else:
                info.pydev_step_stop = None

            self.set_trace_for_frame_and_parents(frame)
            info.pydev_smart_step_context.smart_step_stop = None

        elif info.pydev_step_cmd == CMD_STEP_OVER:
            info.pydev_step_stop = frame
            info.pydev_smart_step_context.smart_step_stop = None
            self.set_trace_for_frame_and_parents(frame)

        elif info.pydev_step_cmd == CMD_SMART_STEP_INTO:
            info.pydev_step_stop = None
            info.pydev_smart_step_context.smart_step_stop = frame
            self.set_trace_for_frame_and_parents(frame)

        elif info.pydev_step_cmd in (CMD_RUN_TO_LINE, CMD_SET_NEXT_STATEMENT):
            info.pydev_step_stop = None
            self.set_trace_for_frame_and_parents(frame)
            stop = False
            response_msg = ""
            old_line = frame.f_lineno
            if not IS_PYCHARM:
                stop, _, response_msg = self.set_next_statement(frame, event, info.pydev_func_name, info.pydev_next_line)
                if stop:
                    # Set next did not work...
                    info.pydev_step_cmd = -1
                    info.pydev_state = STATE_SUSPEND
                    thread.stop_reason = CMD_THREAD_SUSPEND
                    # return to the suspend state and wait for other command (without sending any
                    # additional notification to the client).
                    self._do_wait_suspend(thread, frame, event, arg, suspend_type, from_this_thread)
                    return
            else:
                try:
                    stop, old_line, response_msg = self.set_next_statement(frame, event, info.pydev_func_name, info.pydev_next_line)
                except ValueError as e:
                    response_msg = "%s" % e
                finally:
                    if GOTO_HAS_RESPONSE:
                        seq = info.pydev_message
                        cmd = self.cmd_factory.make_set_next_stmnt_status_message(seq, stop, response_msg)
                        self.writer.add_command(cmd)
                        info.pydev_message = ''

                if stop:
                    cmd = self.cmd_factory.make_thread_run_message(get_current_thread_id(thread), info.pydev_step_cmd)
                    self.writer.add_command(cmd)
                    info.pydev_state = STATE_SUSPEND
                    thread.stop_reason = CMD_SET_NEXT_STATEMENT
                    self.do_wait_suspend(thread, frame, event, arg)
                    return
                else:
                    info.pydev_step_cmd = -1
                    info.pydev_state = STATE_SUSPEND
                    thread.stop_reason = CMD_THREAD_SUSPEND
                    # return to the suspend state and wait for other command
                    self.do_wait_suspend(thread, frame, event, arg, send_suspend_message=False)
                    return

        elif info.pydev_step_cmd == CMD_STEP_RETURN:
            back_frame = frame.f_back
            if self.is_files_filter_enabled:
                while back_frame is not None:
                    if self.apply_files_filter(back_frame,
                                               back_frame.f_code.co_filename, False):
                        frame = back_frame
                        back_frame = back_frame.f_back
                    else:
                        break

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

        del frame
        cmd = self.cmd_factory.make_thread_run_message(get_current_thread_id(thread), info.pydev_step_cmd)
        self.writer.add_command(cmd)

        with CustomFramesContainer.custom_frames_lock:
            # The ones that remained on last_running must now be removed.
            for frame_id in from_this_thread:
                # print >> sys.stderr, 'Removing created frame: ', frame_id
                self.writer.add_command(self.cmd_factory.make_thread_killed_message(frame_id))

    def stop_on_unhandled_exception(self, thread, frame, frames_byid, arg):
        pydev_log.debug("We are stopping in post-mortem\n")
        thread_id = get_thread_id(thread)
        pydevd_vars.add_additional_frame_by_id(thread_id, frames_byid)
        exctype, value, tb = arg
        tb = pydevd_utils.get_top_level_trace_in_project_scope(tb)
        if sys.excepthook != dummy_excepthook:
            original_excepthook(exctype, value, tb)
        disable_excepthook()  # Avoid printing the exception for the second time.
        try:
            try:
                add_exception_to_frame(frame, arg)
                self.set_suspend(thread, CMD_ADD_EXCEPTION_BREAK)
                self.do_wait_suspend(thread, frame, 'exception', arg, is_unhandled_exception=True)
            except KeyboardInterrupt as e:
                raise e
            except:
                pydev_log.error("We've got an error while stopping in post-mortem: %s\n" % (arg[0],))
        finally:
            remove_exception_from_frame(frame)
            pydevd_vars.remove_additional_frame_by_id(thread_id)
            frame = None

    # Rewrite with good monitoring
    def set_trace_for_frame_and_parents(self, frame, **kwargs):
        disable = kwargs.pop('disable', False)
        assert not kwargs

        if USE_LOW_IMPACT_MONITORING:
            debugger = get_global_debugger()
            if debugger and not debugger.is_pep669_monitoring_enabled:
                enable_pep669_monitoring()
        else:
            while frame is not None:
                # Don't change the tracing on debugger-related files
                file_type = self.get_file_type(frame)

                if file_type is None:
                    if disable:
                        if frame.f_trace is not None and frame.f_trace is not NO_FTRACE:
                            frame.f_trace = NO_FTRACE

                    elif frame.f_trace is not self.trace_dispatch:
                        frame.f_trace = self.trace_dispatch

                frame = frame.f_back

            del frame

    def _create_pydb_command_thread(self):
        curr_pydb_command_thread = self.py_db_command_thread
        if curr_pydb_command_thread is not None:
            curr_pydb_command_thread.do_kill_pydev_thread()

        new_pydb_command_thread = self.py_db_command_thread = PyDBCommandThread(self)
        new_pydb_command_thread.start()

    def _create_check_output_thread(self):
        curr_output_checker_thread = self.check_alive_thread
        if curr_output_checker_thread is not None:
            curr_output_checker_thread.do_kill_pydev_thread()

        check_alive_thread = self.check_alive_thread = CheckAliveThread(self)
        check_alive_thread.start()

    def start_auxiliary_daemon_threads(self):
        self._create_pydb_command_thread()
        self._create_check_output_thread()

    def __waiting(self, timeout):
        try:
            def get_pydb_daemon_threads_to_wait():
                pydb_daemon_threads = set(self.created_pydb_daemon_threads)
                pydb_daemon_threads.discard(self.check_alive_thread)
                pydb_daemon_threads.discard(threading.current_thread())
                return pydb_daemon_threads

            pydev_log.debug("PyDB.dispose_and_kill_all_pydevd_threads waiting for pydb daemon threads to finish")
            started_at = time.time()
            # Note: we wait for all except the check_alive_thread (which is not really a daemon
            # thread and it can call this method itself).
            while time.time() < started_at + timeout:
                if len(get_pydb_daemon_threads_to_wait()) == 0:
                    break
                time.sleep(1 / 10.0)
            else:
                thread_names = [t.name for t in
                                get_pydb_daemon_threads_to_wait()]
                if thread_names:
                    pydev_log.debug("The following pydb threads may not have finished correctly: %s" % ", ".join(thread_names))
        finally:
            self._wait_for_threads_to_finish_called_event.set()

    def __wait_for_threads_to_finish(self, timeout):
        try:
            with self._wait_for_threads_to_finish_called_lock:
                wait_for_threads_to_finish_called = self._wait_for_threads_to_finish_called
                self._wait_for_threads_to_finish_called = True

            if wait_for_threads_to_finish_called:
                # Make sure that we wait for the previous call to be finished.
                self._wait_for_threads_to_finish_called_event.wait(timeout=timeout)
            else:
                self.__waiting(timeout)
        except KeyboardInterrupt:
            try:
                self.__waiting(timeout)
            except:
                pass


    def dispose_and_kill_all_pydevd_threads(self, wait=True, timeout=0.5):
        """
        When this method is called we finish the debug session, terminate threads
        and if this was registered as the global instance, unregister it -- afterwards
        it should be possible to create a new instance and set as global to start
        a new debug session.

        :param bool wait:
            If True we'll wait for the threads to be actually finished before proceeding
            (based on the available timeout).
            Note that this must be thread-safe and if one thread is waiting the other thread should
            also wait.
        """
        try:
            back_frame = sys._getframe().f_back
            pydev_log.debug(
                'PyDB.dispose_and_kill_all_pydevd_threads (called from: File {file}, line {line}, in {name})'
                .format(
                    file=back_frame.f_code.co_filename,
                    line=back_frame.f_lineno,
                    name=back_frame.f_code.co_name)

            )
            back_frame = None
            with self._disposed_lock:
                disposed = self.pydb_disposed
                self.pydb_disposed = True

            if disposed:
                if wait:
                    pydev_log.debug("PyDB.dispose_and_kill_all_pydevd_threads (already disposed - wait)")
                    self.__wait_for_threads_to_finish(timeout)
                else:
                    pydev_log.debug("PyDB.dispose_and_kill_all_pydevd_threads (already disposed - no wait)")
                return

            pydev_log.debug("PyDB.dispose_and_kill_all_pydevd_threads (first call)")

            # Wait until a time when there are no commands being processed to kill the threads.
            started_at = time.time()
            while time.time() < started_at + timeout:
                with self._main_lock:
                    writer = self.writer
                    if writer is None or writer.empty():
                        pydev_log.debug("PyDB.dispose_and_kill_all_pydevd_threads no commands being processed.")
                        break
            else:
                pydev_log.debug("PyDB.dispose_and_kill_all_pydevd_threads timed out waiting for writer to be empty.")

            pydb_daemon_threads = set(self.created_pydb_daemon_threads)
            for t in pydb_daemon_threads:
                if hasattr(t, "do_kill_pydev_thread"):
                    pydev_log.debug("PyDB.dispose_and_kill_all_pydevd_threads killing thread: %s" % t)
                    t.do_kill_pydev_thread()
            
            if wait:
                self.__wait_for_threads_to_finish(timeout)
            else:
                pydev_log.debug("PyDB.dispose_and_kill_all_pydevd_threads: no wait")

            py_db = get_global_debugger()
            if py_db is self:
                set_global_debugger(None)
        except:
            pydev_log.debug("PyDB.dispose_and_kill_all_pydevd_threads: exception")
            try:
                if DebugInfoHolder.DEBUG_TRACE_LEVEL >= 3:
                    log_exception()
            except:
                pass
        finally:
            pydev_log.debug("PyDB.dispose_and_kill_all_pydevd_threads: finished")

    def prepare_to_run(self, enable_tracing_from_start=True):
        """ Shared code to prepare debugging by installing traces and registering threads """
        self._create_pydb_command_thread()
        if self.redirect_output or self.signature_factory is not None or self.thread_analyser is not None:
            # we need all data to be sent to IDE even after program finishes
            self._create_check_output_thread()
            # turn off frame evaluation for concurrency visualization
            self.frame_eval_func = None

        if not USE_LOW_IMPACT_MONITORING:
            self.patch_threads()

        if enable_tracing_from_start:
            if USE_LOW_IMPACT_MONITORING:
                enable_pep669_monitoring()
            else:
                pydevd_tracing.SetTrace(self.trace_dispatch)

        if show_tracing_warning or show_frame_eval_warning:
            cmd = self.cmd_factory.make_show_warning_message("cython")
            self.writer.add_command(cmd)

    def patch_threads(self):
        try:
            # not available in jython!
            threading.settrace(self.trace_dispatch)  # for all future threads
        except:
            pass

        from _pydev_bundle.pydev_monkey import patch_thread_modules
        patch_thread_modules()

    def run(self, file, globals=None, locals=None, is_module=False, set_trace=True):
        module_name = None
        entry_point_fn = ''
        if is_module:
            # When launching with `python -m <module>`, python automatically adds
            # an empty path to the PYTHONPATH which resolves files in the current
            # directory, so, depending how pydevd itself is launched, we may need
            # to manually add such an entry to properly resolve modules in the
            # current directory
            if '' not in sys.path:
                sys.path.insert(0, '')
            file, _, entry_point_fn = file.partition(':')
            module_name = file
            filename = get_fullname(file)
            if filename is None:
                mod_dir = get_package_dir(module_name)
                if mod_dir is None:
                    sys.stderr.write("No module named %s\n" % file)
                    return
                else:
                    filename = get_fullname("%s.__main__" % module_name)
                    if filename is None:
                        sys.stderr.write("No module named %s\n" % file)
                        return
                    else:
                        file = filename
            else:
                file = filename
                mod_dir = os.path.dirname(filename)
                main_py = os.path.join(mod_dir, '__main__.py')
                main_pyc = os.path.join(mod_dir, '__main__.pyc')
                if filename.endswith('__init__.pyc'):
                    if os.path.exists(main_pyc):
                        filename = main_pyc
                    elif os.path.exists(main_py):
                        filename = main_py
                elif filename.endswith('__init__.py'):
                    if os.path.exists(main_pyc) and not os.path.exists(main_py):
                        filename = main_pyc
                    elif os.path.exists(main_py):
                        filename = main_py

            sys.argv[0] = filename

        if os.path.isdir(file):
            new_target = os.path.join(file, '__main__.py')
            if os.path.isfile(new_target):
                file = new_target

        m = None
        if globals is None:
            m = save_main_module(file, 'pydevd')
            globals = m.__dict__
            try:
                globals['__builtins__'] = __builtins__
            except NameError:
                pass  # Not there on Jython...

        if locals is None:
            locals = globals

        # Predefined (writable) attributes: __name__ is the module's name;
        # __doc__ is the module's documentation string, or None if unavailable;
        # __file__ is the pathname of the file from which the module was loaded,
        # if it was loaded from a file. The __file__ attribute is not present for
        # C modules that are statically linked into the interpreter; for extension modules
        # loaded dynamically from a shared library, it is the pathname of the shared library file.

        # I think this is an ugly hack, bug it works (seems to) for the bug that says that sys.path should be the same in
        # debug and run.
        if sys.path[0] != '' and m is not None and m.__file__.startswith(sys.path[0]):
            # print >> sys.stderr, 'Deleting: ', sys.path[0]
            del sys.path[0]

        if not is_module:
            # now, the local directory has to be added to the pythonpath
            # sys.path.insert(0, os.getcwd())
            # Changed: it's not the local directory, but the directory of the file launched
            # The file being run must be in the pythonpath (even if it was not before)
            sys.path.insert(0, os.path.split(rPath(file))[0])

        if set_trace:

            self.wait_for_ready_to_run()

            if self.break_on_caught_exceptions or self.has_plugin_line_breaks or self.has_plugin_exception_breaks \
                    or self.signature_factory:
                # disable frame evaluation if there are exception breakpoints with 'On raise' activation policy
                # or if there are plugin exception breakpoints or if collecting run-time types is enabled
                self.frame_eval_func = None

            # call prepare_to_run when we already have all information about breakpoints
            self.prepare_to_run()

        t = threadingCurrentThread()
        thread_id = get_current_thread_id(t)

        if self.thread_analyser is not None:
            wrap_threads()
            self.thread_analyser.set_start_time(cur_time())
            send_message("threading_event", 0, t.name, thread_id, "thread", "start", file, 1, None, parent=get_thread_id(t))

        if self.asyncio_analyser is not None:
            if IS_PY36_OR_GREATER:
                wrap_asyncio()
            # we don't have main thread in asyncio graph, so we should add a fake event
            send_message("asyncio_event", 0, "Task", "Task", "thread", "stop", file, 1, frame=None, parent=None)

        try:
            if INTERACTIVE_MODE_AVAILABLE:
                self.init_gui_support()
        except:
            sys.stderr.write("Matplotlib support in debugger failed\n")
            log_exception()

        if hasattr(sys, 'exc_clear'):
            # we should clean exception information in Python 2, before user's code execution
            sys.exc_clear()

        # Notify that the main thread is created.
        self.notify_thread_created(thread_id, t)

        if self.stop_on_start:
            info = set_additional_thread_info(t)
            t.additional_info.pydev_step_cmd = CMD_STEP_INTO_MY_CODE

        # Note: important: set the tracing right before calling _exec.
        if set_trace:
            self.enable_tracing()

        apply_func = get_apply()
        if apply_func is not None:
            apply_func()

        return self._exec(is_module, entry_point_fn, module_name, file, globals, locals)

    def _exec(self, is_module, entry_point_fn, module_name, file, globals, locals):
        """
        This function should have frames tracked by unhandled exceptions (the `_exec` name is important).
        """
        t = threading.current_thread()  # Keep in 't' local variable to be accessed afterwards from frame.f_locals.
        if not is_module:
            pydev_imports.execfile(file, globals, locals)  # execute the script
        else:
            # treat ':' as a separator between module and entry point function
            # if there is no entry point we run we same as with -m switch. Otherwise we perform
            # an import and execute the entry point
            if entry_point_fn:
                mod = __import__(module_name, level=0, fromlist=[entry_point_fn], globals=globals, locals=locals)
                func = getattr(mod, entry_point_fn)
                func()
            else:
                # Run with the -m switch
                import runpy
                if hasattr(runpy, '_run_module_as_main'):
                    runpy._run_module_as_main(module_name, alter_argv=False)
                else:
                    runpy.run_module(module_name)
        return globals

    def wait_for_commands(self, globals):
        self._activate_gui_if_needed()

        thread = threading.current_thread()
        from _pydevd_bundle import pydevd_frame_utils

        frame = pydevd_frame_utils.Frame(None, -1, pydevd_frame_utils.FCode("Console", os.path.abspath(os.path.dirname(__file__))), globals, globals)
        thread_id = get_current_thread_id(thread)
        pydevd_vars.add_additional_frame_by_id(thread_id, {id(frame): frame})

        cmd = self.cmd_factory.make_show_console_message(thread_id, frame)
        if self.writer is not None:
            self.writer.add_command(cmd)

        while True:
            if self.gui_in_use:
                # call input hooks if only matplotlib is in use
                self._call_input_hook()
            self.process_internal_commands()
            time.sleep(0.01)

    def exiting(self):
        # noinspection PyBroadException
        try:
            sys.stdout.flush()
        except:
            pass
        # noinspection PyBroadException
        try:
            sys.stderr.flush()
        except:
            pass
        self.check_output_redirect()
        cmd = self.cmd_factory.make_exit_message()
        self.writer.add_command(cmd)

    trace_dispatch = _trace_dispatch
    frame_eval_func = frame_eval_func
    dummy_trace_dispatch = dummy_trace_dispatch

    # noinspection SpellCheckingInspection
    @staticmethod
    def stoptrace():
        """A proxy method for calling :func:`stoptrace` from the modules where direct import
        is impossible because, for example, a circular dependency."""
        PyDBDaemonThread.created_pydb_daemon_threads = {}
        stoptrace()


def set_debug(setup):
    setup['DEBUG_RECORD_SOCKET_READS'] = True
    setup['DEBUG_TRACE_BREAKPOINTS'] = 1
    setup['DEBUG_TRACE_LEVEL'] = 3


def enable_qt_support(qt_support_mode):
    from _pydev_bundle import pydev_monkey_qt
    pydev_monkey_qt.patch_qt(qt_support_mode)


def dump_threads(stream=None):
    """
    Helper to dump thread info (default is printing to stderr).
    """
    pydevd_utils.dump_threads(stream)


def usage(do_exit=True, exit_code=0):
    sys.stdout.write('Usage:\n')
    sys.stdout.write('\tpydevd.py --port N [(--client hostname) | --server] --file executable [file_options]\n')
    if do_exit:
        sys.exit(exit_code)


def init_stdout_redirect(on_write=None):
    if not hasattr(sys, '_pydevd_out_buffer_'):
        wrap_buffer = True if not IS_PY2 else False
        original = sys.stdout
        sys._pydevd_out_buffer_ = _CustomWriter(1, original, wrap_buffer, on_write)
        sys.stdout_original = original
        sys.stdout = pydevd_io.IORedirector(original, sys._pydevd_out_buffer_, wrap_buffer)  # @UndefinedVariable


def init_stderr_redirect(on_write=None):
    if not hasattr(sys, '_pydevd_err_buffer_'):
        wrap_buffer = True if not IS_PY2 else False
        original = sys.stderr
        sys._pydevd_err_buffer_ = _CustomWriter(2, original, wrap_buffer, on_write)
        sys.stderr_original = original
        sys.stderr = pydevd_io.IORedirector(original, sys._pydevd_err_buffer_,  wrap_buffer)  # @UndefinedVariable


class _CustomWriter(object):
    def __init__(self, out_ctx, wrap_stream, wrap_buffer, on_write=None):
        """
        :param out_ctx:
            1=stdout and 2=stderr

        :param wrap_stream:
            Either sys.stdout or sys.stderr.

        :param bool wrap_buffer:
            If True the buffer attribute (which wraps writing bytes) should be
            wrapped.

        :param callable(str) on_write:
            May be a custom callable to be called when to write something.
            If not passed the default implementation will create an io message
            and send it through the debugger.
        """
        self.encoding = getattr(wrap_stream, 'encoding', os.environ.get('PYTHONIOENCODING', 'utf-8'))
        self._out_ctx = out_ctx
        if wrap_buffer:
            self.buffer = _CustomWriter(out_ctx, wrap_stream, wrap_buffer=False, on_write=on_write)
        self._on_write = on_write

    def flush(self):
        pass  # no-op here

    def write(self, s):
        if self._on_write is not None:
            self._on_write(s)
            return

        if s:
            if IS_PY2:
                # Need s in bytes
                if isinstance(s, unicode):
                    # Note: python 2.6 does not accept the "errors" keyword.
                    s = s.encode('utf-8', 'replace')
            else:
                # Need s in str
                if isinstance(s, bytes):
                    s = s.decode(self.encoding, errors='replace')

            py_db = get_global_debugger()
            if py_db is not None:
                # Note that the actual message contents will be a xml with utf-8, although
                # the entry is str on py3 and bytes on py2.
                cmd = py_db.cmd_factory.make_io_message(s, self._out_ctx)
                py_db.writer.add_command(cmd)


# =======================================================================================================================
# settrace
# =======================================================================================================================
def settrace(
        host=None,
        stdout_to_server=False,
        stderr_to_server=False,
        port=5678,
        suspend=True,
        trace_only_current_thread=False,
        overwrite_prev_trace=False,
        patch_multiprocessing=False,
        stop_at_frame=None,
        block_until_connected=True,
        wait_for_ready_to_run=True,
):
    """Sets the tracing function with the pydev debug function and initializes needed facilities.

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

    @param overwrite_prev_trace: deprecated

    @param patch_multiprocessing: if True we'll patch the functions which create new processes so that launched
        processes are debugged.

    @param stop_at_frame: if passed it'll stop at the given frame, otherwise it'll stop in the function which
        called this method.
    """
    with _set_trace_lock:
        _locked_settrace(
            host,
            stdout_to_server,
            stderr_to_server,
            port,
            suspend,
            trace_only_current_thread,
            patch_multiprocessing,
            stop_at_frame,
            block_until_connected,
            wait_for_ready_to_run,
        )


_set_trace_lock = ForkSafeLock()


def _locked_settrace(
        host,
        stdout_to_server,
        stderr_to_server,
        port,
        suspend,
        trace_only_current_thread,
        patch_multiprocessing,
        stop_at_frame,
        block_until_connected,
        wait_for_ready_to_run,
):
    if patch_multiprocessing:
        try:
            from _pydev_bundle import pydev_monkey
        except:
            pass
        else:
            pydev_monkey.patch_new_process_functions()

    if host is None:
        from _pydev_bundle import pydev_localhost
        host = pydev_localhost.get_localhost()

    global buffer_stdout_to_server
    global buffer_stderr_to_server

    py_db = get_global_debugger()

    if py_db is None:
        py_db = PyDB()
        pydevd_vm_type.setup_type()

        if SetupHolder.setup is None:
            setup = {
                'client': host,  # dispatch expects client to be set to the host address when server is False
                'server': False,
                'port': int(port),
                'multiprocess': patch_multiprocessing,
            }
            SetupHolder.setup = setup

        if block_until_connected:
            pydev_log.debug("pydev debugger: process %d is connecting\n" % os.getpid())
            py_db.connect(host, port)  # Note: connect can raise error.
        else:
            # Create a dummy writer and wait for the real connection.
            py_db.writer = WriterThread(NULL, py_db, terminate_on_socket_close=False)
            py_db.create_wait_for_connection_thread()

        buffer_stdout_to_server = stdout_to_server
        buffer_stderr_to_server = stderr_to_server

        if buffer_stdout_to_server:
            init_stdout_redirect()

        if buffer_stderr_to_server:
            init_stderr_redirect()

        patch_stdin(py_db)

        t = threadingCurrentThread()
        additional_info = set_additional_thread_info(t)

        if not wait_for_ready_to_run:
            py_db.ready_to_run = True

        py_db.wait_for_ready_to_run()
        py_db.start_auxiliary_daemon_threads()

        try:
            if INTERACTIVE_MODE_AVAILABLE:
                py_db.init_gui_support()
        except:
            pydev_log.error("Matplotlib support in debugger failed")

        if trace_only_current_thread:
            py_db.enable_tracing()
        else:
            # Trace future threads.
            py_db.patch_threads()

            py_db.enable_tracing(py_db.trace_dispatch, apply_to_all_threads=True)

            # As this is the first connection, also set tracing for any untraced threads
            py_db.set_tracing_for_untraced_contexts(True)

        py_db.set_trace_for_frame_and_parents(get_frame().f_back)

        with CustomFramesContainer.custom_frames_lock:
            for _frameId, custom_frame in dict_iter_items(CustomFramesContainer.custom_frames):
                py_db.set_trace_for_frame_and_parents(custom_frame.frame)

        pydev_log.debug("pydev debugger: process %d debugging initialization is finished\n" % os.getpid())
    else:
        # ok, we're already in debug mode, with all set, so, let's just set the break
        debugger = get_global_debugger()
        debugger.set_trace_for_frame_and_parents(get_frame().f_back)

        t = threadingCurrentThread()
        additional_info = set_additional_thread_info(t)

        if trace_only_current_thread:
            py_db.enable_tracing()
        else:
            # Trace future threads.
            py_db.patch_threads()
            py_db.enable_tracing(py_db.trace_dispatch, apply_to_all_threads=True)

    # Suspend as the last thing after all tracing is in place.
    if suspend:
        if stop_at_frame is not None:
            # If the step was set we have to go to run state and
            # set the proper frame for it to stop.
            additional_info.pydev_state = STATE_RUN
            additional_info.pydev_step_cmd = CMD_STEP_OVER
            additional_info.pydev_step_stop = stop_at_frame
            additional_info.suspend_type = PYTHON_SUSPEND
        else:
            # Ask to break as soon as possible.
            py_db.set_suspend(t, CMD_SET_BREAK)


class Dispatcher(object):
    def __init__(self):
        self.reader = None
        self.client = None
        self.host = None
        self.port = None

    def connect(self, host, port):
        self.host = host
        self.port = port
        self.client = start_client(self.host, self.port)
        self.reader = DispatchReader(self)
        self.reader.pydev_do_not_trace = False  # We run reader in the same thread so we don't want to loose tracing.
        self.reader.run()

    def close(self):
        try:
            self.reader.do_kill_pydev_thread()
        except:
            pass


class DispatchReader(ReaderThread):
    def __init__(self, dispatcher):
        self.dispatcher = dispatcher
        ReaderThread.__init__(self, dispatcher.client, get_global_debugger(), self.dispatcher.client)

    @overrides(ReaderThread._on_run)
    def _on_run(self):
        dummy_thread = threading.current_thread()
        dummy_thread.is_pydev_daemon_thread = False
        return ReaderThread._on_run(self)

    @overrides(PyDBDaemonThread.do_kill_pydev_thread)
    def do_kill_pydev_thread(self):
        if not self._kill_received:
            ReaderThread.do_kill_pydev_thread(self)
            try:
                self.sock.shutdown(SHUT_RDWR)
            except:
                pass
            try:
                self.sock.close()
            except:
                pass

    def process_command(self, cmd_id, seq, text):
        if cmd_id == 99:
            self.dispatcher.port = int(text)
            self._kill_received = True


def _should_use_existing_connection(setup):
    """
    The new connection dispatch approach is used by PyDev when the `multiprocess` option is set,
    the existing connection approach is used by PyCharm when the `multiproc` option is set.
    """
    return setup.get('multiproc', False)


def dispatch():
    setup = SetupHolder.setup
    host = setup['client']
    port = setup['port']
    if _should_use_existing_connection(setup):
        dispatcher = Dispatcher()
        try:
            dispatcher.connect(host, port)
            port = dispatcher.port
        finally:
            dispatcher.close()
    return host, port


def settrace_forked():
    """
    When creating a fork from a process in the debugger, we need to reset the whole debugger environment!
    """
    from _pydevd_bundle.pydevd_constants import GlobalDebuggerHolder

    py_db = GlobalDebuggerHolder.global_dbg
    if py_db is not None:
        py_db.created_pydb_daemon_threads = {}  # Just making sure we won't touch those (paused) threads.
        py_db = None

    GlobalDebuggerHolder.global_dbg = None
    threading.current_thread().additional_info = None

    setup = SetupHolder.setup
    if setup is None:
        setup = {}

    host, port = dispatch()

    import pydevd_tracing
    pydevd_tracing.restore_sys_set_trace_func()

    if port is not None:
        global forked
        forked = True

        custom_frames_container_init()

        if clear_thread_local_info is not None:
            clear_thread_local_info()

        settrace(
            host,
            port=port,
            suspend=False,
            trace_only_current_thread=False,
            overwrite_prev_trace=True,
            patch_multiprocessing=True,
        )


# noinspection SpellCheckingInspection
def stoptrace():
    """Stops tracing in the current process and undoes all monkey-patches done by the debugger."""
    pydev_log.debug("pydevd.stoptrace()")
    pydevd_tracing.restore_sys_set_trace_func()

    # if PYDEVD_USE_SYS_MONITORING:
    #     pydevd_sys_monitoring.stop_monitoring(all_threads=True)
    # else:
    sys.settrace(None)
    try:
        # not available in jython!
        threading.settrace(None)  # for all future threads
    except:
        pass

    from _pydev_bundle.pydev_monkey import undo_patch_thread_modules
    undo_patch_thread_modules()

    # Either or both standard streams can be closed at this point,
    # in which case flush() will fail.
    try:
        sys.stdout.flush()
    except:
        pass
    try:
        sys.stderr.flush()
    except:
        pass

    debugger = get_global_debugger()

    if debugger is not None:
        debugger.dispose_and_kill_all_pydevd_threads()

    if clear_thread_local_info is not None:
        clear_thread_local_info()


# =======================================================================================================================
# SetupHolder
# =======================================================================================================================
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
    from _pydev_bundle.pydev_stdin import DebugConsoleStdIn
    orig_stdin = sys.stdin
    sys.stdin = DebugConsoleStdIn(debugger, orig_stdin)


def handle_keyboard_interrupt():
    debugger = get_global_debugger()

    if not debugger:
        return

    debugger.disable_tracing()
    _, value, tb = sys.exc_info()

    while tb:
        filename = tb.tb_frame.f_code.co_filename
        if debugger.in_project_scope(filename) and '_pydevd' not in filename:
            break
        tb = tb.tb_next
    if tb:
        limit = 1
        tb_next = tb.tb_next

        # When stopping the suspended debugger, traceback can contain two stack traces with the same frame.
        if tb_next and tb_next.tb_frame is tb.tb_frame:
            tb_next = None

        while tb_next:
            filename = tb_next.tb_frame.f_code.co_filename
            if get_file_type(os.path.basename(filename)) or '_pydevd' in filename:
                break
            limit += 1
            if tb_next.tb_next and tb_next.tb_next.tb_frame is not tb_next.tb_frame:
                tb_next = tb_next.tb_next
            else:
                break
        try:
            value = value.with_traceback(tb)
        except AttributeError:
            value.__traceback__ = tb
        value.__cause__ = None
        traceback.print_exception(type(value), value, tb, limit=limit)

    disable_excepthook()


# Dispatch on_debugger_modules_loaded here, after all primary debugger modules are loaded
from _pydevd_bundle.pydevd_extension_api import DebuggerEventHandler
from _pydevd_bundle import pydevd_extension_utils

for handler in pydevd_extension_utils.extensions_of_type(DebuggerEventHandler):
    handler.on_debugger_modules_loaded(debugger_version=__version__)


# =======================================================================================================================
# main
# =======================================================================================================================
def main():
    # parse the command line. --file is our last argument that is required
    try:
        from _pydevd_bundle.pydevd_command_line_handling import process_command_line
        setup = process_command_line(sys.argv)
        SetupHolder.setup = setup
    except ValueError:
        log_exception()
        usage(exit_code=1)

    # noinspection PyUnboundLocalVariable
    if setup['help']:
        usage()

    if SHOW_DEBUG_INFO_ENV:
        set_debug(setup)

    if setup['print-in-debugger-startup']:
        try:
            pid = ' (pid: %s)' % os.getpid()
        except:
            pid = ''
        sys.stderr.write("pydev debugger: starting%s\n" % pid)

    fix_getpass.fix_getpass()

    pydev_log.debug("Executing file %s" % setup['file'])
    pydev_log.debug("arguments: %s" % str(sys.argv))

    pydevd_vm_type.setup_type(setup.get('vm_type', None))

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
        pass  # Not usable on jython 2.1
    else:
        if setup['multiprocess']:  # PyDev
            pydev_monkey.patch_new_process_functions()

        elif setup['multiproc']:  # PyCharm
            pydev_log.debug("Started in multiproc mode\n")

            dispatcher = Dispatcher()
            try:
                dispatcher.connect(host, port)
                if dispatcher.port is not None:
                    port = dispatcher.port
                    pydev_log.debug("Received port %d\n" % port)
                    pydev_log.debug("pydev debugger: process %d is connecting\n" % os.getpid())

                    try:
                        pydev_monkey.patch_new_process_functions()
                    except:
                        pydev_log.error("Error patching process functions\n")
                        log_exception()
                else:
                    pydev_log.error("pydev debugger: couldn't get port for new debug process\n")
            finally:
                dispatcher.close()
        else:
            try:
                pydev_monkey.patch_new_process_functions_with_warning()
            except:
                pydev_log.error("Error patching process functions\n")
                log_exception()

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
                            log_exception()

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
        sys.argv.insert(2, '--python_startup_args=%s' % json.dumps(setup), )
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

        global connected
        connected = True  # Mark that we're connected when started from inside ide.
        try:
            globals = debugger.run(setup['file'], None, None, is_module)
        except KeyboardInterrupt as e:
            handle_keyboard_interrupt()
            raise

        if setup['cmd-line']:
            debugger.wait_for_commands(globals)

        # CheckOutputThread is not a daemon, so need to wait for its completion
        if debugger.check_alive_thread is not None:
            debugger.wait_output_checker_thread = True

            try:
                debugger.check_alive_thread.join()
            except:
                pass


if __name__ == '__main__':
    main()
