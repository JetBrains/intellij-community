from _pydevd_bundle.pydevd_constants import *
from _pydevd_bundle import pydevd_tracing
import sys
from _pydev_bundle import pydev_log
from _pydevd_bundle import pydevd_import_class

_original_excepthook = None
_handle_exceptions = None


from _pydev_imps._pydev_saved_modules import threading

threadingCurrentThread = threading.currentThread

from _pydevd_bundle.pydevd_comm import get_global_debugger

class ExceptionBreakpoint:

    def __init__(
        self,
        qname,
        notify_always,
        notify_on_terminate,
        notify_on_first_raise_only,
        ignore_libraries
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
        self.ignore_libraries = ignore_libraries

        self.type = exctype


    def __str__(self):
        return self.qname


class LineBreakpoint(object):
    def __init__(self, line, condition, func_name, expression, suspend_policy="NONE"):
        self.line = line
        self.condition = condition
        self.func_name = func_name
        self.expression = expression
        self.suspend_policy = suspend_policy

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
            for exception_breakpoint in dict_iter_values(exceptions):
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

    if exctype is KeyboardInterrupt:
        return

    frames = []
    debugger = get_global_debugger()
    user_frame = None

    while tb:
        frame = tb.tb_frame
        if exception_breakpoint.ignore_libraries and not debugger.not_in_scope(frame.f_code.co_filename):
            user_frame = tb.tb_frame
        frames.append(tb.tb_frame)
        tb = tb.tb_next

    thread = threadingCurrentThread()
    frames_byid = dict([(id(frame),frame) for frame in frames])
    if exception_breakpoint.ignore_libraries and user_frame is not None:
        frame = user_frame
    else:
        frame = frames[-1]
    exception = (exctype, value, tb)
    try:
        thread.additional_info.pydev_message = exception_breakpoint.qname
    except:
        thread.additional_info.pydev_message = exception_breakpoint.qname.encode('utf-8')

    pydevd_tracing.SetTrace(None) #no tracing from here

    pydev_log.debug('Handling post-mortem stop on exception breakpoint %s' % exception_breakpoint.qname)

    debugger.handle_post_mortem_stop(thread, frame, frames_byid, exception)

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
        return pydevd_import_class.import_name(kls)
