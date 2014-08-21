from pydevd_constants import JINJA2_SUSPEND, STATE_SUSPEND
from pydevd_plugins.jinja2_debug import suspend_jinja2, is_jinja2_suspended, is_jinja2_context_call, is_jinja2_render_call, \
    is_jinja2_internal_function, find_jinja2_render_frame
from pydevd_plugins.jinja2_frame import Jinja2TemplateFrame, get_jinja2_template_filename, get_jinja2_template_line
from runfiles import DictContains
from pydevd_comm import CMD_STEP_OVER, CMD_ADD_EXCEPTION_BREAK
from pydevd_breakpoints import get_exception_name


#def has_line_breaks(mainDebugger):
#    return hasattr(mainDebugger, 'jinja2_breakpoints') and mainDebugger.jinja2_breakpoints

def has_exception_breaks(mainDebugger):
    return hasattr(mainDebugger, 'jinja2_exception_break') and mainDebugger.jinja2_exception_break

def can_not_skip(mainDebugger, frame, info):
    check = hasattr(mainDebugger, 'jinja2_breakpoints') and mainDebugger.jinja2_breakpoints and is_jinja2_render_call(frame) or \
         is_jinja2_render_call(frame) and info.pydev_call_inside_jinja2 is not None #when we come from python function to jinja2 template
    return check

def prepare_for_cmds(mainDebugger, info):
    if info.pydev_step_cmd != CMD_STEP_OVER:
        info.pydev_call_inside_jinja2 = None


def cmd_step_into(mainDebugger, frame, event, args, stop_info):
    mainDebugger, filename, info, thread = args
    if is_jinja2_suspended(thread):
        stop_info['jinja2_stop'] = event in ('call', 'line') and is_jinja2_render_call(frame)
        stop_info['stop'] = False
        if info.pydev_call_from_jinja2 is not None:
            if is_jinja2_internal_function(frame):
                #if internal Jinja2 function was called, we sould continue debugging inside template
                info.pydev_call_from_jinja2 = None
            else:
                #we go into python code from Jinja2 rendering frame
                stop_info['stop'] = True

        if event == 'call' and is_jinja2_context_call(frame.f_back):
            #we called function from context, the next step will be in function
            info.pydev_call_from_jinja2 = 1

    if event == 'return' and is_jinja2_context_call(frame.f_back):
        #we return from python code to Jinja2 rendering frame
        info.pydev_step_stop = info.pydev_call_from_jinja2
        info.pydev_call_from_jinja2 = None
        thread.additionalInfo.suspend_type = JINJA2_SUSPEND
        stop_info['stop'] = False

    #print "info.pydev_call_from_jinja2", info.pydev_call_from_jinja2, "stop_info", stop_info, \
    #    "thread.additionalInfo.suspend_type", thread.additionalInfo.suspend_type
    #print "event", event, "farme.locals", frame.f_locals


def cmd_step_over(mainDebugger, frame, event, args, stop_info):
    mainDebugger, filename, info, thread = args
    if is_jinja2_suspended(thread):
        stop_info['stop'] = False

        if info.pydev_call_inside_jinja2 is None:
            if is_jinja2_render_call(frame):
                if event == 'call':
                    info.pydev_call_inside_jinja2 = frame.f_back
                if event in ('line', 'return'):
                    info.pydev_call_inside_jinja2 = frame
        else:
            if event == 'line':
                if is_jinja2_render_call(frame) and info.pydev_call_inside_jinja2 is frame:
                    stop_info['jinja2_stop'] = True
            if event == 'return':
                if frame is info.pydev_call_inside_jinja2 and not DictContains(frame.f_back.f_locals,'event'):
                    info.pydev_call_inside_jinja2 = find_jinja2_render_frame(frame.f_back)
        return True
    else:
        if event == 'return' and is_jinja2_context_call(frame.f_back):
            #we return from python code to Jinja2 rendering frame
            info.pydev_call_from_jinja2 = None
            info.pydev_call_inside_jinja2 = find_jinja2_render_frame(frame)
            thread.additionalInfo.suspend_type = JINJA2_SUSPEND
            stop_info['stop'] = False
            return True
    #print "info.pydev_call_from_jinja2", info.pydev_call_from_jinja2, "stop", stop, "jinja_stop", jinja2_stop, \
    #    "thread.additionalInfo.suspend_type", thread.additionalInfo.suspend_type
    #print "event", event, "info.pydev_call_inside_jinja2", info.pydev_call_inside_jinja2
    #print "frame", frame, "frame.f_back", frame.f_back, "step_stop", info.pydev_step_stop
    #print "is_context_call", is_jinja2_context_call(frame)
    #print "render", is_jinja2_render_call(frame)
    #print "-------------"
    return False


