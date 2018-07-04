from _pydevd_bundle.pydevd_comm import CMD_SET_BREAK, CMD_ADD_EXCEPTION_BREAK
import inspect
from _pydevd_bundle.pydevd_constants import STATE_SUSPEND, get_thread_id, dict_iter_items, DJANGO_SUSPEND, IS_PY2
from pydevd_file_utils import get_abs_path_real_path_and_base_from_file, normcase
from _pydevd_bundle.pydevd_breakpoints import LineBreakpoint, get_exception_name
from _pydevd_bundle import pydevd_vars
import traceback
from _pydev_bundle import pydev_log
from _pydevd_bundle.pydevd_frame_utils import add_exception_to_frame, FCode, just_raised

IS_DJANGO18 = False
IS_DJANGO19 = False
IS_DJANGO19_OR_HIGHER = False
try:
    import django
    version = django.VERSION
    IS_DJANGO18 = version[0] == 1 and version[1] == 8
    IS_DJANGO19 = version[0] == 1 and version[1] == 9
    IS_DJANGO19_OR_HIGHER = ((version[0] == 1 and version[1] >= 9) or version[0] > 1)
except:
    pass


class DjangoLineBreakpoint(LineBreakpoint):
    def __init__(self, file, line, condition, func_name, expression):
        self.file = file
        LineBreakpoint.__init__(self, line, condition, func_name, expression)

    def is_triggered(self, template_frame_file, template_frame_line):
        return self.file == template_frame_file and self.line == template_frame_line

    def __str__(self):
        return "DjangoLineBreakpoint: %s-%d" %(self.file, self.line)


def add_line_breakpoint(plugin, pydb, type, file, line, condition, expression, func_name):
    if type == 'django-line':
        breakpoint = DjangoLineBreakpoint(file, line, condition, func_name, expression)
        if not hasattr(pydb, 'django_breakpoints'):
            _init_plugin_breaks(pydb)
        return breakpoint, pydb.django_breakpoints
    return None

def add_exception_breakpoint(plugin, pydb, type, exception):
    if type == 'django':
        if not hasattr(pydb, 'django_exception_break'):
            _init_plugin_breaks(pydb)
        pydb.django_exception_break[exception] = True
        pydb.set_tracing_for_untraced_contexts_if_not_frame_eval()
        return True
    return False

def _init_plugin_breaks(pydb):
    pydb.django_exception_break = {}
    pydb.django_breakpoints = {}

def remove_exception_breakpoint(plugin, pydb, type, exception):
    if type == 'django':
        try:
            del pydb.django_exception_break[exception]
            return True
        except:
            pass
    return False

def get_breakpoints(plugin, pydb, type):
    if type == 'django-line':
        return pydb.django_breakpoints
    return None

def _inherits(cls, *names):
    if cls.__name__ in names:
        return True
    inherits_node = False
    for base in inspect.getmro(cls):
        if base.__name__ in names:
            inherits_node = True
            break
    return inherits_node


def _is_django_render_call(frame):
    try:
        name = frame.f_code.co_name
        if name != 'render':
            return False

        if 'self' not in frame.f_locals:
            return False

        cls = frame.f_locals['self'].__class__

        inherits_node = _inherits(cls, 'Node')

        if not inherits_node:
            return False

        clsname = cls.__name__
        if IS_DJANGO19:
            # in Django 1.9 we need to save the flag that there is included template
            if clsname == 'IncludeNode':
                if 'context' in frame.f_locals:
                    context = frame.f_locals['context']
                    context._has_included_template = True

        return clsname != 'TextNode' and clsname != 'NodeList'
    except:
        traceback.print_exc()
        return False


def _is_django_context_get_call(frame):
    try:
        if 'self' not in frame.f_locals:
            return False

        cls = frame.f_locals['self'].__class__

        return _inherits(cls, 'BaseContext')
    except:
        traceback.print_exc()
        return False


def _is_django_resolve_call(frame):
    try:
        name = frame.f_code.co_name
        if name != '_resolve_lookup':
            return False

        if 'self' not in frame.f_locals:
            return False

        cls = frame.f_locals['self'].__class__

        clsname = cls.__name__
        return clsname == 'Variable'
    except:
        traceback.print_exc()
        return False


def _is_django_suspended(thread):
    return thread.additional_info.suspend_type == DJANGO_SUSPEND


