import traceback
from pydevd_breakpoints import LineBreakpoint, get_exception_name
from pydevd_constants import GetThreadId, STATE_SUSPEND, DictContains, DictIterItems, DictKeys
from pydevd_comm import CMD_SET_BREAK, CMD_STEP_OVER, CMD_ADD_EXCEPTION_BREAK
import pydevd_vars
from pydevd_file_utils import GetFileNameAndBaseFromFile
from pydevd_frame_utils import add_exception_to_frame, FCode

JINJA2_SUSPEND = 3

class Jinja2LineBreakpoint(LineBreakpoint):

    def __init__(self, file, line, condition, func_name, expression):
        self.file = file
        LineBreakpoint.__init__(self, line, condition, func_name, expression)

    def is_triggered(self, template_frame_file, template_frame_line):
        return self.file == template_frame_file and self.line == template_frame_line

    def __str__(self):
        return "Jinja2LineBreakpoint: %s-%d" %(self.file, self.line)


def add_line_breakpoint(plugin, pydb, type, file, line, condition, expression, func_name):
    result = None
    if type == 'jinja2-line':
        breakpoint = Jinja2LineBreakpoint(file, line, condition, func_name, expression)
        if not hasattr(pydb, 'jinja2_breakpoints'):
            _init_plugin_breaks(pydb)
        result = breakpoint, pydb.jinja2_breakpoints
        return result
    return result

def add_exception_breakpoint(plugin, pydb, type, exception):
    if type == 'jinja2':
        if not hasattr(pydb, 'jinja2_exception_break'):
            _init_plugin_breaks(pydb)
        pydb.jinja2_exception_break[exception] = True
        pydb.setTracingForUntracedContexts()
        return True
    return False

def _init_plugin_breaks(pydb):
    pydb.jinja2_exception_break = {}
    pydb.jinja2_breakpoints = {}

def remove_exception_breakpoint(plugin, pydb, type, exception):
    if type == 'jinja2':
        try:
            del pydb.jinja2_exception_break[exception]
            return True
        except:
            pass
    return False

def get_breakpoints(plugin, pydb, type):
    if type == 'jinja2-line':
        return pydb.jinja2_breakpoints
    return None


def _is_jinja2_render_call(frame):
    try:
        name = frame.f_code.co_name
        if DictContains(frame.f_globals, "__jinja_template__") and name in ("root", "loop", "macro") or name.startswith("block_"):
            return True
        return False
    except:
        traceback.print_exc()
        return False


def _suspend_jinja2(pydb, thread, frame, cmd=CMD_SET_BREAK, message=None):
    frame = Jinja2TemplateFrame(frame)

    if frame.f_lineno is None:
        return None

    pydevd_vars.addAdditionalFrameById(GetThreadId(thread), {id(frame): frame})
    pydb.setSuspend(thread, cmd)

    thread.additionalInfo.suspend_type = JINJA2_SUSPEND
    thread.additionalInfo.filename = frame.f_code.co_filename
    thread.additionalInfo.line = frame.f_lineno
    if cmd == CMD_ADD_EXCEPTION_BREAK:
        # send exception name as message
        thread.additionalInfo.message = message

    return frame

def _is_jinja2_suspended(thread):
    return thread.additionalInfo.suspend_type == JINJA2_SUSPEND

def _is_jinja2_context_call(frame):
    return DictContains(frame.f_locals, "_Context__obj")

def _is_jinja2_internal_function(frame):
    return DictContains(frame.f_locals, 'self') and frame.f_locals['self'].__class__.__name__ in \
        ('LoopContext', 'TemplateReference', 'Macro', 'BlockReference')

def _find_jinja2_render_frame(frame):
    while frame is not None and not _is_jinja2_render_call(frame):
        frame = frame.f_back

    return frame


#=======================================================================================================================
# Jinja2 Frame
#=======================================================================================================================

class Jinja2TemplateFrame:

    def __init__(self, frame):
        file_name = _get_jinja2_template_filename(frame)
        self.back_context = None
        if 'context' in frame.f_locals:
            #sometimes we don't have 'context', e.g. in macros
            self.back_context = frame.f_locals['context']
        self.f_code = FCode('template', file_name)
        self.f_lineno = _get_jinja2_template_line(frame)
        self.f_back = frame
        self.f_globals = {}
        self.f_locals = self.collect_context(frame)
        self.f_trace = None

    def collect_context(self, frame):
        res = {}
        for k, v in frame.f_locals.items():
            if not k.startswith('l_'):
                res[k] = v
            elif v and not _is_missing(v):
                res[k[2:]] = v
        if self.back_context is not None:
            for k, v in self.back_context.items():
                res[k] = v
        return res

    def changeVariable(self, frame, name, value):
        in_vars_or_parents = False
        if name in frame.f_locals['context'].parent:
            self.back_context.parent[name] = value
            in_vars_or_parents = True
        if name in frame.f_locals['context'].vars:
            self.back_context.vars[name] = value
            in_vars_or_parents = True

        l_name = 'l_' + name
        if l_name in frame.f_locals:
            if in_vars_or_parents:
                frame.f_locals[l_name] = self.back_context.resolve(name)
            else:
                frame.f_locals[l_name] = value


