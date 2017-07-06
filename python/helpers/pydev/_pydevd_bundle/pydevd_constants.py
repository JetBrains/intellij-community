'''
This module holds the constants used for specifying the states of the debugger.
'''
from __future__ import nested_scopes

STATE_RUN = 1
STATE_SUSPEND = 2

PYTHON_SUSPEND = 1
DJANGO_SUSPEND = 2
JINJA2_SUSPEND = 3

try:
    __setFalse = False
except:
    import __builtin__

    setattr(__builtin__, 'True', 1)
    setattr(__builtin__, 'False', 0)

class DebugInfoHolder:
    #we have to put it here because it can be set through the command line (so, the
    #already imported references would not have it).
    DEBUG_RECORD_SOCKET_READS = False
    DEBUG_TRACE_LEVEL = -1
    DEBUG_TRACE_BREAKPOINTS = -1

#Hold a reference to the original _getframe (because psyco will change that as soon as it's imported)
import sys #Note: the sys import must be here anyways (others depend on it)
try:
    get_frame = sys._getframe
except AttributeError:
    def get_frame():
        raise AssertionError('sys._getframe not available (possible causes: enable -X:Frames on IronPython?)')

#Used to determine the maximum size of each variable passed to eclipse -- having a big value here may make
#the communication slower -- as the variables are being gathered lazily in the latest version of eclipse,
#this value was raised from 200 to 1000.
MAXIMUM_VARIABLE_REPRESENTATION_SIZE = 1000
# Prefix for saving functions return values in locals
RETURN_VALUES_DICT = '__pydevd_ret_val_dict'

import os

from _pydevd_bundle import pydevd_vm_type

IS_JYTHON = pydevd_vm_type.get_vm_type() == pydevd_vm_type.PydevdVmType.JYTHON

IS_JYTH_LESS25 = False
if IS_JYTHON:
    if sys.version_info[0] == 2 and sys.version_info[1] < 5:
        IS_JYTH_LESS25 = True

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
            or (sys.version_info[0] == 3 and sys.version_info[1] >= 3)
            or (sys.version_info[0] > 3)
            ):
            # Supported in 2.7 or 3.3 onwards (32 or 64)
            CYTHON_SUPPORTED = True


#=======================================================================================================================
# Python 3?
#=======================================================================================================================
IS_PY3K = False
IS_PY34_OR_GREATER = False
IS_PY36_OR_GREATER = False
IS_PY2 = True
IS_PY27 = False
IS_PY24 = False
try:
    if sys.version_info[0] >= 3:
        IS_PY3K = True
        IS_PY2 = False
        if (sys.version_info[0] == 3 and sys.version_info[1] >= 4) or sys.version_info[0] > 3:
            IS_PY34_OR_GREATER = True
        if (sys.version_info[0] == 3 and sys.version_info[1] >= 6) or sys.version_info[0] > 3:
            IS_PY36_OR_GREATER = True
    elif sys.version_info[0] == 2 and sys.version_info[1] == 7:
        IS_PY27 = True
    elif sys.version_info[0] == 2 and sys.version_info[1] == 4:
        IS_PY24 = True
except AttributeError:
    pass  #Not all versions have sys.version_info

try:
    SUPPORT_GEVENT = os.getenv('GEVENT_SUPPORT', 'False') == 'True'
except:
    # Jython 2.1 doesn't accept that construct
    SUPPORT_GEVENT = False

# At the moment gevent supports Python >= 2.6 and Python >= 3.3
USE_LIB_COPY = SUPPORT_GEVENT and \
               ((not IS_PY3K and sys.version_info[1] >= 6) or
                (IS_PY3K and sys.version_info[1] >= 3))


INTERACTIVE_MODE_AVAILABLE = sys.platform in ('darwin', 'win32') or os.getenv('DISPLAY') is not None
SHOW_CYTHON_WARNING = False


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
_nextThreadIdLock = thread.allocate_lock()

#=======================================================================================================================
# Jython?
#=======================================================================================================================
try:
    dict_contains = dict.has_key
except:
    try:
        #Py3k does not have has_key anymore, and older versions don't have __contains__
        dict_contains = dict.__contains__
    except:
        try:
            dict_contains = dict.has_key
        except NameError:
            def dict_contains(d, key):
                return d.has_key(key)

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
    dict_keys = None
    try:
        dict_keys = dict.keys
    except:
        pass

    if IS_JYTHON or not dict_keys:
        def dict_keys(d):
            return d.keys()

    try:
        dict_iter_values = dict.itervalues
    except:
        try:
            dict_iter_values = dict.values #Older versions don't have the itervalues
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
    #Python 3k does not have it
    xrange = range

try:
    import itertools
    izip = itertools.izip
except:
    izip = zip

try:
    object
except NameError:
    class object:
        pass

    import __builtin__

    setattr(__builtin__, 'object', object)


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

#=======================================================================================================================
# StringIO
#=======================================================================================================================
try:
    from StringIO import StringIO
except:
    from io import StringIO


#=======================================================================================================================
# get_pid
#=======================================================================================================================
def get_pid():
    try:
        return os.getpid()
    except AttributeError:
        try:
            #Jython does not have it!
            import java.lang.management.ManagementFactory  #@UnresolvedImport -- just for jython
            pid = java.lang.management.ManagementFactory.getRuntimeMXBean().getName()
            return pid.replace('@', '_')
        except:
            #ok, no pid available (will be unable to debug multiple processes)
            return '000001'

def clear_cached_thread_id(thread):
    try:
        del thread.__pydevd_id__
    except AttributeError:
        pass

#=======================================================================================================================
# get_thread_id
#=======================================================================================================================
def get_thread_id(thread):
    try:
        tid = thread.__pydevd_id__
        if tid is None:
            # Fix for https://sw-brainwy.rhcloud.com/tracker/PyDev/645
            # if __pydevd_id__ is None, recalculate it... also, use an heuristic
            # that gives us always the same id for the thread (using thread.ident or id(thread)).
            raise AttributeError()
    except AttributeError:
        _nextThreadIdLock.acquire()
        try:
            #We do a new check with the lock in place just to be sure that nothing changed
            tid = getattr(thread, '__pydevd_id__', None)
            if tid is None:
                pid = get_pid()
                try:
                    tid = thread.__pydevd_id__ = 'pid_%s_id_%s' % (pid, thread.get_ident())
                except:
                    # thread.ident isn't always there... (use id(thread) instead if it's not there).
                    tid = thread.__pydevd_id__ = 'pid_%s_id_%s' % (pid, id(thread))
        finally:
            _nextThreadIdLock.release()

    return tid

#===============================================================================
# Null
#===============================================================================
class Null:
    """
    Gotten from: http://aspn.activestate.com/ASPN/Cookbook/Python/Recipe/68205
    """

    def __init__(self, *args, **kwargs):
        return None

    def __call__(self, *args, **kwargs):
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

if __name__ == '__main__':
    if Null():
        sys.stdout.write('here\n')

