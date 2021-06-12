# -*- coding: utf-8 -*-

from __future__ import absolute_import

import struct
import asyncio
from io import BytesIO

from .base import TAsyncTransportBase, readall
from .buffered import TAsyncBufferedTransport


class TAsyncFramedTransport(TAsyncTransportBase):
    """Class that wraps another transport and frames its I/O when writing."""
    def __init__(self, trans):
        self._trans = trans
        self._rbuf = BytesIO()
        self._wbuf = BytesIO()

    def is_open(self):
        return self._trans.is_open()

    @asyncio.coroutine
    def open(self):
        return (yield from self._trans.open())

    def close(self):
        return self._trans.close()

    @asyncio.coroutine
    def read(self, sz):
        # Important: don't attempt to read the next frame if the caller
        # doesn't actually need any data.
        if sz == 0:
            return b''

        ret = self._rbuf.read(sz)
        if len(ret) != 0:
            return ret

        yield from self.read_frame()
        return self._rbuf.read(sz)

    @asyncio.coroutine
    def read_frame(self):
        buff = yield from readall(self._trans.read, 4)
        sz, = struct.unpack('!i', buff)
        frame = yield from readall(self._trans.read, sz)
        self._rbuf = BytesIO(frame)

    def write(self, buf):
        self._wbuf.write(buf)

    @asyncio.coroutine
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
        yield from self._trans.flush()

    def getvalue(self):
        return self._trans.getvalue()


class TAsyncFramedTransportFactory(object):
    def get_transport(self, trans):
        return TAsyncBufferedTransport(TAsyncFramedTransport(trans))
