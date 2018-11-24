# -*- coding: utf-8 -*-

from __future__ import absolute_import

from io import BytesIO

from thriftpy._compat import CYTHON
from .. import TTransportBase


class TBufferedTransport(TTransportBase):
    """Class that wraps another transport and buffers its I/O.

    The implementation uses a (configurable) fixed-size read buffer
    but buffers all writes until a flush is performed.
    """
    DEFAULT_BUFFER = 4096

    def __init__(self, trans, buf_size=DEFAULT_BUFFER):
        self._trans = trans
        self._wbuf = BytesIO()
        self._rbuf = BytesIO(b"")
        self._buf_size = buf_size

    def is_open(self):
        return self._trans.is_open()

    def open(self):
        return self._trans.open()

    def close(self):
        return self._trans.close()

    def _read(self, sz):
        ret = self._rbuf.read(sz)
        if len(ret) != 0:
            return ret

        self._rbuf = BytesIO(self._trans.read(max(sz, self._buf_size)))
        return self._rbuf.read(sz)

    def write(self, buf):
        self._wbuf.write(buf)

    def flush(self):
        out = self._wbuf.getvalue()
        # reset wbuf before write/flush to preserve state on underlying failure
        self._wbuf = BytesIO()
        self._trans.write(out)
        self._trans.flush()

    def getvalue(self):
        return self._trans.getvalue()


class TBufferedTransportFactory(object):
    def get_transport(self, trans):
        return TBufferedTransport(trans)


if CYTHON:
    from .cybuffered import TCyBufferedTransport, TCyBufferedTransportFactory  # noqa
