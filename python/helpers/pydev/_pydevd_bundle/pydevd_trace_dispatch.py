# Defines which version of the trace_dispatch we'll use.
# Should give warning only here if cython is not available but supported.

import os
import sys
from _pydevd_bundle.pydevd_constants import CYTHON_SUPPORTED


use_cython = os.getenv('PYDEVD_USE_CYTHON', None)
dirname = os.path.dirname(os.path.dirname(__file__))
# Do not show incorrect warning for .egg files for Remote debugger
if not CYTHON_SUPPORTED or dirname.endswith('.egg'):
    # Do not try to import cython extensions if cython isn't supported
    use_cython = 'NO'


def delete_old_compiled_extensions():
    pydev_dir = os.path.dirname(os.path.dirname(__file__))
    _pydevd_bundle_dir = os.path.dirname(__file__)
    _pydevd_frame_eval_dir = os.path.join(pydev_dir, '_pydevd_frame_eval')
    try:
        import shutil
        for file in os.listdir(_pydevd_bundle_dir):
            if file.startswith("pydevd") and file.endswith(".so"):
                os.remove(os.path.join(_pydevd_bundle_dir, file))
        for file in os.listdir(_pydevd_frame_eval_dir):
            if file.startswith("pydevd") and file.endswith(".so"):
                os.remove(os.path.join(_pydevd_frame_eval_dir, file))
        build_dir = os.path.join(pydev_dir, "build")
        if os.path.exists(build_dir):
            shutil.rmtree(os.path.join(pydev_dir, "build"))
    except OSError:
        from _pydev_bundle.pydev_monkey import log_error_once
        log_error_once("warning: failed to delete old cython speedups. Please delete all *.so files from the directories "
                       "\"%s\" and \"%s\"" % (_pydevd_bundle_dir, _pydevd_frame_eval_dir))


if use_cython == 'YES':
    # We must import the cython version if forcing cython
    from _pydevd_bundle.pydevd_cython_wrapper import trace_dispatch as _trace_dispatch, global_cache_skips, global_cache_frame_skips
    def trace_dispatch(py_db, frame, event, arg):
        return _trace_dispatch(py_db, frame, event, arg)

elif use_cython == 'NO':
    # Use the regular version if not forcing cython
    from _pydevd_bundle.pydevd_trace_dispatch_regular import trace_dispatch, global_cache_skips, global_cache_frame_skips  # @UnusedImport

elif use_cython is None:
    # Regular: use fallback if not found and give message to user
    try:
        from _pydevd_bundle.pydevd_cython_wrapper import trace_dispatch as _trace_dispatch, global_cache_skips, global_cache_frame_skips
        def trace_dispatch(py_db, frame, event, arg):
            return _trace_dispatch(py_db, frame, event, arg)

        # This version number is always available
        from _pydevd_bundle.pydevd_additional_thread_info_regular import version as regular_version
        # This version number from the already compiled cython extension
        from _pydevd_bundle.pydevd_cython_wrapper import version as cython_version
        if cython_version != regular_version:
            delete_old_compiled_extensions()
            raise ImportError()

    except ImportError:
        from _pydevd_bundle.pydevd_additional_thread_info_regular import PyDBAdditionalThreadInfo  # @UnusedImport
        from _pydevd_bundle.pydevd_trace_dispatch_regular import trace_dispatch, global_cache_skips, global_cache_frame_skips  # @UnusedImport
        from _pydev_bundle.pydev_monkey import log_error_once

        log_error_once("warning: Debugger speedups using cython not found. Run '\"%s\" \"%s\" build_ext --inplace' to build." % (
            sys.executable, os.path.join(dirname, 'setup_cython.py')))

else:
    raise RuntimeError('Unexpected value for PYDEVD_USE_CYTHON: %s (accepted: YES, NO)' % (use_cython,))
