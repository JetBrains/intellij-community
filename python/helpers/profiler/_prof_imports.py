import sys

IS_PY3K = False

try:
    if sys.version_info[0] >= 3:
        IS_PY3K = True
except AttributeError:
    pass  #Not all versions have sys.version_info


if IS_PY3K:
    from thriftpy3 import TSerialization
    from thriftpy3.protocol import TJSONProtocol, TBinaryProtocol
    from profilerpy3.ttypes import ProfilerRequest
else:
    from thrift import TSerialization
    from thrift.protocol import TJSONProtocol, TBinaryProtocol
    from profiler.ttypes import ProfilerRequest

