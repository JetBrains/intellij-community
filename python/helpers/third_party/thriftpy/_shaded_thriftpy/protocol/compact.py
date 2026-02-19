# -*- coding: utf-8 -*-

from __future__ import absolute_import

import array
from struct import pack, unpack

from .exc import TProtocolException
from .base import TProtocolBase
from ..thrift import TException
from ..thrift import TType

from _shaded_thriftpy._compat import PY3

CLEAR = 0
FIELD_WRITE = 1
VALUE_WRITE = 2
CONTAINER_WRITE = 3
BOOL_WRITE = 4
FIELD_READ = 5
CONTAINER_READ = 6
VALUE_READ = 7
BOOL_READ = 8

BIN_TYPES = (TType.STRING, TType.BINARY)


def check_integer_limits(i, bits):
    if bits == 8 and (i < -128 or i > 127):
        raise TProtocolException(TProtocolException.INVALID_DATA,
                                 "i8 requires -128 <= number <= 127")
    elif bits == 16 and (i < -32768 or i > 32767):
        raise TProtocolException(TProtocolException.INVALID_DATA,
                                 "i16 requires -32768 <= number <= 32767")
    elif bits == 32 and (i < -2147483648 or i > 2147483647):
        raise TProtocolException(
            TProtocolException.INVALID_DATA,
            "i32 requires -2147483648 <= number <= 2147483647")
    elif bits == 64 and (i < -9223372036854775808 or i > 9223372036854775807):
        raise TProtocolException(
            TProtocolException.INVALID_DATA,
            "i64 requires -9223372036854775808 <= number <= \
                    9223372036854775807")


def make_zig_zag(n, bits):
    check_integer_limits(n, bits)
    return (n << 1) ^ (n >> (bits - 1))


def from_zig_zag(n):
    return (n >> 1) ^ -(n & 1)


def write_varint(trans, n):
    out = []
    while True:
        if n & ~0x7f == 0:
            out.append(n)
            break
        else:
            out.append((n & 0xff) | 0x80)
            n = n >> 7
    data = array.array('B', out)

    if PY3:
        trans.write(data.tobytes())
    else:
        trans.write(data.tostring())


def read_varint(trans):
    result = 0
    shift = 0

    while True:
        x = trans.read(1)
        byte = ord(x)
        result |= (byte & 0x7f) << shift
        if byte >> 7 == 0:
            return result
        shift += 7


class CompactType(object):
    STOP = 0x00
    TRUE = 0x01
    FALSE = 0x02
    BYTE = 0x03
    I16 = 0x04
    I32 = 0x05
    I64 = 0x06
    DOUBLE = 0x07
    BINARY = 0x08
    LIST = 0x09
    SET = 0x0A
    MAP = 0x0B
    STRUCT = 0x0C


CTYPES = {
    TType.STOP: CompactType.STOP,
    TType.BOOL: CompactType.TRUE,
    TType.BYTE: CompactType.BYTE,
    TType.I16: CompactType.I16,
    TType.I32: CompactType.I32,
    TType.I64: CompactType.I64,
    TType.DOUBLE: CompactType.DOUBLE,
    TType.STRING: CompactType.BINARY,
    TType.STRUCT: CompactType.STRUCT,
    TType.LIST: CompactType.LIST,
    TType.SET: CompactType.SET,
    TType.MAP: CompactType.MAP,
    TType.BINARY: CompactType.BINARY,
}
TTYPES = dict((v, k) for k, v in CTYPES.items())
TTYPES[CompactType.FALSE] = TType.BOOL


