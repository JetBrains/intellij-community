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


def just_raised(trace):
    if trace is None:
        return False
    return trace.tb_next is None


def cached_call(obj, func, *args):
    cached_name = '_cached_' + func.__name__
    if not hasattr(obj, cached_name):
        setattr(obj, cached_name, func(*args))

    return getattr(obj, cached_name)


