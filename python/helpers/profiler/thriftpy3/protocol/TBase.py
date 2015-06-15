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

from thriftpy3.Thrift import *
from thriftpy3.protocol import TBinaryProtocol
from thriftpy3.transport import TTransport

try:
  from thriftpy3.protocol import fastbinary
except:
  fastbinary = None


class TBase(object):
  __slots__ = []

  def __repr__(self):
    L = ['%s=%r' % (key, getattr(self, key))
              for key in self.__slots__]
    return '%s(%s)' % (self.__class__.__name__, ', '.join(L))

  def __eq__(self, other):
    if not isinstance(other, self.__class__):
      return False
    for attr in self.__slots__:
      my_val = getattr(self, attr)
      other_val = getattr(other, attr)
      if my_val != other_val:
        return False
    return True

  def __ne__(self, other):
    return not (self == other)

  def read(self, iprot):
    if (iprot.__class__ == TBinaryProtocol.TBinaryProtocolAccelerated and
        isinstance(iprot.trans, TTransport.CReadableTransport) and
        self.thrift_spec is not None and
        fastbinary is not None):
      fastbinary.decode_binary(self,
                               iprot.trans,
                               (self.__class__, self.thrift_spec))
      return
    iprot.readStruct(self, self.thrift_spec)

  def write(self, oprot):
    if (oprot.__class__ == TBinaryProtocol.TBinaryProtocolAccelerated and
        self.thrift_spec is not None and
        fastbinary is not None):
      oprot.trans.write(
        fastbinary.encode_binary(self, (self.__class__, self.thrift_spec)))
      return
    oprot.writeStruct(self, self.thrift_spec)


class TExceptionBase(TBase, Exception):
  __slots__ = []
