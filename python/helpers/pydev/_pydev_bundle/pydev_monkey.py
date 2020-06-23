# License: EPL
import os
import sys
import traceback
from _pydev_imps._pydev_saved_modules import threading
from _pydevd_bundle.pydevd_constants import get_global_debugger, IS_WINDOWS, IS_MACOS, IS_JYTHON, IS_PY36_OR_LESSER, IS_PY38_OR_GREATER, \
    get_current_thread_id
from _pydev_bundle import pydev_log

try:
    xrange
except:
    xrange = range


PYTHON_NAMES = ['python', 'jython', 'pypy']

#===============================================================================
# Things that are dependent on having the pydevd debugger
#===============================================================================
def log_debug(msg):
    pydev_log.debug(msg)


def log_error_once(msg):
    pydev_log.error_once(msg)


pydev_src_dir = os.path.dirname(os.path.dirname(__file__))


def _get_python_c_args(host, port, indC, args, setup):
    host_literal = "'" + host + "'" if host is not None else 'None'
    return ("import sys; sys.path.append(r'%s'); import pydevd; "
            "pydevd.settrace(host=%s, port=%s, suspend=False, trace_only_current_thread=False, patch_multiprocessing=True); "
            "from pydevd import SetupHolder; SetupHolder.setup = %s; %s"
            ) % (
               pydev_src_dir,
               host_literal,
               port,
               setup,
               args[indC + 1])


def _get_host_port():
    import pydevd
    host, port = pydevd.dispatch()
    return host, port


def _is_managed_arg(arg):
    return arg.endswith('pydevd.py')


def _is_already_patched(args):
    for arg in args:
        if 'pydevd' in arg:
            return True
    return False


def _is_py3_and_has_bytes_args(args):
    if not isinstance('', type(u'')):
        return False
    for arg in args:
        if isinstance(arg, bytes):
            return True
    return False


def _on_forked_process():
    import pydevd
    pydevd.threadingCurrentThread().__pydevd_main_thread = True
    pydevd.settrace_forked()


def _on_set_trace_for_new_thread(global_debugger):
    if global_debugger is not None:
        global_debugger.enable_tracing()


#===============================================================================
# Things related to monkey-patching
#===============================================================================
def is_python_args(args):
    return not _is_py3_and_has_bytes_args(args) and len(args) > 0 and is_python(args[0])


def is_executable(path):
    return os.access(os.path.abspath(path), os.EX_OK)


def starts_with_python_shebang(path):
    try:
        with open(path) as f:
            for line in f:
                line = line.strip()
                if line:
                    for name in PYTHON_NAMES:
                        if line.startswith('#!/usr/bin/env %s' % name):
                            return True
                    return False
    except UnicodeDecodeError:
        return False
    except:
        traceback.print_exc()
        return False


def is_python(path):
    if path.endswith("'") or path.endswith('"'):
        path = path[1:len(path) - 1]
    filename = os.path.basename(path).lower()
    for name in PYTHON_NAMES:
        if filename.find(name) != -1:
            return True
    return not IS_WINDOWS and is_executable(path) and starts_with_python_shebang(path)


def remove_quotes_from_args(args):
    if sys.platform == "win32":
        new_args = []
        for x in args:
            if len(x) > 1 and x.startswith('"') and x.endswith('"'):
                x = x[1:-1]
            new_args.append(x)
        return new_args
    else:
        return args


def quote_args(args):
    if sys.platform == "win32":
        quoted_args = []
        for x in args:
            if x.startswith('"') and x.endswith('"'):
                quoted_args.append(x)
            else:
                if ' ' in x:
                    x = x.replace('"', '\\"')
                    quoted_args.append('"%s"' % x)
                else:
                    quoted_args.append(x)
        return quoted_args
    else:
        return args


def get_c_option_index(args):
    """
    Get index of "-c" argument and check if it's interpreter's option
    :param args: list of arguments
    :return: index of "-c" if it's an interpreter's option and -1 if it doesn't exist or program's option
    """
    try:
        ind_c = args.index('-c')
    except ValueError:
        return -1
    else:
        for i in range(1, ind_c):
            if not args[i].startswith('-'):
                # there is an arg without "-" before "-c", so it's not an interpreter's option
                return -1
        return ind_c