class TCompactProtocol(TProtocolBase):
    """Compact implementation of the Thrift protocol driver."""
    PROTOCOL_ID = 0x82
    VERSION = 1
    VERSION_MASK = 0x1f
    TYPE_MASK = 0xe0
    TYPE_BITS = 0x07
    TYPE_SHIFT_AMOUNT = 5

    def __init__(self, trans, decode_response=True):
        TProtocolBase.__init__(self, trans)
        self._last_fid = 0
        self._bool_fid = None
        self._bool_value = None
        self._structs = []
        self.decode_response = decode_response

    def _get_ttype(self, byte):
        return TTYPES[byte & 0x0f]

    def _read_size(self):
        result = read_varint(self.trans)
        if result < 0:
            raise TException("Length < 0")
        return result

    def read_message_begin(self):
        proto_id = self._read_ubyte()
        if proto_id != self.PROTOCOL_ID:
            raise TProtocolException(TProtocolException.BAD_VERSION,
                                     'Bad protocol id in the message: %d'
                                     % proto_id)

        ver_type = self._read_ubyte()
        type = (ver_type >> self.TYPE_SHIFT_AMOUNT) & self.TYPE_BITS
        version = ver_type & self.VERSION_MASK
        if version != self.VERSION:
            raise TProtocolException(TProtocolException.BAD_VERSION,
                                     'Bad version: %d (expect %d)'
                                     % (version, self.VERSION))
        seqid = read_varint(self.trans)
        name = self._read_string()
        return name, type, seqid

    def read_message_end(self):
        assert len(self._structs) == 0

    def _read_field_begin(self):
        type = self._read_ubyte()
        if type & 0x0f == TType.STOP:
            return None, 0, 0

        delta = type >> 4
        if delta == 0:
            fid = from_zig_zag(read_varint(self.trans))
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

    def _read_map_begin(self):
        size = self._read_size()
        types = 0
        if size > 0:
            types = self._read_ubyte()
        vtype = self._get_ttype(types)
        ktype = self._get_ttype(types >> 4)
        return (ktype, vtype, size)

    def _read_collection_begin(self):
        size_type = self._read_ubyte()
        size = size_type >> 4
        type = self._get_ttype(size_type)
        if size == 15:
            size = self._read_size()
        return type, size

    def _read_collection_end(self):
        pass

    def _read_byte(self):
        result, = unpack('!b', self.trans.read(1))
        return result

    def _read_ubyte(self):
        result, = unpack('!B', self.trans.read(1))
        return result

    def _read_int(self):
        return from_zig_zag(read_varint(self.trans))

    def _read_double(self):
        buff = self.trans.read(8)
        val, = unpack('<d', buff)
        return val

    def _read_binary(self):
        length = self._read_size()
        return self.trans.read(length)

    def _read_string(self):
        len = self._read_size()
        byte_payload = self.trans.read(len)

        if self.decode_response:
            try:
                byte_payload = byte_payload.decode('utf-8')
            except UnicodeDecodeError:
                pass
        return byte_payload

    def _read_bool(self):
        if self._bool_value is not None:
            result = self._bool_value
            self._bool_value = None
            return result
        return self._read_byte() == CompactType.TRUE

    def read_struct(self, obj):
        self._read_struct_begin()
        while True:
            fname, ftype, fid = self._read_field_begin()
            if ftype == TType.STOP:
                break

            if fid not in obj.thrift_spec:
                self.skip(ftype)
                continue

            try:
                field = obj.thrift_spec[fid]
            except IndexError:
                self.skip(ftype)
                raise
            else:
                if field is not None and\
                        (ftype == field[0]
                         or (ftype in BIN_TYPES
                             and field[0] in BIN_TYPES)):
                    fname = field[1]
                    fspec = field[2]
                    val = self._read_val(field[0], fspec)
                    setattr(obj, fname, val)
                else:
                    self.skip(ftype)
            self._read_field_end()
        self._read_struct_end()

    def _read_val(self, ttype, spec=None):
        if ttype == TType.BOOL:
            return self._read_bool()

        elif ttype == TType.BYTE:
            return self._read_byte()

        elif ttype in (TType.I16, TType.I32, TType.I64):
            return self._read_int()

        elif ttype == TType.DOUBLE:
            return self._read_double()

        elif ttype == TType.BINARY:
            return self._read_binary()

        elif ttype == TType.STRING:
            return self._read_string()

        elif ttype in (TType.LIST, TType.SET):
            if isinstance(spec, tuple):
                v_type, v_spec = spec[0], spec[1]
            else:
                v_type, v_spec = spec, None
            result = []
            r_type, sz = self._read_collection_begin()

            for i in range(sz):
                result.append(self._read_val(v_type, v_spec))

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
            sk_type, sv_type, sz = self._read_map_begin()
            if sk_type in BIN_TYPES:
                sk_type = k_type
            if sv_type in BIN_TYPES:
                sv_type = v_type
            if sk_type != k_type or sv_type != v_type:
                for _ in range(sz):
                    self.skip(sk_type)
                    self.skip(sv_type)
                self._read_collection_end()
                return {}

            for i in range(sz):
                k_val = self._read_val(k_type, k_spec)
                v_val = self._read_val(v_type, v_spec)
                result[k_val] = v_val
            self._read_collection_end()
            return result

        elif ttype == TType.STRUCT:
            obj = spec()
            self.read_struct(obj)
            return obj

    def _write_size(self, i32):
        write_varint(self.trans, i32)

    def _write_field_header(self, type, fid):
        delta = fid - self._last_fid
        if 0 < delta <= 15:
            self._write_ubyte(delta << 4 | type)
        else:
            self._write_byte(type)
            self._write_i16(fid)
        self._last_fid = fid

    def write_message_begin(self, name, type, seqid):
        self._write_ubyte(self.PROTOCOL_ID)
        self._write_ubyte(self.VERSION | (type << self.TYPE_SHIFT_AMOUNT))
        write_varint(self.trans, seqid)
        self._write_string(name)

    def write_message_end(self):
        pass

    def _write_field_stop(self):
        self._write_byte(0)

    def _write_field_begin(self, name, type, fid):
        if type == TType.BOOL:
            self._bool_fid = fid
        else:
            self._write_field_header(CTYPES[type], fid)

    def _write_field_end(self):
        pass

    def _write_struct_begin(self):
        self._structs.append(self._last_fid)
        self._last_fid = 0

    def _write_struct_end(self):
        self._last_fid = self._structs.pop()

    def _write_collection_begin(self, etype, size):
        if size <= 14:
            self._write_ubyte(size << 4 | CTYPES[etype])
        else:
            self._write_ubyte(0xf0 | CTYPES[etype])
            self._write_size(size)

    def _write_map_begin(self, ktype, vtype, size):
        if size == 0:
            self._write_byte(0)
        else:
            self._write_size(size)
            self._write_ubyte(CTYPES[ktype] << 4 | CTYPES[vtype])

    def _write_collection_end(self):
        pass

    def _write_ubyte(self, byte):
        self.trans.write(pack('!B', byte))

    def _write_byte(self, byte):
        self.trans.write(pack('!b', byte))

    def _write_bool(self, bool):
        ctype = CompactType.TRUE if bool else CompactType.FALSE
        if self._bool_fid is not None:
            self._write_field_header(ctype, self._bool_fid)
            self._bool_fid = None
        else:
            self._write_byte(ctype)

    def _write_i16(self, i16):
        write_varint(self.trans, make_zig_zag(i16, 16))

    def _write_i32(self, i32):
        write_varint(self.trans, make_zig_zag(i32, 32))

    def _write_i64(self, i64):
        write_varint(self.trans, make_zig_zag(i64, 64))

    def _write_double(self, dub):
        self.trans.write(pack('<d', dub))

    def _write_binary(self, b):
        self._write_size(len(b))
        self.trans.write(b)

    def _write_string(self, s):
        if not isinstance(s, bytes):
            s = s.encode('utf-8')
        self._write_size(len(s))
        self.trans.write(s)

    def write_struct(self, obj):
        self._write_struct_begin()

        for field in obj.thrift_spec:
            if field is None:
                continue
            fspec = obj.thrift_spec[field]
            if len(fspec) == 3:
                ftype, fname, freq = fspec
                f_container_spec = None
            else:
                ftype, fname, f_container_spec, f_req = fspec
            val = getattr(obj, fname, None)
            if val is None:
                continue

            self._write_field_begin(fname, ftype, field)
            self._write_val(ftype, val, f_container_spec)
            self._write_field_end()
        self._write_field_stop()
        self._write_struct_end()

    def _write_val(self, ttype, val, spec=None):

        if ttype == TType.BOOL:
            self._write_bool(val)

        elif ttype == TType.BYTE:
            self._write_byte(val)

        elif ttype == TType.I16:
            self._write_i16(val)

        elif ttype == TType.I32:
            self._write_i32(val)

        elif ttype == TType.I64:
            self._write_i64(val)

        elif ttype == TType.DOUBLE:
            self._write_double(val)

        elif ttype == TType.BINARY:
            self._write_binary(val)

        elif ttype == TType.STRING:
            self._write_string(val)

        elif ttype == TType.LIST or ttype == TType.SET:
            if isinstance(spec, tuple):
                e_type, t_spec = spec[0], spec[1]
            else:
                e_type, t_spec = spec, None

            val_len = len(val)
            self._write_collection_begin(e_type, val_len)
            for e_val in val:
                self._write_val(e_type, e_val, t_spec)
            self._write_collection_end()

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

            self._write_map_begin(k_type, v_type, len(val))
            for k in iter(val):
                self._write_val(k_type, k, k_spec)
                self._write_val(v_type, val[k], v_spec)
            self._write_collection_end()

        elif ttype == TType.STRUCT:
            self.write_struct(val)

    def skip(self, ttype):
        if ttype == TType.STOP:
            return

        elif ttype == TType.BOOL:
            self._read_bool()

        elif ttype == TType.BYTE:
            self._read_byte()

        elif ttype in (TType.I16, TType.I32, TType.I64):
            from_zig_zag(read_varint(self.trans))

        elif ttype == TType.DOUBLE:
            self._read_double()

        elif ttype == TType.BINARY:
            self._read_binary()

        elif ttype == TType.STRING:
            self._read_string()

        elif ttype == TType.STRUCT:
            name = self._read_struct_begin()
            while True:
                (name, ttype, id) = self._read_field_begin()
                if ttype == TType.STOP:
                    break
                self.skip(ttype)
                self._read_field_end()
            self._read_struct_end()

        elif ttype == TType.MAP:
            ktype, vtype, size = self._read_map_begin()
            for i in range(size):
                self.skip(ktype)
                self.skip(vtype)
            self._read_collection_end()

        elif ttype == TType.SET:
            etype, size = self._read_collection_begin()
            for i in range(size):
                self.skip(etype)
            self._read_collection_end()

        elif ttype == TType.LIST:
            etype, size = self._read_collection_begin()
            for i in range(size):
                self.skip(etype)
            self._read_collection_end()


class TCompactProtocolFactory(object):
    def __init__(self, decode_response=True):
        self.decode_response = decode_response

    def get_protocol(self, trans):
        return TCompactProtocol(trans, decode_response=self.decode_response)
