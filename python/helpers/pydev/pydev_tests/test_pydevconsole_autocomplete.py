#  Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import inspect
import sys
import unittest

from _pydev_bundle._pydev_calltip_util import getargspec_py2, is_bound_method
from _pydevd_bundle import pydevd_io
from _pydevd_bundle.pydevd_constants import IS_PY2


def get_fob(obj):
    """A helper function that simulates the transformation of an object
     as a function 'get_description(obj)' from '_pydev_calltip_util.py'"""
    try:
        ob_call = obj.__call__
    except:
        ob_call = None

    if isinstance(obj, type) or type(obj).__name__ == 'classobj':
        fob = getattr(obj, '__init__', lambda: None)
        if not callable(fob):
            fob = obj
    elif is_bound_method(ob_call):
        fob = ob_call
    else:
        fob = obj

    return fob


class Test(unittest.TestCase):
    """Test checks the correctness of 'getargspec_py2(obj)' from '_pydev_calltip_util.py'.
     The function is needed because in Python 2 we don't have 'inspect.getfullargspec(obj)'.
     And 'inspect.getargspec(obj)' doesn't work with build-in functions.
     Therefore, we are trying to get args from the object.__doc__"""

    def test_getargspec_py2(self):
        if IS_PY2:
            self.original_stdout = sys.stdout
            sys.stdout = pydevd_io.IOBuf()

            try:
                # dict -> dict()
                actual = getargspec_py2(dict)
                expected = inspect.ArgSpec(args=[], varargs=None, keywords=None,
                                           defaults=())
                self.assertEqual(expected, actual)

                # vars -> vars([object])
                fob = get_fob(vars)
                actual = getargspec_py2(fob)
                expected = inspect.ArgSpec(args=['[object]'], varargs=None,
                                           keywords=None,
                                           defaults=())
                self.assertEqual(expected, actual)

                # str.join -> str.join(iterable)
                val = ""
                fob = get_fob(val.join)
                actual = getargspec_py2(fob)
                expected = inspect.ArgSpec(args=['iterable'], varargs=None,
                                           keywords=None,
                                           defaults=())
                self.assertEqual(expected, actual)

                # list -> list()
                actual = getargspec_py2(list)
                expected = inspect.ArgSpec(args=[], varargs=None, keywords=None,
                                           defaults=())
                self.assertEqual(expected, actual)

                # compile -> compile(source, filename, mode[, flags[, dont_inherit]])
                fob = get_fob(compile)
                actual = getargspec_py2(fob)
                expected = inspect.ArgSpec(
                    args=['source', 'filename', 'mode[', 'flags[', 'dont_inherit]]'],
                    varargs=None, keywords=None, defaults=())
                self.assertEqual(expected, actual)
            finally:
                sys.stdout = self.original_stdout
