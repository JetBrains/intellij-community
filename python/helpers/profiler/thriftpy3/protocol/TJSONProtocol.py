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

from TProtocol import TType, TProtocolBase, TProtocolException, \
    checkIntegerLimits
import base64
import json
import math

__all__ = ['TJSONProtocol',
           'TJSONProtocolFactory',
           'TSimpleJSONProtocol',
           'TSimpleJSONProtocolFactory']

VERSION = 1

COMMA = ','
COLON = ':'
LBRACE = '{'
RBRACE = '}'
LBRACKET = '['
RBRACKET = ']'
QUOTE = '"'
BACKSLASH = '\\'
ZERO = '0'

ESCSEQ = '\\u00'
ESCAPE_CHAR = '"\\bfnrt'
ESCAPE_CHAR_VALS = ['"', '\\', '\b', '\f', '\n', '\r', '\t']
NUMERIC_CHAR = '+-.0123456789Ee'

CTYPES = {TType.BOOL:       'tf',
          TType.BYTE:       'i8',
          TType.I16:        'i16',
          TType.I32:        'i32',
          TType.I64:        'i64',
          TType.DOUBLE:     'dbl',
          TType.STRING:     'str',
          TType.STRUCT:     'rec',
          TType.LIST:       'lst',
          TType.SET:        'set',
          TType.MAP:        'map'}

JTYPES = {}
for key in CTYPES.keys():
  JTYPES[CTYPES[key]] = key


class JSONBaseContext(object):

  def __init__(self, protocol):
    self.protocol = protocol
    self.first = True

  def doIO(self, function):
    pass
  
  def write(self):
    pass

  def read(self):
    pass

  def escapeNum(self):
    return False

  def __str__(self):
    return self.__class__.__name__


class JSONListContext(JSONBaseContext):
    
  def doIO(self, function):
    if self.first is True:
      self.first = False
    else:
      function(COMMA)

  def write(self):
    self.doIO(self.protocol.trans.write)

  def read(self):
    self.doIO(self.protocol.readJSONSyntaxChar)


class JSONPairContext(JSONBaseContext):
  
  def __init__(self, protocol):
    super(JSONPairContext, self).__init__(protocol)
    self.colon = True

  def doIO(self, function):
    if self.first:
      self.first = False
      self.colon = True
    else:
      function(COLON if self.colon else COMMA)
      self.colon = not self.colon

  def write(self):
    self.doIO(self.protocol.trans.write)

  def read(self):
    self.doIO(self.protocol.readJSONSyntaxChar)

  def escapeNum(self):
    return self.colon

  def __str__(self):
    return '%s, colon=%s' % (self.__class__.__name__, self.colon)


class LookaheadReader():
  hasData = False
  data = ''

  def __init__(self, protocol):
    self.protocol = protocol

  def read(self):
    if self.hasData is True:
      self.hasData = False
    else:
      self.data = self.protocol.trans.read(1)
    return self.data

  def peek(self):
    if self.hasData is False:
      self.data = self.protocol.trans.read(1)
    self.hasData = True
    return self.data

