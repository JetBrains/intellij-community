import os
import sys

IS_PY36_OR_OLDER = False
if (sys.version_info[0] == 3 and sys.version_info[1] >= 6) or sys.version_info[0] > 3:
    IS_PY36_OR_OLDER = True

frame_eval_func = None
stop_frame_eval = None

use_frame_eval = os.environ.get('PYDEVD_USE_FRAME_EVAL', None)

if use_frame_eval == 'NO':
    frame_eval_func, stop_frame_eval = None, None

else:
    if IS_PY36_OR_OLDER:
        try:
            from _pydevd_frame_eval.pydevd_frame_eval_cython_wrapper import frame_eval_func, stop_frame_eval
        except ImportError:
            from _pydev_bundle.pydev_monkey import log_error_once

            dirname = os.path.dirname(os.path.dirname(__file__))
            log_error_once("warning: Debugger speedups using cython not found. Run '\"%s\" \"%s\" build_ext --inplace' to build." % (
                sys.executable, os.path.join(dirname, 'setup_cython.py')))
