import os
import shlex
import sys
import pydev_log
import traceback

helpers = os.path.dirname(__file__)

def is_python(path):
    if path.endswith("'") or path.endswith('"'):
        path = path[1:len(path)-1]
    filename = os.path.basename(path).lower()
    for name in ['python', 'jython', 'pypy']:
        if filename.find(name) != -1:
            return True

    return False

def patch_args(args):
    try:
        pydev_log.debug("Patching args: %s"% str(args))

        import sys
        new_args = []
        i = 0
        if len(args) == 0:
            return args

        if is_python(args[0]):
            try:
                indC = args.index('-c')
            except ValueError:
                indC = -1

            if indC != -1:
                import pydevd
                host, port = pydevd.dispatch()

                if port is not None:
                    args[indC + 1] = "import sys; sys.path.append('%s'); import pydevd; pydevd.settrace(host='%s', port=%s, suspend=False); %s"%(helpers, host, port, args[indC + 1])
                    return args
            else:
                new_args.append(args[0])
        else:
            pydev_log.debug("Process is not python, returning.")
            return args

        i = 1
        while i < len(args):
            if args[i].startswith('-'):
                new_args.append(args[i])
            else:
                break
            i+=1

        if args[i].endswith('pydevd.py'): #no need to add pydevd twice
            return args

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
    except:
        traceback.print_exc()
        return args


def args_to_str(args):
    quoted_args = []
    for x in args:
        if x.startswith('"') and x.endswith('"'):
            quoted_args.append(x)
        else:
            quoted_args.append('"%s"' % x)

    return ' '.join(quoted_args)

def remove_quotes(str):
    if str.startswith('"') and str.endswith('"'):
        return str[1:-1]
    else:
        return str

def str_to_args(str):
    return [remove_quotes(x) for x in shlex.split(str)]

def patch_arg_str_win(arg_str):
    new_arg_str = arg_str.replace('\\', '/')
    args = str_to_args(new_arg_str)
    if not is_python(args[0]):
        return arg_str
    art = args_to_str(patch_args(args))
    return art

def monkey_patch_module(module, funcname, create_func):
    if hasattr(module, funcname):
        original_name = 'original_' + funcname
        if not hasattr(module, original_name):
            setattr(module, original_name, getattr(module, funcname))
            setattr(module, funcname, create_func(original_name))


def monkey_patch_os(funcname, create_func):
    monkey_patch_module(os, funcname, create_func)


def warn_multiproc():
    import pydev_log

    pydev_log.error_once(
        "New process is launching. Breakpoints won't work.\n To debug that process please enable 'Attach to subprocess automatically while debugging' option in the debugger settings.\n")


def create_warn_multiproc(original_name):

    def new_warn_multiproc(*args):
        import os

        warn_multiproc()

        return getattr(os, original_name)(*args)
    return new_warn_multiproc

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

def create_CreateProcess(original_name):
    """
CreateProcess(*args, **kwargs)
    """
    def new_CreateProcess(appName, commandLine, *args):
        try:
            import _subprocess
        except ImportError:
            import _winapi as _subprocess
        return getattr(_subprocess, original_name)(appName, patch_arg_str_win(commandLine), *args)
    return new_CreateProcess

def create_CreateProcessWarnMultiproc(original_name):
    """
CreateProcess(*args, **kwargs)
    """
    def new_CreateProcess(*args):
        try:
            import _subprocess
        except ImportError:
            import _winapi as _subprocess
        warn_multiproc()
        return getattr(_subprocess, original_name)(*args)
    return new_CreateProcess

def create_fork(original_name):
    def new_fork():
        import os
        child_process = getattr(os, original_name)() # fork
        if not child_process:
            import pydevd

            pydevd.settrace_forked()
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

    if sys.platform != 'win32':
        monkey_patch_os('fork', create_fork)
    else:
        #Windows
        try:
            import _subprocess
        except ImportError:
            import _winapi as _subprocess
        monkey_patch_module(_subprocess, 'CreateProcess', create_CreateProcess)


def patch_new_process_functions_with_warning():
    monkey_patch_os('execl', create_warn_multiproc)
    monkey_patch_os('execle', create_warn_multiproc)
    monkey_patch_os('execlp', create_warn_multiproc)
    monkey_patch_os('execlpe', create_warn_multiproc)
    monkey_patch_os('execv', create_warn_multiproc)
    monkey_patch_os('execve', create_warn_multiproc)
    monkey_patch_os('execvp', create_warn_multiproc)
    monkey_patch_os('execvpe', create_warn_multiproc)
    monkey_patch_os('spawnl', create_warn_multiproc)
    monkey_patch_os('spawnle', create_warn_multiproc)
    monkey_patch_os('spawnlp', create_warn_multiproc)
    monkey_patch_os('spawnlpe', create_warn_multiproc)
    monkey_patch_os('spawnv', create_warn_multiproc)
    monkey_patch_os('spawnve', create_warn_multiproc)
    monkey_patch_os('spawnvp', create_warn_multiproc)
    monkey_patch_os('spawnvpe', create_warn_multiproc)

    if sys.platform != 'win32':
        monkey_patch_os('fork', create_warn_multiproc)
    else:
        #Windows
        try:
            import _subprocess
        except ImportError:
            import _winapi as _subprocess
        monkey_patch_module(_subprocess, 'CreateProcess', create_CreateProcessWarnMultiproc)
