import inspect
from django_frame import DjangoTemplateFrame
from pydevd_comm import CMD_SET_BREAK
from pydevd_constants import DJANGO_SUSPEND, GetThreadId, DictContains
from pydevd_breakpoints import LineBreakpoint
import pydevd_vars
import traceback

class DjangoLineBreakpoint(LineBreakpoint):

    def __init__(self, file, line, condition, func_name, expression):
        self.file = file
        LineBreakpoint.__init__(self, line, condition, func_name, expression)

    def is_triggered(self, template_frame_file, template_frame_line):
        return self.file == template_frame_file and self.line == template_frame_line

    def __str__(self):
        return "DjangoLineBreakpoint: %s-%d" %(self.file, self.line)


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





