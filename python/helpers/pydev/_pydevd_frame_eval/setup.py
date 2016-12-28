from distutils.core import setup
from Cython.Build import cythonize
import sys

if sys.version_info < (3, 6):
    raise Exception('PEP 523 API is available on Python 3.6 and higher')

setup(name='pydevd_frame_evaluator',
      ext_modules=cythonize("pydevd_frame_evaluator.pyx"),
      requires=['Cython'])
