# some code borrowed from cython by 'scoder'
from cpython.ref cimport PyObject, Py_INCREF, Py_XINCREF, Py_XDECREF

cdef extern from "frameobject.h":
    ctypedef struct PyFrameObject:
        PyObject *f_trace
    ctypedef struct PyThreadState:
        int recursion_depth

from cpython.pystate cimport (
Py_tracefunc, PyThreadState_Get,
PyTrace_CALL, PyTrace_EXCEPTION, PyTrace_LINE, PyTrace_RETURN)
import sys
import traceback
version = 1
cdef extern from *:
    void PyEval_SetTrace(Py_tracefunc cfunc, PyObject *obj)
    void PyFrame_FastToLocals(PyFrameObject*)

try:
    RecursionLimitExceeded = RecursionError
except NameError:
    RecursionLimitExceeded = RuntimeError

cdef int trace_trampoline(PyObject* _traceobj, PyFrameObject* _frame, int what, PyObject* _arg) except -1:
    """
    This is (more or less) what CPython does in sysmodule.c, function trace_trampoline().
    """
    cdef PyObject *tmp

    if what == PyTrace_CALL:
        if _traceobj is NULL:
            return 0
        callback = <object>_traceobj
    elif _frame.f_trace:
        callback = <object>_frame.f_trace
    else:
        return 0

    PyFrame_FastToLocals(_frame)

    frame = <object>_frame
    arg = <object>_arg if _arg else None
    what_str = what_to_str(what)

    try:
        result = callback(frame, what_str, arg)
    except RecursionLimitExceeded as e:
        if not _check_if_recursion_limit_exceeded_by_user(e):
            detach_debugger(_frame)
        raise
    except:
        detach_debugger(_frame)
        raise

    if result is not None:
        # A bug in Py2.6 prevents us from calling the Python-level setter here,
        # or otherwise we would get miscalculated line numbers. Was fixed in Py2.7.
        tmp = _frame.f_trace
        Py_INCREF(result)
        _frame.f_trace = <PyObject*>result
        Py_XDECREF(tmp)

    return 0


cdef bint ALREADY_WARNED = False
def _warn_user_of_stack_overflow():
    global ALREADY_WARNED
    if not ALREADY_WARNED:
        sys.stderr.write("Stack overflow detected\n")
        traceback.print_stack()
    ALREADY_WARNED= True



cdef bint _check_if_recursion_limit_exceeded_by_user(object exc):
    cdef PyThreadState* thread_state
    if 'maximum recursion depth exceeded' in exc.args[0]:
        thread_state = PyThreadState_Get()
        current_depth = thread_state.recursion_depth
        max_depth = sys.getrecursionlimit()
        if (max_depth - current_depth) < 20:
            # Warning doesn't work because we don't have enough stack
            # instead we need to set this value for later.
            # _warn_user_of_stack_overflow()

            return True
    return False

cdef void detach_debugger(PyFrameObject* _frame):
     PyEval_SetTrace(NULL, NULL)
     tmp = _frame.f_trace
     _frame.f_trace = NULL
     Py_XDECREF(tmp)
cdef int dummy_trace_trampoline(PyObject* _traceobj, PyFrameObject* _frame, int what, PyObject* _arg):
    return 0

cdef inline str what_to_str(int what):
    if what == PyTrace_CALL:
        return "call"
    elif what == PyTrace_EXCEPTION:
        return "exc"
    elif what == PyTrace_LINE:
        return "line"
    elif what == PyTrace_RETURN:
        return "return"
    else:
        raise Exception("Invalid arg")

def set_trace (object callback):
    if callback is None:
        PyEval_SetTrace(NULL, NULL)
    else:
        PyEval_SetTrace(<Py_tracefunc>trace_trampoline, <PyObject*>callback )

def set_dummy_trace():
    PyEval_SetTrace(dummy_trace_trampoline, NULL)

