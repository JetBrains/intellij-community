#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements. See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership. The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License. You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied. See the License for the
# specific language governing permissions and limitations
# under the License.
#

from thrift.Thrift import *


class TProtocolException(TException):
  """Custom Protocol Exception class"""

  UNKNOWN = 0
  INVALID_DATA = 1
  NEGATIVE_SIZE = 2
  SIZE_LIMIT = 3
  BAD_VERSION = 4
  NOT_IMPLEMENTED = 5
  DEPTH_LIMIT = 6

  def __init__(self, type=UNKNOWN, message=None):
    TException.__init__(self, message)
    self.type = type


class TProtocolBase:
  """Base class for Thrift protocol driver."""

  def __init__(self, trans):
    self.trans = trans

  def writeMessageBegin(self, name, ttype, seqid):
    pass

  def writeMessageEnd(self):
    pass

  def writeStructBegin(self, name):
    pass

  def writeStructEnd(self):
    pass

  def writeFieldBegin(self, name, ttype, fid):
    pass

  def writeFieldEnd(self):
    pass

  def writeFieldStop(self):
    pass

  def writeMapBegin(self, ktype, vtype, size):
    pass

  def writeMapEnd(self):
    pass

  def writeListBegin(self, etype, size):
    pass

  def writeListEnd(self):
    pass

  def writeSetBegin(self, etype, size):
    pass

  def writeSetEnd(self):
    pass

  def writeBool(self, bool_val):
    pass

  def writeByte(self, byte):
    pass

  def writeI16(self, i16):
    pass

  def writeI32(self, i32):
    pass

  def writeI64(self, i64):
    pass

  def writeDouble(self, dub):
    pass

  def writeString(self, str_val):
    pass

  def readMessageBegin(self):
    pass

  def readMessageEnd(self):
    pass

  def readStructBegin(self):
    pass

  def readStructEnd(self):
    pass

  def readFieldBegin(self):
    pass

  def readFieldEnd(self):
    pass

  def readMapBegin(self):
    pass

  def readMapEnd(self):
    pass

  def readListBegin(self):
    pass

  def readListEnd(self):
    pass

  def readSetBegin(self):
    pass

  def readSetEnd(self):
    pass

  def readBool(self):
    pass

  def readByte(self):
    pass

  def readI16(self):
    pass

  def readI32(self):
    pass

  def readI64(self):
    pass

  def readDouble(self):
    pass

  def readString(self):
    pass

  def skip(self, ttype):
    if ttype == TType.STOP:
      return
    elif ttype == TType.BOOL:
      self.readBool()
    elif ttype == TType.BYTE:
      self.readByte()
    elif ttype == TType.I16:
      self.readI16()
    elif ttype == TType.I32:
      self.readI32()
    elif ttype == TType.I64:
      self.readI64()
    elif ttype == TType.DOUBLE:
      self.readDouble()
    elif ttype == TType.STRING:
      self.readString()
    elif ttype == TType.STRUCT:
      name = self.readStructBegin()
      while True:
        (name, ttype, id) = self.readFieldBegin()
        if ttype == TType.STOP:
          break
        self.skip(ttype)
        self.readFieldEnd()
      self.readStructEnd()
    elif ttype == TType.MAP:
      (ktype, vtype, size) = self.readMapBegin()
      for i in xrange(size):
        self.skip(ktype)
        self.skip(vtype)
      self.readMapEnd()
    elif ttype == TType.SET:
      (etype, size) = self.readSetBegin()
      for i in xrange(size):
        self.skip(etype)
      self.readSetEnd()
    elif ttype == TType.LIST:
      (etype, size) = self.readListBegin()
      for i in xrange(size):
        self.skip(etype)
      self.readListEnd()

  # tuple of: ( 'reader method' name, is_container bool, 'writer_method' name )
  _TTYPE_HANDLERS = (
       (None, None, False),  # 0 TType.STOP
       (None, None, False),  # 1 TType.VOID # TODO: handle void?
       ('readBool', 'writeBool', False),  # 2 TType.BOOL
       ('readByte',  'writeByte', False),  # 3 TType.BYTE and I08
       ('readDouble', 'writeDouble', False),  # 4 TType.DOUBLE
       (None, None, False),  # 5 undefined
       ('readI16', 'writeI16', False),  # 6 TType.I16
       (None, None, False),  # 7 undefined
       ('readI32', 'writeI32', False),  # 8 TType.I32
       (None, None, False),  # 9 undefined
       ('readI64', 'writeI64', False),  # 10 TType.I64
       ('readString', 'writeString', False),  # 11 TType.STRING and UTF7
       ('readContainerStruct', 'writeContainerStruct', True),  # 12 *.STRUCT
       ('readContainerMap', 'writeContainerMap', True),  # 13 TType.MAP
       ('readContainerSet', 'writeContainerSet', True),  # 14 TType.SET
       ('readContainerList', 'writeContainerList', True),  # 15 TType.LIST
       (None, None, False),  # 16 TType.UTF8 # TODO: handle utf8 types?
       (None, None, False)  # 17 TType.UTF16 # TODO: handle utf16 types?
      )

  def readFieldByTType(self, ttype, spec):
    try:
      (r_handler, w_handler, is_container) = self._TTYPE_HANDLERS[ttype]
    except IndexError:
      raise TProtocolException(type=TProtocolException.INVALID_DATA,
                               message='Invalid field type %d' % (ttype))
    if r_handler is None:
      raise TProtocolException(type=TProtocolException.INVALID_DATA,
                               message='Invalid field type %d' % (ttype))
    reader = getattr(self, r_handler)
    if not is_container:
      return reader()
    return reader(spec)

  def readContainerList(self, spec):
    results = []
    ttype, tspec = spec[0], spec[1]
    r_handler = self._TTYPE_HANDLERS[ttype][0]
    reader = getattr(self, r_handler)
    (list_type, list_len) = self.readListBegin()
    if tspec is None:
      # list values are simple types
      for idx in xrange(list_len):
        results.append(reader())
    else:
      # this is like an inlined readFieldByTType
      container_reader = self._TTYPE_HANDLERS[list_type][0]
      val_reader = getattr(self, container_reader)
      for idx in xrange(list_len):
        val = val_reader(tspec)
        results.append(val)
    self.readListEnd()
    return results

  def readContainerSet(self, spec):
    results = set()
    ttype, tspec = spec[0], spec[1]
    r_handler = self._TTYPE_HANDLERS[ttype][0]
    reader = getattr(self, r_handler)
    (set_type, set_len) = self.readSetBegin()
    if tspec is None:
      # set members are simple types
      for idx in xrange(set_len):
        results.add(reader())
    else:
      container_reader = self._TTYPE_HANDLERS[set_type][0]
      val_reader = getattr(self, container_reader)
      for idx in xrange(set_len):
        results.add(val_reader(tspec))
    self.readSetEnd()
    return results

  def readContainerStruct(self, spec):
    (obj_class, obj_spec) = spec
    obj = obj_class()
    obj.read(self)
    return obj

  def readContainerMap(self, spec):
    results = dict()
    key_ttype, key_spec = spec[0], spec[1]
    val_ttype, val_spec = spec[2], spec[3]
    (map_ktype, map_vtype, map_len) = self.readMapBegin()
    # TODO: compare types we just decoded with thrift_spec and
    # abort/skip if types disagree
    key_reader = getattr(self, self._TTYPE_HANDLERS[key_ttype][0])
    val_reader = getattr(self, self._TTYPE_HANDLERS[val_ttype][0])
    # list values are simple types
    for idx in xrange(map_len):
      if key_spec is None:
        k_val = key_reader()
      else:
        k_val = self.readFieldByTType(key_ttype, key_spec)
      if val_spec is None:
        v_val = val_reader()
      else:
        v_val = self.readFieldByTType(val_ttype, val_spec)
      # this raises a TypeError with unhashable keys types
      # i.e. this fails: d=dict(); d[[0,1]] = 2
      results[k_val] = v_val
    self.readMapEnd()
    return results

  def readStruct(self, obj, thrift_spec):
    self.readStructBegin()
    while True:
      (fname, ftype, fid) = self.readFieldBegin()
      if ftype == TType.STOP:
        break
      try:
        field = thrift_spec[fid]
      except IndexError:
        self.skip(ftype)
      else:
        if field is not None and ftype == field[1]:
          fname = field[2]
          fspec = field[3]
          val = self.readFieldByTType(ftype, fspec)
          setattr(obj, fname, val)
        else:
          self.skip(ftype)
      self.readFieldEnd()
    self.readStructEnd()

  def writeContainerStruct(self, val, spec):
    val.write(self)

  def writeContainerList(self, val, spec):
    self.writeListBegin(spec[0], len(val))
    r_handler, w_handler, is_container = self._TTYPE_HANDLERS[spec[0]]
    e_writer = getattr(self, w_handler)
    if not is_container:
      for elem in val:
        e_writer(elem)
    else:
      for elem in val:
        e_writer(elem, spec[1])
    self.writeListEnd()

  def writeContainerSet(self, val, spec):
    self.writeSetBegin(spec[0], len(val))
    r_handler, w_handler, is_container = self._TTYPE_HANDLERS[spec[0]]
    e_writer = getattr(self, w_handler)
    if not is_container:
      for elem in val:
        e_writer(elem)
    else:
      for elem in val:
        e_writer(elem, spec[1])
    self.writeSetEnd()

  def writeContainerMap(self, val, spec):
    k_type = spec[0]
    v_type = spec[2]
    ignore, ktype_name, k_is_container = self._TTYPE_HANDLERS[k_type]
    ignore, vtype_name, v_is_container = self._TTYPE_HANDLERS[v_type]
    k_writer = getattr(self, ktype_name)
    v_writer = getattr(self, vtype_name)
    self.writeMapBegin(k_type, v_type, len(val))
    for m_key, m_val in val.iteritems():
      if not k_is_container:
        k_writer(m_key)
      else:
        k_writer(m_key, spec[1])
      if not v_is_container:
        v_writer(m_val)
      else:
        v_writer(m_val, spec[3])
    self.writeMapEnd()

  def writeStruct(self, obj, thrift_spec):
    self.writeStructBegin(obj.__class__.__name__)
    for field in thrift_spec:
      if field is None:
        continue
      fname = field[2]
      val = getattr(obj, fname)
      if val is None:
        # skip writing out unset fields
        continue
      fid = field[0]
      ftype = field[1]
      fspec = field[3]
      # get the writer method for this value
      self.writeFieldBegin(fname, ftype, fid)
      self.writeFieldByTType(ftype, val, fspec)
      self.writeFieldEnd()
    self.writeFieldStop()
    self.writeStructEnd()

  def writeFieldByTType(self, ttype, val, spec):
    r_handler, w_handler, is_container = self._TTYPE_HANDLERS[ttype]
    writer = getattr(self, w_handler)
    if is_container:
      writer(val, spec)
    else:
      writer(val)

def checkIntegerLimits(i, bits):
    if bits == 8 and (i < -128 or i > 127):
        raise TProtocolException(TProtocolException.INVALID_DATA,
                                 "i8 requires -128 <= number <= 127")
    elif bits == 16 and (i < -32768 or i > 32767):
        raise TProtocolException(TProtocolException.INVALID_DATA,
                                 "i16 requires -32768 <= number <= 32767")
    elif bits == 32 and (i < -2147483648 or i > 2147483647):
        raise TProtocolException(TProtocolException.INVALID_DATA,
                                 "i32 requires -2147483648 <= number <= 2147483647")
    elif bits == 64 and (i < -9223372036854775808 or i > 9223372036854775807):
         raise TProtocolException(TProtocolException.INVALID_DATA,
                                  "i64 requires -9223372036854775808 <= number <= 9223372036854775807")

class TProtocolFactory:
  def getProtocol(self, trans):
    pass
