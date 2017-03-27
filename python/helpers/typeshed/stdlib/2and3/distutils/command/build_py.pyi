# Stubs for distutils.command.bdist_msi

from distutils.cmd import Command
import sys

if sys.version_info >= (3,):
    class build_py(Command): ...
    class build_py_2to3(Command): ...
