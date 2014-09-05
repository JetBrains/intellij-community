from pydevd_constants import *
import pydevd_tracing
import sys
import pydev_log
import pydevd_import_class

_original_excepthook = None
_handle_exceptions = None


import _pydev_threading as threading

threadingCurrentThread = threading.currentThread

from pydevd_comm import GetGlobalDebugger

class ExceptionBreakpoint:

    def __init__(
        self,
        qname,
        notify_always,
        notify_on_terminate,
        notify_on_first_raise_only,
        ):
        exctype = _get_class(qname)
        self.qname = qname
        if exctype is not None:
            self.name = exctype.__name__
        else:
            self.name = None

        self.notify_on_terminate = notify_on_terminate
        self.notify_always = notify_always
        self.notify_on_first_raise_only = notify_on_first_raise_only

        self.type = exctype


    def __str__(self):
        return self.qname


class LineBreakpoint(object):
    def __init__(self, line, condition, func_name, expression):
        self.line = line
        self.condition = condition
        self.func_name = func_name
        self.expression = expression

def get_exception_full_qname(exctype):
    if not exctype:
        return None
    return str(exctype.__module__) + '.' + exctype.__name__

def get_exception_name(exctype):
    if not exctype:
        return None
    return exctype.__name__


def get_exception_breakpoint(exctype, exceptions):
    exception_full_qname = get_exception_full_qname(exctype)

    exc = None
    if exceptions is not None:
        try:
            return exceptions[exception_full_qname]
        except KeyError:
            for exception_breakpoint in DictIterValues(exceptions):
                if exception_breakpoint.type is not None and issubclass(exctype, exception_breakpoint.type):
                    if exc is None or issubclass(exception_breakpoint.type, exc.type):
                        exc = exception_breakpoint
    return exc

#=======================================================================================================================
# _excepthook
#=======================================================================================================================
def _excepthook(exctype, value, tb):
    global _handle_exceptions
    if _handle_exceptions:
        exception_breakpoint = get_exception_breakpoint(exctype, _handle_exceptions)
    else:
        exception_breakpoint = None

    #Always call the original excepthook before going on to call the debugger post mortem to show it.
    _original_excepthook(exctype, value, tb)

    if not exception_breakpoint:
        return

    if tb is None:  #sometimes it can be None, e.g. with GTK
        return

    frames = []

    while tb:
        frames.append(tb.tb_frame)
        tb = tb.tb_next

    thread = threadingCurrentThread()
    frames_byid = dict([(id(frame),frame) for frame in frames])
    frame = frames[-1]
    thread.additionalInfo.exception = (exctype, value, tb)
    thread.additionalInfo.pydev_force_stop_at_exception = (frame, frames_byid)
    thread.additionalInfo.message = exception_breakpoint.qname
    debugger = GetGlobalDebugger()

    pydevd_tracing.SetTrace(None) #no tracing from here

    pydev_log.debug('Handling post-mortem stop on exception breakpoint %s'% exception_breakpoint.qname)

    debugger.handle_post_mortem_stop(thread.additionalInfo, thread)

#=======================================================================================================================
# _set_pm_excepthook
#=======================================================================================================================
def _set_pm_excepthook(handle_exceptions_dict=None):
    '''
    Should be called to register the excepthook to be used.

    It's only useful for uncaught exceptions. I.e.: exceptions that go up to the excepthook.

    @param handle_exceptions: dict(exception -> ExceptionBreakpoint)
        The exceptions that should be handled.
    '''
    global _handle_exceptions
    global _original_excepthook
    if sys.excepthook != _excepthook:
        #Only keep the original if it's not our own _excepthook (if called many times).
        _original_excepthook = sys.excepthook

    _handle_exceptions = handle_exceptions_dict
    sys.excepthook = _excepthook

def _restore_pm_excepthook():
    global _original_excepthook
    if _original_excepthook:
        sys.excepthook = _original_excepthook
        _original_excepthook = None


def update_exception_hook(dbg):
    if dbg.break_on_uncaught_exceptions:
        _set_pm_excepthook(dbg.break_on_uncaught_exceptions)
    else:
        _restore_pm_excepthook()

def _get_class( kls ):
    if IS_PY24 and "BaseException" == kls:
        kls = "Exception"

    try:
        return eval(kls)
    except:
        return pydevd_import_class.ImportName(kls)