def patch_args(args):
    try:
        log_debug("Patching args: %s"% str(args))

        if _is_py3_and_has_bytes_args(args):
            warn_bytes_args()
            return args

        args = remove_quotes_from_args(args)

        from pydevd import SetupHolder
        new_args = []
        if len(args) == 0:
            return args

        if is_python(args[0]):

            for name in PYTHON_NAMES:
                if args[0].find(name) != -1:
                    break
            else:
                # Executable file with Python shebang.
                args.insert(0, sys.executable)

            ind_c = get_c_option_index(args)

            if ind_c != -1:
                if _is_already_patched(args):
                    return args

                host, port = _get_host_port()

                if port is not None:
                    new_args.extend(args)
                    new_args[ind_c + 1] = _get_python_c_args(host, port, ind_c, args, SetupHolder.setup)
                    return quote_args(new_args)
            else:
                # Check for Python ZIP Applications and don't patch the args for them.
                # Assumes the first non `-<flag>` argument is what we need to check.
                # There's probably a better way to determine this but it works for most cases.
                continue_next = False
                for i in range(1, len(args)):
                    if continue_next:
                        continue_next = False
                        continue

                    arg = args[i]
                    if arg.startswith('-'):
                        # Skip the next arg too if this flag expects a value.
                        continue_next = arg in ['-m', '-W', '-X']
                        continue

                    if arg.rsplit('.')[-1] in ['zip', 'pyz', 'pyzw']:
                        log_debug('Executing a PyZip, returning')
                        return args
                    break

                new_args.append(args[0])
        else:
            log_debug("Process is not python, returning.")
            return args

        i = 1
        # Original args should be something as:
        # ['X:\\pysrc\\pydevd.py', '--multiprocess', '--print-in-debugger-startup',
        #  '--vm_type', 'python', '--client', '127.0.0.1', '--port', '56352', '--file', 'x:\\snippet1.py']
        from _pydevd_bundle.pydevd_command_line_handling import setup_to_argv
        SetupHolder.setup['module'] = False  # clean module param from parent process
        original = setup_to_argv(SetupHolder.setup) + ['--file']
        while i < len(args):
            if args[i] == '-m':
                # Always insert at pos == 1 (i.e.: pydevd "--module" --multiprocess ...)
                original.insert(1, '--module')
            else:
                if args[i] == '-':
                    # this is the marker that input is going to be from stdin for Python
                    # for now we just disable the debugging here, don't crash but is not supported
                    return args
                elif args[i].startswith('-'):
                    new_args.append(args[i])
                else:
                    break
            i += 1

        # Note: undoing https://github.com/Elizaveta239/PyDev.Debugger/commit/053c9d6b1b455530bca267e7419a9f63bf51cddf
        # (i >= len(args) instead of i < len(args))
        # in practice it'd raise an exception here and would return original args, which is not what we want... providing
        # a proper fix for https://youtrack.jetbrains.com/issue/PY-9767 elsewhere.
        if i >= len(args) or _is_managed_arg(args[i]):  # no need to add pydevd twice
            return args

        for x in original:
            new_args.append(x)
            if x == '--file':
                break

        while i < len(args):
            new_args.append(args[i])
            i += 1

        return quote_args(new_args)
    except:
        traceback.print_exc()
        return args


def str_to_args_windows(args):
    # see http:#msdn.microsoft.com/en-us/library/a1y7w461.aspx
    result = []

    DEFAULT = 0
    ARG = 1
    IN_DOUBLE_QUOTE = 2

    state = DEFAULT
    backslashes = 0
    buf = ''

    args_len = len(args)
    for i in xrange(args_len):
        ch = args[i]
        if (ch == '\\'):
            backslashes += 1
            continue
        elif (backslashes != 0):
            if ch == '"':
                while backslashes >= 2:
                    backslashes -= 2
                    buf += '\\'
                if (backslashes == 1):
                    if (state == DEFAULT):
                        state = ARG

                    buf += '"'
                    backslashes = 0
                    continue
                    # else fall through to switch
            else:
                # false alarm, treat passed backslashes literally...
                if (state == DEFAULT):
                    state = ARG

                while backslashes > 0:
                    backslashes -= 1
                    buf += '\\'
                    # fall through to switch
        if ch in (' ', '\t'):
            if (state == DEFAULT):
                # skip
                continue
            elif (state == ARG):
                state = DEFAULT
                result.append(buf)
                buf = ''
                continue

        if state in (DEFAULT, ARG):
            if ch == '"':
                state = IN_DOUBLE_QUOTE
            else:
                state = ARG
                buf += ch

        elif state == IN_DOUBLE_QUOTE:
            if ch == '"':
                if (i + 1 < args_len and args[i + 1] == '"'):
                    # Undocumented feature in Windows:
                    # Two consecutive double quotes inside a double-quoted argument are interpreted as
                    # a single double quote.
                    buf += '"'
                    i += 1
                elif len(buf) == 0:
                    # empty string on Windows platform. Account for bug in constructor of
                    # JDK's java.lang.ProcessImpl.
                    result.append("\"\"")
                    state = DEFAULT
                else:
                    state = ARG
            else:
                buf += ch

        else:
            raise RuntimeError('Illegal condition')

    if len(buf) > 0 or state != DEFAULT:
        result.append(buf)

    return result


