import os
import sys

from _pydevd_bundle.pydevd_constants import IS_PYCHARM

IS_PY36_OR_GREATER = sys.version_info >= (3, 6)

frame_eval_func = None
stop_frame_eval = None
dummy_trace_dispatch = None
show_frame_eval_warning = False
clear_thread_local_info = None

# "NO" means we should not use frame evaluation, 'YES' we should use it (and fail if not there) and unspecified uses if possible.
use_frame_eval = os.environ.get('PYDEVD_USE_FRAME_EVAL', None)

if use_frame_eval == 'NO':
    pass

elif use_frame_eval == 'YES':
    # Fail if unable to use
    from _pydevd_frame_eval.pydevd_frame_eval_cython_wrapper import frame_eval_func, stop_frame_eval, dummy_trace_dispatch, clear_thread_local_info

elif use_frame_eval is None:
    # Try to use if possible
    if IS_PY36_OR_GREATER:
        try:
            from _pydevd_frame_eval.pydevd_frame_eval_cython_wrapper import frame_eval_func, stop_frame_eval, dummy_trace_dispatch, clear_thread_local_info
        except ImportError:
            from _pydev_bundle.pydev_monkey import log_error_once

            dirname = os.path.dirname(os.path.dirname(__file__))
            if not IS_PYCHARM:
                log_error_once("warning: Debugger speedups using cython not found. Run '\"%s\" \"%s\" build_ext --inplace' to build." % (
                    sys.executable, os.path.join(dirname, 'setup_cython.py')))
            else:
                show_frame_eval_warning = True

else:
    raise RuntimeError('Unexpected value for PYDEVD_USE_FRAME_EVAL: %s (accepted: YES, NO)' % (use_frame_eval,))