def change_variable(plugin, frame, attr, expression):
    if isinstance(frame, Jinja2TemplateFrame):
        result = eval(expression, frame.f_globals, frame.f_locals)
        frame.changeVariable(frame.f_back, attr, result)
        return result
    return False


def _is_missing(item):
    if item.__class__.__name__ == 'MissingType':
        return True
    return False

def _find_render_function_frame(frame):
    #in order to hide internal rendering functions
    old_frame = frame
    try:
        while not (DictContains(frame.f_locals, 'self') and frame.f_locals['self'].__class__.__name__ == 'Template' and \
                               frame.f_code.co_name == 'render'):
            frame = frame.f_back
            if frame is None:
                return old_frame
        return frame
    except:
        return old_frame

def _get_jinja2_template_line(frame):
    debug_info = None
    if DictContains(frame.f_globals,'__jinja_template__'):
        _debug_info = frame.f_globals['__jinja_template__']._debug_info
        if _debug_info != '':
            #sometimes template contains only plain text
            debug_info = frame.f_globals['__jinja_template__'].debug_info

    if debug_info is None:
        return None

    lineno = frame.f_lineno

    for pair in debug_info:
        if pair[1] == lineno:
            return pair[0]

    return None

def _get_jinja2_template_filename(frame):
    if DictContains(frame.f_globals, '__jinja_template__'):
        fname = frame.f_globals['__jinja_template__'].filename
        filename, base = GetFileNameAndBaseFromFile(fname)
        return filename
    return None


#=======================================================================================================================
# Jinja2 Step Commands
#=======================================================================================================================


def has_exception_breaks(plugin):
    if len(plugin.main_debugger.jinja2_exception_break) > 0:
        return True
    return False

def has_line_breaks(plugin):
    for file, breakpoints in DictIterItems(plugin.main_debugger.jinja2_breakpoints):
        if len(breakpoints) > 0:
            return True
    return False

def can_not_skip(plugin, pydb, pydb_frame, frame):
    if pydb.jinja2_breakpoints and _is_jinja2_render_call(frame):
        filename = _get_jinja2_template_filename(frame)
        jinja2_breakpoints_for_file = pydb.jinja2_breakpoints.get(filename)
        if jinja2_breakpoints_for_file:
            return True
    return False


def cmd_step_into(plugin, pydb, frame, event, args, stop_info, stop):
    pydb, filename, info, thread = args
    plugin_stop = False
    stop_info['jinja2_stop'] = False
    if not hasattr(info, 'pydev_call_from_jinja2'):
        info.pydev_call_from_jinja2 = None
    if not hasattr(info, 'pydev_call_inside_jinja2'):
        info.pydev_call_inside_jinja2 = None
    if _is_jinja2_suspended(thread):
        stop_info['jinja2_stop'] = event in ('call', 'line') and _is_jinja2_render_call(frame)
        plugin_stop = stop_info['jinja2_stop']
        stop = False
        if info.pydev_call_from_jinja2 is not None:
            if _is_jinja2_internal_function(frame):
                #if internal Jinja2 function was called, we sould continue debugging inside template
                info.pydev_call_from_jinja2 = None
            else:
                #we go into python code from Jinja2 rendering frame
                stop = True

        if event == 'call' and _is_jinja2_context_call(frame.f_back):
            #we called function from context, the next step will be in function
            info.pydev_call_from_jinja2 = 1

    if event == 'return' and _is_jinja2_context_call(frame.f_back):
        #we return from python code to Jinja2 rendering frame
        info.pydev_step_stop = info.pydev_call_from_jinja2
        info.pydev_call_from_jinja2 = None
        thread.additionalInfo.suspend_type = JINJA2_SUSPEND
        stop = False

        #print "info.pydev_call_from_jinja2", info.pydev_call_from_jinja2, "stop_info", stop_info, \
        #    "thread.additionalInfo.suspend_type", thread.additionalInfo.suspend_type
        #print "event", event, "farme.locals", frame.f_locals
    return stop, plugin_stop


