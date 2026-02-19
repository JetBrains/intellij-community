# -*- coding: utf-8 -*-

import asyncio

from _shaded_thriftpy.protocol import TProtocolBase


class TAsyncProtocolBase(TProtocolBase):
    """Base class for Thrift async protocol layer."""

    @asyncio.coroutine
    def skip(self, ttype):
        raise NotImplementedError

    @asyncio.coroutine
    def read_message_begin(self):
        raise NotImplementedError

    @asyncio.coroutine
    def read_message_end(self):
        raise NotImplementedError

    def write_message_begin(self, name, ttype, seqid):
        raise NotImplementedError

    def write_message_end(self):
        raise NotImplementedError

    @asyncio.coroutine
    def read_struct(self, obj):
        raise NotImplementedError

    def write_struct(self, obj):
        raise NotImplementedError
