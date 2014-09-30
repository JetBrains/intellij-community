'''
This module holds the constants used for specifying the states of the debugger.
'''
STATE_RUN = 1
STATE_SUSPEND = 2

PYTHON_SUSPEND = 1

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

#Optimize with psyco? This gave a 50% speedup in the debugger in tests
USE_PSYCO_OPTIMIZATION = True

#Hold a reference to the original _getframe (because psyco will change that as soon as it's imported)
import sys #Note: the sys import must be here anyways (others depend on it)
try:
    GetFrame = sys._getframe
except AttributeError:
    def GetFrame():
        raise AssertionError('sys._getframe not available (possible causes: enable -X:Frames on IronPython?)')

#Used to determine the maximum size of each variable passed to eclipse -- having a big value here may make
#the communication slower -- as the variables are being gathered lazily in the latest version of eclipse,
#this value was raised from 200 to 1000.
MAXIMUM_VARIABLE_REPRESENTATION_SIZE = 1000

import os

import pydevd_vm_type

IS_JYTHON = pydevd_vm_type.GetVmType() == pydevd_vm_type.PydevdVmType.JYTHON

IS_JYTH_LESS25 = False
if IS_JYTHON:
    if sys.version_info[0] == 2 and sys.version_info[1] < 5:
        IS_JYTH_LESS25 = True

#=======================================================================================================================
# Python 3?
#=======================================================================================================================
IS_PY3K = False
IS_PY27 = False
IS_PY24 = False
try:
    if sys.version_info[0] >= 3:
        IS_PY3K = True
    elif sys.version_info[0] == 2 and sys.version_info[1] == 7:
        IS_PY27 = True
    elif sys.version_info[0] == 2 and sys.version_info[1] == 4:
        IS_PY24 = True
except AttributeError:
    pass  #Not all versions have sys.version_info

try:
    IS_64_BITS = sys.maxsize > 2 ** 32
except AttributeError:
    try:
        import struct
        IS_64_BITS = struct.calcsize("P") * 8 > 32
    except:
        IS_64_BITS = False

SUPPORT_GEVENT = os.getenv('GEVENT_SUPPORT', 'False') == 'True'

USE_LIB_COPY = SUPPORT_GEVENT and not IS_PY3K and sys.version_info[1] >= 6
import _pydev_threading as threading

from _pydev_imps import _pydev_thread
_nextThreadIdLock = _pydev_thread.allocate_lock()

#=======================================================================================================================
# Jython?
#=======================================================================================================================
try:
    DictContains = dict.has_key
except:
    try:
        #Py3k does not have has_key anymore, and older versions don't have __contains__
        DictContains = dict.__contains__
    except:
        try:
            DictContains = dict.has_key
        except NameError:
            def DictContains(d, key):
                return d.has_key(key)
#=======================================================================================================================
# Jython?
#=======================================================================================================================
try:
    DictPop = dict.pop
except:
    def DictPop(d, key, default=None):
        try:
            ret = d[key]
            del d[key]
            return ret
        except:
            return default


if IS_PY3K:
    def DictKeys(d):
        return list(d.keys())

    def DictValues(d):
        return list(d.values())

    DictIterValues = dict.values

    def DictIterItems(d):
        return d.items()

    def DictItems(d):
        return list(d.items())

else:
    DictKeys = dict.keys
    try:
        DictIterValues = dict.itervalues
    except:
        DictIterValues = dict.values #Older versions don't have the itervalues

    DictValues = dict.values

    def DictIterItems(d):
        return d.iteritems()

    def DictItems(d):
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
# NextId
#=======================================================================================================================
class NextId:

    def __init__(self):
        self._id = 0

    def __call__(self):
        #No need to synchronize here
        self._id += 1
        return self._id

_nextThreadId = NextId()

#=======================================================================================================================
# GetThreadId
#=======================================================================================================================
def GetThreadId(thread):
    try:
        return thread.__pydevd_id__
    except AttributeError:
        _nextThreadIdLock.acquire()
        try:
            #We do a new check with the lock in place just to be sure that nothing changed
            if not hasattr(thread, '__pydevd_id__'):
                try:
                    pid = os.getpid()
                except AttributeError:
                    try:
                        #Jython does not have it!
                        import java.lang.management.ManagementFactory  #@UnresolvedImport -- just for jython
                        pid = java.lang.management.ManagementFactory.getRuntimeMXBean().getName()
                        pid = pid.replace('@', '_')
                    except:
                        #ok, no pid available (will be unable to debug multiple processes)
                        pid = '000001'

                thread.__pydevd_id__ = 'pid%s_seq%s' % (pid, _nextThreadId())
        finally:
            _nextThreadIdLock.release()

    return thread.__pydevd_id__

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

