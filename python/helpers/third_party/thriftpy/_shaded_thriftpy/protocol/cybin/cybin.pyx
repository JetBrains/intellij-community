from libc.stdlib cimport free, malloc
from libc.stdint cimport int16_t, int32_t, int64_t
from cpython cimport bool

from thriftpy.transport.cybase cimport CyTransportBase, STACK_STRING_LEN

from ..thrift import TDecodeException

cdef extern from "endian_port.h":
    int16_t htobe16(int16_t n)
    int32_t htobe32(int32_t n)
    int64_t htobe64(int64_t n)
    int16_t be16toh(int16_t n)
    int32_t be32toh(int32_t n)
    int64_t be64toh(int64_t n)

DEF VERSION_MASK = -65536
DEF VERSION_1 = -2147418112
DEF TYPE_MASK = 0x000000ff

ctypedef enum TType:
    T_STOP = 0,
    T_VOID = 1,
    T_BOOL = 2,
    T_BYTE = 3,
    T_I08 = 3,
    T_I16 = 6,
    T_I32 = 8,
    T_U64 = 9,
    T_I64 = 10,
    T_DOUBLE = 4,
    T_STRING = 11,
    T_UTF7 = 11,
    T_NARY = 11
    T_STRUCT = 12,
    T_MAP = 13,
    T_SET = 14,
    T_LIST = 15,
    T_UTF8 = 16,
    T_UTF16 = 17

class ProtocolError(Exception):
    pass


cdef inline char read_i08(CyTransportBase buf) except? -1:
    cdef char data = 0
    buf.c_read(1, &data)
    return data


cdef inline int16_t read_i16(CyTransportBase buf) except? -1:
    cdef char data[2]
    buf.c_read(2, data)
    return be16toh((<int16_t*>data)[0])


cdef inline int32_t read_i32(CyTransportBase buf) except? -1:
    cdef char data[4]
    buf.c_read(4, data)
    return be32toh((<int32_t*>data)[0])


cdef inline int64_t read_i64(CyTransportBase buf) except? -1:
    cdef char data[8]
    buf.c_read(8, data)
    return be64toh((<int64_t*>data)[0])


cdef inline int write_i08(CyTransportBase buf, char val) except -1:
    buf.c_write(&val, 1)
    return 0


cdef inline int write_i16(CyTransportBase buf, int16_t val) except -1:
    val = htobe16(val)
    buf.c_write(<char*>(&val), 2)
    return 0


cdef inline int write_i32(CyTransportBase buf, int32_t val) except -1:
    val = htobe32(val)
    buf.c_write(<char*>(&val), 4)
    return 0


cdef inline int write_i64(CyTransportBase buf, int64_t val) except -1:
    val = htobe64(val)
    buf.c_write(<char*>(&val), 8)
    return 0


cdef inline int write_double(CyTransportBase buf, double val) except -1:
    cdef int64_t v = htobe64((<int64_t*>(&val))[0])
    buf.c_write(<char*>(&v), 8)
    return 0


cdef inline write_list(CyTransportBase buf, object val, spec):
    cdef TType e_type
    cdef int val_len

    if isinstance(spec, int):
        e_type = spec
        e_spec = None
    else:
        e_type = spec[0]
        e_spec = spec[1]

    val_len = len(val)
    write_i08(buf, e_type)
    write_i32(buf, val_len)

    for e_val in val:
        c_write_val(buf, e_type, e_val, e_spec)


cdef inline write_string(CyTransportBase buf, bytes val):
    cdef int val_len = len(val)
    write_i32(buf, val_len)

    buf.c_write(<char*>val, val_len)


cdef inline write_dict(CyTransportBase buf, object val, spec):
    cdef int val_len
    cdef TType v_type, k_type

    key = spec[0]
    if isinstance(key, int):
        k_type = key
        k_spec = None
    else:
        k_type = key[0]
        k_spec = key[1]

    value = spec[1]
    if isinstance(value, int):
        v_type = value
        v_spec = None
    else:
        v_type = value[0]
        v_spec = value[1]

    val_len = len(val)

    write_i08(buf, k_type)
    write_i08(buf, v_type)
    write_i32(buf, val_len)

    for k, v in val.items():
        c_write_val(buf, k_type, k, k_spec)
        c_write_val(buf, v_type, v, v_spec)


