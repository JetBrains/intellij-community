from setuptools import setup
from Cython.Build import cythonize

setup(
    name='Cythonize',
    ext_modules=cythonize([
        "_pydevd_bundle/pydevd_trace_dispatch_cython.pyx",
        "_pydevd_bundle/pydevd_additional_thread_info_cython.pyx",
    ]),
)
