from pydev.pydevd_file_utils import NormFileToServer
from pydevd_breakpoints import LineBreakpoint


def get_source(frame):
    try:
        return frame.f_locals['self'].source
    except:
        return None

def get_template_file_name(frame):
    try:
        source = get_source(frame)
        return source[0].name
    except:
        return None

def get_template_line(frame):
    source = get_source(frame)
    file_name = get_template_file_name(frame)
    try:
        return offset_to_line_number(read_file(file_name), source[1][0])
    except:
        return None

class DjangoTemplateFrame:
    def __init__(self, frame):
        file_name = get_template_file_name(frame)
        context = frame.f_locals['context']
        self.f_code = FCode('Django Template', file_name)
        self.f_lineno = get_template_line(frame)
        self.f_back = frame
        self.f_globals = {}
        self.f_locals = collect_context(context)
        self.f_trace = None


class FCode:
    def __init__(self, name, filename):
        self.co_name = name
        self.co_filename = filename


def collect_context(context):
    res = {}
    for d in context.dicts:
        for k,v in d.items():
            res[k] = v
    return res

def read_file(filename):
    f = open(filename, "r")
    s =  f.read()
    f.close()
    return s

def offset_to_line_number(text, offset):
    curLine = 1
    curOffset = 0
    while curOffset < offset:
        if curOffset == len(text):
            return -1
        c = text[curOffset]
        if c == '\n':
            curLine += 1
        elif c == '\r':
            curLine += 1
            if curOffset < len(text) and text[curOffset + 1] == '\n':
                curOffset += 1

        curOffset += 1

    return curLine

class DjangoLineBreakpoint(LineBreakpoint):
    def __init__(self, type, file, line, flag, condition, func_name, expression):
        self.file = file
        self.line = line
        LineBreakpoint.__init__(self, type, flag, condition, func_name, expression)

    def __eq__(self, other):
        if not isinstance(other, DjangoLineBreakpoint):
            return False
        return self.file == other.file and self.line == other.line

    def is_triggered(self, frame):
        file = get_template_file_name(frame)
        line = get_template_line(frame)
        return self.file == file and self.line == line

def is_django_render_call(frame):
    name = frame.f_code.co_name
    #is_rendering_node = isinstance(frame.f_locals['self'], )
    return name == 'render'




