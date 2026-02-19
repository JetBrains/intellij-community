# -*- coding: utf-8 -*-

"""
Transport for json protocol that apache thrift files will understand
unfortunately, _shaded_thriftpy's TJSONProtocol is not compatible with apache's
"""

from __future__ import absolute_import
import json
import base64

from six import string_types

from _shaded_thriftpy.protocol import TProtocolBase
from _shaded_thriftpy.thrift import TType


CTYPES = {
    TType.BOOL: 'tf',
    TType.BYTE: 'i8',
    TType.I16: 'i16',
    TType.I32: 'i32',
    TType.I64: 'i64',
    TType.DOUBLE: 'dbl',
    TType.STRING: 'str',
    TType.BINARY: 'str',  # apache sends binary data as base64 encoded
    TType.STRUCT: 'rec',
    TType.LIST: 'lst',
    TType.SET: 'set',
    TType.MAP: 'map',
}

JTYPES = {v: k for k, v in CTYPES.items()}

VERSION = 1


def flatten(suitable_for_isinstance):
    """
    isinstance() can accept a bunch of really annoying different types:
        * a single type
        * a tuple of types
        * an arbitrary nested tree of tuples
    Return a flattened tuple of the given argument.
    """

    types = list()

    if not isinstance(suitable_for_isinstance, tuple):
        suitable_for_isinstance = (suitable_for_isinstance,)
    for thing in suitable_for_isinstance:
        if isinstance(thing, tuple):
            types.extend(flatten(thing))
        else:
            types.append(thing)
    return tuple(types)


class TApacheJSONProtocolFactory(object):
    @staticmethod
    def get_protocol(trans):
        return TApacheJSONProtocol(trans)


