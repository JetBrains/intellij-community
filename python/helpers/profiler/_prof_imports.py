import sys

IS_PY3K = False

try:
    if sys.version_info[0] >= 3:
        IS_PY3K = True
except AttributeError:
    pass  #Not all versions have sys.version_info


if IS_PY3K:
    # noinspection PyUnresolvedReferences
    from thriftpy3 import TSerialization
    # noinspection PyUnresolvedReferences
    from thriftpy3.protocol import TBinaryProtocol
    # noinspection PyUnresolvedReferences
    from profilerpy3.ttypes import ProfilerRequest, ProfilerResponse, Stats, FuncStat, Function
else:
    # noinspection PyUnresolvedReferences
    from thrift import TSerialization
    # noinspection PyUnresolvedReferences
    from thrift.protocol import TBinaryProtocol
    # noinspection PyUnresolvedReferences
    from profiler.ttypes import ProfilerRequest, ProfilerResponse, Stats, FuncStat, Function

