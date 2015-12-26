# Defines which version of the trace dispatch we'll use.
try:
    from _pydevd_bundle.pydevd_trace_dispatch_cython import trace_dispatch as _trace_dispatch
    def trace_dispatch(py_db, frame, event, arg):
        return _trace_dispatch(py_db, frame, event, arg)
except ImportError:
    from _pydevd_bundle.pydevd_trace_dispatch_regular import trace_dispatch  # @UnusedImport