def patch_arg_str_win(arg_str):
    args = str_to_args_windows(arg_str)
    # Fix https://youtrack.jetbrains.com/issue/PY-9767 (args may be empty)
    if not args or not is_python(args[0]):
        return arg_str
    arg_str = ' '.join(patch_args(args))
    log_debug("New args: %s" % arg_str)
    return arg_str


def patch_fork_exec_executable_list(args, other_args):
    # When calling a Python executable script with `subprocess.call` the latest uses the first argument as an executable for `fork_exec`.
    # This leads to `subprocess.call(["foo.py", "bar", "baz"])` after patching the args will be transformed into something like
    # foo.py pydevd.py --port 59043 --client 127.0.0.1 --multiproc --file foo.py bar baz.
    # To fix the issue we need to look inside the `fork_exec` executable list and, if necessary, replace an executable script with Python.
    i = 0
    for arg in args:
        i += 1
        if arg == '--file':
            break
    else:
        return other_args
    executable_list = other_args[0]
    if args[i].encode() in executable_list:
        return ((sys.executable.encode(),),) + other_args[1:]
    return other_args


def monkey_patch_module(module, funcname, create_func):
    if hasattr(module, funcname):
        original_name = 'original_' + funcname
        if not hasattr(module, original_name):
            setattr(module, original_name, getattr(module, funcname))
            setattr(module, funcname, create_func(original_name))


def monkey_patch_os(funcname, create_func):
    monkey_patch_module(os, funcname, create_func)


def warn_multiproc():
    log_error_once(
            "pydev debugger: New process is launching (breakpoints won't work in the new process).\n"
            "pydev debugger: To debug that process please enable 'Attach to subprocess automatically while debugging?' option in the debugger settings.\n")


def warn_bytes_args():
    log_error_once(
            "pydev debugger: bytes arguments were passed to a new process creation function. Breakpoints may not work correctly.\n")


def create_warn_multiproc(original_name):

    def new_warn_multiproc(*args):
        import os

        warn_multiproc()

        return getattr(os, original_name)(*args)
    return new_warn_multiproc


def create_execl(original_name):
    def new_execl(path, *args):
        """
        os.execl(path, arg0, arg1, ...)
        os.execle(path, arg0, arg1, ..., env)
        os.execlp(file, arg0, arg1, ...)
        os.execlpe(file, arg0, arg1, ..., env)
        """
        import os
        args = patch_args(args)
        if is_python_args(args):
            path = args[0]
            send_process_will_be_substituted()
        return getattr(os, original_name)(path, *args)
    return new_execl


def create_execv(original_name):
    def new_execv(path, args):
        """
        os.execv(path, args)
        os.execvp(file, args)
        """
        import os
        args = patch_args(args)
        if is_python_args(args):
            path = args[0]
            send_process_will_be_substituted()
        return getattr(os, original_name)(path, args)
    return new_execv


def create_execve(original_name):
    """
    os.execve(path, args, env)
    os.execvpe(file, args, env)
    """
    def new_execve(path, args, env):
        import os
        args = patch_args(args)
        if is_python_args(args):
            path = args[0]
            send_process_will_be_substituted()
        return getattr(os, original_name)(path, args, env)
    return new_execve


