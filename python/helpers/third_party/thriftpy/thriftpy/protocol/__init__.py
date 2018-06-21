# -*- coding: utf-8 -*-

from __future__ import absolute_import

from .binary import TBinaryProtocol, TBinaryProtocolFactory
from .json import TJSONProtocol, TJSONProtocolFactory
from .compact import TCompactProtocol, TCompactProtocolFactory
from .multiplex import TMultiplexedProtocol, TMultiplexedProtocolFactory

from thriftpy._compat import PYPY, CYTHON
if not PYPY:
    # enable cython binary by default for CPython.
    if CYTHON:
        from .cybin import TCyBinaryProtocol, TCyBinaryProtocolFactory
        TBinaryProtocol = TCyBinaryProtocol  # noqa
        TBinaryProtocolFactory = TCyBinaryProtocolFactory  # noqa
else:
    # disable cython binary protocol for PYPY since it's slower.
    TCyBinaryProtocol = TBinaryProtocol
    TCyBinaryProtocolFactory = TBinaryProtocolFactory

__all__ = ['TBinaryProtocol', 'TBinaryProtocolFactory',
           'TCyBinaryProtocol', 'TCyBinaryProtocolFactory',
           'TJSONProtocol', 'TJSONProtocolFactory',
           'TMultiplexedProtocol', 'TMultiplexedProtocolFactory',
           'TCompactProtocol', 'TCompactProtocolFactory']
