from _pydevd_bundle.pydevd_constants import IS_PY3K

class Frame(object):
    def __init__(
            self,
            f_back,
            f_fileno,
            f_code,
            f_locals,
            f_globals=None,
            f_trace=None):
        self.f_back = f_back
        self.f_lineno = f_fileno
        self.f_code = f_code
        self.f_locals = f_locals
        self.f_globals = f_globals
        self.f_trace = f_trace

        if self.f_globals is None:
            self.f_globals = {}


class FCode(object):
    def __init__(self, name, filename):
        self.co_name = name
        self.co_filename = filename


def add_exception_to_frame(frame, exception_info):
    frame.f_locals['__exception__'] = exception_info

FILES_WITH_IMPORT_HOOKS = ['pydev_monkey_qt.py', 'pydev_import_hook.py']

def just_raised(trace):
    if trace is None:
        return False
    if trace.tb_next is None:
        if IS_PY3K:
            if trace.tb_frame.f_code.co_filename != '<frozen importlib._bootstrap>':
                # Do not stop on inner exceptions in py3 while importing
                return True
        else:
            return True
    if trace.tb_next is not None:
        filename = trace.tb_next.tb_frame.f_code.co_filename
        # ImportError should appear in a user's code, not inside debugger
        for file in FILES_WITH_IMPORT_HOOKS:
            if filename.endswith(file):
                return True
    return False

def cached_call(obj, func, *args):
    cached_name = '_cached_' + func.__name__
    if not hasattr(obj, cached_name):
        setattr(obj, cached_name, func(*args))

    return getattr(obj, cached_name)