def cmd_step_over(plugin, pydb, frame, event, args, stop_info, stop):
    pydb, filename, info, thread = args
    plugin_stop = False
    stop_info['jinja2_stop'] = False
    if not hasattr(info, 'pydev_call_from_jinja2'):
        info.pydev_call_from_jinja2 = None
    if not hasattr(info, 'pydev_call_inside_jinja2'):
        info.pydev_call_inside_jinja2 = None
    if _is_jinja2_suspended(thread):
        stop = False

        if info.pydev_call_inside_jinja2 is None:
            if _is_jinja2_render_call(frame):
                if event == 'call':
                    info.pydev_call_inside_jinja2 = frame.f_back
                if event in ('line', 'return'):
                    info.pydev_call_inside_jinja2 = frame
        else:
            if event == 'line':
                if _is_jinja2_render_call(frame) and info.pydev_call_inside_jinja2 is frame:
                    stop_info['jinja2_stop'] = True
                    plugin_stop = stop_info['jinja2_stop']
            if event == 'return':
                if frame is info.pydev_call_inside_jinja2 and not DictContains(frame.f_back.f_locals,'event'):
                    info.pydev_call_inside_jinja2 = _find_jinja2_render_frame(frame.f_back)
        return stop, plugin_stop
    else:
        if event == 'return' and _is_jinja2_context_call(frame.f_back):
            #we return from python code to Jinja2 rendering frame
            info.pydev_call_from_jinja2 = None
            info.pydev_call_inside_jinja2 = _find_jinja2_render_frame(frame)
            thread.additionalInfo.suspend_type = JINJA2_SUSPEND
            stop = False
            return stop, plugin_stop
    #print "info.pydev_call_from_jinja2", info.pydev_call_from_jinja2, "stop", stop, "jinja_stop", jinja2_stop, \
    #    "thread.additionalInfo.suspend_type", thread.additionalInfo.suspend_type
    #print "event", event, "info.pydev_call_inside_jinja2", info.pydev_call_inside_jinja2
    #print "frame", frame, "frame.f_back", frame.f_back, "step_stop", info.pydev_step_stop
    #print "is_context_call", _is_jinja2_context_call(frame)
    #print "render", _is_jinja2_render_call(frame)
    #print "-------------"
    return stop, plugin_stop


def stop(plugin, pydb, frame, event, args, stop_info, arg, step_cmd):
    pydb, filename, info, thread = args
    if DictContains(stop_info, 'jinja2_stop') and stop_info['jinja2_stop']:
        frame = _suspend_jinja2(pydb, thread, frame, step_cmd)
        if frame:
            pydb.doWaitSuspend(thread, frame, event, arg)
            return True
    return False


def get_breakpoint(plugin, pydb, pydb_frame, frame, event, args):
    pydb, filename, info, thread = args
    new_frame = None
    jinja2_breakpoint = None
    flag = False
    type = 'jinja2'
    if event in ('line', 'call') and info.pydev_state != STATE_SUSPEND and \
            pydb.jinja2_breakpoints and _is_jinja2_render_call(frame):
        filename = _get_jinja2_template_filename(frame)
        jinja2_breakpoints_for_file = pydb.jinja2_breakpoints.get(filename)
        new_frame = Jinja2TemplateFrame(frame)

        if jinja2_breakpoints_for_file:
            lineno = frame.f_lineno
            template_lineno = _get_jinja2_template_line(frame)
            if template_lineno is not None and DictContains(jinja2_breakpoints_for_file, template_lineno):
                jinja2_breakpoint = jinja2_breakpoints_for_file[template_lineno]
                flag = True
                new_frame = Jinja2TemplateFrame(frame)

    return flag, jinja2_breakpoint, new_frame, type


def suspend(plugin, pydb, thread, frame, bp_type):
    if bp_type == 'jinja2':
        return _suspend_jinja2(pydb, thread, frame)
    return None


def exception_break(plugin, pydb, pydb_frame, frame, args, arg):
    pydb, filename, info, thread = args
    exception, value, trace = arg
    if pydb.jinja2_exception_break:
        exception_type = DictKeys(pydb.jinja2_exception_break)[0]
        if get_exception_name(exception) in ('UndefinedError', 'TemplateNotFound', 'TemplatesNotFound'):
            #errors in rendering
            render_frame = _find_jinja2_render_frame(frame)
            if render_frame:
                suspend_frame = _suspend_jinja2(pydb, thread, render_frame, CMD_ADD_EXCEPTION_BREAK, message=exception_type)
                if suspend_frame:
                    add_exception_to_frame(suspend_frame, (exception, value, trace))
                    flag = True
                    suspend_frame.f_back = frame
                    frame = suspend_frame
                    return flag, frame
        elif get_exception_name(exception) in ('TemplateSyntaxError', 'TemplateAssertionError'):
            #errors in compile time
            name = frame.f_code.co_name
            if name in ('template', 'top-level template code') or name.startswith('block '):
                #Jinja2 translates exception info and creates fake frame on his own
                pydb_frame.setSuspend(thread, CMD_ADD_EXCEPTION_BREAK, message=exception_type)
                add_exception_to_frame(frame, (exception, value, trace))
                thread.additionalInfo.suspend_type = JINJA2_SUSPEND
                flag = True
                return flag, frame
    return None