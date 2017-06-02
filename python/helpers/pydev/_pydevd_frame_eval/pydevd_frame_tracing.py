import sys

from _pydev_bundle import pydev_log
from _pydev_imps._pydev_saved_modules import threading
from _pydevd_bundle.pydevd_comm import get_global_debugger, CMD_SET_BREAK
from pydevd_file_utils import get_abs_path_real_path_and_base_from_frame, NORM_PATHS_AND_BASE_CONTAINER
from _pydevd_bundle.pydevd_frame import handle_breakpoint_condition, handle_breakpoint_expression


class DummyTracingHolder:
    dummy_trace_func = None

    def set_trace_func(self, trace_func):
        self.dummy_trace_func = trace_func


dummy_tracing_holder = DummyTracingHolder()


def update_globals_dict(globals_dict):
    new_globals = {'_pydev_stop_at_break': _pydev_stop_at_break}
    globals_dict.update(new_globals)


def handle_breakpoint(frame, thread, global_debugger, breakpoint):
    # ok, hit breakpoint, now, we have to discover if it is a conditional breakpoint
    new_frame = frame
    condition = breakpoint.condition
    info = thread.additional_info
    if condition is not None:
        eval_result = handle_breakpoint_condition(global_debugger, info, breakpoint, new_frame)
        if not eval_result:
            return False

    if breakpoint.expression is not None:
        handle_breakpoint_expression(breakpoint, info, new_frame)

    if breakpoint.suspend_policy == "ALL":
        global_debugger.suspend_all_other_threads(thread)

    return True


def _get_line_for_frame(frame):
    # it's absolutely necessary to reset tracing function for frame in order to get the real line number
    tracing_func = frame.f_trace
    frame.f_trace = None
    line = frame.f_lineno
    frame.f_trace = tracing_func
    return line


def _pydev_stop_at_break():
    frame = sys._getframe(1)
    t = threading.currentThread()
    if t.additional_info.is_tracing:
        return

    if t.additional_info.pydev_step_cmd == -1 and frame.f_trace in (None, dummy_tracing_holder.dummy_trace_func):
        # do not handle breakpoints while stepping, because they're handled by old tracing function
        t.additional_info.is_tracing = True
        debugger = get_global_debugger()

        try:
            abs_path_real_path_and_base = NORM_PATHS_AND_BASE_CONTAINER[frame.f_code.co_filename]
        except:
            abs_path_real_path_and_base = get_abs_path_real_path_and_base_from_frame(frame)
        filename = abs_path_real_path_and_base[1]

        breakpoints_for_file = debugger.breakpoints.get(filename)
        line = _get_line_for_frame(frame)
        try:
            breakpoint = breakpoints_for_file[line]
        except KeyError:
            pydev_log.debug("Couldn't find breakpoint in the file {} on line {}".format(frame.f_code.co_filename, line))
            t.additional_info.is_tracing = False
            return
        if breakpoint and handle_breakpoint(frame, t, debugger, breakpoint):
            pydev_log.debug("Suspending at breakpoint in file: {} on line {}".format(frame.f_code.co_filename, line))
            debugger.set_suspend(t, CMD_SET_BREAK)
            debugger.do_wait_suspend(t, frame, 'line', None)

        t.additional_info.is_tracing = False


def pydev_trace_code_wrapper():
    # import this module again, because it's inserted inside user's code
    global _pydev_stop_at_break
    _pydev_stop_at_break()
