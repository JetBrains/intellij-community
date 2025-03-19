import os
import sys

from _pydev_bundle.pydev_monkey import log_error_once
from _pydevd_bundle.pydevd_constants import IS_PYCHARM, IS_PY311, IS_PY312_OR_GREATER

IS_PY36_OR_GREATER = sys.version_info >= (3, 6)

frame_eval_func = None
stop_frame_eval = None
dummy_trace_dispatch = None
show_frame_eval_warning = False
clear_thread_local_info = None

# "NO" means we should not use frame evaluation, 'YES' we should use it (and fail if not there) and unspecified uses if possible.
use_frame_eval = os.environ.get('PYDEVD_USE_FRAME_EVAL', None)
use_cython = os.getenv('PYDEVD_USE_CYTHON', None)


if not IS_PY36_OR_GREATER or sys.version_info[:3] == (3, 6, 1):  # PY-37312
    pass

elif IS_PY311:  # PY-51730
    pass

elif IS_PY312_OR_GREATER:  # PEP 669 tracing should be used instead.
    pass

elif use_cython == 'NO':
    log_error_once("warning: PYDEVD_USE_CYTHON environment variable is set to 'NO'. "
                   "Frame evaluator will be also disabled because it requires Cython extensions to be enabled in order to operate correctly.")

else:
    if use_frame_eval == 'NO':
        pass

    elif use_frame_eval == 'YES':
        try:
            import _pydevd_bundle.pydevd_cython_wrapper
        except ImportError:
            # Frame evaluator doesn't work without the Cython speedups.
            pass
        else:
            # Fail if unable to use
            from _pydevd_frame_eval.pydevd_frame_eval_cython_wrapper import frame_eval_func, stop_frame_eval, dummy_trace_dispatch, clear_thread_local_info

    elif use_frame_eval is None:
        # Try to use if possible
        try:
            try:
                import _pydevd_bundle.pydevd_cython_wrapper
            except ImportError:
                # Frame evaluator doesn't work without the Cython speedups.
                pass
            else:
                from _pydevd_frame_eval.pydevd_frame_eval_cython_wrapper import frame_eval_func, stop_frame_eval, dummy_trace_dispatch, clear_thread_local_info
        except ImportError:
            dirname = os.path.dirname(os.path.dirname(__file__))
            if not IS_PYCHARM:
                log_error_once("warning: Debugger speedups using cython not found. Run '\"%s\" \"%s\" build_ext --inplace' to build." % (
                    sys.executable, os.path.join(dirname, 'setup_cython.py')))
            else:
                show_frame_eval_warning = True

    else:
        raise RuntimeError('Unexpected value for PYDEVD_USE_FRAME_EVAL: %s (accepted: YES, NO)' % (use_frame_eval,))
