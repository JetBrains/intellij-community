import pstats
import sys

from _prof_imports import IS_PY3K
from _prof_imports import ProfilerResponse
from _prof_imports import TBinaryProtocolFactory
from _prof_imports import serialize
from prof_util import stats_to_response

if __name__ == '__main__':

    filename = sys.argv[1]

    m = ProfilerResponse(id=0)

    if filename.endswith('.prof'):
        import vmprof_profiler
        vmprof_profiler.tree_stats_to_response(filename, m)
    else:
        stats = pstats.Stats(filename)
        stats_to_response(stats.stats, m)

    data = serialize(m, TBinaryProtocolFactory())

    # setup stdout to write binary data to it
    if IS_PY3K:
        out = sys.stdout.buffer
    elif sys.platform == 'win32':
        import os, msvcrt
        msvcrt.setmode(sys.stdout.fileno(), os.O_BINARY)
        out = sys.stdout
    else:
        out = sys.stdout

    out.write(data)
    out.flush()