class TJSONProtocolBase(TProtocolBase):

  def __init__(self, trans):
    TProtocolBase.__init__(self, trans)
    self.resetWriteContext()
    self.resetReadContext()

  def resetWriteContext(self):
    self.context = JSONBaseContext(self)
    self.contextStack = [self.context]

  def resetReadContext(self):
    self.resetWriteContext()
    self.reader = LookaheadReader(self)

  def pushContext(self, ctx):
    self.contextStack.append(ctx)
    self.context = ctx

  def popContext(self):
    self.contextStack.pop()
    if self.contextStack:
      self.context = self.contextStack[-1]
    else:
      self.context = JSONBaseContext(self)

  def writeJSONString(self, string):
    self.context.write()
    self.trans.write(json.dumps(string))

  def writeJSONNumber(self, number):
    self.context.write()
    jsNumber = str(number)
    if self.context.escapeNum():
      jsNumber = "%s%s%s" % (QUOTE, jsNumber,  QUOTE)
    self.trans.write(jsNumber)

  def writeJSONBase64(self, binary):
    self.context.write()
    self.trans.write(QUOTE)
    self.trans.write(base64.b64encode(binary))
    self.trans.write(QUOTE)

  def writeJSONObjectStart(self):
    self.context.write()
    self.trans.write(LBRACE)
    self.pushContext(JSONPairContext(self))

  def writeJSONObjectEnd(self):
    self.popContext()
    self.trans.write(RBRACE)

  def writeJSONArrayStart(self):
    self.context.write()
    self.trans.write(LBRACKET)
    self.pushContext(JSONListContext(self))

  def writeJSONArrayEnd(self):
    self.popContext()
    self.trans.write(RBRACKET)

  def readJSONSyntaxChar(self, character):
    current = self.reader.read()
    if character != current:
      raise TProtocolException(TProtocolException.INVALID_DATA,
                               "Unexpected character: %s" % current)

  def readJSONString(self, skipContext):
    string = []
    if skipContext is False:
      self.context.read()
    self.readJSONSyntaxChar(QUOTE)
    while True:
      character = self.reader.read()
      if character == QUOTE:
        break
      if character == ESCSEQ[0]:
        character = self.reader.read()
        if character == ESCSEQ[1]:
          self.readJSONSyntaxChar(ZERO)
          self.readJSONSyntaxChar(ZERO)
          character = json.JSONDecoder().decode('"\u00%s"' % self.trans.read(2))
        else:
          off = ESCAPE_CHAR.find(character)
          if off == -1:
            raise TProtocolException(TProtocolException.INVALID_DATA,
                                     "Expected control char")
          character = ESCAPE_CHAR_VALS[off]
      string.append(character)
    return ''.join(string)

  def isJSONNumeric(self, character):
    return (True if NUMERIC_CHAR.find(character) != - 1 else False)

  def readJSONQuotes(self):
    if (self.context.escapeNum()):
      self.readJSONSyntaxChar(QUOTE)

  def readJSONNumericChars(self):
    numeric = []
    while True:
      character = self.reader.peek()
      if self.isJSONNumeric(character) is False:
        break
      numeric.append(self.reader.read())
    return ''.join(numeric)

  def readJSONInteger(self):
    self.context.read()
    self.readJSONQuotes()
    numeric = self.readJSONNumericChars()
    self.readJSONQuotes()
    try:
      return int(numeric)
    except ValueError:
      raise TProtocolException(TProtocolException.INVALID_DATA,
                               "Bad data encounted in numeric data")

  def readJSONDouble(self):
    self.context.read()
    if self.reader.peek() == QUOTE:
      string  = self.readJSONString(True)
      try:
        double = float(string)
        if (self.context.escapeNum is False and
            not math.isinf(double) and
            not math.isnan(double)):
          raise TProtocolException(TProtocolException.INVALID_DATA,
                                   "Numeric data unexpectedly quoted")
        return double
      except ValueError:
        raise TProtocolException(TProtocolException.INVALID_DATA,
                                 "Bad data encounted in numeric data")
    else:
      if self.context.escapeNum() is True:
        self.readJSONSyntaxChar(QUOTE)
      try:
        return float(self.readJSONNumericChars())
      except ValueError:
        raise TProtocolException(TProtocolException.INVALID_DATA,
                                 "Bad data encounted in numeric data")

  def readJSONBase64(self):
    string = self.readJSONString(False)
    return base64.b64decode(string)

  def readJSONObjectStart(self):
    self.context.read()
    self.readJSONSyntaxChar(LBRACE)
    self.pushContext(JSONPairContext(self))

  def readJSONObjectEnd(self):
    self.readJSONSyntaxChar(RBRACE)
    self.popContext()

  def readJSONArrayStart(self):
    self.context.read()
    self.readJSONSyntaxChar(LBRACKET)
    self.pushContext(JSONListContext(self))

  def readJSONArrayEnd(self):
    self.readJSONSyntaxChar(RBRACKET)
    self.popContext()


