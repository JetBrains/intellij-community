# Licensed under the Apache License: http://www.apache.org/licenses/LICENSE-2.0
# For details: https://github.com/nedbat/coveragepy/blob/master/NOTICE.txt

"""Add things to old Pythons so I can pretend they are newer."""

# This file's purpose is to provide modules to be imported from here.
# pylint: disable=unused-import

import os
import sys

from datetime import datetime

from coverage import env


# Pythons 2 and 3 differ on where to get StringIO.
try:
    from cStringIO import StringIO
except ImportError:
    from io import StringIO

# In py3, ConfigParser was renamed to the more-standard configparser.
# But there's a py3 backport that installs "configparser" in py2, and I don't
# want it because it has annoying deprecation warnings. So try the real py2
# import first.
try:
    import ConfigParser as configparser
except ImportError:
    import configparser

# What's a string called?
try:
    string_class = basestring
except NameError:
    string_class = str

# What's a Unicode string called?
try:
    unicode_class = unicode
except NameError:
    unicode_class = str

# range or xrange?
try:
    range = xrange      # pylint: disable=redefined-builtin
except NameError:
    range = range

try:
    from itertools import zip_longest
except ImportError:
    from itertools import izip_longest as zip_longest

# Where do we get the thread id from?
try:
    from thread import get_ident as get_thread_id
except ImportError:
    from threading import get_ident as get_thread_id

try:
    os.PathLike
except AttributeError:
    # This is Python 2 and 3
    path_types = (bytes, string_class, unicode_class)
else:
    # 3.6+
    path_types = (bytes, str, os.PathLike)

# shlex.quote is new, but there's an undocumented implementation in "pipes",
# who knew!?
try:
    from shlex import quote as shlex_quote
except ImportError:
    # Useful function, available under a different (undocumented) name
    # in Python versions earlier than 3.3.
    from pipes import quote as shlex_quote

try:
    import reprlib
except ImportError:             # pragma: not covered
    # We need this on Python 2, but in testing environments, a backport is
    # installed, so this import isn't used.
    import repr as reprlib

# A function to iterate listlessly over a dict's items, and one to get the
# items as a list.
try:
    {}.iteritems
except AttributeError:
    # Python 3
    def iitems(d):
        """Produce the items from dict `d`."""
        return d.items()

    def litems(d):
        """Return a list of items from dict `d`."""
        return list(d.items())
else:
    # Python 2
    def iitems(d):
        """Produce the items from dict `d`."""
        return d.iteritems()

    def litems(d):
        """Return a list of items from dict `d`."""
        return d.items()

# Getting the `next` function from an iterator is different in 2 and 3.
try:
    iter([]).next
except AttributeError:
    def iternext(seq):
        """Get the `next` function for iterating over `seq`."""
        return iter(seq).__next__
else:
    def iternext(seq):
        """Get the `next` function for iterating over `seq`."""
        return iter(seq).next

# Python 3.x is picky about bytes and strings, so provide methods to
# get them right, and make them no-ops in 2.x
if env.PY3:
    def to_bytes(s):
        """Convert string `s` to bytes."""
        return s.encode('utf8')

    def to_string(b):
        """Convert bytes `b` to string."""
        return b.decode('utf8')

    def binary_bytes(byte_values):
        """Produce a byte string with the ints from `byte_values`."""
        return bytes(byte_values)

    def byte_to_int(byte):
        """Turn a byte indexed from a bytes object into an int."""
        return byte

    def bytes_to_ints(bytes_value):
        """Turn a bytes object into a sequence of ints."""
        # In Python 3, iterating bytes gives ints.
        return bytes_value

else:
    def to_bytes(s):
        """Convert string `s` to bytes (no-op in 2.x)."""
        return s

    def to_string(b):
        """Convert bytes `b` to string."""
        return b

    def binary_bytes(byte_values):
        """Produce a byte string with the ints from `byte_values`."""
        return "".join(chr(b) for b in byte_values)

    def byte_to_int(byte):
        """Turn a byte indexed from a bytes object into an int."""
        return ord(byte)

    def bytes_to_ints(bytes_value):
        """Turn a bytes object into a sequence of ints."""
        for byte in bytes_value:
            yield ord(byte)


try:
    # In Python 2.x, the builtins were in __builtin__
    BUILTINS = sys.modules['__builtin__']
except KeyError:
    # In Python 3.x, they're in builtins
    BUILTINS = sys.modules['builtins']


# imp was deprecated in Python 3.3
try:
    import importlib
    import importlib.util
    imp = None
except ImportError:
    importlib = None

# We only want to use importlib if it has everything we need.
try:
    importlib_util_find_spec = importlib.util.find_spec
except Exception:
    import imp
    importlib_util_find_spec = None

# What is the .pyc magic number for this version of Python?
try:
    PYC_MAGIC_NUMBER = importlib.util.MAGIC_NUMBER
except AttributeError:
    PYC_MAGIC_NUMBER = imp.get_magic()


def code_object(fn):
    """Get the code object from a function."""
    try:
        return fn.func_code
    except AttributeError:
        return fn.__code__


try:
    from types import SimpleNamespace
except ImportError:
    # The code from https://docs.python.org/3/library/types.html#types.SimpleNamespace
    class SimpleNamespace:
        """Python implementation of SimpleNamespace, for Python 2."""
        def __init__(self, **kwargs):
            self.__dict__.update(kwargs)

        def __repr__(self):
            keys = sorted(self.__dict__)
            items = ("{}={!r}".format(k, self.__dict__[k]) for k in keys)
            return "{}({})".format(type(self).__name__, ", ".join(items))


def format_local_datetime(dt):
    """Return a string with local timezone representing the date.
    If python version is lower than 3.6, the time zone is not included.
    """
    try:
        return dt.astimezone().strftime('%Y-%m-%d %H:%M %z')
    except (TypeError, ValueError):
        # Datetime.astimezone in Python 3.5 can not handle naive datetime
        return dt.strftime('%Y-%m-%d %H:%M')


def invalidate_import_caches():
    """Invalidate any import caches that may or may not exist."""
    if importlib and hasattr(importlib, "invalidate_caches"):
        importlib.invalidate_caches()


def import_local_file(modname, modfile=None):
    """Import a local file as a module.

    Opens a file in the current directory named `modname`.py, imports it
    as `modname`, and returns the module object.  `modfile` is the file to
    import if it isn't in the current directory.

    """
    try:
        import importlib.util as importlib_util
    except ImportError:
        importlib_util = None

    if modfile is None:
        modfile = modname + '.py'
    if importlib_util:
        spec = importlib_util.spec_from_file_location(modname, modfile)
        mod = importlib_util.module_from_spec(spec)
        sys.modules[modname] = mod
        spec.loader.exec_module(mod)
    else:
        for suff in imp.get_suffixes():                 # pragma: part covered
            if suff[0] == '.py':
                break

        with open(modfile, 'r') as f:
            # pylint: disable=undefined-loop-variable
            mod = imp.load_module(modname, f, modfile, suff)

    return mod
