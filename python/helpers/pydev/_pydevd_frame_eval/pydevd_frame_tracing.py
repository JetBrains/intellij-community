import sys

from _pydev_bundle import pydev_log
from _pydev_imps._pydev_saved_modules import threading
from _pydevd_bundle.pydevd_additional_thread_info import PyDBAdditionalThreadInfo
from _pydevd_bundle.pydevd_comm import get_global_debugger, CMD_SET_BREAK


def _pydev_stop_at_break():
    debugger = get_global_debugger()
    t = threading.currentThread()
    try:
        additional_info = t.additional_info
        if additional_info is None:
            raise AttributeError()
    except:
        t.additional_info = PyDBAdditionalThreadInfo()
    frame = sys._getframe(1)
    pydev_log.debug("Suspending at breakpoint in file: {} on line {}".format(frame.f_code.co_filename, frame.f_lineno))

    debugger.set_suspend(t, CMD_SET_BREAK)
    debugger.do_wait_suspend(t, frame, 'line', None)


def pydev_trace_code_wrapper():
    # import this module again, because it's inserted inside user's code
    from _pydevd_frame_eval.pydevd_frame_tracing import _pydev_stop_at_break
    _pydev_stop_at_break()
