# -*- coding: utf-8 -*-

from __future__ import absolute_import

import struct

from ..thrift import TType

from .exc import TProtocolException

# VERSION_MASK = 0xffff0000
VERSION_MASK = -65536
# VERSION_1 = 0x80010000
VERSION_1 = -2147418112
TYPE_MASK = 0x000000ff


def pack_i8(byte):
    return struct.pack("!b", byte)


def pack_i16(i16):
    return struct.pack("!h", i16)


def pack_i32(i32):
    return struct.pack("!i", i32)


def pack_i64(i64):
    return struct.pack("!q", i64)


def pack_double(dub):
    return struct.pack("!d", dub)


def pack_string(string):
    return struct.pack("!i%ds" % len(string), len(string), string)


def unpack_i8(buf):
    return struct.unpack("!b", buf)[0]


def unpack_i16(buf):
    return struct.unpack("!h", buf)[0]


def unpack_i32(buf):
    return struct.unpack("!i", buf)[0]


def unpack_i64(buf):
    return struct.unpack("!q", buf)[0]


def unpack_double(buf):
    return struct.unpack("!d", buf)[0]


def write_message_begin(outbuf, name, ttype, seqid, strict=True):
    if strict:
        outbuf.write(pack_i32(VERSION_1 | ttype))
        outbuf.write(pack_string(name.encode('utf-8')))
    else:
        outbuf.write(pack_string(name.encode('utf-8')))
        outbuf.write(pack_i8(ttype))

    outbuf.write(pack_i32(seqid))


def write_field_begin(outbuf, ttype, fid):
    outbuf.write(pack_i8(ttype) + pack_i16(fid))


def write_field_stop(outbuf):
    outbuf.write(pack_i8(TType.STOP))


def write_list_begin(outbuf, etype, size):
    outbuf.write(pack_i8(etype) + pack_i32(size))


def write_map_begin(outbuf, ktype, vtype, size):
    outbuf.write(pack_i8(ktype) + pack_i8(vtype) + pack_i32(size))


def write_val(outbuf, ttype, val, spec=None):
    if ttype == TType.BOOL:
        if val:
            outbuf.write(pack_i8(1))
        else:
            outbuf.write(pack_i8(0))

    elif ttype == TType.BYTE:
        outbuf.write(pack_i8(val))

    elif ttype == TType.I16:
        outbuf.write(pack_i16(val))

    elif ttype == TType.I32:
        outbuf.write(pack_i32(val))

    elif ttype == TType.I64:
        outbuf.write(pack_i64(val))

    elif ttype == TType.DOUBLE:
        outbuf.write(pack_double(val))

    elif ttype == TType.STRING:
        if not isinstance(val, bytes):
            val = val.encode('utf-8')
        outbuf.write(pack_string(val))

    elif ttype == TType.SET or ttype == TType.LIST:
        if isinstance(spec, tuple):
            e_type, t_spec = spec[0], spec[1]
        else:
            e_type, t_spec = spec, None

        val_len = len(val)
        write_list_begin(outbuf, e_type, val_len)
        for e_val in val:
            write_val(outbuf, e_type, e_val, t_spec)

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

        write_map_begin(outbuf, k_type, v_type, len(val))
        for k in iter(val):
            write_val(outbuf, k_type, k, k_spec)
            write_val(outbuf, v_type, val[k], v_spec)

    elif ttype == TType.STRUCT:
        for fid in iter(val.thrift_spec):
            f_spec = val.thrift_spec[fid]
            if len(f_spec) == 3:
                f_type, f_name, f_req = f_spec
                f_container_spec = None
            else:
                f_type, f_name, f_container_spec, f_req = f_spec

            v = getattr(val, f_name)
            if v is None:
                continue

            write_field_begin(outbuf, f_type, fid)
            write_val(outbuf, f_type, v, f_container_spec)
        write_field_stop(outbuf)


def read_message_begin(inbuf, strict=True):
    sz = unpack_i32(inbuf.read(4))
    if sz < 0:
        version = sz & VERSION_MASK
        if version != VERSION_1:
            raise TProtocolException(
                type=TProtocolException.BAD_VERSION,
                message='Bad version in read_message_begin: %d' % (sz))
        name_sz = unpack_i32(inbuf.read(4))
        name = inbuf.read(name_sz).decode('utf-8')

        type_ = sz & TYPE_MASK
    else:
        if strict:
            raise TProtocolException(type=TProtocolException.BAD_VERSION,
                                     message='No protocol version header')

        name = inbuf.read(sz).decode('utf-8')
        type_ = unpack_i8(inbuf.read(1))

    seqid = unpack_i32(inbuf.read(4))

    return name, type_, seqid


def read_field_begin(inbuf):
    f_type = unpack_i8(inbuf.read(1))
    if f_type == TType.STOP:
        return f_type, 0

    return f_type, unpack_i16(inbuf.read(2))


def read_list_begin(inbuf):
    e_type = unpack_i8(inbuf.read(1))
    sz = unpack_i32(inbuf.read(4))
    return e_type, sz


