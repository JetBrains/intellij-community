class Frame:
    def __init__(
            self,
            f_back,
            f_fileno,
            f_code,
            f_locals,
            f_globals={},
            f_trace=None):
        self.f_back = f_back
        self.f_lineno = f_fileno
        self.f_code = f_code
        self.f_locals = f_locals
        self.f_globals = f_globals
        self.f_trace = f_trace


class FCode:
    def __init__(self, name, filename):
        self.co_name = name
        self.co_filename = filename