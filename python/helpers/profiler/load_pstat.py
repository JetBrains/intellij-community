import sys
import pstats

from prof_util import statsToResponse

from _prof_imports import TSerialization
from _prof_imports import TBinaryProtocol
from _prof_imports import ProfilerResponse
from _prof_imports import IS_PY3K


if __name__ == '__main__':

    file_name = sys.argv[1]

    stats = pstats.Stats(file_name)

    m = ProfilerResponse(id=0)

    statsToResponse(stats.stats, m)

    data = TSerialization.serialize(m, TBinaryProtocol.TBinaryProtocolFactory())

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








