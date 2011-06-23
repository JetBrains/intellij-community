from pydevd_constants import *
import sys

_original_excepthook = None
_handle_exceptions = None


NOTIFY_ALWAYS="NOTIFY_ALWAYS"
NOTIFY_ON_TERMINATE="NOTIFY_ON_TERMINATE"

import threading
threadingCurrentThread = threading.currentThread

from pydevd_comm import GetGlobalDebugger

class ExceptionBreakpoint:
    def __init__(self, qname, notify_always, notify_on_terminate):
        exctype = get_class(qname)
        self.qname = qname
        if exctype is not None:
            self.name = exctype.__name__
        else:
            self.name = None

        self.type = exctype
        self.notify = {NOTIFY_ALWAYS: notify_always, NOTIFY_ON_TERMINATE: notify_on_terminate}

    def __str__(self):
        return self.qname

class LineBreakpoint:
    def __init__(self, type, flag, condition, func_name, expression):
        self.type = type
        self.condition = condition
        self.func_name = func_name
        self.expression = expression

    def get_break_dict(self, breakpoints, file):
        if DictContains(breakpoints, file):
            breakDict = breakpoints[file]
        else:
            breakDict = {}
        breakpoints[file] = breakDict
        return breakDict

    def trace(self, file, line, func_name):
        if DEBUG_TRACE_BREAKPOINTS > 0:
            sys.stderr.write('Added breakpoint:%s - line:%s - func_name:%s\n' % (file, line, func_name))
            sys.stderr.flush()

    def add(self, breakpoints, file, line, func_name):
      self.trace(file, line, func_name)

      breakDict = self.get_break_dict(breakpoints, file)

      breakDict[line] = self

def get_exception_full_qname(exctype):
    if not exctype:
        return None
    return exctype.__module__ + '.' + exctype.__name__

def get_exception_name(exctype):
    if not exctype:
        return None
    return exctype.__name__


def get_exception_breakpoint(exctype, exceptions, notify_class):
    name = get_exception_full_qname(exctype)
    exc = None
    if exceptions is not None:
        for k, e in exceptions.items():
            if name == k:
                exc = e
                break
            if e.notify[notify_class] and ((e.type is not None and issubclass(exctype, e.type))):
                if exc is None or issubclass(e.type, exc.type):
                    exc = e
    return exc

#=======================================================================================================================
# excepthook
#=======================================================================================================================
def excepthook(exctype, value, tb):
    global _handle_exceptions
    if _handle_exceptions is not None:
        exception_breakpoint = get_exception_breakpoint(exctype, _handle_exceptions, NOTIFY_ON_TERMINATE)
    else:
        exception_breakpoint = None

    if exception_breakpoint is None:
        return _original_excepthook(exctype, value, tb)

    #Always call the original excepthook before going on to call the debugger post mortem to show it.
    _original_excepthook(exctype, value, tb)

    frames = []

    traceback = tb
    while tb:
        frames.append(tb.tb_frame)
        tb = tb.tb_next

    thread = threadingCurrentThread()
    frames_byid = dict([(id(frame),frame) for frame in frames])
    frame = frames[-1]
    thread.additionalInfo.exception = (exctype, value, tb)
    thread.additionalInfo.pydev_force_stop_at_exception = (frame, frames_byid)
    thread.additionalInfo.message = exception_breakpoint.name
    #sys.exc_info = lambda : (exctype, value, traceback)
    debugger = GetGlobalDebugger()
    debugger.force_post_mortem_stop += 1


#=======================================================================================================================
# set_pm_excepthook
#=======================================================================================================================
def set_pm_excepthook(handle_exceptions_arg=None):
    '''
    Should be called to register the excepthook to be used.

    It's only useful for uncaucht exceptions. I.e.: exceptions that go up to the excepthook.

    Can receive a parameter to stop only on some exceptions.

    E.g.:
        register_excepthook((IndexError, ValueError))

        or

        register_excepthook(IndexError)

        if passed without a parameter, will break on any exception

    @param handle_exceptions: exception or tuple(exceptions)
        The exceptions that should be handled.
    '''
    global _handle_exceptions
    global _original_excepthook
    if sys.excepthook != excepthook:
        #Only keep the original if it's not our own excepthook (if called many times).
        _original_excepthook = sys.excepthook

    _handle_exceptions = handle_exceptions_arg
    sys.excepthook = excepthook

def restore_pm_excepthook():
    global _original_excepthook
    if _original_excepthook:
        sys.excepthook = _original_excepthook
        _original_excepthook = None


def update_exception_hook(dbg):
    if dbg.exception_set:
        set_pm_excepthook(dict(dbg.exception_set))
    else:
        restore_pm_excepthook()

def get_class( kls ):
    if IS_PY24 and "BaseException" == kls:
        kls = "Exception"
    parts = kls.split('.')
    module = ".".join(parts[:-1])
    if module == "":
        if IS_PY3K:
            module = "builtins"
        else:
            module = "__builtin__"
    try:
        m = __import__( module )
        for comp in parts[-1:]:
            if m is None:
                return None
            m = getattr(m, comp, None)
        return m
    except ImportError:
        return None