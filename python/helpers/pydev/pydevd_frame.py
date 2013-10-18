from django_debug import is_django_render_call, get_template_file_name, get_template_line, is_django_suspended, suspend_django, is_django_resolve_call, is_django_context_get_call
from django_debug import find_django_render_frame
from django_frame import just_raised
from django_frame import is_django_exception_break_context
from django_frame import DjangoTemplateFrame
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

      if info.pydev_state != STATE_SUSPEND:  #and breakpoint is not None:
          (exception, value, trace) = arg

          if trace is not None: #on jython trace is None on the first event
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
                      if mainDebugger.django_exception_break and get_exception_name(exception) in ['VariableDoesNotExist', 'TemplateDoesNotExist', 'TemplateSyntaxError'] and just_raised(trace) and is_django_exception_break_context(frame):
                          render_frame = find_django_render_frame(frame)
                          if render_frame:
                              suspend_frame = suspend_django(self, mainDebugger, thread, render_frame, CMD_ADD_DJANGO_EXCEPTION_BREAK)

                              if suspend_frame:
                                  add_exception_to_frame(suspend_frame, (exception, value, trace))
                                  flag = True
                                  thread.additionalInfo.message = 'VariableDoesNotExist'
                                  suspend_frame.f_back = frame
                                  frame = suspend_frame
                  except :
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

                if  mainDebugger.django_breakpoints:
                    can_skip = False

                # Let's check to see if we are in a function that has a breakpoint. If we don't have a breakpoint,
                # we will return nothing for the next trace
                #also, after we hit a breakpoint and go to some other debugging state, we have to force the set trace anyway,
                #so, that's why the additional checks are there.
                if not breakpoints_for_file:
                    if can_skip:
                        if mainDebugger.always_exception_set or mainDebugger.django_exception_break:
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
                if event == 'call' and info.pydev_state != STATE_SUSPEND and mainDebugger.django_breakpoints \
                and is_django_render_call(frame):
                    (flag, frame) = self.shouldStopOnDjangoBreak(frame, event, arg)

                #return is not taken into account for breakpoint hit because we'd have a double-hit in this case
                #(one for the line and the other for the return).

                if not flag and event != 'return' and info.pydev_state != STATE_SUSPEND and breakpoints_for_file is not None\
                and DictContains(breakpoints_for_file, line):
                    #ok, hit breakpoint, now, we have to discover if it is a conditional breakpoint
                    # lets do the conditional stuff here
                    breakpoint = breakpoints_for_file[line]

                    stop = True
                    if info.pydev_step_cmd == CMD_STEP_OVER and info.pydev_step_stop is frame and event in ('line', 'return'):
                        stop = False #we don't stop on breakpoint if we have to stop by step-over (it will be processed later)
                    else:
                        if breakpoint.condition is not None:
                            try:
                                val = eval(breakpoint.condition, frame.f_globals, frame.f_locals)
                                if not val:
                                    return self.trace_dispatch

                            except:
                                pydev_log.info('Error while evaluating condition \'%s\': %s\n' % (breakpoint.condition, sys.exc_info()[1]))

                                return self.trace_dispatch

                    if breakpoint.expression is not None:
                        try:
                            try:
                                val = eval(breakpoint.expression, frame.f_globals, frame.f_locals)
                            except:
                                val = sys.exc_info()[1]
                        finally:
                            if val is not None:
                                thread.additionalInfo.message = val

                    if stop:
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
                    if is_django_suspended(thread):
                        #django_stop = event == 'call' and is_django_render_call(frame)
                        stop = stop and is_django_resolve_call(frame.f_back) and not is_django_context_get_call(frame)
                        if stop:
                            info.pydev_django_resolve_frame = 1 #we remember that we've go into python code from django rendering frame

                elif info.pydev_step_cmd == CMD_STEP_OVER:
                    if is_django_suspended(thread):
                        django_stop = event == 'call' and is_django_render_call(frame)

                        stop = False
                    else:
                        if event == 'return' and info.pydev_django_resolve_frame is not None and is_django_resolve_call(frame.f_back):
                            #we return to Django suspend mode and should not stop before django rendering frame
                            info.pydev_step_stop = info.pydev_django_resolve_frame
                            info.pydev_django_resolve_frame = None
                            thread.additionalInfo.suspend_type = DJANGO_SUSPEND


                        stop = info.pydev_step_stop is frame and event in ('line', 'return')

                elif info.pydev_step_cmd == CMD_SMART_STEP_INTO:
                    stop = False
                    if info.pydev_smart_step_stop is frame:
                        info.pydev_func_name = None
                        info.pydev_smart_step_stop = None

                    if event == 'line' or event == 'exception':
                        curr_func_name = frame.f_code.co_name

                        #global context is set with an empty name
                        if curr_func_name in ('?', '<module>') or curr_func_name is None:
                            curr_func_name = ''

                        if curr_func_name == info.pydev_func_name:
                                stop = True

                elif info.pydev_step_cmd == CMD_STEP_RETURN:
                    stop = event == 'return' and info.pydev_step_stop is frame

                elif info.pydev_step_cmd == CMD_RUN_TO_LINE or info.pydev_step_cmd == CMD_SET_NEXT_STATEMENT:
                    stop = False

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

    def shouldStopOnDjangoBreak(self, frame, event, arg):
      mainDebugger, filename, info, thread = self._args
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
                              pydev_log.debug("Condition '%s' is evaluated to %s. Not suspending.\n" %(django_breakpoint.condition, val))
                      except:
                          pydev_log.info('Error while evaluating condition \'%s\': %s\n' % (django_breakpoint.condition, sys.exc_info()[1]))

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
                      frame = suspend_django(self, mainDebugger, thread, frame)
      return (flag, frame)

def add_exception_to_frame(frame, exception_info):
    frame.f_locals['__exception__'] = exception_info