class TApacheJSONProtocol(TProtocolBase):
    """
    Protocol that implements the Apache JSON Protocol
    """

    def __init__(self, trans):
        TProtocolBase.__init__(self, trans)
        self._req = None

    def _load_data(self):
        data = b""
        l_braces = 0
        in_string = False
        while True:
            # read(sz) will wait until it has read exactly sz bytes,
            # so we must read until we get a balanced json list in absence of knowing
            # how long the json string will be
            if hasattr(self.trans, 'getvalue'):
                try:
                    data = self.trans.getvalue()
                    break
                except:
                    pass
            new_data = self.trans.read(1)
            data += new_data
            if new_data == b'"' and not data.endswith(b'\\"'):
                in_string = not in_string
            if not in_string:
                if new_data == b"[":
                    l_braces += 1
                elif new_data == b"]":
                    l_braces -= 1
            if l_braces == 0:
                break
        if data:
            self._req = json.loads(data.decode('utf8'))
        else:
            self._req = None

    def read_message_begin(self):
        if not self._req:
            self._load_data()
        return self._req[1:4]

    def read_message_end(self):
        pass

    def skip(self, ttype):
        pass

    def write_message_end(self):
        pass

    def write_message_begin(self, name, ttype, seqid):
        self.api = name
        self.ttype = ttype
        self.seqid = seqid

    def write_struct(self, obj):
        """
        Write json to self.trans following apache style jsonification of `obj`

        :param obj: A _shaded_thriftpy object
        :return:
        """
        doc = [VERSION, self.api, self.ttype, self.seqid, self._thrift_to_dict(obj)]
        json_str = json.dumps(doc, separators=(',', ':'))
        self.trans.write(json_str.encode("utf8"))

    def _thrift_to_dict(self, thrift_obj, item_type=None):
        """
        Convert a _shaded_thriftpy into an apache conformant dict, eg:

        >>> {0: {'rec': {1: {'str': "304"}, 14: {'rec': {1: {'lst': ["rec", 0]}}}}}}

        >>> {"0":{"rec":{"1":{"str":"284"},"14":{"rec":{"1":{"lst":
        >>>  ["rec",2,{"1":{"i32":12345.0},"2":{"i32":2.0},"3":{"str":"Testing notifications"},"4":{"tf":1}},
              {"1":{"i32":567809.0},"2":{"i32":2.0},"3":{"str":"Other test"},"4":{"tf":0}}]}}}}}}

        :param thrift_obj: the thing we want to make into a dict
        :param item_type: the type of the item we are to convert
        :return:
        """
        if not hasattr(thrift_obj, 'thrift_spec'):
            # use item_type to render it
            if item_type is not None:
                if isinstance(item_type, tuple) and len(item_type) > 1:
                    to_type = item_type[1]
                    flat_key_val = [TType.STRUCT if hasattr(t, 'thrift_spec') else t for t in flatten(to_type)]
                    if flat_key_val[0] == TType.LIST or isinstance(thrift_obj, list):
                        return [CTYPES[flat_key_val[1]], len(thrift_obj)] + [self._thrift_to_dict(v, to_type[1]) for v
                                                                             in thrift_obj]
                    elif flat_key_val[0] == TType.MAP or isinstance(thrift_obj, dict):
                        if to_type[0] == TType.MAP:
                            key_type = flat_key_val[1]
                            val_type = flat_key_val[2]
                        else:
                            key_type = flat_key_val[0]
                            val_type = flat_key_val[1]
                        return [CTYPES[key_type], CTYPES[val_type], len(thrift_obj), {
                            self._thrift_to_dict(k, key_type):
                                self._thrift_to_dict(v, to_type[1]) for k, v in thrift_obj.items()
                        }]
                    if to_type == TType.BINARY or item_type[0] == TType.BINARY:
                        return base64.b64encode(thrift_obj).decode('ascii')
            if isinstance(thrift_obj, bool):
                return int(thrift_obj)
            if item_type == TType.BINARY or (isinstance(item_type, tuple) and item_type[0] == TType.BINARY):
                return base64.b64encode(thrift_obj).decode('ascii')
            return thrift_obj
        result = {}
        for field_idx, thrift_spec in thrift_obj.thrift_spec.items():
            ttype, field_name, spec = thrift_spec[:3]
            if isinstance(spec, int):
                spec = (spec,)
            val = getattr(thrift_obj, field_name)
            if val is not None:
                if ttype == TType.STRUCT:
                    result[field_idx] = {
                        CTYPES[ttype]: self._thrift_to_dict(val)
                    }
                elif ttype in [TType.LIST, TType.SET]:
                    # format is [list_item_type, length, items]
                    result[field_idx] = {
                        CTYPES[ttype]: [CTYPES[spec[0]], len(val)] + [self._thrift_to_dict(v, spec) for v in val]
                    }
                elif ttype == TType.MAP:
                    key_type = CTYPES[spec[0]]
                    val_type = CTYPES[spec[1][0] if isinstance(spec[1], tuple) else spec[1]]
                    # format is [key_type, value_type, length, dict]
                    result[field_idx] = {
                        CTYPES[ttype]: [key_type, val_type, len(val),
                                        {self._thrift_to_dict(k, spec[0]):
                                         self._thrift_to_dict(v, spec) for k, v in val.items()}]
                    }
                elif ttype == TType.BINARY:
                    result[field_idx] = {
                        CTYPES[ttype]: base64.b64encode(val).decode('ascii')
                    }
                elif ttype == TType.BOOL:
                    result[field_idx] = {
                        CTYPES[ttype]: int(val)
                    }
                else:
                    result[field_idx] = {
                        CTYPES[ttype]: val
                    }
        return result

    def _dict_to_thrift(self, data, base_type):
        """
        Convert an apache thrift dict (where key is the type, value is the data)

        :param data: the dict data
        :param base_type: the type we are going to convert data to
        :return:
        """
        # if the result is a python type, return it:
        if isinstance(data, (str, int, float, bool, bytes, string_types)) or data is None:
            if base_type in (TType.I08, TType.I16, TType.I32, TType.I64):
                return int(data)
            if base_type == TType.BINARY:
                return base64.b64decode(data)
            if base_type == TType.BOOL:
                return {
                    'true': True,
                    'false': False,
                    '1': True,
                    '0': False
                }[data.lower()]
            if isinstance(data, bool):
                return int(data)
            return data

        if isinstance(base_type, tuple):
            container_type = base_type[0]
            item_type = base_type[1]
            if container_type == TType.STRUCT:
                return self._dict_to_thrift(data, item_type)
            elif container_type in (TType.LIST, TType.SET):
                return [self._dict_to_thrift(v, item_type) for v in data[2:]]
            elif container_type == TType.MAP:
                return {
                    self._dict_to_thrift(k, item_type[0]):
                        self._dict_to_thrift(v, item_type[1]) for k, v in data[3].items()
                }
        result = {}
        base_spec = base_type.thrift_spec
        for field_idx, val in data.items():
            thrift_spec = base_spec[int(field_idx)]
            # spec has field type, field name, (sub spec), False
            field_name = thrift_spec[1]
            for ftype, value in val.items():
                ttype = JTYPES[ftype]
                if thrift_spec[0] == TType.BINARY:
                    bin_data = val.get('str', '')
                    m = len(bin_data) % 4
                    if m != 0:
                        bin_data += '=' * (4-m)
                    result[field_name] = base64.b64decode(bin_data)
                elif ttype == TType.STRUCT:
                    result[field_name] = self._dict_to_thrift(value, thrift_spec[2])
                elif ttype in (TType.LIST, TType.SET):
                    result[field_name] = [self._dict_to_thrift(v, thrift_spec[2]) for v in value[2:]]
                elif ttype == TType.MAP:
                    key_spec = thrift_spec[2][0]
                    val_spec = thrift_spec[2][1]
                    result[field_name] = {
                        self._dict_to_thrift(k, key_spec): self._dict_to_thrift(v, val_spec)
                        for k, v in value[3].items()
                    }
                else:
                    result[field_name] = {
                        'tf': bool,
                        'i8': int,
                        'i16': int,
                        'i32': int,
                        'i64': int,
                        'dbl': float,
                        'str': str,
                    }[ftype](value)
        if hasattr(base_type, '__call__'):
            return base_type(**result)
        else:
            for k, v in result.items():
                setattr(base_type, k, v)
            return base_type

    def read_struct(self, obj):
        """
        Read the next struct into obj, usually the argument from an incoming request
        Only really used to read the arguments off a request into whatever we want
        see _shaded_thriftpy.thrift.TProcessor.process_in for how this class will be used

        Will turn the contents of self.req[4] into the args of obj,
        ie. self.req[4]["1"] must be rendered into obj.thrift_spec

        :param obj:
        :return:
        """
        return self._dict_to_thrift(self._req[4], obj)
