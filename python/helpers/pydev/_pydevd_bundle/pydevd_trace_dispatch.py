# Defines which version of the trace_dispatch we'll use.
# Should give warning only here if cython is not available but supported.

import os
import sys
from _pydevd_bundle.pydevd_constants import CYTHON_SUPPORTED, IS_PYCHARM


use_cython = os.getenv('PYDEVD_USE_CYTHON', None)
dirname = os.path.dirname(os.path.dirname(__file__))
# Do not show incorrect warning for .egg files for Remote debugger
if not CYTHON_SUPPORTED or dirname.endswith('.egg'):
    # Do not try to import cython extensions if cython isn't supported
    use_cython = 'NO'


def delete_old_compiled_extensions():
    import _pydevd_bundle_ext
    cython_extensions_dir = os.path.dirname(os.path.dirname(_pydevd_bundle_ext.__file__))
    _pydevd_bundle_ext_dir = os.path.dirname(_pydevd_bundle_ext.__file__)
    _pydevd_frame_eval_ext_dir = os.path.join(cython_extensions_dir, '_pydevd_frame_eval_ext')
    try:
        import shutil
        for file in os.listdir(_pydevd_bundle_ext_dir):
            if file.startswith("pydevd") and file.endswith(".so"):
                os.remove(os.path.join(_pydevd_bundle_ext_dir, file))
        for file in os.listdir(_pydevd_frame_eval_ext_dir):
            if file.startswith("pydevd") and file.endswith(".so"):
                os.remove(os.path.join(_pydevd_frame_eval_ext_dir, file))
        build_dir = os.path.join(cython_extensions_dir, "build")
        if os.path.exists(build_dir):
            shutil.rmtree(os.path.join(cython_extensions_dir, "build"))
    except OSError:
        from _pydev_bundle.pydev_monkey import log_error_once
        log_error_once("warning: failed to delete old cython speedups. Please delete all *.so files from the directories "
                       "\"%s\" and \"%s\"" % (_pydevd_bundle_ext_dir, _pydevd_frame_eval_ext_dir))

show_tracing_warning = False

if use_cython == 'YES':
    # We must import the cython version if forcing cython
    from _pydevd_bundle.pydevd_cython_wrapper import trace_dispatch as _trace_dispatch, global_cache_skips, global_cache_frame_skips, fix_top_level_trace_and_get_trace_func
    def trace_dispatch(py_db, frame, event, arg):
        if _trace_dispatch is None:
            return None
        return _trace_dispatch(py_db, frame, event, arg)

elif use_cython == 'NO':
    # Use the regular version if not forcing cython
    from _pydevd_bundle.pydevd_trace_dispatch_regular import trace_dispatch, global_cache_skips, global_cache_frame_skips, fix_top_level_trace_and_get_trace_func  # @UnusedImport

elif use_cython is None:
    # Regular: use fallback if not found and give message to user
    try:
        from _pydevd_bundle.pydevd_cython_wrapper import trace_dispatch as _trace_dispatch, global_cache_skips, global_cache_frame_skips, fix_top_level_trace_and_get_trace_func
        def trace_dispatch(py_db, frame, event, arg):
            if _trace_dispatch is None:
                return None
            return _trace_dispatch(py_db, frame, event, arg)

    except ImportError as e:
        if hasattr(e, 'version_mismatch'):
            delete_old_compiled_extensions()
        from _pydevd_bundle.pydevd_trace_dispatch_regular import trace_dispatch, global_cache_skips, global_cache_frame_skips, fix_top_level_trace_and_get_trace_func  # @UnusedImport
        from _pydev_bundle.pydev_monkey import log_error_once

        if not IS_PYCHARM:
            log_error_once("warning: Debugger speedups using cython not found. Run '\"%s\" \"%s\" build_ext --inplace' to build." % (
                sys.executable, os.path.join(dirname, 'setup_cython.py')))
        else:
            show_tracing_warning = True

else:
    raise RuntimeError('Unexpected value for PYDEVD_USE_CYTHON: %s (accepted: YES, NO)' % (use_cython,))
