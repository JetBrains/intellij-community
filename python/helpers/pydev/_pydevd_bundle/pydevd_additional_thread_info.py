# Defines which version of the trace dispatch we'll use.
try:
    from _pydevd_bundle.pydevd_additional_thread_info_cython import PyDBAdditionalThreadInfo
except ImportError:
    from _pydevd_bundle.pydevd_additional_thread_info_regular import PyDBAdditionalThreadInfo  # @UnusedImport
