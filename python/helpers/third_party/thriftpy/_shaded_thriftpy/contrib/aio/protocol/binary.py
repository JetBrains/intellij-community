# -*- coding: utf-8 -*-

from __future__ import absolute_import

import asyncio

from _shaded_thriftpy.thrift import TType

from _shaded_thriftpy.protocol.exc import TProtocolException
from _shaded_thriftpy.protocol.binary import (
    VERSION_MASK,
    VERSION_1,
    TYPE_MASK,
    unpack_i8,
    unpack_i16,
    unpack_i32,
    unpack_i64,
    unpack_double,
    write_message_begin,
    write_val
)

from .base import TAsyncProtocolBase

BIN_TYPES = (TType.STRING, TType.BINARY)

@asyncio.coroutine
def read_message_begin(inbuf, strict=True):
    sz = unpack_i32((yield from inbuf.read(4)))
    if sz < 0:
        version = sz & VERSION_MASK
        if version != VERSION_1:
            raise TProtocolException(
                type=TProtocolException.BAD_VERSION,
                message='Bad version in read_message_begin: %d' % (sz))
        name_sz = unpack_i32((yield from inbuf.read(4)))
        name = yield from inbuf.read(name_sz)
        name = name.decode('utf-8')

        type_ = sz & TYPE_MASK
    else:
        if strict:
            raise TProtocolException(type=TProtocolException.BAD_VERSION,
                                     message='No protocol version header')

        name = yield from inbuf.read(sz)
        type_ = unpack_i8((yield from inbuf.read(1)))

    seqid = unpack_i32((yield from inbuf.read(4)))

    return name, type_, seqid


@asyncio.coroutine
def read_field_begin(inbuf):
    f_type = unpack_i8((yield from inbuf.read(1)))
    if f_type == TType.STOP:
        return f_type, 0

    return f_type, unpack_i16((yield from inbuf.read(2)))


@asyncio.coroutine
def read_list_begin(inbuf):
    e_type = unpack_i8((yield from inbuf.read(1)))
    sz = unpack_i32((yield from inbuf.read(4)))
    return e_type, sz


@asyncio.coroutine
def read_map_begin(inbuf):
    k_type = unpack_i8((yield from inbuf.read(1)))
    v_type = unpack_i8((yield from inbuf.read(1)))
    sz = unpack_i32((yield from inbuf.read(4)))
    return k_type, v_type, sz


@asyncio.coroutine
def read_val(inbuf, ttype, spec=None, decode_response=True):
    if ttype == TType.BOOL:
        return bool(unpack_i8((yield from inbuf.read(1))))

    elif ttype == TType.BYTE:
        return unpack_i8((yield from inbuf.read(1)))

    elif ttype == TType.I16:
        return unpack_i16((yield from inbuf.read(2)))

    elif ttype == TType.I32:
        return unpack_i32((yield from inbuf.read(4)))

    elif ttype == TType.I64:
        return unpack_i64((yield from inbuf.read(8)))

    elif ttype == TType.DOUBLE:
        return unpack_double((yield from inbuf.read(8)))

    elif ttype == TType.BINARY:
        sz = unpack_i32((yield from inbuf.read(4)))
        return inbuf.read(sz)

    elif ttype == TType.STRING:
        sz = unpack_i32((yield from inbuf.read(4)))
        byte_payload = yield from inbuf.read(sz)

        # Since we cannot tell if we're getting STRING or BINARY
        # if not asked not to decode, try both
        if decode_response:
            try:
                return byte_payload.decode('utf-8')
            except UnicodeDecodeError:
                pass
        return byte_payload

    elif ttype == TType.SET or ttype == TType.LIST:
        if isinstance(spec, tuple):
            v_type, v_spec = spec[0], spec[1]
        else:
            v_type, v_spec = spec, None

        result = []
        r_type, sz = yield from read_list_begin(inbuf)
        # the v_type is useless here since we already get it from spec
        if r_type != v_type and not (r_type in BIN_TYPES and v_type in BIN_TYPES):
            for _ in range(sz):
                yield from skip(inbuf, r_type)
            return []

        for i in range(sz):
            result.append(
                (yield from read_val(
                    inbuf, v_type, v_spec, decode_response
                ))
            )
        return result

    elif ttype == TType.MAP:
        if isinstance(spec[0], int):
            k_type = spec[0]
            k_spec = None
        else:
            k_type, k_spec = spec[0]

        if isinstance(spec[1], int):
            v_type = spec[1]
            v_spec = None
        else:
            v_type, v_spec = spec[1]

        result = {}
        sk_type, sv_type, sz = yield from read_map_begin(inbuf)
        if sk_type in BIN_TYPES:
            sk_type = k_type
        if sv_type in BIN_TYPES:
            sv_type = v_type
        if sk_type != k_type or sv_type != v_type:
            for _ in range(sz):
                yield from skip(inbuf, sk_type)
                yield from skip(inbuf, sv_type)
            return {}

        for i in range(sz):
            k_val = yield from read_val(inbuf, k_type, k_spec, decode_response)
            v_val = yield from read_val(inbuf, v_type, v_spec, decode_response)
            result[k_val] = v_val

        return result

    elif ttype == TType.STRUCT:
        obj = spec()
        yield from read_struct(inbuf, obj, decode_response)
        return obj