def create_spawnl(original_name):
    def new_spawnl(mode, path, *args):
        """
        os.spawnl(mode, path, arg0, arg1, ...)
        os.spawnlp(mode, file, arg0, arg1, ...)
        """
        import os
        args = patch_args(args)
        send_process_created_message()
        return getattr(os, original_name)(mode, path, *args)
    return new_spawnl


def create_spawnv(original_name):
    def new_spawnv(mode, path, args):
        """
        os.spawnv(mode, path, args)
        os.spawnvp(mode, file, args)
        """
        import os
        args = patch_args(args)
        send_process_created_message()
        return getattr(os, original_name)(mode, path, args)
    return new_spawnv


def create_spawnve(original_name):
    """
    os.spawnve(mode, path, args, env)
    os.spawnvpe(mode, file, args, env)
    """
    def new_spawnve(mode, path, args, env):
        import os
        args = patch_args(args)
        send_process_created_message()
        return getattr(os, original_name)(mode, path, args, env)
    return new_spawnve


def create_posix_spawn(original_name):
    """
    os.posix_spawn(path, argv, env, *, file_actions=None, ... (6 more))
    os.posix_spawnp(path, argv, env, *, file_actions=None, ... (6 more))
    """
    def new_posix_spawn(path, argv, env, **kwargs):
        import os
        argv = patch_args(argv)
        send_process_created_message()
        return getattr(os, original_name)(path, argv, env, **kwargs)
    return new_posix_spawn


def create_fork_exec(original_name):
    """
    _posixsubprocess.fork_exec(args, executable_list, close_fds, ... (13 more))
    """
    def new_fork_exec(args, *other_args):
        import _posixsubprocess  # @UnresolvedImport
        args = patch_args(args)
        send_process_created_message()
        return getattr(_posixsubprocess, original_name)(args, *patch_fork_exec_executable_list(args, other_args))
    return new_fork_exec


def create_warn_fork_exec(original_name):
    """
    _posixsubprocess.fork_exec(args, executable_list, close_fds, ... (13 more))
    """
    def new_warn_fork_exec(*args):
        try:
            import _posixsubprocess
            warn_multiproc()
            return getattr(_posixsubprocess, original_name)(*args)
        except:
            pass
    return new_warn_fork_exec


def create_CreateProcess(original_name):
    """
    CreateProcess(*args, **kwargs)
    """
    def new_CreateProcess(app_name, cmd_line, *args):
        try:
            import _subprocess
        except ImportError:
            import _winapi as _subprocess
        send_process_created_message()
        return getattr(_subprocess, original_name)(app_name, patch_arg_str_win(cmd_line), *args)
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


def apply_foundation_framework_hack():
    # Hack in order to prevent the crash on macOS - load the Foundation framework before any forking in the debugger.
    # See: https://bugs.python.org/issue35219.
    import ctypes
    try:
        ctypes.cdll.LoadLibrary('/System/Library/Frameworks/Foundation.framework/Foundation')
    except OSError:
        log_error_once('Failed to load the Foundation framework shared library. Debugging of code that uses `os.fork()` may not work.\n'
                       'Consider setting the `OBJC_DISABLE_INITIALIZE_FORK_SAFETY=YES` environment variable.')
    else:
        log_debug('Successfully loaded the Foundation framework shared library.')


def create_fork(original_name):
    def new_fork():
        import os

        # A simple fork will result in a new python process
        is_new_python_process = True
        frame = sys._getframe()

        while frame is not None:
            if frame.f_code.co_name == '_execute_child' and 'subprocess' in frame.f_code.co_filename:
                # If we're actually in subprocess.Popen creating a child, it may
                # result in something which is not a Python process, (so, we
                # don't want to connect with it in the forked version).
                executable = frame.f_locals.get('executable')
                if executable is not None:
                    is_new_python_process = False
                    if is_python(executable):
                        is_new_python_process = True
                break

            frame = frame.f_back
        frame = None  # Just make sure we don't hold on to it.

        if IS_MACOS and IS_PY36_OR_LESSER:
            is_fork_safety_disabled = os.environ.get('OBJC_DISABLE_INITIALIZE_FORK_SAFETY') == 'YES'
            if not is_fork_safety_disabled:
                apply_foundation_framework_hack()

        child_process = getattr(os, original_name)()  # fork
        if not child_process:
            if is_new_python_process:
                _on_forked_process()
        else:
            if is_new_python_process:
                send_process_created_message()
        return child_process
    return new_fork