def read_map_begin(inbuf):
    k_type, v_type = unpack_i8(inbuf.read(1)), unpack_i8(inbuf.read(1))
    sz = unpack_i32(inbuf.read(4))
    return k_type, v_type, sz


def read_val(inbuf, ttype, spec=None, decode_response=True):
    if ttype == TType.BOOL:
        return bool(unpack_i8(inbuf.read(1)))

    elif ttype == TType.BYTE:
        return unpack_i8(inbuf.read(1))

    elif ttype == TType.I16:
        return unpack_i16(inbuf.read(2))

    elif ttype == TType.I32:
        return unpack_i32(inbuf.read(4))

    elif ttype == TType.I64:
        return unpack_i64(inbuf.read(8))

    elif ttype == TType.DOUBLE:
        return unpack_double(inbuf.read(8))

    elif ttype == TType.STRING:
        sz = unpack_i32(inbuf.read(4))
        byte_payload = inbuf.read(sz)

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
        r_type, sz = read_list_begin(inbuf)
        # the v_type is useless here since we already get it from spec
        if r_type != v_type:
            for _ in range(sz):
                skip(inbuf, r_type)
            return []

        for i in range(sz):
            result.append(read_val(inbuf, v_type, v_spec, decode_response))
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
        sk_type, sv_type, sz = read_map_begin(inbuf)
        if sk_type != k_type or sv_type != v_type:
            for _ in range(sz):
                skip(inbuf, sk_type)
                skip(inbuf, sv_type)
            return {}

        for i in range(sz):
            k_val = read_val(inbuf, k_type, k_spec, decode_response)
            v_val = read_val(inbuf, v_type, v_spec, decode_response)
            result[k_val] = v_val

        return result

    elif ttype == TType.STRUCT:
        obj = spec()
        read_struct(inbuf, obj, decode_response)
        return obj


def read_struct(inbuf, obj, decode_response=True):
    while True:
        f_type, fid = read_field_begin(inbuf)
        if f_type == TType.STOP:
            break

        if fid not in obj.thrift_spec:
            skip(inbuf, f_type)
            continue

        if len(obj.thrift_spec[fid]) == 3:
            sf_type, f_name, f_req = obj.thrift_spec[fid]
            f_container_spec = None
        else:
            sf_type, f_name, f_container_spec, f_req = obj.thrift_spec[fid]

        # it really should equal here. but since we already wasted
        # space storing the duplicate info, let's check it.
        if f_type != sf_type:
            skip(inbuf, f_type)
            continue

        setattr(obj, f_name,
                read_val(inbuf, f_type, f_container_spec, decode_response))


def skip(inbuf, ftype):
    if ftype == TType.BOOL or ftype == TType.BYTE:
        inbuf.read(1)

    elif ftype == TType.I16:
        inbuf.read(2)

    elif ftype == TType.I32:
        inbuf.read(4)

    elif ftype == TType.I64:
        inbuf.read(8)

    elif ftype == TType.DOUBLE:
        inbuf.read(8)

    elif ftype == TType.STRING:
        inbuf.read(unpack_i32(inbuf.read(4)))

    elif ftype == TType.SET or ftype == TType.LIST:
        v_type, sz = read_list_begin(inbuf)
        for i in range(sz):
            skip(inbuf, v_type)

    elif ftype == TType.MAP:
        k_type, v_type, sz = read_map_begin(inbuf)
        for i in range(sz):
            skip(inbuf, k_type)
            skip(inbuf, v_type)

    elif ftype == TType.STRUCT:
        while True:
            f_type, fid = read_field_begin(inbuf)
            if f_type == TType.STOP:
                break
            skip(inbuf, f_type)


class TBinaryProtocol(object):
    """Binary implementation of the Thrift protocol driver."""

    def __init__(self, trans,
                 strict_read=True, strict_write=True,
                 decode_response=True):
        self.trans = trans
        self.strict_read = strict_read
        self.strict_write = strict_write
        self.decode_response = decode_response

    def skip(self, ttype):
        skip(self.trans, ttype)

    def read_message_begin(self):
        api, ttype, seqid = read_message_begin(
            self.trans, strict=self.strict_read)
        return api, ttype, seqid

    def read_message_end(self):
        pass

    def write_message_begin(self, name, ttype, seqid):
        write_message_begin(self.trans, name, ttype, seqid,
                            strict=self.strict_write)

    def write_message_end(self):
        pass

    def read_struct(self, obj):
        return read_struct(self.trans, obj, self.decode_response)

    def write_struct(self, obj):
        write_val(self.trans, TType.STRUCT, obj)


class TBinaryProtocolFactory(object):
    def __init__(self, strict_read=True, strict_write=True,
                 decode_response=True):
        self.strict_read = strict_read
        self.strict_write = strict_write
        self.decode_response = decode_response

    def get_protocol(self, trans):
        return TBinaryProtocol(trans,
                               self.strict_read, self.strict_write,
                               self.decode_response)
