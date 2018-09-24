
from _pydevd_bundle.pydevd_breakpoints import LineBreakpoint
from _pydevd_bundle.pydevd_constants import dict_iter_items, get_thread_id, JUPYTER_SUSPEND, dict_keys
from _pydevd_bundle.pydevd_comm import CMD_SET_BREAK, CMD_ADD_EXCEPTION_BREAK
from _pydevd_bundle import pydevd_vars
from _pydevd_bundle.pydevd_frame_utils import FCode, add_exception_to_frame

import os


class JupyterLineBreakpoint(LineBreakpoint):
    def __init__(self, file, line, condition, func_name, expression):
        LineBreakpoint.__init__(self, line, condition, func_name, expression)
        self.file = file
        self.cell_file = None

    def is_triggered(self, template_frame_file, template_frame_line):
        return self.file == template_frame_file and self.line == template_frame_line

    def __str__(self):
        return "JupyterLineBreakpoint: %s-%d-%s" % (self.file, self.line, self.cell_file)

    def __repr__(self):
        return "JupyterLineBreakpoint: %s-%d-%s" % (self.file, self.line, self.cell_file)


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
    pydb.jupyter_cell_name_to_id = {}
    pydb.jupyter_cell_id_to_name = {}


def add_exception_breakpoint(plugin, pydb, type, exception):
    if type == 'jupyter':
        if not hasattr(pydb, 'jupyter_exception_break'):
            _init_plugin_breaks(pydb)
        pydb.jupyter_exception_break[exception] = True
        pydb.set_tracing_for_untraced_contexts_if_not_frame_eval()
        return True
    return False


def remove_exception_breakpoint(plugin, pydb, type, exception):
    if type == 'jupyter':
        try:
            del pydb.jupyter_exception_break[exception]
            return True
        except:
            pass
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


def can_not_skip(plugin, pydb, pydb_frame, frame, info):
    step_cmd = info.pydev_step_cmd
    if step_cmd == 108 and _is_equals(frame, _get_stop_frame(info)):
        return True
    if pydb.jupyter_breakpoints:
        filename = frame.f_code.co_filename
        if filename in pydb.jupyter_cell_name_to_id:
            cell_id = pydb.jupyter_cell_name_to_id[filename]
            line_to_bp = pydb.jupyter_breakpoints[cell_id]
            if len(line_to_bp) > 0:
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


def _is_equals(frame, other_frame):
    # we can't compare frame directly, because Jupyter compiles ast nodes in cell separately
    return frame.f_code.co_filename == other_frame.f_code.co_filename and \
        frame.f_code.co_name == other_frame.f_code.co_name


def _get_stop_frame(info):
    stop_frame = None
    if hasattr(info, 'pydev_step_stop'):
        if isinstance(info.pydev_step_stop, JupyterFrame):
            stop_frame = info.pydev_step_stop.f_back
        else:
            stop_frame = info.pydev_step_stop
    return stop_frame


def cmd_step_over(plugin, pydb, frame, event, args, stop_info, stop):
    thread = args[3]
    plugin_stop = False
    info = args[2]
    if _is_jupyter_suspended(thread):
        stop = False
        if _is_inside_jupyter_cell(frame):
            stop_frame = _get_stop_frame(info)
            if stop_frame is None:
                if event == "line":
                    plugin_stop = stop_info['jupyter_stop'] = True
            else:
                if event == "line":
                    stop_info['jupyter_stop'] = _is_equals(frame, stop_frame)
                    plugin_stop = stop_info['jupyter_stop']
                elif event == "return":
                    if not _is_equals(frame.f_back, stop_frame):
                        info.pydev_step_stop = info.pydev_step_stop.f_back
    return stop, plugin_stop


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
        if filename in pydb.jupyter_cell_name_to_id:
            cell_id = pydb.jupyter_cell_name_to_id[filename]
            line_to_bp = pydb.jupyter_breakpoints[cell_id]
            if frame_line in line_to_bp:
                bp = line_to_bp[frame_line]
                return True, bp, frame, "jupyter-line"
    return False


def suspend_jupyter(pydb, thread, frame, cmd=CMD_SET_BREAK, message=None):
    frame = JupyterFrame(frame, pydb)
    pydb.set_suspend(thread, cmd)
    thread.additional_info.suspend_type = JUPYTER_SUSPEND
    if cmd == CMD_ADD_EXCEPTION_BREAK:
        # send exception name as message
        if message:
            message = str(message)
        thread.additional_info.pydev_message = message
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
    if pydb.jupyter_exception_break and _is_inside_jupyter_cell(frame):
        thread = args[3]
        exception, value, trace = arg
        exception_type = dict_keys(pydb.jupyter_exception_break)[0]
        suspend_frame = suspend_jupyter(pydb, thread, frame, CMD_ADD_EXCEPTION_BREAK, message="jupyter-%s" % exception_type)
        if suspend_frame:
            add_exception_to_frame(suspend_frame, (exception, value, trace))
            flag = True
            suspend_frame.f_back = frame
            return flag, suspend_frame
    return None


def _convert_filename(frame, pydb):
    filename = frame.f_code.co_filename
    file_basename = os.path.basename(filename)
    if file_basename.startswith("<ipython-input"):
        return pydb.jupyter_cell_name_to_id[filename]
    else:
        return filename


class JupyterFrame(object):
    def __init__(self, frame, pydb):
        file_name = _convert_filename(frame, pydb)
        self.f_code = FCode('<ipython cell>', file_name)
        self.f_lineno = frame.f_lineno
        self.f_back = frame
        self.f_globals = frame.f_globals
        self.f_locals = frame.f_locals
        self.f_trace = None