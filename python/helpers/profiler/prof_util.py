__author__ = 'traff'

import threading
import os
import tempfile
from _prof_imports import Stats, FuncStat, Function

class ProfDaemonThread(threading.Thread):
    def __init__(self):
        super(ProfDaemonThread, self).__init__()
        self.setDaemon(True)
        self.killReceived = False

    def run(self):
        self.OnRun()

    def OnRun(self):
        pass

def generate_snapshot_filepath(basepath, local_temp_dir=False):
    if basepath is None:
        basepath = 'snapshot'
    if local_temp_dir:
        basepath = os.path.join(tempfile.gettempdir(), os.path.basename(basepath.replace('\\', '/')))

    n = 0
    path = basepath + '.pstat'
    while os.path.exists(path):
        n+=1
        path = basepath + (str(n) if n>0 else '') + '.pstat'

    return path

def statsToResponse(stats, m):
    ystats = Stats()
    ystats.func_stats = []
    m.ystats = ystats

    for func, stat in stats.items():
        path, line, func_name = func
        cc, nc, tt, ct, callers = stat
        func = Function()
        func_stat = FuncStat()
        func.func_stat = func_stat
        ystats.func_stats.append(func)

        func_stat.file = path
        func_stat.line = line
        func_stat.func_name = func_name
        func_stat.calls_count = nc
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

