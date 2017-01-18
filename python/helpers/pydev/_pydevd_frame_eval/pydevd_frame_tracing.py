import sys
import traceback

from _pydev_bundle import pydev_log
from _pydev_imps._pydev_saved_modules import threading
from _pydevd_bundle.pydevd_comm import get_global_debugger, CMD_SET_BREAK
from pydevd_file_utils import get_abs_path_real_path_and_base_from_frame, NORM_PATHS_AND_BASE_CONTAINER


def update_globals_dict(globals_dict):
    new_globals = {'_pydev_stop_at_break': _pydev_stop_at_break}
    globals_dict.update(new_globals)


def handle_breakpoint(frame, info, global_debugger, breakpoint):
    # ok, hit breakpoint, now, we have to discover if it is a conditional breakpoint
    new_frame = frame
    condition = breakpoint.condition
    if condition is not None:
        try:
            val = eval(condition, new_frame.f_globals, new_frame.f_locals)
            if not val:
                return False

        except:
            if type(condition) != type(''):
                if hasattr(condition, 'encode'):
                    condition = condition.encode('utf-8')

            msg = 'Error while evaluating expression: %s\n' % (condition,)
            sys.stderr.write(msg)
            traceback.print_exc()
            if not global_debugger.suspend_on_breakpoint_exception:
                return False
            else:
                try:
                    # add exception_type and stacktrace into thread additional info
                    etype, value, tb = sys.exc_info()
                    try:
                        error = ''.join(traceback.format_exception_only(etype, value))
                        stack = traceback.extract_stack(f=tb.tb_frame.f_back)

                        # On self.set_suspend(thread, CMD_SET_BREAK) this info will be
                        # sent to the client.
                        info.conditional_breakpoint_exception = \
                            ('Condition:\n' + condition + '\n\nError:\n' + error, stack)
                    finally:
                        etype, value, tb = None, None, None
                except:
                    traceback.print_exc()

    if breakpoint.expression is not None:
        try:
            try:
                val = eval(breakpoint.expression, new_frame.f_globals, new_frame.f_locals)
            except:
                val = sys.exc_info()[1]
        finally:
            if val is not None:
                info.pydev_message = str(val)
    return True


def _pydev_stop_at_break():
    frame = sys._getframe(1)

    t = threading.currentThread()
    if t.additional_info.is_tracing:
        return

    if t.additional_info.pydev_step_cmd == -1:
        # do not handle breakpoints while stepping, because they're handled by old tracing function
        t.additional_info.is_tracing = True
        debugger = get_global_debugger()
        pydev_log.debug("Suspending at breakpoint in file: {} on line {}".format(frame.f_code.co_filename, frame.f_lineno))

        try:
            abs_path_real_path_and_base = NORM_PATHS_AND_BASE_CONTAINER[frame.f_code.co_filename]
        except:
            abs_path_real_path_and_base = get_abs_path_real_path_and_base_from_frame(frame)
        filename = abs_path_real_path_and_base[1]

        breakpoints_for_file = debugger.breakpoints.get(filename)
        line = frame.f_lineno
        breakpoint = breakpoints_for_file[line]
        if breakpoint and handle_breakpoint(frame, t.additional_info, debugger, breakpoint):
            debugger.set_suspend(t, CMD_SET_BREAK)
            debugger.do_wait_suspend(t, frame, 'line', None)

        t.additional_info.is_tracing = False


def pydev_trace_code_wrapper():
    # import this module again, because it's inserted inside user's code
    global _pydev_stop_at_break
    _pydev_stop_at_break()
