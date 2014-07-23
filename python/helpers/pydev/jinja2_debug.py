
from pydevd_breakpoints import LineBreakpoint
from jinja2_frame import Jinja2TemplateFrame, get_jinja2_template_filename, get_jinja2_template_line
from pydevd_constants import JINJA2_SUSPEND, GetThreadId
from pydevd_comm import  CMD_SET_BREAK
import pydevd_vars
from runfiles import DictContains

class Jinja2LineBreakpoint(LineBreakpoint):

    def __init__(self, type, file, line, flag, condition, func_name, expression):
        self.file = file
        self.line = line
        LineBreakpoint.__init__(self, type, flag, condition, func_name, expression)

    def __eq__(self, other):
        if not isinstance(other, Jinja2LineBreakpoint):
            return False
        return self.file == other.file and self.line == other.line

    def is_triggered(self, frame):
        file = get_jinja2_template_filename(frame)
        line = get_jinja2_template_line(frame)
        return self.file == file and self.line == line

    def __str__(self):
        return "Jinja2LineBreakpoint: %s-%d" %(self.file, self.line)

def is_jinja2_render_call(frame):
    try:
        name = frame.f_code.co_name
        if name in ("root", "loop", "macro") or name.startswith("block_"):
            return True
        return False
    except:
        traceback.print_exc()
        return False


def suspend_jinja2(py_db_frame, mainDebugger, thread, frame, cmd=CMD_SET_BREAK):
    frame = Jinja2TemplateFrame(frame)

    if frame.f_lineno is None:
        return None

    pydevd_vars.addAdditionalFrameById(GetThreadId(thread), {id(frame): frame})
    py_db_frame.setSuspend(thread, cmd)

    thread.additionalInfo.suspend_type = JINJA2_SUSPEND
    thread.additionalInfo.filename = frame.f_code.co_filename
    thread.additionalInfo.line = frame.f_lineno

    return frame

def is_jinja2_suspended(thread):
    return thread.additionalInfo.suspend_type == JINJA2_SUSPEND

def is_jinja2_context_call(frame):
    return DictContains(frame.f_locals, "_Context__obj")

def is_jinja2_internal_function(frame):
    return DictContains(frame.f_locals, 'self') and frame.f_locals['self'].__class__.__name__ in \
        ('LoopContext', 'TemplateReference', 'Macro', 'Markup', 'BlockReference')