@asyncio.coroutine
def read_struct(inbuf, obj, decode_response=True):
    while True:
        f_type, fid = yield from read_field_begin(inbuf)
        if f_type == TType.STOP:
            break

        if fid not in obj.thrift_spec:
            yield from skip(inbuf, f_type)
            continue

        if len(obj.thrift_spec[fid]) == 3:
            sf_type, f_name, f_req = obj.thrift_spec[fid]
            f_container_spec = None
        else:
            sf_type, f_name, f_container_spec, f_req = obj.thrift_spec[fid]

        # it really should equal here. but since we already wasted
        # space storing the duplicate info, let's check it.
        if f_type != sf_type:
            if f_type in BIN_TYPES:
                f_type = sf_type
            else:
                yield from skip(inbuf, f_type)
                continue

        _buf = yield from read_val(
            inbuf, f_type, f_container_spec, decode_response)
        setattr(obj, f_name, _buf)


@asyncio.coroutine
def skip(inbuf, ftype):
    if ftype == TType.BOOL or ftype == TType.BYTE:
        yield from inbuf.read(1)

    elif ftype == TType.I16:
        yield from inbuf.read(2)

    elif ftype == TType.I32:
        yield from inbuf.read(4)

    elif ftype == TType.I64:
        yield from inbuf.read(8)

    elif ftype == TType.DOUBLE:
        yield from inbuf.read(8)

    elif ftype in BIN_TYPES:
        _size = yield from inbuf.read(4)
        yield from inbuf.read(unpack_i32(_size))

    elif ftype == TType.SET or ftype == TType.LIST:
        v_type, sz = yield from read_list_begin(inbuf)
        for i in range(sz):
            yield from skip(inbuf, v_type)

    elif ftype == TType.MAP:
        k_type, v_type, sz = yield from read_map_begin(inbuf)
        for i in range(sz):
            yield from skip(inbuf, k_type)
            yield from skip(inbuf, v_type)

    elif ftype == TType.STRUCT:
        while True:
            f_type, fid = yield from read_field_begin(inbuf)
            if f_type == TType.STOP:
                break
            yield from skip(inbuf, f_type)


class TAsyncBinaryProtocol(TAsyncProtocolBase):
    """Binary implementation of the Thrift protocol driver."""

    def __init__(self, trans,
                 strict_read=True, strict_write=True,
                 decode_response=True):
        TAsyncProtocolBase.__init__(self, trans)
        self.strict_read = strict_read
        self.strict_write = strict_write
        self.decode_response = decode_response

    @asyncio.coroutine
    def skip(self, ttype):
        yield from skip(self.trans, ttype)

    @asyncio.coroutine
    def read_message_begin(self):
        api, ttype, seqid = yield from read_message_begin(
            self.trans, strict=self.strict_read)
        return api, ttype, seqid

    @asyncio.coroutine
    def read_message_end(self):
        pass

    def write_message_begin(self, name, ttype, seqid):
        write_message_begin(
            self.trans, name, ttype,
            seqid, strict=self.strict_write
        )

    def write_message_end(self):
        pass

    @asyncio.coroutine
    def read_struct(self, obj):
        return (yield from read_struct(self.trans, obj, self.decode_response))

    def write_struct(self, obj):
        write_val(self.trans, TType.STRUCT, obj)


class TAsyncBinaryProtocolFactory(object):
    def __init__(self, strict_read=True, strict_write=True,
                 decode_response=True):
        self.strict_read = strict_read
        self.strict_write = strict_write
        self.decode_response = decode_response

    def get_protocol(self, trans):
        return TAsyncBinaryProtocol(
            trans,
            self.strict_read,
            self.strict_write,
            self.decode_response
        )
