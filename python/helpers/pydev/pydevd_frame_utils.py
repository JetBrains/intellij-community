class Frame:
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

class FCode:
    def __init__(self, name, filename):
        self.co_name = name
        self.co_filename = filename

def add_exception_to_frame(frame, exception_info):
    frame.f_locals['__exception__'] = exception_info
