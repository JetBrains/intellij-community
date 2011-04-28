import inspect
from pydevd_comm import CMD_SET_BREAK
from pydevd_constants import DJANGO_SUSPEND, GetThreadId
from pydevd_file_utils import NormFileToServer
from runfiles import DictContains
from pydevd_breakpoints import LineBreakpoint
import traceback


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
    try:
        name = frame.f_code.co_name
        if name != 'render':
            return False

        if not DictContains(frame.f_locals, 'self'):
            return False

        cls = frame.f_locals['self'].__class__

        inherits_node = False
        for base in inspect.getmro(cls):
            if base.__name__ == 'Node':
                inherits_node = True
                break

        if not inherits_node:
            return False

        clsname = cls.__name__
        return clsname != 'TextNode' and clsname != 'NodeList'
    except :
        traceback.print_exc()
        return False

def is_django_suspended(thread):
    return thread.additionalInfo.suspend_type == DJANGO_SUSPEND

def suspend_django(py_db_frame, mainDebugger, thread, frame):
    frame = DjangoTemplateFrame(frame)

    if frame.f_lineno is None:
        return None

    #try:
    #    if thread.additionalInfo.filename == frame.f_code.co_filename and thread.additionalInfo.line == frame.f_lineno:
    #        return None # don't stay twice on the same line
    #except AttributeError:
    #    pass

    mainDebugger.additional_frames.addAdditionalFrameById(GetThreadId(thread), {id(frame): frame})


    py_db_frame.setSuspend(thread, CMD_SET_BREAK)
    thread.additionalInfo.suspend_type = DJANGO_SUSPEND

    thread.additionalInfo.filename = frame.f_code.co_filename
    thread.additionalInfo.line = frame.f_lineno

    return frame





