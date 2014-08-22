import inspect
from pydevd_plugins.django_frame import DjangoTemplateFrame, get_template_file_name, get_template_line
from pydevd_comm import CMD_SET_BREAK
from pydevd_constants import DJANGO_SUSPEND, GetThreadId
from pydevd_file_utils import NormFileToServer
from runfiles import DictContains
from pydevd_breakpoints import LineBreakpoint
import pydevd_vars
import traceback

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

    def __str__(self):
        return "DjangoLineBreakpoint: %s-%d" %(self.file, self.line)


def add_line_breakpoint(pydb, type, file, line, condition, expression, func_name):
    if type == 'django-line':
        breakpoint = DjangoLineBreakpoint(type, file, line, True, condition, func_name, expression)
        if not hasattr(pydb, 'django_breakpoints'):
            pydb.django_breakpoints = {}
        breakpoint.add(pydb.django_breakpoints, file, line, func_name)
        return True
    return False

def add_exception_breakpoint(pydb, type, exception):
    if type == 'django':
        if not hasattr(pydb, 'django_exception_break'):
            pydb.django_exception_break = {}
        pydb.django_exception_break[exception] = True
        pydb.setTracingForUntracedContexts()
        return True
    return False


def remove_exception_breakpoint(pydb, type, exception):
    if type == 'django':
        try:
            del pydb.django_exception_break[exception]
            return True
        except:
            pass
    return False

def find_and_remove_line_break(pydb, type, file, line):
    if type == 'django-line':
        del pydb.django_breakpoints[file][line]
        return True
    return False

def remove_line_break(pydb, type, file, line):
    try:
        del pydb.django_breakpoints[file][line]
        return True
    except:
        return False


def inherits(cls, *names):
    if cls.__name__ in names:
        return True
    inherits_node = False
    for base in inspect.getmro(cls):
        if base.__name__ in names:
            inherits_node = True
            break
    return inherits_node


def is_django_render_call(frame):
    try:
        name = frame.f_code.co_name
        if name != 'render':
            return False

        if not DictContains(frame.f_locals, 'self'):
            return False

        cls = frame.f_locals['self'].__class__

        inherits_node = inherits(cls, 'Node')

        if not inherits_node:
            return False

        clsname = cls.__name__
        return clsname != 'TextNode' and clsname != 'NodeList'
    except:
        traceback.print_exc()
        return False


def is_django_context_get_call(frame):
    try:
        if not DictContains(frame.f_locals, 'self'):
            return False

        cls = frame.f_locals['self'].__class__

        return inherits(cls, 'BaseContext')
    except:
        traceback.print_exc()
        return False


def is_django_resolve_call(frame):
    try:
        name = frame.f_code.co_name
        if name != '_resolve_lookup':
            return False

        if not DictContains(frame.f_locals, 'self'):
            return False

        cls = frame.f_locals['self'].__class__

        clsname = cls.__name__
        return clsname == 'Variable'
    except:
        traceback.print_exc()
        return False


def is_django_suspended(thread):
    return thread.additionalInfo.suspend_type == DJANGO_SUSPEND


def suspend_django(py_db_frame, mainDebugger, thread, frame, cmd=CMD_SET_BREAK):
    frame = DjangoTemplateFrame(frame)

    if frame.f_lineno is None:
        return None

    #try:
    #    if thread.additionalInfo.filename == frame.f_code.co_filename and thread.additionalInfo.line == frame.f_lineno:
    #        return None # don't stay twice on the same line
    #except AttributeError:
    #    pass

    pydevd_vars.addAdditionalFrameById(GetThreadId(thread), {id(frame): frame})

    py_db_frame.setSuspend(thread, cmd)
    thread.additionalInfo.suspend_type = DJANGO_SUSPEND

    thread.additionalInfo.filename = frame.f_code.co_filename
    thread.additionalInfo.line = frame.f_lineno

    return frame


def find_django_render_frame(frame):
    while frame is not None and not is_django_render_call(frame):
        frame = frame.f_back

    return frame

def change_variable(mainDebugger, frame, attr, expression):
    if isinstance(frame, DjangoTemplateFrame):
        result = eval(expression, frame.f_globals, frame.f_locals)
        frame.changeVariable(attr, result)