def stop(mainDebugger, frame, event, args, stop_info, arg):
    mainDebugger, filename, info, thread = args
    if DictContains(stop_info, 'jinja2_stop') and stop_info['jinja2_stop']:
        frame = suspend_jinja2(mainDebugger, mainDebugger, thread, frame)
        if frame:
            mainDebugger.doWaitSuspend(thread, frame, event, arg)
            return True
    return False


def should_stop_on_break(mainDebugger, pydb_frame, frame, event, args, arg):
    mainDebugger, filename, info, thread = args
    if event in ('line', 'call') and info.pydev_state != STATE_SUSPEND and hasattr(mainDebugger, 'jinja2_breakpoints') and \
            mainDebugger.jinja2_breakpoints and is_jinja2_render_call(frame):
        return True, shouldStopOnJinja2Break(mainDebugger, pydb_frame, frame, event, arg, args)
    return False, None


def shouldStopOnJinja2Break(mainDebugger, pydb_frame, frame, event, arg, args):
    mainDebugger, filename, info, thread = args
    flag = False
    filename = get_jinja2_template_filename(frame)
    jinja2_breakpoints_for_file = mainDebugger.jinja2_breakpoints.get(filename)
    new_frame = Jinja2TemplateFrame(frame)
    if jinja2_breakpoints_for_file:
        lineno = frame.f_lineno
        template_lineno = get_jinja2_template_line(frame)

        if template_lineno is not None and DictContains(jinja2_breakpoints_for_file, template_lineno):
            jinja2_breakpoint = jinja2_breakpoints_for_file[template_lineno]

            if jinja2_breakpoint.is_triggered(frame):
                flag = True
                new_frame = Jinja2TemplateFrame(frame)

                if jinja2_breakpoint.condition is not None:
                    try:
                        val = eval(jinja2_breakpoint.condition, new_frame.f_globals, new_frame.f_locals)
                        if not val:
                            flag = False
                    except:
                        pydev_log.info(
                            'Error while evaluating condition \'%s\': %s\n' % (jinja2_breakpoint.condition, sys.exc_info()[1]))

                if jinja2_breakpoint.expression is not None:
                    try:
                        try:
                            val = eval(jinja2_breakpoint.expression, new_frame.f_globals, new_frame.f_locals)
                        except:
                            val = sys.exc_info()[1]
                    finally:
                        if val is not None:
                            thread.additionalInfo.message = val

                if flag:
                    frame = suspend_jinja2(pydb_frame, mainDebugger, thread, frame)
    return flag, frame


def add_exception_to_frame(frame, exception_info):
    frame.f_locals['__exception__'] = exception_info


def exception_break(mainDebugger, pydb_frame, frame, event, args, arg):
    mainDebugger, filename, info, thread = args
    exception, value, trace = arg
    if hasattr(mainDebugger, 'jinja2_exception_break') and mainDebugger.jinja2_exception_break:
        if get_exception_name(exception) in ('UndefinedError', 'TemplateNotFound', 'TemplatesNotFound'):
            #errors in rendering
            render_frame = find_jinja2_render_frame(frame)
            if render_frame:
                suspend_frame = suspend_jinja2(pydb_frame, mainDebugger, thread, render_frame, CMD_ADD_EXCEPTION_BREAK)
                if suspend_frame:
                    add_exception_to_frame(suspend_frame, (exception, value, trace))
                    flag = True
                    suspend_frame.f_back = frame
                    frame = suspend_frame
                    return True, (flag, frame)
        elif get_exception_name(exception) in ('TemplateSyntaxError', 'TemplateAssertionError'):
            #errors in compile time
            name = frame.f_code.co_name
            if name in ('template', 'top-level template code') or name.startswith('block '):
                #Jinja2 translates exception info and creates fake frame on his own
                pydb_frame.setSuspend(thread, CMD_ADD_EXCEPTION_BREAK)
                add_exception_to_frame(frame, (exception, value, trace))
                thread.additionalInfo.suspend_type = JINJA2_SUSPEND
                flag = True
                return True, (flag, frame)
    return False, None

