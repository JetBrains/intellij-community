
from _pydevd_bundle.pydevd_breakpoints import LineBreakpoint
from _pydevd_bundle.pydevd_constants import dict_iter_items, get_thread_id, PYTHON_SUSPEND
from _pydevd_bundle.pydevd_comm import CMD_SET_BREAK
import sys
from _pydevd_bundle import pydevd_vars
from _pydevd_bundle.pydevd_frame_utils import FCode

class IpnbLineBreakpoint(LineBreakpoint):

    def __init__(self, file, line, condition, func_name, expression):
        self.file = file
        LineBreakpoint.__init__(self, line, condition, func_name, expression)
        self.cell_file = None
        self.cell_line = line
        self.update_cell_file = False

    def is_triggered(self, template_frame_file, template_frame_line):
        return self.file == template_frame_file and self.line == template_frame_line

    def __str__(self):
        return "IpnbLineBreakpoint: %s-%d-%s-%s-%s" % \
               (self.file, self.line, self.cell_file, self.cell_line, self.update_cell_file)

    def __repr__(self):
        return "IpnbLineBreakpoint: %s-%d-%s-%s-%s" % \
               (self.file, self.line, self.cell_file, self.cell_line, self.update_cell_file)



def add_line_breakpoint(plugin, pydb, type, file, line, condition, expression, func_name):
    result = None
    if type == 'ipnb-line':
        breakpoint = IpnbLineBreakpoint(file, line, condition, func_name, expression)
        if not hasattr(pydb, 'ipnb_breakpoints'):
            _init_plugin_breaks(pydb)
        result = breakpoint, pydb.ipnb_breakpoints
        return result
    return result


def _init_plugin_breaks(pydb):
    pydb.ipnb_exception_break = {}
    pydb.ipnb_breakpoints = {}
    pydb.ipnb_cell_to_file = {}


def add_exception_breakpoint(plugin, pydb, type, exception):
    return False


def remove_exception_breakpoint(plugin, pydb, type, exception):
    return False


def get_breakpoints(plugin, pydb, type):
    if type == 'ipnb-line':
        return pydb.ipnb_breakpoints
    return None


def change_variable(plugin, frame, attr, expression):
    return False


def has_exception_breaks(plugin):
    if len(plugin.main_debugger.ipnb_exception_break) > 0:
        return True
    return False


#=======================================================================================================================
# Ipnb Step Commands
#=======================================================================================================================


def has_line_breaks(plugin):
    for file, breakpoints in dict_iter_items(plugin.main_debugger.ipnb_breakpoints):
        if len(breakpoints) > 0:
            return True
    return False


def can_not_skip(plugin, pydb, pydb_frame, frame):
    if pydb.ipnb_breakpoints:
        filename = frame.f_code.co_filename
        for file, breakpoints in dict_iter_items(pydb.ipnb_breakpoints):
            for line, breakpoint in dict_iter_items(breakpoints):
                if breakpoint.cell_file == filename:
                    return True
    return False


def cmd_step_into(plugin, pydb, frame, event, args, stop_info, stop):
    stop = event == "line" or event == "return"
    return stop, False


def cmd_step_over(plugin, pydb, frame, event, args, stop_info, stop):
    stop_frame = stop_info.pydev_step_stop
    stop = stop_frame is frame and (event == "line" or event == "return")
    return stop, False


def stop(plugin, pydb, frame, event, args, stop_info, arg, step_cmd):
    pass


def get_breakpoint(plugin, pydb, pydb_frame, frame, event, args):
    filename = frame.f_code.co_filename
    frame_line = frame.f_lineno
    for file, breakpoints in dict_iter_items(plugin.main_debugger.ipnb_breakpoints):
        for line, breakpoint in dict_iter_items(breakpoints):
            if breakpoint.cell_file == filename and breakpoint.cell_line == frame_line:
                return True, breakpoint, frame, "ipnb-line"
    return False


def suspend(plugin, pydb, thread, frame, bp_type):
    frame = IpnbFrame(frame, pydb)
    pydb.set_suspend(thread, CMD_SET_BREAK)
    thread.additional_info.suspend_type = PYTHON_SUSPEND
    pydevd_vars.add_additional_frame_by_id(get_thread_id(thread), {id(frame): frame})
    return frame


def exception_break(plugin, pydb, pydb_frame, frame, args, arg):
    return None


class IpnbFrame(object):

    def __init__(self, frame, py_db):
        file_name = py_db.ipnb_cell_to_file[frame.f_code.co_filename]
        self.f_code = FCode('<ipython cell>', file_name)
        self.f_lineno = frame.f_lineno
        self.f_back = frame.f_back
        self.f_globals = frame.f_globals
        self.f_locals = frame.f_locals
        self.f_trace = None