def suspend_django(main_debugger, thread, frame, cmd=CMD_SET_BREAK):
    frame = DjangoTemplateFrame(frame)

    if frame.f_lineno is None:
        return None

    pydevd_vars.add_additional_frame_by_id(get_thread_id(thread), {id(frame): frame})

    main_debugger.set_suspend(thread, cmd)
    thread.additional_info.suspend_type = DJANGO_SUSPEND

    return frame


def _find_django_render_frame(frame):
    while frame is not None and not _is_django_render_call(frame):
        frame = frame.f_back

    return frame

#=======================================================================================================================
# Django Frame
#=======================================================================================================================

def _read_file(filename):
    # type: (str) -> str
    if IS_PY2:
        f = open(filename, 'r')
    else:
        f = open(filename, 'r', encoding='utf-8', errors='replace')
    s = f.read()
    f.close()
    return s


def _offset_to_line_number(text, offset):
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


def _get_source_django_18_or_lower(frame):
    # This method is usable only for the Django <= 1.8
    try:
        node = frame.f_locals['self']
        if hasattr(node, 'source'):
            return node.source
        else:
            if IS_DJANGO18:
                # The debug setting was changed since Django 1.8
                pydev_log.error_once("WARNING: Template path is not available. Set the 'debug' option in the OPTIONS of a DjangoTemplates "
                                     "backend.")
            else:
                # The debug setting for Django < 1.8
                pydev_log.error_once("WARNING: Template path is not available. Please set TEMPLATE_DEBUG=True in your settings.py to make "
                                     "django template breakpoints working")
            return None

    except:
        pydev_log.debug(traceback.format_exc())
        return None


def _get_template_file_name(frame):
    try:
        if IS_DJANGO19:
            # The Node source was removed since Django 1.9
            if 'context' in frame.f_locals:
                context = frame.f_locals['context']
                if hasattr(context, '_has_included_template'):
                    #  if there was included template we need to inspect the previous frames and find its name
                    back = frame.f_back
                    while back is not None and frame.f_code.co_name in ('render', '_render'):
                        locals = back.f_locals
                        if 'self' in locals:
                            self = locals['self']
                            if self.__class__.__name__ == 'Template' and hasattr(self, 'origin') and \
                                    hasattr(self.origin, 'name'):
                                return normcase(self.origin.name)
                        back = back.f_back
                else:
                    if hasattr(context, 'template') and hasattr(context.template, 'origin') and \
                            hasattr(context.template.origin, 'name'):
                        return normcase(context.template.origin.name)
            return None
        elif IS_DJANGO19_OR_HIGHER:
            # For Django 1.10 and later there is much simpler way to get template name
            if 'self' in frame.f_locals:
                self = frame.f_locals['self']
                if hasattr(self, 'origin') and hasattr(self.origin, 'name'):
                    return normcase(self.origin.name)
            return None

        source = _get_source_django_18_or_lower(frame)
        if source is None:
            pydev_log.debug("Source is None\n")
            return None
        fname = source[0].name

        if fname == '<unknown source>':
            pydev_log.debug("Source name is %s\n" % fname)
            return None
        else:
            abs_path_real_path_and_base = get_abs_path_real_path_and_base_from_file(fname)
            return abs_path_real_path_and_base[1]
    except:
        pydev_log.debug(traceback.format_exc())
        return None


def _get_template_line(frame):
    if IS_DJANGO19_OR_HIGHER:
        # The Node source was removed since Django 1.9
        self = frame.f_locals['self']
        if hasattr(self, 'token') and hasattr(self.token, 'lineno'):
            return self.token.lineno
        else:
            return None
    source = _get_source_django_18_or_lower(frame)
    file_name = _get_template_file_name(frame)
    try:
        return _offset_to_line_number(_read_file(file_name), source[1][0])
    except:
        return None


class DjangoTemplateFrame:
    def __init__(self, frame):
        file_name = _get_template_file_name(frame)
        self.back_context = frame.f_locals['context']
        self.f_code = FCode('Django Template', file_name)
        self.f_lineno = _get_template_line(frame)
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

    def _change_variable(self, name, value):
        for d in self.back_context.dicts:
            for k, v in d.items():
                if k == name:
                    d[k] = value


def change_variable(plugin, frame, attr, expression):
    if isinstance(frame, DjangoTemplateFrame):
        result = eval(expression, frame.f_globals, frame.f_locals)
        frame._change_variable(attr, result)
        return result
    return False


def _is_django_exception_break_context(frame):
    try:
        name = frame.f_code.co_name
    except:
        name = None
    return name in ['_resolve_lookup', 'find_template']