class TJSONProtocol(TJSONProtocolBase):

  def readMessageBegin(self):
    self.resetReadContext()
    self.readJSONArrayStart()
    if self.readJSONInteger() != VERSION:
      raise TProtocolException(TProtocolException.BAD_VERSION,
                               "Message contained bad version.")
    name = self.readJSONString(False)
    typen = self.readJSONInteger()
    seqid = self.readJSONInteger()
    return (name, typen, seqid)

  def readMessageEnd(self):
    self.readJSONArrayEnd()

  def readStructBegin(self):
    self.readJSONObjectStart()

  def readStructEnd(self):
    self.readJSONObjectEnd()

  def readFieldBegin(self):
    character = self.reader.peek()
    ttype = 0
    id = 0
    if character == RBRACE:
      ttype = TType.STOP
    else:
      id = self.readJSONInteger()
      self.readJSONObjectStart()
      ttype = JTYPES[self.readJSONString(False)]
    return (None, ttype, id)

  def readFieldEnd(self):
    self.readJSONObjectEnd()

  def readMapBegin(self):
    self.readJSONArrayStart()
    keyType = JTYPES[self.readJSONString(False)]
    valueType = JTYPES[self.readJSONString(False)]
    size = self.readJSONInteger()
    self.readJSONObjectStart()
    return (keyType, valueType, size)

  def readMapEnd(self):
    self.readJSONObjectEnd()
    self.readJSONArrayEnd()

  def readCollectionBegin(self):
    self.readJSONArrayStart()
    elemType = JTYPES[self.readJSONString(False)]
    size = self.readJSONInteger()
    return (elemType, size)
  readListBegin = readCollectionBegin
  readSetBegin = readCollectionBegin

  def readCollectionEnd(self):
    self.readJSONArrayEnd()
  readSetEnd = readCollectionEnd
  readListEnd = readCollectionEnd

  def readBool(self):
    return (False if self.readJSONInteger() == 0 else True)

  def readNumber(self):
    return self.readJSONInteger()
  readByte = readNumber
  readI16 = readNumber
  readI32 = readNumber
  readI64 = readNumber

  def readDouble(self):
    return self.readJSONDouble()

  def readString(self):
    return self.readJSONString(False)

  def readBinary(self):
    return self.readJSONBase64()

  def writeMessageBegin(self, name, request_type, seqid):
    self.resetWriteContext()
    self.writeJSONArrayStart()
    self.writeJSONNumber(VERSION)
    self.writeJSONString(name)
    self.writeJSONNumber(request_type)
    self.writeJSONNumber(seqid)

  def writeMessageEnd(self):
    self.writeJSONArrayEnd()

  def writeStructBegin(self, name):
    self.writeJSONObjectStart()

  def writeStructEnd(self):
    self.writeJSONObjectEnd()

  def writeFieldBegin(self, name, ttype, id):
    self.writeJSONNumber(id)
    self.writeJSONObjectStart()
    self.writeJSONString(CTYPES[ttype])

  def writeFieldEnd(self):
    self.writeJSONObjectEnd()

  def writeFieldStop(self):
    pass

  def writeMapBegin(self, ktype, vtype, size):
    self.writeJSONArrayStart()
    self.writeJSONString(CTYPES[ktype])
    self.writeJSONString(CTYPES[vtype])
    self.writeJSONNumber(size)
    self.writeJSONObjectStart()

  def writeMapEnd(self):
    self.writeJSONObjectEnd()
    self.writeJSONArrayEnd()
    
  def writeListBegin(self, etype, size):
    self.writeJSONArrayStart()
    self.writeJSONString(CTYPES[etype])
    self.writeJSONNumber(size)
    
  def writeListEnd(self):
    self.writeJSONArrayEnd()

  def writeSetBegin(self, etype, size):
    self.writeJSONArrayStart()
    self.writeJSONString(CTYPES[etype])
    self.writeJSONNumber(size)
    
  def writeSetEnd(self):
    self.writeJSONArrayEnd()

  def writeBool(self, boolean):
    self.writeJSONNumber(1 if boolean is True else 0)

  def writeByte(self, byte):
    checkIntegerLimits(byte, 8)
    self.writeJSONNumber(byte)

  def writeI16(self, i16):
    checkIntegerLimits(i16, 16)
    self.writeJSONNumber(i16)

  def writeI32(self, i32):
    checkIntegerLimits(i32, 32)
    self.writeJSONNumber(i32)

  def writeI64(self, i64):
    checkIntegerLimits(i64, 64)
    self.writeJSONNumber(i64)

  def writeDouble(self, dbl):
    self.writeJSONNumber(dbl)

  def writeString(self, string):
    self.writeJSONString(string)
    
  def writeBinary(self, binary):
    self.writeJSONBase64(binary)


class TJSONProtocolFactory:

  def getProtocol(self, trans):
    return TJSONProtocol(trans)


class TSimpleJSONProtocol(TJSONProtocolBase):
    """Simple, readable, write-only JSON protocol.
    
    Useful for interacting with scripting languages.
    """

    def readMessageBegin(self):
        raise NotImplementedError()
    
    def readMessageEnd(self):
        raise NotImplementedError()
    
    def readStructBegin(self):
        raise NotImplementedError()
    
    def readStructEnd(self):
        raise NotImplementedError()
    
    def writeMessageBegin(self, name, request_type, seqid):
        self.resetWriteContext()
    
    def writeMessageEnd(self):
        pass
    
    def writeStructBegin(self, name):
        self.writeJSONObjectStart()
    
    def writeStructEnd(self):
        self.writeJSONObjectEnd()
      
    def writeFieldBegin(self, name, ttype, fid):
        self.writeJSONString(name)
    
    def writeFieldEnd(self):
        pass
    
    def writeMapBegin(self, ktype, vtype, size):
        self.writeJSONObjectStart()
    
    def writeMapEnd(self):
        self.writeJSONObjectEnd()
    
    def _writeCollectionBegin(self, etype, size):
        self.writeJSONArrayStart()
    
    def _writeCollectionEnd(self):
        self.writeJSONArrayEnd()
    writeListBegin = _writeCollectionBegin
    writeListEnd = _writeCollectionEnd
    writeSetBegin = _writeCollectionBegin
    writeSetEnd = _writeCollectionEnd

    def writeByte(self, byte):
        checkIntegerLimits(byte, 8)
        self.writeJSONNumber(byte)

    def writeI16(self, i16):
        checkIntegerLimits(i16, 16)
        self.writeJSONNumber(i16)

    def writeI32(self, i32):
        checkIntegerLimits(i32, 32)
        self.writeJSONNumber(i32)

    def writeI64(self, i64):
        checkIntegerLimits(i64, 64)
        self.writeJSONNumber(i64)
    
    def writeBool(self, boolean):
        self.writeJSONNumber(1 if boolean is True else 0)

    def writeDouble(self, dbl):
        self.writeJSONNumber(dbl)
    
    def writeString(self, string):
        self.writeJSONString(string)
      
    def writeBinary(self, binary):
        self.writeJSONBase64(binary)


class TSimpleJSONProtocolFactory(object):

    def getProtocol(self, trans):
        return TSimpleJSONProtocol(trans)
