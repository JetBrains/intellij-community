# -*- coding: utf-8 -*-

"""
    thriftpy2._compat
    ~~~~~~~~~~~~~

    py2/py3 compatibility support.
"""

from __future__ import absolute_import

import platform
import sys

PY3 = sys.version_info[0] == 3
PY35 = sys.version_info >= (3, 5)
PYPY = "__pypy__" in sys.modules

UNIX = platform.system() in ("Linux", "Darwin")
CYTHON = UNIX and not PYPY  # Cython always disabled in pypy and windows

# only Python 2.7.9 and Python 3.4 or above have true ssl context
MODERN_SSL = sys.version_info >= (2, 7, 9)

if PY3:
    text_type = str
    string_types = (str,)
    from urllib.request import urlopen
    from urllib.parse import urlparse

    def u(s):
        return s
else:
    text_type = unicode  # noqa
    string_types = (str, unicode)  # noqa
    from urllib2 import urlopen  # noqa
    from urlparse import urlparse  # noqa

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
