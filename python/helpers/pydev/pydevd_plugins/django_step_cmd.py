
from pydevd_plugins.django_debug import is_django_render_call, get_template_file_name, get_template_line, is_django_suspended, \
    suspend_django, is_django_resolve_call, is_django_context_get_call
from pydevd_plugins.django_debug import find_django_render_frame
from runfiles import DictContains
from pydevd_constants import DJANGO_SUSPEND, STATE_SUSPEND
from pydevd_plugins.django_frame import just_raised, is_django_exception_break_context, DjangoTemplateFrame
import pydev_log
from pydevd_comm import CMD_ADD_EXCEPTION_BREAK
from pydevd_breakpoints import get_exception_name

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


def should_stop_on_break(mainDebugger, pydb_frame, frame, event, args, arg):
    mainDebugger, filename, info, thread = args
    if event == 'call' and info.pydev_state != STATE_SUSPEND and hasattr(mainDebugger, 'django_breakpoints') and \
            mainDebugger.django_breakpoints and is_django_render_call(frame):
        return True, shouldStopOnDjangoBreak(mainDebugger, pydb_frame, frame, event, arg, args)
    return False, None


def shouldStopOnDjangoBreak(mainDebugger, pydb_frame, frame, event, arg, args):
    mainDebugger, filename, info, thread = args
    flag = False
    filename = get_template_file_name(frame)
    pydev_log.debug("Django is rendering a template: %s\n" % filename)
    django_breakpoints_for_file = mainDebugger.django_breakpoints.get(filename)
    if django_breakpoints_for_file:
        pydev_log.debug("Breakpoints for that file: %s\n" % django_breakpoints_for_file)
        template_line = get_template_line(frame)
        pydev_log.debug("Tracing template line: %d\n" % template_line)

        if DictContains(django_breakpoints_for_file, template_line):
            django_breakpoint = django_breakpoints_for_file[template_line]

            if django_breakpoint.is_triggered(frame):
                pydev_log.debug("Breakpoint is triggered.\n")
                flag = True
                new_frame = DjangoTemplateFrame(frame)

                if django_breakpoint.condition is not None:
                    try:
                        val = eval(django_breakpoint.condition, new_frame.f_globals, new_frame.f_locals)
                        if not val:
                            flag = False
                            pydev_log.debug("Condition '%s' is evaluated to %s. Not suspending.\n" % (django_breakpoint.condition, val))
                    except:
                        pydev_log.info(
                            'Error while evaluating condition \'%s\': %s\n' % (django_breakpoint.condition, sys.exc_info()[1]))

                if django_breakpoint.expression is not None:
                    try:
                        try:
                            val = eval(django_breakpoint.expression, new_frame.f_globals, new_frame.f_locals)
                        except:
                            val = sys.exc_info()[1]
                    finally:
                        if val is not None:
                            thread.additionalInfo.message = val
                if flag:
                    frame = suspend_django(pydb_frame, mainDebugger, thread, frame)
    return (flag, frame)


def add_exception_to_frame(frame, exception_info):
    frame.f_locals['__exception__'] = exception_info


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