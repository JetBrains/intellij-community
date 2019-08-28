'''
This module holds the constants used for specifying the states of the debugger.
'''
from __future__ import nested_scopes

STATE_RUN = 1
STATE_SUSPEND = 2

PYTHON_SUSPEND = 1
DJANGO_SUSPEND = 2
JINJA2_SUSPEND = 3
JUPYTER_SUSPEND = 4


class DebugInfoHolder:
    # we have to put it here because it can be set through the command line (so, the
    # already imported references would not have it).
    DEBUG_RECORD_SOCKET_READS = False
    DEBUG_TRACE_LEVEL = -1
    DEBUG_TRACE_BREAKPOINTS = -1


# Hold a reference to the original _getframe (because psyco will change that as soon as it's imported)
import sys  # Note: the sys import must be here anyways (others depend on it)
IS_IRONPYTHON = sys.platform == 'cli'
try:
    get_frame = sys._getframe
    if IS_IRONPYTHON:

        def get_frame():
            try:
                return sys._getframe()
            except ValueError:
                pass

except AttributeError:

    def get_frame():
        raise AssertionError('sys._getframe not available (possible causes: enable -X:Frames on IronPython?)')

# Used to determine the maximum size of each variable passed to eclipse -- having a big value here may make
# the communication slower -- as the variables are being gathered lazily in the latest version of eclipse,
# this value was raised from 200 to 1000.
MAXIMUM_VARIABLE_REPRESENTATION_SIZE = 1000
# Prefix for saving functions return values in locals
RETURN_VALUES_DICT = '__pydevd_ret_val_dict'

import os

from _pydevd_bundle import pydevd_vm_type

# Constant detects when running on Jython/windows properly later on.
IS_WINDOWS = sys.platform == 'win32'

IS_JYTHON = pydevd_vm_type.get_vm_type() == pydevd_vm_type.PydevdVmType.JYTHON
IS_JYTH_LESS25 = False

if IS_JYTHON:
    import java.lang.System  # @UnresolvedImport
    IS_WINDOWS = java.lang.System.getProperty("os.name").lower().startswith("windows")
    if sys.version_info[0] == 2 and sys.version_info[1] < 5:
        IS_JYTH_LESS25 = True
elif IS_IRONPYTHON:
    import System
    IS_WINDOWS = "windows" in System.Environment.OSVersion.VersionString.lower()

IS_MACOS = sys.platform == 'darwin'

IS_PYTHON_STACKLESS = "stackless" in sys.version.lower()
CYTHON_SUPPORTED = False

try:
    import platform
    python_implementation = platform.python_implementation()
except:
    pass
else:
    if python_implementation == 'CPython' and not IS_PYTHON_STACKLESS:
        # Only available for CPython!
        if (
            (sys.version_info[0] == 2 and sys.version_info[1] >= 7)
            or (sys.version_info[0] == 3 and sys.version_info[1] >= 5)
            or (sys.version_info[0] > 3)
        ):
            # Supported in 2.7 or 3.5 onwards (32 or 64)
            CYTHON_SUPPORTED = True

#=======================================================================================================================
# Python 3?
#=======================================================================================================================
IS_PY3K = False
IS_PY34_OR_GREATER = False
IS_PY36_OR_GREATER = False
IS_PY36_OR_LESSER = False
IS_PY2 = True
IS_PY27 = False
IS_PY24 = False
try:
    if sys.version_info[0] >= 3:
        IS_PY3K = True
        IS_PY2 = False
        IS_PY34_OR_GREATER = sys.version_info >= (3, 4)
        IS_PY36_OR_GREATER = sys.version_info >= (3, 6)
        IS_PY36_OR_LESSER = sys.version_info[:2] <= (3, 6)
    elif sys.version_info[0] == 2 and sys.version_info[1] == 7:
        IS_PY27 = True
    elif sys.version_info[0] == 2 and sys.version_info[1] == 4:
        IS_PY24 = True
except AttributeError:
    pass  # Not all versions have sys.version_info

try:
    SUPPORT_GEVENT = os.getenv('GEVENT_SUPPORT', 'False') == 'True'
except:
    # Jython 2.1 doesn't accept that construct
    SUPPORT_GEVENT = False

# At the moment gevent supports Python >= 2.6 and Python >= 3.3
USE_LIB_COPY = SUPPORT_GEVENT and \
               ((not IS_PY3K and sys.version_info[1] >= 6) or
                (IS_PY3K and sys.version_info[1] >= 3))


class ValuesPolicy:
    SYNC = 0
    ASYNC = 1
    ON_DEMAND = 2