def send_process_created_message():
    debugger = get_global_debugger()
    if debugger is not None:
        debugger.send_process_created_message()


def send_process_will_be_substituted():
    """Sends a message that a new process is going to be created.
    When `PyDB` works in server mode this method also waits for the
    response from IDE to be sure that IDE received this message.
    """
    from _pydevd_bundle.pydevd_comm import get_global_debugger
    debugger = get_global_debugger()
    if debugger is not None:
        debugger.send_process_will_be_substituted()


def patch_new_process_functions():
    # os.execl(path, arg0, arg1, ...)
    # os.execle(path, arg0, arg1, ..., env)
    # os.execlp(file, arg0, arg1, ...)
    # os.execlpe(file, arg0, arg1, ..., env)
    # os.execv(path, args)
    # os.execve(path, args, env)
    # os.execvp(file, args)
    # os.execvpe(file, args, env)
    monkey_patch_os('execl', create_execl)
    monkey_patch_os('execle', create_execl)
    monkey_patch_os('execlp', create_execl)
    monkey_patch_os('execlpe', create_execl)
    monkey_patch_os('execv', create_execv)
    monkey_patch_os('execve', create_execve)
    monkey_patch_os('execvp', create_execv)
    monkey_patch_os('execvpe', create_execve)

    # os.spawnl(mode, path, ...)
    # os.spawnle(mode, path, ..., env)
    # os.spawnlp(mode, file, ...)
    # os.spawnlpe(mode, file, ..., env)
    # os.spawnv(mode, path, args)
    # os.spawnve(mode, path, args, env)
    # os.spawnvp(mode, file, args)
    # os.spawnvpe(mode, file, args, env)

    monkey_patch_os('spawnl', create_spawnl)
    monkey_patch_os('spawnle', create_spawnl)
    monkey_patch_os('spawnlp', create_spawnl)
    monkey_patch_os('spawnlpe', create_spawnl)
    monkey_patch_os('spawnv', create_spawnv)
    monkey_patch_os('spawnve', create_spawnve)
    monkey_patch_os('spawnvp', create_spawnv)
    monkey_patch_os('spawnvpe', create_spawnve)

    if IS_PY38_OR_GREATER and not IS_WINDOWS:
        monkey_patch_os('posix_spawn', create_posix_spawn)
        monkey_patch_os('posix_spawnp', create_posix_spawn)

    if not IS_JYTHON:
        if not IS_WINDOWS:
            monkey_patch_os('fork', create_fork)
            try:
                import _posixsubprocess
                monkey_patch_module(_posixsubprocess, 'fork_exec', create_fork_exec)
            except ImportError:
                pass
        else:
            # Windows
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

    if IS_PY38_OR_GREATER and not IS_WINDOWS:
        monkey_patch_os('posix_spawn', create_warn_multiproc)
        monkey_patch_os('posix_spawnp', create_warn_multiproc)

    if not IS_JYTHON:
        if not IS_WINDOWS:
            monkey_patch_os('fork', create_warn_multiproc)
            try:
                import _posixsubprocess
                monkey_patch_module(_posixsubprocess, 'fork_exec', create_warn_fork_exec)
            except ImportError:
                pass
        else:
            # Windows
            try:
                import _subprocess
            except ImportError:
                import _winapi as _subprocess
            monkey_patch_module(_subprocess, 'CreateProcess', create_CreateProcessWarnMultiproc)


