# Defines which version of the PyDBAdditionalThreadInfo we'll use.

import os
use_cython = os.getenv('PYDEVD_USE_CYTHON', None)

if use_cython == 'YES':
    # We must import the cython version if forcing cython
    from _pydevd_bundle.pydevd_cython_wrapper import PyDBAdditionalThreadInfo  # @UnusedImport

elif use_cython == 'NO':
    # Use the regular version if not forcing cython
    from _pydevd_bundle.pydevd_additional_thread_info_regular import PyDBAdditionalThreadInfo  # @UnusedImport @Reimport

elif use_cython is None:
    # Regular: use fallback if not found (message is already given elsewhere).
    try:
        from _pydevd_bundle.pydevd_cython_wrapper import PyDBAdditionalThreadInfo
    except ImportError:
        from _pydevd_bundle.pydevd_additional_thread_info_regular import PyDBAdditionalThreadInfo  # @UnusedImport
else:
    raise RuntimeError('Unexpected value for PYDEVD_USE_CYTHON: %s (accepted: YES, NO)' % (use_cython,))


