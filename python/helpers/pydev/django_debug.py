import inspect
from django_frame import DjangoTemplateFrame, get_template_file_name, get_template_line
from pydevd_comm import CMD_SET_BREAK
from pydevd_constants import DJANGO_SUSPEND, GetThreadId
from pydevd_file_utils import NormFileToServer
from runfiles import DictContains
from pydevd_breakpoints import LineBreakpoint
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

def suspend_django(py_db_frame, mainDebugger, thread, frame, cmd=CMD_SET_BREAK):
    frame = DjangoTemplateFrame(frame)

    if frame.f_lineno is None:
        return None

    #try:
    #    if thread.additionalInfo.filename == frame.f_code.co_filename and thread.additionalInfo.line == frame.f_lineno:
    #        return None # don't stay twice on the same line
    #except AttributeError:
    #    pass

    mainDebugger.additional_frames.addAdditionalFrameById(GetThreadId(thread), {id(frame): frame})


    py_db_frame.setSuspend(thread, cmd)
    thread.additionalInfo.suspend_type = DJANGO_SUSPEND

    thread.additionalInfo.filename = frame.f_code.co_filename
    thread.additionalInfo.line = frame.f_lineno

    return frame

def find_django_render_frame(frame):
    while frame is not None and not is_django_render_call(frame):
        frame = frame.f_back

    return frame





