# -*- coding: utf-8 -*-

from __future__ import absolute_import

import asyncio
from struct import unpack

from _shaded_thriftpy.protocol.exc import TProtocolException
from _shaded_thriftpy.thrift import TException, TType
from _shaded_thriftpy.protocol.compact import (
    from_zig_zag,
    CompactType,
    TCompactProtocol,
)

from .base import TAsyncProtocolBase

BIN_TYPES = (TType.STRING, TType.BINARY)

@asyncio.coroutine
def read_varint(trans):
    result = 0
    shift = 0

    while True:
        x = yield from trans.read(1)
        byte = ord(x)
        result |= (byte & 0x7f) << shift
        if byte >> 7 == 0:
            return result
        shift += 7


class TAsyncCompactProtocol(TCompactProtocol,  # Inherit all of the writing
                            TAsyncProtocolBase):
    """Compact implementation of the Thrift protocol driver."""
    PROTOCOL_ID = 0x82
    VERSION = 1
    VERSION_MASK = 0x1f
    TYPE_MASK = 0xe0
    TYPE_BITS = 0x07
    TYPE_SHIFT_AMOUNT = 5

    @asyncio.coroutine
    def _read_size(self):
        result = yield from read_varint(self.trans)
        if result < 0:
            raise TException("Length < 0")
        return result

    @asyncio.coroutine
    def read_message_begin(self):
        proto_id = yield from self._read_ubyte()
        if proto_id != self.PROTOCOL_ID:
            raise TProtocolException(TProtocolException.BAD_VERSION,
                                     'Bad protocol id in the message: %d'
                                     % proto_id)

        ver_type = yield from self._read_ubyte()
        type = (ver_type >> self.TYPE_SHIFT_AMOUNT) & self.TYPE_BITS
        version = ver_type & self.VERSION_MASK
        if version != self.VERSION:
            raise TProtocolException(TProtocolException.BAD_VERSION,
                                     'Bad version: %d (expect %d)'
                                     % (version, self.VERSION))
        seqid = yield from read_varint(self.trans)
        name = yield from self._read_string()
        return name, type, seqid

    @asyncio.coroutine
    def read_message_end(self):  # TAsyncClient expects coroutine
        assert len(self._structs) == 0

    @asyncio.coroutine
    def _read_field_begin(self):
        type = yield from self._read_ubyte()
        if type & 0x0f == TType.STOP:
            return None, 0, 0

        delta = type >> 4
        if delta == 0:
            fid = from_zig_zag((yield from read_varint(self.trans)))
        else:
            fid = self._last_fid + delta
        self._last_fid = fid

        type = type & 0x0f
        if type == CompactType.TRUE:
            self._bool_value = True
        elif type == CompactType.FALSE:
            self._bool_value = False

        return None, self._get_ttype(type), fid

    def _read_field_end(self):
        pass

    def _read_struct_begin(self):
        self._structs.append(self._last_fid)
        self._last_fid = 0

    def _read_struct_end(self):
        self._last_fid = self._structs.pop()

    @asyncio.coroutine
    def _read_map_begin(self):
        size = yield from self._read_size()
        types = 0
        if size > 0:
            types = yield from self._read_ubyte()
        vtype = self._get_ttype(types)
        ktype = self._get_ttype(types >> 4)
        return ktype, vtype, size

    @asyncio.coroutine
    def _read_collection_begin(self):
        size_type = yield from self._read_ubyte()
        size = size_type >> 4
        type = self._get_ttype(size_type)
        if size == 15:
            size = yield from self._read_size()
        return type, size

    def _read_collection_end(self):
        pass

    @asyncio.coroutine
    def _read_byte(self):
        result, = unpack('!b', (yield from self.trans.read(1)))
        return result

    @asyncio.coroutine
    def _read_ubyte(self):
        result, = unpack('!B', (yield from self.trans.read(1)))
        return result

    @asyncio.coroutine
    def _read_int(self):
        return from_zig_zag((yield from read_varint(self.trans)))

    @asyncio.coroutine
    def _read_double(self):
        buff = yield from self.trans.read(8)
        val, = unpack('<d', buff)
        return val

    @asyncio.coroutine
    def _read_binary(self):
        length = yield from self._read_size()
        return (yield from self.trans.read(length))

    @asyncio.coroutine
    def _read_string(self):
        len = yield from self._read_size()
        byte_payload = yield from self.trans.read(len)

        if self.decode_response:
            try:
                byte_payload = byte_payload.decode('utf-8')
            except UnicodeDecodeError:
                pass
        return byte_payload

    @asyncio.coroutine
    def _read_bool(self):
        if self._bool_value is not None:
            result = self._bool_value
            self._bool_value = None
            return result
        return (yield from self._read_byte()) == CompactType.TRUE

    @asyncio.coroutine
    def read_struct(self, obj):
        self._read_struct_begin()
        while True:
            fname, ftype, fid = yield from self._read_field_begin()
            if ftype == TType.STOP:
                break

            if fid not in obj.thrift_spec:
                yield from self.skip(ftype)
                continue

            try:
                field = obj.thrift_spec[fid]
            except IndexError:
                yield from self.skip(ftype)
                raise
            else:
                if field is not None and \
                        (ftype == field[0]
                         or (ftype in BIN_TYPES
                             and field[0] in BIN_TYPES)):
                    fname = field[1]
                    fspec = field[2]
                    val = yield from self._read_val(field[0], fspec)
                    setattr(obj, fname, val)
                else:
                    yield from self.skip(ftype)
            self._read_field_end()
        self._read_struct_end()

    @asyncio.coroutine
    def _read_val(self, ttype, spec=None):
        if ttype == TType.BOOL:
            return (yield from self._read_bool())

        elif ttype == TType.BYTE:
            return (yield from self._read_byte())

        elif ttype in (TType.I16, TType.I32, TType.I64):
            return (yield from self._read_int())

        elif ttype == TType.DOUBLE:
            return (yield from self._read_double())

        elif ttype == TType.BINARY:
            return (yield from self._read_binary())

        elif ttype == TType.STRING:
            return (yield from self._read_string())

        elif ttype in (TType.LIST, TType.SET):
            if isinstance(spec, tuple):
                v_type, v_spec = spec[0], spec[1]
            else:
                v_type, v_spec = spec, None
            result = []
            r_type, sz = yield from self._read_collection_begin()

            for i in range(sz):
                result.append((yield from self._read_val(v_type, v_spec)))

            self._read_collection_end()
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
            sk_type, sv_type, sz = yield from self._read_map_begin()
            if sk_type != k_type or sv_type != v_type:
                for _ in range(sz):
                    yield from self.skip(sk_type)
                    yield from self.skip(sv_type)
                self._read_collection_end()
                return {}

            for i in range(sz):
                k_val = yield from self._read_val(k_type, k_spec)
                v_val = yield from self._read_val(v_type, v_spec)
                result[k_val] = v_val
            self._read_collection_end()
            return result

        elif ttype == TType.STRUCT:
            obj = spec()
            yield from self.read_struct(obj)
            return obj

    @asyncio.coroutine
    def skip(self, ttype):
        if ttype == TType.STOP:
            return

        elif ttype == TType.BOOL:
            yield from self._read_bool()

        elif ttype == TType.BYTE:
            yield from self._read_byte()

        elif ttype in (TType.I16, TType.I32, TType.I64):
            from_zig_zag((yield from read_varint(self.trans)))

        elif ttype == TType.DOUBLE:
            yield from self._read_double()

        elif ttype == TType.BINARY:
            yield from self._read_binary()

        elif ttype == TType.STRING:
            yield from self._read_string()

        elif ttype == TType.STRUCT:
            self._read_struct_begin()
            while True:
                name, ttype, id = yield from self._read_field_begin()
                if ttype == TType.STOP:
                    break
                yield from self.skip(ttype)
                self._read_field_end()
            self._read_struct_end()

        elif ttype == TType.MAP:
            ktype, vtype, size = yield from self._read_map_begin()
            for i in range(size):
                yield from self.skip(ktype)
                yield from self.skip(vtype)
            self._read_collection_end()

        elif ttype == TType.SET:
            etype, size = yield from self._read_collection_begin()
            for i in range(size):
                yield from self.skip(etype)
            self._read_collection_end()

        elif ttype == TType.LIST:
            etype, size = yield from self._read_collection_begin()
            for i in range(size):
                yield from self.skip(etype)
            self._read_collection_end()


class TAsyncCompactProtocolFactory(object):
    def __init__(self, decode_response=True):
        self.decode_response = decode_response

    def get_protocol(self, trans):
        return TAsyncCompactProtocol(
            trans,
            decode_response=self.decode_response,
        )
