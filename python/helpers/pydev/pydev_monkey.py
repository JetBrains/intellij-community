import os

def is_python(path):
    if path.endswith("'") or path.endswith('"'):
        path = path[1:len(path)-1]
    filename = os.path.basename(path)
    for name in ['python', 'jython', 'pypy']:
        if filename.find(name) != -1:
            return True

    return False

def patch_args(args):
    import sys
    new_args = []
    i = 0
    if len(args) == 0:
        return args

    if is_python(args[0]):
        new_args.append(args[0])
    else:
        return args

    i = 1
    while i < len(args):
        if args[i].startswith('-'):
            new_args.append(args[i])
        else:
            break
        i+=1

    for x in sys.original_argv:
        if sys.platform == "win32" and not x.endswith('"'):
            arg = '"%s"'%x
        else:
            arg = x
        new_args.append(arg)
        if x == '--file':
            break

    while i < len(args):
        new_args.append(args[i])
        i+=1

    return new_args

def monkey_patch_os(funcname, create_func):
    original_name = 'original_' + funcname
    setattr(os, original_name, getattr(os, funcname))
    setattr(os, funcname, create_func(original_name))

def create_execl(original_name):
    def new_execl(path, *args):
        '''
os.execl(path, arg0, arg1, ...)
os.execle(path, arg0, arg1, ..., env)
os.execlp(file, arg0, arg1, ...)
os.execlpe(file, arg0, arg1, ..., env)
        '''
        import os
        args = patch_args(args)
        return getattr(os, original_name)(path, *args)
    return new_execl

def create_execv(original_name):
    def new_execv(path, args):
        '''
os.execv(path, args)
os.execvp(file, args)
        '''
        import os
        return getattr(os, original_name)(path, patch_args(args))
    return new_execv

def create_execve(original_name):
    """
os.execve(path, args, env)
os.execvpe(file, args, env)
    """
    def new_execve(path, args, env):
        import os
        return getattr(os, original_name)(path, patch_args(args), env)
    return new_execve


def create_spawnl(original_name):
    def new_spawnl(mode, path, *args):
        '''
os.spawnl(mode, path, arg0, arg1, ...)
os.spawnlp(mode, file, arg0, arg1, ...)
        '''
        import os
        args = patch_args(args)
        return getattr(os, original_name)(mode, path, *args)
    return new_spawnl

def create_spawnv(original_name):
    def new_spawnv(mode, path, args):
        '''
os.spawnv(mode, path, args)
os.spawnvp(mode, file, args)
        '''
        import os
        return getattr(os, original_name)(mode, path, patch_args(args))
    return new_spawnv

def create_spawnve(original_name):
    """
os.spawnve(mode, path, args, env)
os.spawnvpe(mode, file, args, env)
    """
    def new_spawnve(mode, path, args, env):
        import os
        return getattr(os, original_name)(mode, path, patch_args(args), env)
    return new_spawnve

def create_fork(original_name):
    def new_fork():
        import os
        import sys
        child_process = getattr(os, original_name)() # fork
        if child_process == 0:
            argv = sys.original_argv[:]
            import pydevd
            setup = pydevd.processCommandLine(argv)


    #        pydevd.debugger.FinishDebuggingSession()
    #        for t in pydevd.threadingEnumerate():
    #            if hasattr(t, 'doKillPydevThread'):
    #                t.killReceived = True
            import pydevd_tracing
            pydevd_tracing.RestoreSysSetTraceFunc()

            pydevd.dispatcher = pydevd.Dispatcher()
            pydevd.dispatcher.connect(setup)

            if pydevd.dispatcher.port is not None:
                port = pydevd.dispatcher.port
                pydevd.connected = False
                pydevd.settrace(setup['client'], port=port, suspend=False, overwrite_prev_trace=True)
        return child_process
    return new_fork

def patch_new_process_functions():
#os.execl(path, arg0, arg1, ...)
#os.execle(path, arg0, arg1, ..., env)
#os.execlp(file, arg0, arg1, ...)
#os.execlpe(file, arg0, arg1, ..., env)
#os.execv(path, args)
#os.execve(path, args, env)
#os.execvp(file, args)
#os.execvpe(file, args, env)
    monkey_patch_os('execl', create_execl)
    monkey_patch_os('execle', create_execl)
    monkey_patch_os('execlp', create_execl)
    monkey_patch_os('execlpe', create_execl)
    monkey_patch_os('execv', create_execv)
    monkey_patch_os('execve', create_execve)
    monkey_patch_os('execvp', create_execv)
    monkey_patch_os('execvpe', create_execve)

#os.spawnl(mode, path, ...)
#os.spawnle(mode, path, ..., env)
#os.spawnlp(mode, file, ...)
#os.spawnlpe(mode, file, ..., env)
#os.spawnv(mode, path, args)
#os.spawnve(mode, path, args, env)
#os.spawnvp(mode, file, args)
#os.spawnvpe(mode, file, args, env)

    monkey_patch_os('spawnl', create_spawnl)
    monkey_patch_os('spawnle', create_spawnl)
    monkey_patch_os('spawnlp', create_spawnl)
    monkey_patch_os('spawnlpe', create_spawnl)
    monkey_patch_os('spawnv', create_spawnv)
    monkey_patch_os('spawnve', create_spawnve)
    monkey_patch_os('spawnvp', create_spawnv)
    monkey_patch_os('spawnvpe', create_spawnve)

    monkey_patch_os('fork', create_fork)
  