__author__ = 'traff'

import os
import sys
import tempfile
import threading

from _prof_imports import pkgutil, Stats, FuncStat, Function

try:
    execfile=execfile #Not in Py3k
except NameError:
    #We must redefine it in Py3k if it's not already there
    def execfile(file, glob=None, loc=None):
        if glob is None:
            import sys
            glob = sys._getframe().f_back.f_globals
        if loc is None:
            loc = glob
    
        # It seems that the best way is using tokenize.open(): http://code.activestate.com/lists/python-dev/131251/
        import tokenize
        stream = tokenize.open(file)  # @UndefinedVariable
        try:
            contents = stream.read()
        finally:
            stream.close()
    
        #execute the script (note: it's important to compile first to have the filename set in debug mode)
        exec(compile(contents+"\n", file, 'exec'), glob, loc)


def save_main_module(file, module_name):
    sys.modules[module_name] = sys.modules['__main__']
    sys.modules[module_name].__name__ = module_name
    from imp import new_module

    m = new_module('__main__')
    sys.modules['__main__'] = m
    if hasattr(sys.modules[module_name], '__loader__'):
        setattr(m, '__loader__', getattr(sys.modules[module_name], '__loader__'))
    m.__file__ = file

    return m

def get_fullname(mod_name):
    try:
        loader = pkgutil.get_loader(mod_name)
    except:
        return None
    if loader is not None:
        for attr in ("get_filename", "_get_filename"):
            meth = getattr(loader, attr, None)
            if meth is not None:
                return meth(mod_name)
    return None


class ProfDaemonThread(threading.Thread):
    def __init__(self):
        super(ProfDaemonThread, self).__init__()
        self.setDaemon(True)
        self.killReceived = False

    def run(self):
        self.OnRun()

    def OnRun(self):
        pass

def generate_snapshot_filepath(basepath, local_temp_dir=False, extension='.pstat'):
    basepath = get_snapshot_basepath(basepath, local_temp_dir)

    n = 0
    path = basepath + extension
    while os.path.exists(path):
        n+=1
        path = basepath + (str(n) if n>0 else '') + extension

    return path


def get_snapshot_basepath(basepath, local_temp_dir):
    if basepath is None:
        basepath = 'snapshot'
    if local_temp_dir:
        basepath = os.path.join(tempfile.gettempdir(), os.path.basename(basepath.replace('\\', '/')))
    return basepath


def stats_to_response(stats, m):
    if stats is None:
        return
    
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


    # m.validate()



