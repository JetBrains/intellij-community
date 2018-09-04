
from _pydevd_bundle.pydevd_breakpoints import LineBreakpoint
from _pydevd_bundle.pydevd_constants import dict_iter_items, get_thread_id, JUPYTER_SUSPEND
from _pydevd_bundle.pydevd_comm import CMD_SET_BREAK
from _pydevd_bundle import pydevd_vars
from _pydevd_bundle.pydevd_frame_utils import FCode

import os


class JupyterLineBreakpoint(LineBreakpoint):
    def __init__(self, file, line, condition, func_name, expression):
        self.file = file
        LineBreakpoint.__init__(self, line, condition, func_name, expression)
        self.cell_file = None
        self.cell_line = line
        self.update_cell_file = False

    def is_triggered(self, template_frame_file, template_frame_line):
        return self.file == template_frame_file and self.line == template_frame_line

    def __str__(self):
        return "JupyterLineBreakpoint: %s-%d-%s-%s-%s" % \
               (self.file, self.line, self.cell_file, self.cell_line, self.update_cell_file)

    def __repr__(self):
        return "JupyterLineBreakpoint: %s-%d-%s-%s-%s" % \
               (self.file, self.line, self.cell_file, self.cell_line, self.update_cell_file)


def add_line_breakpoint(plugin, pydb, type, file, line, condition, expression, func_name):
    if type == 'jupyter-line':
        breakpoint = JupyterLineBreakpoint(file, line, condition, func_name, expression)
        if not hasattr(pydb, 'jupyter_breakpoints'):
            _init_plugin_breaks(pydb)
        return breakpoint, pydb.jupyter_breakpoints
    return None


def _init_plugin_breaks(pydb):
    pydb.jupyter_exception_break = {}
    pydb.jupyter_breakpoints = {}
    pydb.jupyter_cell_to_file = {}


def add_exception_breakpoint(plugin, pydb, type, exception):
    return False


def remove_exception_breakpoint(plugin, pydb, type, exception):
    return False


def get_breakpoints(plugin, pydb, type):
    if type == 'jupyter-line':
        return pydb.jupyter_breakpoints
    return None


def change_variable(plugin, frame, attr, expression):
    return False


def has_exception_breaks(plugin):
    if len(plugin.main_debugger.jupyter_exception_break) > 0:
        return True
    return False


#=======================================================================================================================
# Jupyter Step Commands
#=======================================================================================================================


def has_line_breaks(plugin):
    for file, breakpoints in dict_iter_items(plugin.main_debugger.jupyter_breakpoints):
        if len(breakpoints) > 0:
            return True
    return False


def can_not_skip(plugin, pydb, pydb_frame, frame):
    if pydb.jupyter_breakpoints:
        filename = frame.f_code.co_filename
        for file, breakpoints in dict_iter_items(pydb.jupyter_breakpoints):
            for line, breakpoint in dict_iter_items(breakpoints):
                if breakpoint.cell_file == filename:
                    return True
    return False


def _is_jupyter_suspended(thread):
    return thread.additional_info.suspend_type == JUPYTER_SUSPEND


def cmd_step_into(plugin, pydb, frame, event, args, stop_info, stop):
    plugin_stop = False
    thread = args[3]
    if _is_jupyter_suspended(thread):
        stop = False
        if _is_inside_jupyter_cell(frame):
            stop_info['jupyter_stop'] = event == "line"
            plugin_stop = stop_info['jupyter_stop']
    return stop, plugin_stop


def cmd_step_over(plugin, pydb, frame, event, args, stop_info, stop):
    thread = args[3]
    if _is_jupyter_suspended(thread) and _is_inside_jupyter_cell(frame):
        stop_frame = stop_info.pydev_step_stop
        stop = stop_frame is frame and (event == "line" or event == "return")
    return stop, stop


def stop(plugin, pydb, frame, event, args, stop_info, arg, step_cmd):
    main_debugger = args[0]
    thread = args[3]
    if 'jupyter_stop' in stop_info and stop_info['jupyter_stop'] and _is_inside_jupyter_cell(frame):
        frame = suspend_jupyter(main_debugger, thread, frame, step_cmd)
        if frame:
            main_debugger.do_wait_suspend(thread, frame, event, arg)
            return True
    return False


def get_breakpoint(plugin, pydb, pydb_frame, frame, event, args):
    filename = frame.f_code.co_filename
    frame_line = frame.f_lineno
    if event == "line":
        for file, breakpoints in dict_iter_items(plugin.main_debugger.jupyter_breakpoints):
            for line, breakpoint in dict_iter_items(breakpoints):
                if breakpoint.cell_file == filename and breakpoint.cell_line == frame_line:
                    return True, breakpoint, frame, "jupyter-line"
    return False


def suspend_jupyter(pydb, thread, frame, cmd=CMD_SET_BREAK):
    frame = JupyterFrame(frame, pydb)
    pydb.set_suspend(thread, cmd)
    thread.additional_info.suspend_type = JUPYTER_SUSPEND
    pydevd_vars.add_additional_frame_by_id(get_thread_id(thread), {id(frame): frame})
    return frame


def _is_inside_jupyter_cell(frame):
    while frame is not None:
        filename = frame.f_code.co_filename
        file_basename = os.path.basename(filename)
        if file_basename.startswith("<ipython-input"):
            return True
        frame = frame.f_back
    return False


def suspend(plugin, pydb, thread, frame, bp_type):
    if bp_type == 'jupyter-line':
        return suspend_jupyter(pydb, thread, frame)
    return None


def exception_break(plugin, pydb, pydb_frame, frame, args, arg):
    return None


def _convert_filename(frame, pydb):
    filename = frame.f_code.co_filename
    file_basename = os.path.basename(filename)
    if file_basename.startswith("<ipython-input"):
        return pydb.jupyter_cell_to_file[frame.f_code.co_filename]
    else:
        return filename


class JupyterFrame(object):
    def __init__(self, frame, pydb):
        file_name = _convert_filename(frame, pydb)
        self.f_code = FCode('<ipython cell>', file_name)
        self.f_lineno = frame.f_lineno
        self.f_back = frame.f_back
        self.f_globals = frame.f_globals
        self.f_locals = frame.f_locals
        self.f_trace = None