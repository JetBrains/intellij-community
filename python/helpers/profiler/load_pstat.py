import os
import pstats
import sys

# `_shaded_thriftpy` is bundled under helpers/third_party/thriftpy and imported (transitively) below.
# The IDE only puts that directory on PYTHONPATH, which a wrapper interpreter (e.g. an OSGeo4W/QGIS
# .bat) can reset, so add it from this file's location before the imports that need it. PY-90847
_thriftpy_dir = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'third_party', 'thriftpy')
if _thriftpy_dir not in sys.path:
    sys.path.insert(0, _thriftpy_dir)

from _prof_imports import IS_PY3K
from _prof_imports import ProfilerResponse
from _prof_imports import TBinaryProtocolFactory
from _prof_imports import serialize
from prof_util import ystats_to_response, stats_to_response

try:
    import yappi
    yappi_installed = True
except ImportError:
    yappi_installed = False


if __name__ == '__main__':

    filename = sys.argv[1]

    m = ProfilerResponse(id=0)

    if filename.endswith('.prof'):
        import vmprof_profiler
        vmprof_profiler.tree_stats_to_response(filename, m)
    else:
        if yappi_installed:
            ystats = yappi.YFuncStats(filename)
            ystats_to_response(ystats, m)
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








