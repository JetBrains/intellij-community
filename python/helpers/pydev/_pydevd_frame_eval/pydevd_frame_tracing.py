import sys

from _pydev_bundle import pydev_log
from _pydev_imps._pydev_saved_modules import threading
from _pydevd_bundle.pydevd_comm import get_global_debugger, CMD_SET_BREAK


def update_globals_dict(globals_dict):
    new_globals = {'_pydev_stop_at_break': _pydev_stop_at_break}
    globals_dict.update(new_globals)


def _pydev_stop_at_break():
    frame = sys._getframe(1)

    t = threading.currentThread()
    if t.additional_info.is_tracing:
        return

    if t.additional_info.pydev_step_cmd == -1:
        # do not handle breakpoints while stepping, because they're handled by old tracing function
        t.additional_info.is_tracing = True
        debugger = get_global_debugger()
        pydev_log.debug("Suspending at breakpoint in file: {} on line {}".format(frame.f_code.co_filename, frame.f_lineno))
        debugger.set_suspend(t, CMD_SET_BREAK)
        debugger.do_wait_suspend(t, frame, 'line', None)
        t.additional_info.is_tracing = False


def pydev_trace_code_wrapper():
    # import this module again, because it's inserted inside user's code
    global _pydev_stop_at_break
    _pydev_stop_at_break()
