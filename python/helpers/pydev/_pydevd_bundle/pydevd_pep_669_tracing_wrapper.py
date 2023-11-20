import sys

try:
    from _pydevd_bundle import pydevd_pep_669_tracing_cython as mod
except ImportError:
    from _pydevd_bundle import pydevd_pep_669_tracing as mod

sys.modules['_pydevd_bundle.pydevd_pep_669_tracing'] = mod

enable_pep699_monitoring = mod.enable_pep699_monitoring
