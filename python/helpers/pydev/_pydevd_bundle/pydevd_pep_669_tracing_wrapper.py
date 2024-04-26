import os
import sys

use_cython = os.getenv('PYDEVD_USE_CYTHON', None)

if use_cython == 'NO':
    from _pydevd_bundle import pydevd_pep_669_tracing as mod
elif use_cython == 'YES':
    from _pydevd_bundle import pydevd_pep_669_tracing_cython as mod
else:
    try:
        from _pydevd_bundle import pydevd_pep_669_tracing_cython as mod
    except ImportError:
        from _pydevd_bundle import pydevd_pep_669_tracing as mod

sys.modules['_pydevd_bundle.pydevd_pep_669_tracing'] = mod

enable_pep699_monitoring = mod.enable_pep699_monitoring
