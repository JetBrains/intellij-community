# $Id: _compat.py 5908 2009-04-21 13:43:23Z goodger $
# Author: Georg Brandl <georg@python.org>
# Copyright: This module has been placed in the public domain.

"""
Python 2/3 compatibility definitions.

This module currently provides the following helper symbols:

* bytes (name of byte string type; str in 2.x, bytes in 3.x)
* b (function converting a string literal to an ASCII byte string;
  can be also used to convert a Unicode string into a byte string)
* u_prefix (unicode repr prefix, 'u' in 2.x, nothing in 3.x)
* BytesIO (a StringIO class that works with bytestrings)
"""

import sys

if sys.version_info < (3,0):
    b = bytes = str
    u_prefix = 'u'
    from StringIO import StringIO as BytesIO
else:
    import builtins
    bytes = builtins.bytes
    u_prefix = ''
    def b(s):
        if isinstance(s, str):
            return s.encode('latin1')
        elif isinstance(s, bytes):
            return s
        else:
            raise TypeError("Invalid argument %r for b()" % (s,))
    # using this hack since 2to3 "fixes" the relative import
    # when using ``from io import BytesIO``
    BytesIO = __import__('io').BytesIO
