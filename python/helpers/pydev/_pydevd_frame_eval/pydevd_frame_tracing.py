import sys


def _pydev_trace_function():
    frame = sys._getframe(1)
    print(frame.f_code.co_filename, frame.f_lineno, frame.f_locals)


def _pydev_trace_code_wrapper():
    # we have to import with module again, because we insert this code inside user's code
    from _pydevd_frame_eval.pydevd_frame_tracing import _pydev_trace_function
    _pydev_trace_function()
