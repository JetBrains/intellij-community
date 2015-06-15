import sys
import pstats

from prof_util import statsToResponse

from _prof_imports import TSerialization
from _prof_imports import TJSONProtocol
from _prof_imports import ProfilerResponse
from _prof_imports import IS_PY3K


if __name__ == '__main__':

    file_name = sys.argv[1]

    stats = pstats.Stats(file_name)

    m = ProfilerResponse(id=0)

    statsToResponse(stats.stats, m)

    data = TSerialization.serialize(m, TJSONProtocol.TJSONProtocolFactory())

    if IS_PY3K:
        data = data.decode("utf-8")

    sys.stdout.write(data)
    sys.stdout.flush()








