# -*- coding: utf-8 -*-

from __future__ import absolute_import

import json
import struct
import base64
from warnings import warn

from _shaded_thriftpy._compat import u
from _shaded_thriftpy.thrift import TType

from .exc import TProtocolException
from .base import TProtocolBase

VERSION = 1


def encode_binary(data):
    return base64.b64encode(data).decode('ascii')


def json_value(ttype, val, spec=None):
    TTYPE_TO_JSONFUNC_MAP = {
        TType.BYTE: (int, (val, )),
        TType.I16: (int, (val, )),
        TType.I32: (int, (val, )),
        TType.I64: (int, (val, )),
        TType.DOUBLE: (float, (val, )),
        TType.STRING: (u, (val, )),
        TType.BOOL: (bool, (val, )),
        TType.STRUCT: (struct_to_json, (val, )),
        TType.SET: (list_to_json, (val, spec)),
        TType.LIST: (list_to_json, (val, spec)),
        TType.MAP: (map_to_json, (val, spec)),
        TType.BINARY: (encode_binary, (val, )),
    }
    func, args = TTYPE_TO_JSONFUNC_MAP.get(ttype)
    if func:
        return func(*args)


def obj_value(ttype, val, spec=None):
    # Special case: since `spec` needs to get called if TType is STRUCT,
    # if we initialize inside `TTYPE_TO_OBJFUNC_MAP` it will get called
    # everytime the function gets called and incur in exception as
    # `TypeError: 'NoneType' object is not callable`.
    if ttype == TType.STRUCT:
        return struct_to_obj(val, spec())
    else:
        TTYPE_TO_OBJFUNC_MAP = {
            TType.BYTE: (int, (val, )),
            TType.I16: (int, (val, )),
            TType.I32: (int, (val, )),
            TType.I64: (int, (val, )),
            TType.DOUBLE: (float, (val, )),
            TType.STRING: (u, (val, )),
            TType.BOOL: (bool, (val, )),
            TType.SET: (list_to_obj, (val, spec)),
            TType.LIST: (list_to_obj, (val, spec)),
            TType.MAP: (map_to_obj, (val, spec)),
            TType.BINARY: (base64.b64decode, (val, )),
        }
        func, args = TTYPE_TO_OBJFUNC_MAP.get(ttype)
        if func:
            return func(*args)

def map_to_obj(val, spec):
    res = {}
    if isinstance(spec[0], int):
        key_type, key_spec = spec[0], None
    else:
        key_type, key_spec = spec[0]

    if isinstance(spec[1], int):
        value_type, value_spec = spec[1], None
    else:
        value_type, value_spec = spec[1]

    for v in val:
        res[obj_value(key_type, v["key"], key_spec)] = obj_value(
            value_type, v["value"], value_spec)

    return res


def map_to_json(val, spec):
    res = []
    if isinstance(spec[0], int):
        key_type = spec[0]
        key_spec = None
    else:
        key_type, key_spec = spec[0]

    if isinstance(spec[1], int):
        value_type = spec[1]
        value_spec = None
    else:
        value_type, value_spec = spec[1]

    for k, v in val.items():
        res.append({"key": json_value(key_type, k, key_spec),
                    "value": json_value(value_type, v, value_spec)})

    return res


def list_to_obj(val, spec):
    if isinstance(spec, tuple):
        elem_type, type_spec = spec
    else:
        elem_type, type_spec = spec, None

    return [obj_value(elem_type, i, type_spec) for i in val]


def list_to_json(val, spec):
    if isinstance(spec, tuple):
        elem_type, type_spec = spec
    else:
        elem_type, type_spec = spec, None

    return [json_value(elem_type, i, type_spec) for i in val]


def struct_to_json(val):
    outobj = {}
    for fid, field_spec in val.thrift_spec.items():
        field_type, field_name = field_spec[:2]

        if len(field_spec) <= 3:
            field_type_spec = None
        else:
            field_type_spec = field_spec[2]

        v = getattr(val, field_name)
        if v is None:
            continue

        outobj[field_name] = json_value(field_type, v, field_type_spec)

    return outobj


def struct_to_obj(val, obj):
    for fid, field_spec in obj.thrift_spec.items():
        field_type, field_name = field_spec[:2]

        if len(field_spec) <= 3:
            field_type_spec = None
        else:
            field_type_spec = field_spec[2]

        if field_name in val:
            setattr(obj, field_name,
                    obj_value(field_type, val[field_name], field_type_spec))

    return obj


class TJSONProtocol(TProtocolBase):
    """A JSON protocol.

    The message in the transport are encoded as this: 4 bytes represents
    the length of the json object and immediately followed by the json object.

        '\x00\x00\x00+' '{"payload": {}, "metadata": {"version": 1}}'

    the 4 bytes are the bytes representation of an integer and is encoded in
    big-endian.
    """
    def __init__(self, trans):
        TProtocolBase.__init__(self, trans)
        self._meta = {"version": VERSION}
        self._data = None

    def _write_len(self, x):
        self.trans.write(struct.pack('!I', int(x)))

    def _read_len(self):
        l = self.trans.read(4)
        return struct.unpack('!I', l)[0]

    def read_message_begin(self):
        size = self._read_len()
        self._data = json.loads(self.trans.read(size).decode("utf-8"))
        metadata = self._data["metadata"]

        version = int(metadata["version"])
        if version != VERSION:
            raise TProtocolException(
                type=TProtocolException.BAD_VERSION,
                message="Bad version in read_message_begin:{}".format(version))

        return metadata["name"], metadata["ttype"], metadata["seqid"]

    def read_message_end(self):
        pass

    def write_message_begin(self, name, ttype, seqid):
        self._meta.update({"name": name, "ttype": ttype, "seqid": seqid})

    def write_message_end(self):
        pass

    def read_struct(self, obj):
        if not self._data:
            size = self._read_len()
            self._data = json.loads(self.trans.read(size).decode("utf-8"))

        res = struct_to_obj(self._data["payload"], obj)
        self._data = None
        return res

    def write_struct(self, obj):
        data = json.dumps({
            "metadata": self._meta,
            "payload": struct_to_json(obj)
        })

        self._write_len(len(data))
        self.trans.write(data.encode("utf-8"))

    def skip(self, ttype):
        warn("TJsonProtocol doesn't support skipping. Ignoring.")


class TJSONProtocolFactory(object):
    def get_protocol(self, trans):
        return TJSONProtocol(trans)
