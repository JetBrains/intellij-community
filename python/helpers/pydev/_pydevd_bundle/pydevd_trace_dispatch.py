# Defines which version of the trace_dispatch we'll use.
# Should give warning only here if cython is not available but supported.

import os
use_cython = os.getenv('PYDEVD_USE_CYTHON', None)
import sys

if use_cython == 'YES':
    # We must import the cython version if forcing cython
    from _pydevd_bundle.pydevd_cython_wrapper import trace_dispatch as _trace_dispatch
    def trace_dispatch(py_db, frame, event, arg):
        return _trace_dispatch(py_db, frame, event, arg)

elif use_cython == 'NO':
    # Use the regular version if not forcing cython
    from _pydevd_bundle.pydevd_trace_dispatch_regular import trace_dispatch  # @UnusedImport

elif use_cython is None:
    # Regular: use fallback if not found and give message to user
    try:
        from _pydevd_bundle.pydevd_cython_wrapper import trace_dispatch as _trace_dispatch
        def trace_dispatch(py_db, frame, event, arg):
            return _trace_dispatch(py_db, frame, event, arg)

    except ImportError:
        from _pydevd_bundle.pydevd_additional_thread_info_regular import PyDBAdditionalThreadInfo  # @UnusedImport
        from _pydevd_bundle.pydevd_trace_dispatch_regular import trace_dispatch  # @UnusedImport
        from _pydevd_bundle.pydevd_constants import CYTHON_SUPPORTED

        if CYTHON_SUPPORTED:
            from _pydev_bundle.pydev_monkey import log_error_once
            log_error_once("warning: Debugger speedups using cython not found. Run '\"%s\" \"%s\" build_ext --inplace' to build." % (
                sys.executable, os.path.join(os.path.dirname(os.path.dirname(__file__)), 'setup_cython.py')))

else:
    raise RuntimeError('Unexpected value for PYDEVD_USE_CYTHON: %s (accepted: YES, NO)' % (use_cython,))


