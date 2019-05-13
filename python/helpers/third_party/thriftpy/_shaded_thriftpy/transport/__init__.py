# -*- coding: utf-8 -*-

from __future__ import absolute_import

from _shaded_thriftpy._compat import CYTHON

from ..thrift import TType, TException


def readall(read_fn, sz):
    buff = b''
    have = 0
    while have < sz:
        chunk = read_fn(sz - have)
        have += len(chunk)
        buff += chunk

        if len(chunk) == 0:
            raise TTransportException(TTransportException.END_OF_FILE,
                                      "End of file reading from transport")

    return buff


class TTransportBase(object):
    """Base class for Thrift transport layer."""

    def _read(self, sz):
        raise NotImplementedError

    def read(self, sz):
        return readall(self._read, sz)


class TTransportException(TException):
    """Custom Transport Exception class"""

    thrift_spec = {
        1: (TType.STRING, 'message'),
        2: (TType.I32, 'type'),
    }

    UNKNOWN = 0
    NOT_OPEN = 1
    ALREADY_OPEN = 2
    TIMED_OUT = 3
    END_OF_FILE = 4

    def __init__(self, type=UNKNOWN, message=None):
        super(TTransportException, self).__init__()
        self.type = type
        self.message = message


# Avoid recursive import
from .socket import TSocket, TServerSocket  # noqa
from .sslsocket import TSSLSocket, TSSLServerSocket  # noqa
from ._ssl import create_thriftpy_context  # noqa
from .buffered import TBufferedTransport, TBufferedTransportFactory  # noqa
from .framed import TFramedTransport, TFramedTransportFactory  # noqa
from .memory import TMemoryBuffer  # noqa

if CYTHON:
    from .buffered import TCyBufferedTransport, TCyBufferedTransportFactory
    from .framed import TCyFramedTransport, TCyFramedTransportFactory
    from .memory import TCyMemoryBuffer

    # enable cython binary by default for CPython.
    TMemoryBuffer = TCyMemoryBuffer  # noqa
    TBufferedTransport = TCyBufferedTransport  # noqa
    TBufferedTransportFactory = TCyBufferedTransportFactory  # noqa
    TFramedTransport = TCyFramedTransport  # noqa
    TFramedTransportFactory = TCyFramedTransportFactory  # noqa
else:
    # disable cython binary protocol for PYPY since it's slower.
    TCyMemoryBuffer = TMemoryBuffer
    TCyBufferedTransport = TBufferedTransport
    TCyBufferedTransportFactory = TBufferedTransportFactory
    TCyFramedTransport = TFramedTransport
    TCyFramedTransportFactory = TFramedTransportFactory

__all__ = [
    "TSocket", "TServerSocket",
    "TSSLSocket", "TSSLServerSocket", "create_thriftpy_context",
    "TTransportBase", "TTransportException",
    "TMemoryBuffer", "TFramedTransport", "TFramedTransportFactory",
    "TBufferedTransport", "TBufferedTransportFactory", "TCyMemoryBuffer",
    "TCyBufferedTransport", "TCyBufferedTransportFactory",
    "TCyFramedTransport", "TCyFramedTransportFactory"
]