cdef inline read_struct(CyTransportBase buf, obj, decode_response=True):
    cdef dict field_specs = obj.thrift_spec
    cdef int fid
    cdef TType field_type, ttype
    cdef tuple field_spec
    cdef str name

    while True:
        field_type = <TType>read_i08(buf)
        if field_type == T_STOP:
            break

        fid = read_i16(buf)
        if fid not in field_specs:
            skip(buf, field_type)
            continue

        field_spec = field_specs[fid]
        ttype = field_spec[0]
        if field_type != ttype:
            skip(buf, field_type)
            continue

        name = field_spec[1]
        if len(field_spec) <= 3:
            spec = None
        else:
            spec = field_spec[2]

        setattr(obj, name, c_read_val(buf, ttype, spec, decode_response))

    return obj


cdef inline write_struct(CyTransportBase buf, obj):
    cdef int fid
    cdef TType f_type
    cdef dict thrift_spec = obj.thrift_spec
    cdef tuple field_spec
    cdef str f_name

    for fid, field_spec in thrift_spec.items():
        f_type = field_spec[0]
        f_name = field_spec[1]
        if len(field_spec) <= 3:
            container_spec = None
        else:
            container_spec = field_spec[2]

        v = getattr(obj, f_name)
        if v is None:
            continue

        write_i08(buf, f_type)
        write_i16(buf, fid)
        try:
            c_write_val(buf, f_type, v, container_spec)
        except (TypeError, AttributeError, AssertionError, OverflowError):
            raise TDecodeException(obj.__class__.__name__, fid, f_name, v,
                                   f_type, container_spec)

    write_i08(buf, T_STOP)


cdef inline c_read_binary(CyTransportBase buf, int32_t size):
    cdef char string_val[STACK_STRING_LEN]

    if size > STACK_STRING_LEN:
        data = <char*>malloc(size)
        buf.c_read(size, data)
        py_data = data[:size]
        free(data)
    else:
        buf.c_read(size, string_val)
        py_data = string_val[:size]

    return py_data


cdef inline c_read_string(CyTransportBase buf, int32_t size):
    py_data = c_read_binary(buf, size)
    try:
        return py_data.decode("utf-8")
    except:
        return py_data


cdef c_read_val(CyTransportBase buf, TType ttype, spec=None,
                decode_response=True):
    cdef int size
    cdef int64_t n
    cdef TType v_type, k_type, orig_type, orig_key_type

    if ttype == T_BOOL:
        return <bint>read_i08(buf)

    elif ttype == T_I08:
        return read_i08(buf)

    elif ttype == T_I16:
        return read_i16(buf)

    elif ttype == T_I32:
        return read_i32(buf)

    elif ttype == T_I64:
        return read_i64(buf)

    elif ttype == T_DOUBLE:
        n = read_i64(buf)
        return (<double*>(&n))[0]

    elif ttype == T_STRING:
        size = read_i32(buf)
        if decode_response:
            return c_read_string(buf, size)
        else:
            return c_read_binary(buf, size)

    elif ttype == T_SET or ttype == T_LIST:
        if isinstance(spec, int):
            v_type = spec
            v_spec = None
        else:
            v_type = spec[0]
            v_spec = spec[1]

        orig_type = <TType>read_i08(buf)
        size = read_i32(buf)

        if orig_type != v_type:
            for _ in range(size):
                skip(buf, orig_type)
            return []

        return [c_read_val(buf, v_type, v_spec, decode_response)
                for _ in range(size)]

    elif ttype == T_MAP:
        key = spec[0]
        if isinstance(key, int):
            k_type = key
            k_spec = None
        else:
            k_type = key[0]
            k_spec = key[1]

        value = spec[1]
        if isinstance(value, int):
            v_type = value
            v_spec = None
        else:
            v_type = value[0]
            v_spec = value[1]

        orig_key_type = <TType>read_i08(buf)
        orig_type = <TType>read_i08(buf)
        size = read_i32(buf)

        if orig_key_type != k_type or orig_type != v_type:
            for _ in range(size):
                skip(buf, orig_key_type)
                skip(buf, orig_type)
            return {}

        return {c_read_val(buf, k_type, k_spec, decode_response): c_read_val(buf, v_type, v_spec, decode_response)
                for _ in range(size)}

    elif ttype == T_STRUCT:
        return read_struct(buf, spec(), decode_response)


