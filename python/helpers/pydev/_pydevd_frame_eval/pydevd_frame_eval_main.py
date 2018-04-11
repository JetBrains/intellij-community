import os
import sys

from _pydevd_bundle.pydevd_constants import IS_PYCHARM

IS_PY36_OR_GREATER = sys.version_info >= (3, 6)

frame_eval_func = None
stop_frame_eval = None
enable_cache_frames_without_breaks = None
dummy_trace_dispatch = None
show_frame_eval_warning = False

USE_FRAME_EVAL = os.environ.get('PYDEVD_USE_FRAME_EVAL', None)

if USE_FRAME_EVAL == 'NO':
    frame_eval_func, stop_frame_eval = None, None

else:
    if IS_PY36_OR_GREATER:
        try:
            from _pydevd_frame_eval.pydevd_frame_eval_cython_wrapper import frame_eval_func, stop_frame_eval, enable_cache_frames_without_breaks, \
                dummy_trace_dispatch
        except ImportError:
            from _pydev_bundle.pydev_monkey import log_error_once

            dirname = os.path.dirname(os.path.dirname(__file__))

            if not IS_PYCHARM:
                log_error_once("warning: Debugger speedups using cython not found. Run '\"%s\" \"%s\" build_ext --inplace' to build." % (
                    sys.executable, os.path.join(dirname, 'setup_cython.py')))
            else:
                show_frame_eval_warning = True
