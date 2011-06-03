from django_debug import is_django_render_call, get_template_file_name, get_template_line, is_django_suspended, suspend_django
from django_debug import find_django_render_frame
from django_frame import just_raised
from pydevd_comm import * #@UnusedWildImport
from pydevd_breakpoints import * #@UnusedWildImport
import traceback #@Reimport
import os.path
import sys

basename = os.path.basename

#=======================================================================================================================
# PyDBFrame
#=======================================================================================================================
class PyDBFrame:
    '''This makes the tracing for a given frame, so, the trace_dispatch
    is used initially when we enter into a new context ('call') and then
    is reused for the entire context.
    '''

    def __init__(self, *args):
        #args = mainDebugger, filename, base, info, t, frame
        #yeap, much faster than putting in self and then getting it from self later on
        self._args = args[:-1]

    def setSuspend(self, *args, **kwargs):
        self._args[0].setSuspend(*args, **kwargs)

    def doWaitSuspend(self, *args, **kwargs):
        self._args[0].doWaitSuspend(*args, **kwargs)

    def trace_dispatch(self, frame, event, arg):
        if event not in ('line', 'call', 'return', 'exception'):
            return None

        mainDebugger, filename, info, thread = self._args

        if event is not 'exception':
            breakpoints_for_file = mainDebugger.breakpoints.get(filename)

            can_skip = False


            if info.pydev_state == STATE_RUN:
                #we can skip if:
                #- we have no stop marked
                #- we should make a step return/step over and we're not in the current frame
                can_skip = (info.pydev_step_cmd is None and info.pydev_step_stop is None)\
                or (info.pydev_step_cmd in (CMD_STEP_RETURN, CMD_STEP_OVER) and info.pydev_step_stop is not frame)

            if mainDebugger.always_exception_set or mainDebugger.django_breakpoints or mainDebugger.django_exception_break:
                can_skip = False

            # Let's check to see if we are in a function that has a breakpoint. If we don't have a breakpoint,
            # we will return nothing for the next trace
            #also, after we hit a breakpoint and go to some other debugging state, we have to force the set trace anyway,
            #so, that's why the additional checks are there.
            if not breakpoints_for_file:
                if can_skip:
                    return None

            else:
                #checks the breakpoint to see if there is a context match in some function
                curr_func_name = frame.f_code.co_name

                #global context is set with an empty name
                if curr_func_name in ('?', '<module>'):
                    curr_func_name = ''

                for breakpoint in breakpoints_for_file.values(): #jython does not support itervalues()
                    #will match either global or some function
                    if breakpoint.func_name in ('None', curr_func_name):
                        break

                else: # if we had some break, it won't get here (so, that's a context that we want to skip)
                    if can_skip:
                        #print 'skipping', frame.f_lineno, info.pydev_state, info.pydev_step_stop, info.pydev_step_cmd
                        return None
        else:
            breakpoints_for_file = None

        #We may have hit a breakpoint or we are already in step mode. Either way, let's check what we should do in this frame
        #print 'NOT skipped', frame.f_lineno, frame.f_code.co_name, event

        try:
            line = frame.f_lineno

            if event == 'exception' and info.pydev_state != STATE_SUSPEND:  #and breakpoint is not None:
                (exception, value, trace) = arg

                exception_breakpoint = get_exception_breakpoint(exception, dict(mainDebugger.exception_set), NOTIFY_ALWAYS)
                if exception_breakpoint is not None:
                    curr_func_name = frame.f_code.co_name
                    self.setSuspend(thread, CMD_ADD_EXCEPTION_BREAK)
                    thread.additionalInfo.message = exception_breakpoint.qname
                else:
                    if mainDebugger.django_exception_break and get_exception_name(exception) in ['django.template.base.VariableDoesNotExist', 'django.template.base.TemplateDoesNotExist', 'django.template.base.TemplateSyntaxError'] and just_raised(frame):
                        render_frame = find_django_render_frame(frame)
                        if render_frame:
                            suspend_frame = suspend_django(self, mainDebugger, thread, render_frame)
                            if suspend_frame:
                                suspend_frame.f_back = frame
                                frame = suspend_frame

            elif event == 'call' and info.pydev_state != STATE_SUSPEND and mainDebugger.django_breakpoints \
            and is_django_render_call(frame):
                flag = False
                filename = get_template_file_name(frame)
                django_breakpoints_for_file = mainDebugger.django_breakpoints.get(filename)
                if django_breakpoints_for_file:
                    template_line = get_template_line(frame)
                    if DictContains(django_breakpoints_for_file, template_line):
                        django_breakpoint = django_breakpoints_for_file[template_line]

                        if django_breakpoint.is_triggered(frame):
                            frame = suspend_django(self, mainDebugger, thread, frame)
                            flag = True

                #if not flag:
                #    return self.trace_dispatch


            #return is not taken into account for breakpoint hit because we'd have a double-hit in this case
            #(one for the line and the other for the return).
            elif event != 'return' and info.pydev_state != STATE_SUSPEND and breakpoints_for_file is not None\
            and DictContains(breakpoints_for_file, line):
                #ok, hit breakpoint, now, we have to discover if it is a conditional breakpoint
                # lets do the conditional stuff here
                breakpoint = breakpoints_for_file[line]

                if breakpoint.condition is not None:
                    try:
                        val = eval(breakpoint.condition, frame.f_globals, frame.f_locals)
                        if not val:
                            return self.trace_dispatch

                    except:
                        sys.stderr.write('Error while evaluating condition \'%s\': %s\n' % (breakpoint.condition, sys.exc_info()[1]))
                        sys.stderr.flush()
                        return self.trace_dispatch

                if breakpoint.expression is not None:
                    try:
                        try:
                            val = eval(breakpoint.expression, frame.f_globals, frame.f_locals)
                        except:
                            val = sys.exc_info()[1]
                    finally:
                        if val is not None:
                            thread.log_expression = val

                self.setSuspend(thread, CMD_SET_BREAK)

            # if thread has a suspend flag, we suspend with a busy wait
            if info.pydev_state == STATE_SUSPEND:
                self.doWaitSuspend(thread, frame, event, arg)
                return self.trace_dispatch

        except:
            raise

        #step handling. We stop when we hit the right frame
        try:
            django_stop = False
            if info.pydev_step_cmd == CMD_STEP_INTO:
                stop = event in ('line', 'return')

            elif info.pydev_step_cmd == CMD_STEP_OVER:
                if is_django_suspended(thread):

                    django_stop = event == 'call' and is_django_render_call(frame)

                    stop = False
                else:
                    stop = info.pydev_step_stop is frame and event in ('line', 'return')

            elif info.pydev_step_cmd == CMD_STEP_RETURN:
                stop = event == 'return' and info.pydev_step_stop is frame

            elif info.pydev_step_cmd == CMD_RUN_TO_LINE:
                stop = False

                if event == 'line':
                    #Yes, we can only act on line events (weird hum?)
                    #Note: This code is duplicated at pydevd.py
                    curr_func_name = frame.f_code.co_name

                    #global context is set with an empty name
                    if curr_func_name in ('?', '<module>'):
                        curr_func_name = ''

                    if curr_func_name == info.pydev_func_name:
                        line = info.pydev_next_line
                        if frame.f_lineno == line:
                            stop = True
                        else:
                            if frame.f_trace is None:
                                frame.f_trace = self.trace_dispatch
                            frame.f_lineno = line
                            frame.f_trace = None
                            stop = True

            else:
                stop = False

            if django_stop:
                frame = suspend_django(self, mainDebugger, thread, frame)
                if frame:
                    self.doWaitSuspend(thread, frame, event, arg)
            elif stop:
                #event is always == line or return at this point
                if event == 'line':
                    self.setSuspend(thread, info.pydev_step_cmd)
                    self.doWaitSuspend(thread, frame, event, arg)
                else: #return event
                    back = frame.f_back
                    if back is not None:
                        #When we get to the pydevd run function, the debugging has actually finished for the main thread
                        #(note that it can still go on for other threads, but for this one, we just make it finish)
                        #So, just setting it to None should be OK
                        if basename(back.f_code.co_filename) == 'pydevd.py' and back.f_code.co_name == 'run':
                            back = None

                    if back is not None:
                        #if we're in a return, we want it to appear to the user in the previous frame!
                        self.setSuspend(thread, info.pydev_step_cmd)
                        self.doWaitSuspend(thread, back, event, arg)
                    else:
                        #in jython we may not have a back frame
                        info.pydev_step_stop = None
                        info.pydev_step_cmd = None
                        info.pydev_state = STATE_RUN


        except:
            traceback.print_exc()
            info.pydev_step_cmd = None

        #if we are quitting, let's stop the tracing
        retVal = None
        if not mainDebugger.quitting:
            retVal = self.trace_dispatch

        return retVal

    if USE_PSYCO_OPTIMIZATION:
        try:
            import psyco

            trace_dispatch = psyco.proxy(trace_dispatch)
        except ImportError:
            if hasattr(sys, 'exc_clear'): #jython does not have it
                sys.exc_clear() #don't keep the traceback
            pass #ok, psyco not available
