# -*- coding: utf-8 -*-

from __future__ import absolute_import

import struct
from io import BytesIO

from _shaded_thriftpy._compat import CYTHON
from .. import TTransportBase, readall
from ..buffered import TBufferedTransport


class TFramedTransport(TTransportBase):
    """Class that wraps another transport and frames its I/O when writing."""
    def __init__(self, trans):
        self._trans = trans
        self._rbuf = BytesIO()
        self._wbuf = BytesIO()

    def is_open(self):
        return self._trans.is_open()

    def open(self):
        return self._trans.open()

    def close(self):
        return self._trans.close()

    def read(self, sz):
        # Important: don't attempt to read the next frame if the caller
        # doesn't actually need any data.
        if sz == 0:
            return b''

        ret = self._rbuf.read(sz)
        if len(ret) != 0:
            return ret

        self.read_frame()
        return self._rbuf.read(sz)

    def read_frame(self):
        buff = readall(self._trans.read, 4)
        sz, = struct.unpack('!i', buff)
        frame = readall(self._trans.read, sz)
        self._rbuf = BytesIO(frame)

    def write(self, buf):
        self._wbuf.write(buf)

    def flush(self):
        # reset wbuf before write/flush to preserve state on underlying failure
        out = self._wbuf.getvalue()
        self._wbuf = BytesIO()

        # N.B.: Doing this string concatenation is WAY cheaper than making
        # two separate calls to the underlying socket object. Socket writes in
        # Python turn out to be REALLY expensive, but it seems to do a pretty
        # good job of managing string buffer operations without excessive
        # copies
        self._trans.write(struct.pack("!i", len(out)) + out)
        self._trans.flush()

    def getvalue(self):
        return self._trans.getvalue()


class TFramedTransportFactory(object):
    def get_transport(self, trans):
        return TBufferedTransport(TFramedTransport(trans))


if CYTHON:
    from .cyframed import TCyFramedTransport, TCyFramedTransportFactory  # noqa
