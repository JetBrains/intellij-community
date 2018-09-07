# -*- coding: utf-8 -*-

from __future__ import absolute_import

import json
import struct

from _shaded_thriftpy.thrift import TType

from .exc import TProtocolException

INTEGER = (TType.BYTE, TType.I16, TType.I32, TType.I64)
FLOAT = (TType.DOUBLE,)

VERSION = 1


def json_value(ttype, val, spec=None):
    if ttype in INTEGER or ttype in FLOAT or ttype == TType.STRING:
        return val

    if ttype == TType.BOOL:
        return True if val else False

    if ttype == TType.STRUCT:
        return struct_to_json(val)

    if ttype in (TType.SET, TType.LIST):
        return list_to_json(val, spec)

    if ttype == TType.MAP:
        return map_to_json(val, spec)


def obj_value(ttype, val, spec=None):
    if ttype in INTEGER:
        return int(val)

    if ttype in FLOAT:
        return float(val)

    if ttype in (TType.STRING, TType.BOOL):
        return val

    if ttype == TType.STRUCT:
        return struct_to_obj(val, spec())

    if ttype in (TType.SET, TType.LIST):
        return list_to_obj(val, spec)

    if ttype == TType.MAP:
        return map_to_obj(val, spec)


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


class TJSONProtocol(object):
    """A JSON protocol.

    The message in the transport are encoded as this: 4 bytes represents
    the length of the json object and immediately followed by the json object.

        '\x00\x00\x00+' '{"payload": {}, "metadata": {"version": 1}}'

    the 4 bytes are the bytes representation of an integer and is encoded in
    big-endian.
    """
    def __init__(self, trans):
        self.trans = trans
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


class TJSONProtocolFactory(object):
    def get_protocol(self, trans):
        return TJSONProtocol(trans)
