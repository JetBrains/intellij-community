# -*- coding: utf-8 -*-

from __future__ import absolute_import

from thriftpy._compat import PYPY, CYTHON
from .binary import TBinaryProtocol, TBinaryProtocolFactory
from .compact import TCompactProtocol, TCompactProtocolFactory
from .json import TJSONProtocol, TJSONProtocolFactory
from .multiplex import TMultiplexedProtocol, TMultiplexedProtocolFactory

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