cdef c_write_val(CyTransportBase buf, TType ttype, val, spec=None):
    if ttype == T_BOOL:
        write_i08(buf, 1 if val else 0)

    elif ttype == T_I08:
        write_i08(buf, val)

    elif ttype == T_I16:
        write_i16(buf, val)

    elif ttype == T_I32:
        write_i32(buf, val)

    elif ttype == T_I64:
        write_i64(buf, val)

    elif ttype == T_DOUBLE:
        write_double(buf, val)

    elif ttype == T_STRING:
        if not isinstance(val, bytes):
            try:
                val = val.encode("utf-8")
            except Exception:
                pass
        write_string(buf, val)

    elif ttype == T_SET or ttype == T_LIST:
        write_list(buf, val, spec)

    elif ttype == T_MAP:
        write_dict(buf, val, spec)

    elif ttype == T_STRUCT:
        write_struct(buf, val)


cpdef skip(CyTransportBase buf, TType ttype):
    cdef TType v_type, k_type, f_type
    cdef int size

    if ttype == T_BOOL or ttype == T_I08:
        read_i08(buf)
    elif ttype == T_I16:
        read_i16(buf)
    elif ttype == T_I32:
        read_i32(buf)
    elif ttype == T_I64 or ttype == T_DOUBLE:
        read_i64(buf)
    elif ttype == T_STRING:
        size = read_i32(buf)
        c_read_binary(buf, size)
    elif ttype == T_SET or ttype == T_LIST:
        v_type = <TType>read_i08(buf)
        size = read_i32(buf)
        for _ in range(size):
            skip(buf, v_type)
    elif ttype == T_MAP:
        k_type = <TType>read_i08(buf)
        v_type = <TType>read_i08(buf)
        size = read_i32(buf)
        for _ in range(size):
            skip(buf, k_type)
            skip(buf, v_type)
    elif ttype == T_STRUCT:
        while 1:
            f_type = <TType>read_i08(buf)
            if f_type == T_STOP:
                break
            read_i16(buf)
            skip(buf, f_type)


def read_val(CyTransportBase buf, TType ttype, decode_response=True):
    return c_read_val(buf, ttype, None, decode_response)


def write_val(CyTransportBase buf, TType ttype, val, spec=None):
    c_write_val(buf, ttype, val, spec)


cdef class TCyBinaryProtocol(object):
    cdef public CyTransportBase trans
    cdef public bool strict_read
    cdef public bool strict_write
    cdef public bool decode_response

    def __init__(self, trans, strict_read=True, strict_write=True,
                 decode_response=True):
        self.trans = trans
        self.strict_read = strict_read
        self.strict_write = strict_write
        self.decode_response = decode_response

    def skip(self, ttype):
        skip(self.trans, <TType>(ttype))

    def read_message_begin(self):
        cdef int32_t size, version, seqid
        cdef TType ttype

        size = read_i32(self.trans)
        if size < 0:
            version = size & VERSION_MASK
            if version != VERSION_1:
                raise ProtocolError('invalid version %d' % version)

            name = c_read_val(self.trans, T_STRING)
            ttype = <TType>(size & TYPE_MASK)
        else:
            if self.strict_read:
                raise ProtocolError('No protocol version header')

            name = c_read_string(self.trans, size)
            ttype = <TType>(read_i08(self.trans))

        seqid = read_i32(self.trans)

        return name, ttype, seqid

    def read_message_end(self):
        pass

    def write_message_begin(self, name, TType ttype, int32_t seqid):
        cdef int32_t version = VERSION_1 | ttype
        if self.strict_write:
            write_i32(self.trans, version)
            c_write_val(self.trans, T_STRING, name)
        else:
            c_write_val(self.trans, T_STRING, name)
            write_i08(self.trans, ttype)

        write_i32(self.trans, seqid)

    def write_message_end(self):
        self.trans.c_flush()

    def read_struct(self, obj):
        try:
            return read_struct(self.trans, obj, self.decode_response)
        except Exception:
            self.trans.clean()
            raise

    def write_struct(self, obj):
        try:
            write_struct(self.trans, obj)
        except Exception:
            self.trans.clean()
            raise


class TCyBinaryProtocolFactory(object):
    def __init__(self, strict_read=True, strict_write=True,
                 decode_response=True):
        self.strict_read = strict_read
        self.strict_write = strict_write
        self.decode_response = decode_response

    def get_protocol(self, trans):
        return TCyBinaryProtocol(
            trans, self.strict_read, self.strict_write, self.decode_response)
