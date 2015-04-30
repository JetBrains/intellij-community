import sys
import pstats


from _prof_imports import TSerialization
from _prof_imports import TJSONProtocol
from _prof_imports import ProfilerResponse, Stats, FuncStat, Function


if __name__ == '__main__':

    file_name = sys.argv[1]

    stats = pstats.Stats(file_name)

    m = ProfilerResponse()
    m.id = 0
    ystats = Stats()
    ystats.func_stats = []
    m.ystats = ystats

    for func, stat in stats.stats.items():
        path, line, func_name = func
        cc, nc, tt, ct, callers = stat
        func = Function()
        func_stat = FuncStat()
        func.func_stat = func_stat
        ystats.func_stats.append(func)

        func_stat.file = path
        func_stat.line = line
        func_stat.func_name = func_name
        func_stat.calls_count = cc
        func_stat.total_time = ct
        func_stat.own_time = tt

        func.callers = []
        for f, s in callers.items():
            caller_stat = FuncStat()
            func.callers.append(caller_stat)
            path, line, func_name = f
            cc, nc, tt, ct = s
            caller_stat.file = path
            caller_stat.line = line
            caller_stat.func_name = func_name
            caller_stat.calls_count = cc
            caller_stat.total_time = ct
            caller_stat.own_time = tt


    m.validate()

    data = TSerialization.serialize(m, TJSONProtocol.TJSONProtocolFactory())

    sys.stdout.write(data)
    sys.stdout.flush()