LOAD_VALUES_POLICY = ValuesPolicy.SYNC
if os.getenv('PYDEVD_LOAD_VALUES_ASYNC', 'False') == 'True':
    LOAD_VALUES_POLICY = ValuesPolicy.ASYNC
if os.getenv('PYDEVD_LOAD_VALUES_ON_DEMAND', 'False') == 'True':
    LOAD_VALUES_POLICY = ValuesPolicy.ON_DEMAND
DEFAULT_VALUES_DICT = {ValuesPolicy.ASYNC: "__pydevd_value_async", ValuesPolicy.ON_DEMAND: "__pydevd_value_on_demand"}

INTERACTIVE_MODE_AVAILABLE = sys.platform in ('darwin', 'win32') or os.getenv('DISPLAY') is not None
IS_PYCHARM = True
ASYNC_EVAL_TIMEOUT_SEC = 60
NEXT_VALUE_SEPARATOR = "__pydev_val__"
BUILTINS_MODULE_NAME = '__builtin__' if IS_PY2 else 'builtins'
SHOW_DEBUG_INFO_ENV = os.getenv('PYCHARM_DEBUG') == 'True' or os.getenv('PYDEV_DEBUG') == 'True'

if SHOW_DEBUG_INFO_ENV:
    # show debug info before the debugger start
    DebugInfoHolder.DEBUG_RECORD_SOCKET_READS = True
    DebugInfoHolder.DEBUG_TRACE_LEVEL = 3
    DebugInfoHolder.DEBUG_TRACE_BREAKPOINTS = 1


def protect_libraries_from_patching():
    """
    In this function we delete some modules from `sys.modules` dictionary and import them again inside
      `_pydev_saved_modules` in order to save their original copies there. After that we can use these
      saved modules within the debugger to protect them from patching by external libraries (e.g. gevent).
    """
    patched = ['threading', 'thread', '_thread', 'time', 'socket', 'Queue', 'queue', 'select',
               'xmlrpclib', 'SimpleXMLRPCServer', 'BaseHTTPServer', 'SocketServer',
               'xmlrpc.client', 'xmlrpc.server', 'http.server', 'socketserver']

    for name in patched:
        try:
            __import__(name)
        except:
            pass

    patched_modules = dict([(k, v) for k, v in sys.modules.items()
                            if k in patched])

    for name in patched_modules:
        del sys.modules[name]

    # import for side effects
    import _pydev_imps._pydev_saved_modules

    for name in patched_modules:
        sys.modules[name] = patched_modules[name]


if USE_LIB_COPY:
    protect_libraries_from_patching()

from _pydev_imps._pydev_saved_modules import thread
_thread_id_lock = thread.allocate_lock()
thread_get_ident = thread.get_ident

if IS_PY3K:

    def dict_keys(d):
        return list(d.keys())

    def dict_values(d):
        return list(d.values())

    dict_iter_values = dict.values

    def dict_iter_items(d):
        return d.items()

    def dict_items(d):
        return list(d.items())

else:
    def dict_keys(d):
        return d.keys()

    try:
        dict_iter_values = dict.itervalues
    except:
        try:
            dict_iter_values = dict.values  # Older versions don't have the itervalues
        except:

            def dict_iter_values(d):
                return d.values()

    try:
        dict_values = dict.values
    except:

        def dict_values(d):
            return d.values()

    def dict_iter_items(d):
        try:
            return d.iteritems()
        except:
            return d.items()

    def dict_items(d):
        return d.items()

try:
    xrange = xrange
except:
    # Python 3k does not have it
    xrange = range

try:
    import itertools
    izip = itertools.izip
except:
    izip = zip


try:
    enumerate
except:
    def enumerate(lst):
        ret = []
        i = 0
        for element in lst:
            ret.append((i, element))
            i += 1
        return ret

try:
    from StringIO import StringIO
except:
    from io import StringIO

NO_FTRACE = None

if sys.version_info[:2] in ((3, 3), (3, 4), (3, 5)) or sys.version_info < (2, 7, 12):

    def NO_FTRACE(frame, event, arg):
        # In Python < 2.7.12 and <= 3.5, if we're tracing a method, frame.f_trace may not be set
        # to None, it must always be set to a tracing function.
        # See: tests_python.test_tracing_gotchas.test_tracing_gotchas
        return None


#=======================================================================================================================
# get_pid
#=======================================================================================================================
def get_pid():
    try:
        return os.getpid()
    except AttributeError:
        try:
            # Jython does not have it!
            import java.lang.management.ManagementFactory  # @UnresolvedImport -- just for jython
            pid = java.lang.management.ManagementFactory.getRuntimeMXBean().getName()
            return pid.replace('@', '_')
        except:
            # ok, no pid available (will be unable to debug multiple processes)
            return '000001'


