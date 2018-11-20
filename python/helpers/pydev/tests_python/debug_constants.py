import os

TEST_CYTHON = os.getenv('PYDEVD_USE_CYTHON', None) == 'YES'
TEST_JYTHON = os.getenv('PYDEVD_TEST_JYTHON', None) == 'YES'