class _NewThreadStartupWithTrace:

    def __init__(self, original_func, args, kwargs):
        self.original_func = original_func
        self.args = args
        self.kwargs = kwargs

    def __call__(self):
        # We monkey-patch the thread creation so that this function is called in the new thread. At this point
        # we notify of its creation and start tracing it.
        global_debugger = get_global_debugger()

        thread_id = None
        if global_debugger is not None:
            # Note: if this is a thread from threading.py, we're too early in the boostrap process (because we mocked
            # the start_new_thread internal machinery and thread._bootstrap has not finished), so, the code below needs
            # to make sure that we use the current thread bound to the original function and not use
            # threading.currentThread() unless we're sure it's a dummy thread.
            t = getattr(self.original_func, '__self__', getattr(self.original_func, 'im_self', None))
            if not isinstance(t, threading.Thread):
                # This is not a threading.Thread but a Dummy thread (so, get it as a dummy thread using
                # currentThread).
                t = threading.currentThread()

            if not getattr(t, 'is_pydev_daemon_thread', False):
                thread_id = get_current_thread_id(t)
                global_debugger.notify_thread_created(thread_id, t)
                _on_set_trace_for_new_thread(global_debugger)
            
                if getattr(global_debugger, 'thread_analyser', None) is not None:
                    try:
                        from pydevd_concurrency_analyser.pydevd_concurrency_logger import log_new_thread
                        log_new_thread(global_debugger, t)
                    except:
                        sys.stderr.write("Failed to detect new thread for visualization")
        try:
            ret = self.original_func(*self.args, **self.kwargs)
        finally:
            if thread_id is not None:
                global_debugger.notify_thread_not_alive(thread_id)
        
        return ret


class _NewThreadStartupWithoutTrace:

    def __init__(self, original_func, args, kwargs):
        self.original_func = original_func
        self.args = args
        self.kwargs = kwargs

    def __call__(self):
        return self.original_func(*self.args, **self.kwargs)

_UseNewThreadStartup = _NewThreadStartupWithTrace


def _get_threading_modules_to_patch():
    threading_modules_to_patch = []

    try:
        import thread as _thread
    except:
        import _thread
    threading_modules_to_patch.append(_thread)
    threading_modules_to_patch.append(threading)

    return threading_modules_to_patch

threading_modules_to_patch = _get_threading_modules_to_patch()


def patch_thread_module(thread_module):

    if getattr(thread_module, '_original_start_new_thread', None) is None:
        if thread_module is threading:
            if not hasattr(thread_module, '_start_new_thread'):
                return  # Jython doesn't have it.
            _original_start_new_thread = thread_module._original_start_new_thread = thread_module._start_new_thread
        else:
            _original_start_new_thread = thread_module._original_start_new_thread = thread_module.start_new_thread
    else:
        _original_start_new_thread = thread_module._original_start_new_thread

    class ClassWithPydevStartNewThread:

        def pydev_start_new_thread(self, function, args=(), kwargs={}):
            '''
            We need to replace the original thread_module.start_new_thread with this function so that threads started
            through it and not through the threading module are properly traced.
            '''
            return _original_start_new_thread(_UseNewThreadStartup(function, args, kwargs), ())

    # This is a hack for the situation where the thread_module.start_new_thread is declared inside a class, such as the one below
    # class F(object):
    #    start_new_thread = thread_module.start_new_thread
    #
    #    def start_it(self):
    #        self.start_new_thread(self.function, args, kwargs)
    # So, if it's an already bound method, calling self.start_new_thread won't really receive a different 'self' -- it
    # does work in the default case because in builtins self isn't passed either.
    pydev_start_new_thread = ClassWithPydevStartNewThread().pydev_start_new_thread

    try:
        # We need to replace the original thread_module.start_new_thread with this function so that threads started through
        # it and not through the threading module are properly traced.
        if thread_module is threading:
            thread_module._start_new_thread = pydev_start_new_thread
        else:
            thread_module.start_new_thread = pydev_start_new_thread
            thread_module.start_new = pydev_start_new_thread
    except:
        pass


def patch_thread_modules():
    for t in threading_modules_to_patch:
        patch_thread_module(t)


def undo_patch_thread_modules():
    for t in threading_modules_to_patch:
        try:
            t.start_new_thread = t._original_start_new_thread
        except:
            pass

        try:
            t.start_new = t._original_start_new_thread
        except:
            pass

        try:
            t._start_new_thread = t._original_start_new_thread
        except:
            pass


def disable_trace_thread_modules():
    '''
    Can be used to temporarily stop tracing threads created with thread.start_new_thread.
    '''
    global _UseNewThreadStartup
    _UseNewThreadStartup = _NewThreadStartupWithoutTrace


def enable_trace_thread_modules():
    '''
    Can be used to start tracing threads created with thread.start_new_thread again.
    '''
    global _UseNewThreadStartup
    _UseNewThreadStartup = _NewThreadStartupWithTrace


def get_original_start_new_thread(threading_module):
    try:
        return threading_module._original_start_new_thread
    except:
        return threading_module.start_new_thread
