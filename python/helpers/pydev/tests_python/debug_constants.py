import os
import sys
import platform

TEST_CYTHON = os.getenv('PYDEVD_USE_CYTHON', None) == 'YES'
TEST_JYTHON = os.getenv('PYDEVD_TEST_JYTHON', None) == 'YES'

IS_PY3K = sys.version_info[0] >= 3
IS_PY36_OR_GREATER = sys.version_info[0:2] >= (3, 6)
IS_CPYTHON = platform.python_implementation() == 'CPython'

IS_PY2 = False
if sys.version_info[0] == 2:
    IS_PY2 = True

IS_PY26 = sys.version_info[:2] == (2, 6)
IS_PY34 = sys.version_info[:2] == (3, 4)
IS_PY36 = False
if sys.version_info[0] == 3 and sys.version_info[1] == 6:
    IS_PY36 = True
