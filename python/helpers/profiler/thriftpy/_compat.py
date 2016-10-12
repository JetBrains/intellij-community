# -*- coding: utf-8 -*-

"""
    thriftpy._compat
    ~~~~~~~~~~~~~

    py2/py3 compatibility support.
"""

from __future__ import absolute_import

import platform
import sys
import types

PY3 = sys.version_info[0] == 3
PYPY = "__pypy__" in sys.modules

UNIX = platform.system() in ("Linux", "Darwin")
CYTHON = False  # Cython always disabled in pypy and windows

# only python2.7.9 and python 3.4 or above have true ssl context
MODERN_SSL = (2, 7, 9) <= sys.version_info < (3, 0, 0) or \
    sys.version_info >= (3, 4)

if PY3:
    text_type = str
    string_types = (str,)

    def u(s):
        return s
else:
    text_type = unicode  # noqa
    string_types = (str, unicode)  # noqa

    def u(s):
        if not isinstance(s, text_type):
            s = s.decode("utf-8")
        return s


def with_metaclass(meta, *bases):
    """Create a base class with a metaclass for py2 & py3

    This code snippet is copied from six."""
    # This requires a bit of explanation: the basic idea is to make a
    # dummy metaclass for one level of class instantiation that replaces
    # itself with the actual metaclass.  Because of internal type checks
    # we also need to make sure that we downgrade the custom metaclass
    # for one level to something closer to type (that's why __call__ and
    # __init__ comes back from type etc.).
    class metaclass(meta):
        __call__ = type.__call__
        __init__ = type.__init__

        def __new__(cls, name, this_bases, d):
            if this_bases is None:
                return type.__new__(cls, name, (), d)
            return meta(name, bases, d)
    return metaclass('temporary_class', None, {})


def init_func_generator(spec):
    """Generate `__init__` function based on TPayload.default_spec

    For example::

        spec = [('name', 'Alice'), ('number', None)]

    will generate::

        def __init__(self, name='Alice', number=None):
            kwargs = locals()
            kwargs.pop('self')
            self.__dict__.update(kwargs)

    TODO: The `locals()` part may need refine.
    """
    if not spec:
        def __init__(self):
            pass
        return __init__

    varnames, defaults = zip(*spec)
    varnames = ('self', ) + varnames

    def init(self):
        self.__dict__ = locals().copy()
        del self.__dict__['self']

    code = init.__code__
    if PY3:
        new_code = types.CodeType(len(varnames),
                                  0,
                                  len(varnames),
                                  code.co_stacksize,
                                  code.co_flags,
                                  code.co_code,
                                  code.co_consts,
                                  code.co_names,
                                  varnames,
                                  code.co_filename,
                                  "__init__",
                                  code.co_firstlineno,
                                  code.co_lnotab,
                                  code.co_freevars,
                                  code.co_cellvars)
    else:
        new_code = types.CodeType(len(varnames),
                                  len(varnames),
                                  code.co_stacksize,
                                  code.co_flags,
                                  code.co_code,
                                  code.co_consts,
                                  code.co_names,
                                  varnames,
                                  code.co_filename,
                                  "__init__",
                                  code.co_firstlineno,
                                  code.co_lnotab,
                                  code.co_freevars,
                                  code.co_cellvars)

    return types.FunctionType(new_code,
                              {"__builtins__": __builtins__},
                              argdefs=defaults)
