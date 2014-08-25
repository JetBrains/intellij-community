from pydevd_comm import * #@UnusedWildImport
from pydevd_breakpoints import * #@UnusedWildImport
import traceback #@Reimport
import os.path
import sys
import pydev_log
from pydevd_signature import sendSignatureCallTrace

basename = os.path.basename

#=======================================================================================================================
# PyDBFrame
#=======================================================================================================================
class PyDBFrame:
    '''This makes the tracing for a given frame, so, the trace_dispatch
    is used initially when we enter into a new context ('call') and then
    is reused for the entire context.
    '''

    def __init__(self, args):
        #args = mainDebugger, filename, base, info, t, frame
        #yeap, much faster than putting in self and then getting it from self later on
        self._args = args[:-1]

    def setSuspend(self, *args, **kwargs):
        self._args[0].setSuspend(*args, **kwargs)

    def doWaitSuspend(self, *args, **kwargs):
        self._args[0].doWaitSuspend(*args, **kwargs)

    def trace_exception(self, frame, event, arg):
        if event == 'exception':
            (flag, frame) = self.shouldStopOnException(frame, event, arg)

            if flag:
              self.handle_exception(frame, event, arg)
              return self.trace_dispatch

        return self.trace_exception

    def shouldStopOnException(self, frame, event, arg):
        mainDebugger, filename, info, thread = self._args
        flag = False
        if info.pydev_state != STATE_SUSPEND:  # and breakpoint is not None:
            (exception, value, trace) = arg
            if trace is not None:  #on jython trace is None on the first event
                exception_breakpoint = get_exception_breakpoint(exception, dict(mainDebugger.exception_set), NOTIFY_ALWAYS)
                if exception_breakpoint is not None:
                    if not exception_breakpoint.notify_on_first_raise_only or just_raised(trace):
                        curr_func_name = frame.f_code.co_name
                        add_exception_to_frame(frame, (exception, value, trace))
                        self.setSuspend(thread, CMD_ADD_EXCEPTION_BREAK)
                        thread.additionalInfo.message = exception_breakpoint.qname
                        flag = True
                    else:
                        flag = False
                else:
                    try:
                        exist_result, result = mainDebugger.first_plugin_result('exception_break', self, frame, event, self._args, arg)
                        if exist_result:
                            (flag, frame) = result

                    except:
                        flag = False
        return (flag, frame)

    def handle_exception(self, frame, event, arg):
      mainDebugger = self._args[0]
      thread = self._args[3]
      self.doWaitSuspend(thread, frame, event, arg)
      mainDebugger.SetTraceForFrameAndParents(frame)

    def trace_dispatch(self, frame, event, arg):
        mainDebugger, filename, info, thread = self._args
        try:
            info.is_tracing = True

            if mainDebugger._finishDebuggingSession:
                return None

            if getattr(thread, 'pydev_do_not_trace', None):
                return None

            if event == 'call':
                sendSignatureCallTrace(mainDebugger, frame, filename)

            if event not in ('line', 'call', 'return'):
                if event == 'exception':
                    (flag, frame) = self.shouldStopOnException(frame, event, arg)
                    if flag:
                        self.handle_exception(frame, event, arg)
                        return self.trace_dispatch
                else:
                #I believe this can only happen in jython on some frontiers on jython and java code, which we don't want to trace.
                    return None

            if event is not 'exception':
                breakpoints_for_file = mainDebugger.breakpoints.get(filename)
                can_skip = False

                if info.pydev_state == STATE_RUN:
                    #we can skip if:
                    #- we have no stop marked
                    #- we should make a step return/step over and we're not in the current frame
                    can_skip = (info.pydev_step_cmd is None and info.pydev_step_stop is None)\
                    or (info.pydev_step_cmd in (CMD_STEP_RETURN, CMD_STEP_OVER) and info.pydev_step_stop is not frame)

                if can_skip:
                    can_skip = not mainDebugger.can_not_skip_from_plugins(frame, info)

                # Let's check to see if we are in a function that has a breakpoint. If we don't have a breakpoint,
                # we will return nothing for the next trace
                #also, after we hit a breakpoint and go to some other debugging state, we have to force the set trace anyway,
                #so, that's why the additional checks are there.
                if not breakpoints_for_file:
                    if can_skip:
                        if mainDebugger.always_exception_set or \
                                        mainDebugger.has_exception_breaks_from_plugins():
                            return self.trace_exception
                        else:
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
                flag = False
                #return is not taken into account for breakpoint hit because we'd have a double-hit in this case
                #(one for the line and the other for the return).

                breakpoint = None
                exist_result = False
                stop = False
                if not flag and event != 'return' and info.pydev_state != STATE_SUSPEND and breakpoints_for_file is not None\
                and DictContains(breakpoints_for_file, line):
                    breakpoint = breakpoints_for_file[line]
                    stop = True
                    new_frame = frame
                    if info.pydev_step_cmd == CMD_STEP_OVER and info.pydev_step_stop is frame and event in ('line', 'return'):
                        stop = False #we don't stop on breakpoint if we have to stop by step-over (it will be processed later)
                else:
                    exist_result, result = mainDebugger.first_plugin_result('get_breakpoint', frame, event, self._args)
                    if exist_result:
                         (flag, breakpoint, new_frame) = result

                if breakpoint:
                    #ok, hit breakpoint, now, we have to discover if it is a conditional breakpoint
                    # lets do the conditional stuff here
                    if stop or exist_result:
                        if breakpoint.condition is not None:
                            try:
                                val = eval(breakpoint.condition, new_frame.f_globals, new_frame.f_locals)
                                if not val:
                                    return self.trace_dispatch

                            except:
                                pydev_log.info('Error while evaluating condition \'%s\': %s\n' % (breakpoint.condition, sys.exc_info()[1]))
                                return self.trace_dispatch

                    if breakpoint.expression is not None:
                        try:
                            try:
                                val = eval(breakpoint.expression, new_frame.f_globals, new_frame.f_locals)
                            except:
                                val = sys.exc_info()[1]
                        finally:
                            if val is not None:
                                thread.additionalInfo.message = val
                if stop:
                    self.setSuspend(thread, CMD_SET_BREAK)
                elif flag:
                    exist_result, result = mainDebugger.first_plugin_result('suspend', self, thread, frame)
                    if exist_result:
                        frame = result

                # if thread has a suspend flag, we suspend with a busy wait
                if info.pydev_state == STATE_SUSPEND:
                    self.doWaitSuspend(thread, frame, event, arg)
                    return self.trace_dispatch

            except:
                traceback.print_exc()
                raise

            #step handling. We stop when we hit the right frame
            try:
                mainDebugger.search_for_plugins('prepare_for_cmds', info)
                stop_info = {}

                if info.pydev_step_cmd == CMD_STEP_INTO:
                    stop_info['stop'] = event in ('line', 'return')
                    mainDebugger.search_for_plugins('cmd_step_into', frame, event, self._args, stop_info)

                elif info.pydev_step_cmd == CMD_STEP_OVER:
                    stop_info['stop'] = info.pydev_step_stop is frame and event in ('line', 'return')
                    mainDebugger.search_for_plugins('cmd_step_over', frame, event, self._args, stop_info)

                elif info.pydev_step_cmd == CMD_SMART_STEP_INTO:
                    stop_info['stop'] = False
                    if info.pydev_smart_step_stop is frame:
                        info.pydev_func_name = None
                        info.pydev_smart_step_stop = None

                    if event == 'line' or event == 'exception':
                        curr_func_name = frame.f_code.co_name

                        #global context is set with an empty name
                        if curr_func_name in ('?', '<module>') or curr_func_name is None:
                            curr_func_name = ''

                        if curr_func_name == info.pydev_func_name:
                                stop_info['stop'] = True

                elif info.pydev_step_cmd == CMD_STEP_RETURN:
                    stop_info['stop'] = event == 'return' and info.pydev_step_stop is frame

                elif info.pydev_step_cmd == CMD_RUN_TO_LINE or info.pydev_step_cmd == CMD_SET_NEXT_STATEMENT:
                    stop_info['stop'] = False

                    if event == 'line' or event == 'exception':
                        #Yes, we can only act on line events (weird hum?)
                        #Note: This code is duplicated at pydevd.py
                        #Acting on exception events after debugger breaks with exception
                        curr_func_name = frame.f_code.co_name

                        #global context is set with an empty name
                        if curr_func_name in ('?', '<module>'):
                            curr_func_name = ''

                        if curr_func_name == info.pydev_func_name:
                            line = info.pydev_next_line
                            if frame.f_lineno == line:
                                stop_info['stop'] = True
                            else:
                                if frame.f_trace is None:
                                    frame.f_trace = self.trace_dispatch
                                frame.f_lineno = line
                                frame.f_trace = None
                                stop_info['stop'] = True

                else:
                    stop_info['stop'] = False

                if True in stop_info.values():
                    stopped_on_plugin = mainDebugger.search_for_plugins('stop', frame, event, self._args, stop_info, arg)
                    if DictContains(stop_info, 'stop') and stop_info['stop'] and not stopped_on_plugin:
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
        finally:
            info.is_tracing = False

        #end trace_dispatch

    if USE_PSYCO_OPTIMIZATION:
        try:
            import psyco

            trace_dispatch = psyco.proxy(trace_dispatch)
        except ImportError:
            if hasattr(sys, 'exc_clear'): #jython does not have it
                sys.exc_clear() #don't keep the traceback
            pass #ok, psyco not available


def just_raised(trace):
    if trace is None:
        return False
    return trace.tb_next is None

def add_exception_to_frame(frame, exception_info):
    frame.f_locals['__exception__'] = exception_info