#=======================================================================================================================
# Django Step Commands
#=======================================================================================================================

def can_not_skip(plugin, main_debugger, pydb_frame, frame):
    return main_debugger.django_breakpoints and _is_django_render_call(frame)


def has_exception_breaks(plugin):
    if len(plugin.main_debugger.django_exception_break) > 0:
        return True
    return False


def has_line_breaks(plugin):
    for file, breakpoints in dict_iter_items(plugin.main_debugger.django_breakpoints):
        if len(breakpoints) > 0:
            return True
    return False


def cmd_step_into(plugin, main_debugger, frame, event, args, stop_info, stop):
    info = args[2]
    thread = args[3]
    plugin_stop = False
    if _is_django_suspended(thread):
        stop_info['django_stop'] = event == 'call' and _is_django_render_call(frame)
        plugin_stop = stop_info['django_stop']
        stop = stop and _is_django_resolve_call(frame.f_back) and not _is_django_context_get_call(frame)
        if stop:
            info.pydev_django_resolve_frame = True # we remember that we've go into python code from django rendering frame
    return stop, plugin_stop


def cmd_step_over(plugin, main_debugger, frame, event, args, stop_info, stop):
    info = args[2]
    thread = args[3]
    plugin_stop = False
    if _is_django_suspended(thread):
        stop_info['django_stop'] = event == 'call' and _is_django_render_call(frame)
        plugin_stop = stop_info['django_stop']
        stop = False
        return stop, plugin_stop
    else:
        if event == 'return' and info.pydev_django_resolve_frame and _is_django_resolve_call(frame.f_back):
            #we return to Django suspend mode and should not stop before django rendering frame
            info.pydev_step_stop = frame.f_back
            info.pydev_django_resolve_frame = False
            thread.additional_info.suspend_type = DJANGO_SUSPEND
        stop = info.pydev_step_stop is frame and event in ('line', 'return')
    return stop, plugin_stop


def stop(plugin, main_debugger, frame, event, args, stop_info, arg, step_cmd):
    main_debugger = args[0]
    thread = args[3]
    if 'django_stop' in stop_info and stop_info['django_stop']:
        frame = suspend_django(main_debugger, thread, frame, step_cmd)
        if frame:
            main_debugger.do_wait_suspend(thread, frame, event, arg)
            return True
    return False


def get_breakpoint(plugin, main_debugger, pydb_frame, frame, event, args):
    main_debugger = args[0]
    filename = args[1]
    info = args[2]
    flag = False
    django_breakpoint = None
    new_frame = None
    type = 'django'

    if event == 'call' and info.pydev_state != STATE_SUSPEND and \
            main_debugger.django_breakpoints and _is_django_render_call(frame):
        filename = _get_template_file_name(frame)
        pydev_log.debug("Django is rendering a template: %s\n" % filename)
        django_breakpoints_for_file = main_debugger.django_breakpoints.get(filename)
        if django_breakpoints_for_file:
            pydev_log.debug("Breakpoints for that file: %s\n" % django_breakpoints_for_file)
            template_line = _get_template_line(frame)
            pydev_log.debug("Tracing template line: %s\n" % str(template_line))

            if template_line in django_breakpoints_for_file:
                django_breakpoint = django_breakpoints_for_file[template_line]
                flag = True
                new_frame = DjangoTemplateFrame(frame)
    return flag, django_breakpoint, new_frame, type


def suspend(plugin, main_debugger, thread, frame, bp_type):
    if bp_type == 'django':
        return suspend_django(main_debugger, thread, frame)
    return None

def exception_break(plugin, main_debugger, pydb_frame, frame, args, arg):
    main_debugger = args[0]
    thread = args[3]
    exception, value, trace = arg
    if main_debugger.django_exception_break and \
            get_exception_name(exception) in ['VariableDoesNotExist', 'TemplateDoesNotExist', 'TemplateSyntaxError'] and \
            just_raised(trace) and _is_django_exception_break_context(frame):
        render_frame = _find_django_render_frame(frame)
        if render_frame:
            suspend_frame = suspend_django(main_debugger, thread, render_frame, CMD_ADD_EXCEPTION_BREAK)
            if suspend_frame:
                add_exception_to_frame(suspend_frame, (exception, value, trace))
                flag = True
                thread.additional_info.pydev_message = 'VariableDoesNotExist'
                suspend_frame.f_back = frame
                frame = suspend_frame
                return (flag, frame)
    return None