from pydevd_comm import CMD_SET_BREAK, CMD_ADD_EXCEPTION_BREAK
import inspect
from pydevd_constants import DJANGO_SUSPEND, STATE_SUSPEND, GetThreadId
from pydevd_file_utils import NormFileToServer, GetFileNameAndBaseFromFile
from runfiles import DictContains
from pydevd_breakpoints import LineBreakpoint, get_exception_name
import pydevd_vars
import traceback
import pydev_log
from pydevd_frame import add_exception_to_frame


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

#=======================================================================================================================
# Django Frame
#=======================================================================================================================

def read_file(filename):
    f = open(filename, "r")
    s = f.read()
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


def get_source(frame):
    try:
        node = frame.f_locals['self']
        if hasattr(node, 'source'):
            return node.source
        else:
            pydev_log.error_once("WARNING: Template path is not available. Please set TEMPLATE_DEBUG=True in your settings.py to make "
                                 " django template breakpoints working")
            return None

    except:
        pydev_log.debug(traceback.format_exc())
        return None


def get_template_file_name(frame):
    try:
        source = get_source(frame)
        if source is None:
            pydev_log.debug("Source is None\n")
            return None
        fname = source[0].name

        if fname == '<unknown source>':
            pydev_log.debug("Source name is %s\n" % fname)
            return None
        else:
            filename, base = GetFileNameAndBaseFromFile(fname)
            return filename
    except:
        pydev_log.debug(traceback.format_exc())
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
        self.back_context = frame.f_locals['context']
        self.f_code = FCode('Django Template', file_name)
        self.f_lineno = get_template_line(frame)
        self.f_back = frame
        self.f_globals = {}
        self.f_locals = self.collect_context(self.back_context)
        self.f_trace = None

    def collect_context(self, context):
        res = {}
        try:
            for d in context.dicts:
                for k, v in d.items():
                    res[k] = v
        except  AttributeError:
            pass
        return res

    def changeVariable(self, name, value):
        for d in self.back_context.dicts:
            for k, v in d.items():
                if k == name:
                    d[k] = value


class FCode:
    def __init__(self, name, filename):
        self.co_name = name
        self.co_filename = filename


def is_django_exception_break_context(frame):
    try:
        name = frame.f_code.co_name
    except:
        name = None
    return name in ['_resolve_lookup', 'find_template']


def just_raised(trace):
    if trace is None:
        return False
    return trace.tb_next is None


#=======================================================================================================================
# Django Step Commands
#=======================================================================================================================

def can_not_skip(mainDebugger, frame, info):
    if hasattr(mainDebugger, 'django_breakpoints') and mainDebugger.django_breakpoints and is_django_render_call(frame):
        filename = get_template_file_name(frame)
        django_breakpoints_for_file = mainDebugger.django_breakpoints.get(filename)
        if django_breakpoints_for_file:
            return True
    return False

def has_exception_breaks(mainDebugger):
    return hasattr(mainDebugger, 'django_exception_break') and mainDebugger.django_exception_break


def cmd_step_into(mainDebugger, frame, event, args, stop_info):
    mainDebugger, filename, info, thread = args
    if is_django_suspended(thread):
        #stop_info['django_stop'] = event == 'call' and is_django_render_call(frame)
        stop_info['stop'] = stop_info['stop'] and is_django_resolve_call(frame.f_back) and not is_django_context_get_call(frame)
        if stop_info['stop']:
            info.pydev_django_resolve_frame = 1 #we remember that we've go into python code from django rendering frame


def cmd_step_over(mainDebugger, frame, event, args, stop_info):
    mainDebugger, filename, info, thread = args
    if is_django_suspended(thread):
        stop_info['django_stop'] = event == 'call' and is_django_render_call(frame)
        stop_info['stop'] = False
        return True
    else:
        if event == 'return' and info.pydev_django_resolve_frame is not None and is_django_resolve_call(frame.f_back):
            #we return to Django suspend mode and should not stop before django rendering frame
            info.pydev_step_stop = info.pydev_django_resolve_frame
            info.pydev_django_resolve_frame = None
            thread.additionalInfo.suspend_type = DJANGO_SUSPEND
        stop_info['stop'] = info.pydev_step_stop is frame and event in ('line', 'return')

    return False


def stop(mainDebugger, frame, event, args, stop_info, arg):
    mainDebugger, filename, info, thread = args
    if DictContains(stop_info, 'django_stop') and stop_info['django_stop']:
        frame = suspend_django(mainDebugger, mainDebugger, thread, frame)
        if frame:
            mainDebugger.doWaitSuspend(thread, frame, event, arg)
            return True
    return False


def get_breakpoint(mainDebugger, frame, event, args):
    mainDebugger, filename, info, thread = args
    flag = False
    django_breakpoint = None
    result = None
    new_frame = None
    is_result_exist = False

    if event == 'call' and info.pydev_state != STATE_SUSPEND and hasattr(mainDebugger, 'django_breakpoints') and \
            mainDebugger.django_breakpoints and is_django_render_call(frame):
        filename = get_template_file_name(frame)
        pydev_log.debug("Django is rendering a template: %s\n" % filename)
        django_breakpoints_for_file = mainDebugger.django_breakpoints.get(filename)
        if django_breakpoints_for_file:
            pydev_log.debug("Breakpoints for that file: %s\n" % django_breakpoints_for_file)
            template_line = get_template_line(frame)
            pydev_log.debug("Tracing template line: %d\n" % template_line)

            if DictContains(django_breakpoints_for_file, template_line):
                is_result_exist = True
                django_breakpoint = django_breakpoints_for_file[template_line]
                if django_breakpoint.is_triggered(frame):
                    pydev_log.debug("Breakpoint is triggered.\n")
                    flag = True
                    new_frame = DjangoTemplateFrame(frame)
    result = flag, django_breakpoint, new_frame
    return is_result_exist, result


def suspend(mainDebugger, pydb_frame, thread, frame):
    return True, suspend_django(pydb_frame, mainDebugger, thread, frame)


def exception_break(mainDebugger, pydb_frame, frame, event, args, arg):
    mainDebugger, filename, info, thread = args
    exception, value, trace = arg
    if hasattr(mainDebugger, 'django_exception_break') and mainDebugger.django_exception_break and \
                    get_exception_name(exception) in ['VariableDoesNotExist', 'TemplateDoesNotExist', 'TemplateSyntaxError'] and \
            just_raised(trace) and is_django_exception_break_context(frame):
        render_frame = find_django_render_frame(frame)
        if render_frame:
            suspend_frame = suspend_django(pydb_frame, mainDebugger, thread, render_frame, CMD_ADD_EXCEPTION_BREAK)
            if suspend_frame:
                add_exception_to_frame(suspend_frame, (exception, value, trace))
                flag = True
                thread.additionalInfo.message = 'VariableDoesNotExist'
                suspend_frame.f_back = frame
                frame = suspend_frame
                return True, (flag, frame)
    return False, None