import sys
import os

IS_PY3K = False

try:
    if sys.version_info[0] >= 3:
        IS_PY3K = True
except AttributeError:
    pass  #Not all versions have sys.version_info

import thriftpy
profiler = thriftpy.load(os.path.join(os.path.dirname(os.path.realpath(__file__)), "profiler.thrift"), module_name="profiler_thrift")


from thriftpy.protocol.binary import TBinaryProtocolFactory
from thriftpy.transport import TMemoryBuffer

# noinspection PyUnresolvedReferences
from profiler_thrift import ProfilerRequest, ProfilerResponse, Stats, FuncStat, Function, TreeStats, CallTreeStat

def serialize(thrift_object,
              protocol_factory=TBinaryProtocolFactory()):
    transport = TMemoryBuffer()
    protocol = protocol_factory.get_protocol(transport)
    thrift_object.write(protocol)
    return transport.getvalue()

def deserialize(base,
                buf,
                protocol_factory=TBinaryProtocolFactory()):
    transport = TMemoryBuffer(buf)
    protocol = protocol_factory.get_protocol(transport)
    base.read(protocol)
    return base

