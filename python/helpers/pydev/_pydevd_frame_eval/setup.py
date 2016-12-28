import os
import sys
from distutils.core import setup
from setuptools import Extension
from Cython.Build import cythonize

if sys.version_info < (3, 6):
    raise Exception('PEP 523 API is available on Python 3.6 and higher')

os.chdir(os.path.dirname(os.path.abspath(__file__)))
target_name = 'pydevd_frame_evaluator'

ext_modules = [
    Extension(target_name,  # location of the resulting .so
              ["{}.pyx".format(target_name)], )]

setup(name=target_name,
      ext_modules=cythonize(ext_modules),
      requires=['Cython'])