def clear_cached_thread_id(thread):
    with _thread_id_lock:
        try:
            if thread.__pydevd_id__ != 'console_main':
                # The console_main is a special thread id used in the console and its id should never be reset
                # (otherwise we may no longer be able to get its variables -- see: https://www.brainwy.com/tracker/PyDev/776).
                del thread.__pydevd_id__
        except AttributeError:
            pass


# Don't let threads be collected (so that id(thread) is guaranteed to be unique).
_thread_id_to_thread_found = {}


def _get_or_compute_thread_id_with_lock(thread, is_current_thread):
    with _thread_id_lock:
        # We do a new check with the lock in place just to be sure that nothing changed
        tid = getattr(thread, '__pydevd_id__', None)
        if tid is not None:
            return tid

        _thread_id_to_thread_found[id(thread)] = thread

        # Note: don't use thread.ident because a new thread may have the
        # same id from an old thread.
        pid = get_pid()
        tid = 'pid_%s_id_%s' % (pid, id(thread))

        thread.__pydevd_id__ = tid

    return tid


def get_current_thread_id(thread):
    '''
    Note: the difference from get_current_thread_id to get_thread_id is that
    for the current thread we can get the thread id while the thread.ident
    is still not set in the Thread instance.
    '''
    try:
        # Fast path without getting lock.
        tid = thread.__pydevd_id__
        if tid is None:
            # Fix for https://www.brainwy.com/tracker/PyDev/645
            # if __pydevd_id__ is None, recalculate it... also, use an heuristic
            # that gives us always the same id for the thread (using thread.ident or id(thread)).
            raise AttributeError()
    except AttributeError:
        tid = _get_or_compute_thread_id_with_lock(thread, is_current_thread=True)

    return tid


def get_thread_id(thread):
    try:
        # Fast path without getting lock.
        tid = thread.__pydevd_id__
        if tid is None:
            # Fix for https://www.brainwy.com/tracker/PyDev/645
            # if __pydevd_id__ is None, recalculate it... also, use an heuristic
            # that gives us always the same id for the thread (using thread.ident or id(thread)).
            raise AttributeError()
    except AttributeError:
        tid = _get_or_compute_thread_id_with_lock(thread, is_current_thread=False)

    return tid


def set_thread_id(thread, thread_id):
    with _thread_id_lock:
        thread.__pydevd_id__ = thread_id


#=======================================================================================================================
# Null
#=======================================================================================================================
class Null:
    """
    Gotten from: http://aspn.activestate.com/ASPN/Cookbook/Python/Recipe/68205
    """

    def __init__(self, *args, **kwargs):
        return None

    def __call__(self, *args, **kwargs):
        return self

    def __enter__(self, *args, **kwargs):
        return self

    def __exit__(self, *args, **kwargs):
        return self

    def __getattr__(self, mname):
        if len(mname) > 4 and mname[:2] == '__' and mname[-2:] == '__':
            # Don't pretend to implement special method names.
            raise AttributeError(mname)
        return self

    def __setattr__(self, name, value):
        return self

    def __delattr__(self, name):
        return self

    def __repr__(self):
        return "<Null>"

    def __str__(self):
        return "Null"

    def __len__(self):
        return 0

    def __getitem__(self):
        return self

    def __setitem__(self, *args, **kwargs):
        pass

    def write(self, *args, **kwargs):
        pass

    def __nonzero__(self):
        return 0

    def __iter__(self):
        return iter(())


# Default instance
NULL = Null()


def call_only_once(func):
    '''
    To be used as a decorator

    @call_only_once
    def func():
        print 'Calling func only this time'

    Actually, in PyDev it must be called as:

    func = call_only_once(func) to support older versions of Python.
    '''

    def new_func(*args, **kwargs):
        if not new_func._called:
            new_func._called = True
            return func(*args, **kwargs)

    new_func._called = False
    return new_func


#=======================================================================================================================
# GlobalDebuggerHolder
#=======================================================================================================================
class GlobalDebuggerHolder:
    '''
        Holder for the global debugger.
    '''
    global_dbg = None  # Note: don't rename (the name is used in our attach to process)


#=======================================================================================================================
# get_global_debugger
#=======================================================================================================================
def get_global_debugger():
    return GlobalDebuggerHolder.global_dbg


GetGlobalDebugger = get_global_debugger  # Backward-compatibility


#=======================================================================================================================
# set_global_debugger
#=======================================================================================================================
def set_global_debugger(dbg):
    GlobalDebuggerHolder.global_dbg = dbg


if __name__ == '__main__':
    if Null():
        sys.stdout.write('here\n')

