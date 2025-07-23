
import os
from _pydevd_bundle import pydevd_vars
from _pydevd_bundle.pydevd_breakpoints import LineBreakpoint
from _pydevd_bundle.pydevd_comm import CMD_SET_BREAK, CMD_ADD_EXCEPTION_BREAK
from _pydevd_bundle.pydevd_constants import dict_iter_items, get_thread_id, JUPYTER_SUSPEND, dict_keys
from _pydevd_bundle.pydevd_frame_utils import FCode, add_exception_to_frame


class JupyterLineBreakpoint(LineBreakpoint):
    def __init__(self, file, line, condition, func_name, expression, hit_condition=None, is_logpoint=False):
        LineBreakpoint.__init__(self, line, condition, func_name, expression, hit_condition=hit_condition, is_logpoint=is_logpoint)
        self.file = file
        self.cell_file = None

    def is_triggered(self, template_frame_file, template_frame_line):
        return self.file == template_frame_file and self.line == template_frame_line

    def __str__(self):
        return "JupyterLineBreakpoint: %s-%d-%s" % (self.file, self.line, self.cell_file)

    def __repr__(self):
        return '<JupyterLineBreakpoint(%s, %s, %s, %s, %s, %s)>' % (self.file, self.line, self.condition, self.func_name, self.expression,
                                                                    self.cell_file)


def add_line_breakpoint(plugin, pydb, type, file, line, condition, expression, func_name, hit_condition=None, is_logpoint=False):
    if type == 'jupyter-line':
        breakpoint = JupyterLineBreakpoint(
            file,
            line,
            condition,
            func_name,
            expression,
            hit_condition=hit_condition,
            is_logpoint=is_logpoint
        )
        if not hasattr(pydb, 'jupyter_breakpoints'):
            _init_plugin_breaks(pydb)
        return breakpoint, pydb.jupyter_breakpoints
    return None


def _init_plugin_breaks(pydb):
    pydb.jupyter_exception_break = {}
    pydb.jupyter_breakpoints = {}


def add_exception_breakpoint(plugin, pydb, type, exception):
    if type == 'jupyter':
        if not hasattr(pydb, 'jupyter_exception_break'):
            _init_plugin_breaks(pydb)
        pydb.jupyter_exception_break[exception] = True
        pydb.set_tracing_for_untraced_contexts()
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
        if hasattr(pydb, 'jupyter_breakpoints'):
            return pydb.jupyter_breakpoints
    return None


def change_variable(plugin, frame, attr, expression):
    return False


def has_exception_breaks(plugin):
    if hasattr(plugin.main_debugger, 'jupyter_exception_break'):
        if len(plugin.main_debugger.jupyter_exception_break) > 0:
            return True
    return False


#=======================================================================================================================
# Jupyter Step Commands
#=======================================================================================================================


def has_line_breaks(plugin):
    if hasattr(plugin.main_debugger, 'jupyter_breakpoints'):
        for file, breakpoints in dict_iter_items(plugin.main_debugger.jupyter_breakpoints):
            if len(breakpoints) > 0:
                return True
    return False


def can_not_skip(plugin, pydb, frame, info):
    step_cmd = info.pydev_step_cmd
    if step_cmd == 108 and _is_equals(frame, _get_stop_frame(info)):
        return True
    if not hasattr(pydb, 'cell_info') or not hasattr(pydb, 'jupyter_breakpoints'):
        return False
    if pydb.jupyter_breakpoints:
        filename = frame.f_code.co_filename
        cell_info = pydb.cell_info
        if is_cell_filename(filename):
            if filename not in cell_info.cell_filename_to_cell_id_map:
                cell_info.cache_cell_mapping(filename)
            cell_id = cell_info.cell_filename_to_cell_id_map[filename]
            if cell_id in pydb.jupyter_breakpoints:
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
        filename = frame.f_code.co_filename
        if _is_inside_jupyter_cell(frame, pydb) and not filename.endswith("iostream.py"):
            stop_info['jupyter_stop'] = event == "line"
            plugin_stop = stop_info['jupyter_stop']
    return stop, plugin_stop


def _is_equals(frame, other_frame):
    # We can't compare frames directly, because Jupyter compiles ast nodes
    # in cell separately. At the same time, the frame filename is unique and stays
    # the same within a cell.
    if frame is None or other_frame is None:
        return False
    return frame.f_code.co_filename == other_frame.f_code.co_filename \
           and ((frame.f_code.co_name.startswith('<cell line:')
                 and other_frame.f_code.co_name.startswith('<cell line:'))
                or frame.f_code.co_name == other_frame.f_code.co_name)


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
        if _is_inside_jupyter_cell(frame, pydb):
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
    if 'jupyter_stop' in stop_info and stop_info['jupyter_stop'] and _is_inside_jupyter_cell(frame, pydb):
        frame = suspend_jupyter(main_debugger, thread, frame, step_cmd)
        if frame:
            main_debugger.do_wait_suspend(thread, frame, event, arg)
            return True
    return False


def get_breakpoint(plugin, pydb, frame, event, args):
    filename = frame.f_code.co_filename
    frame_line = frame.f_lineno
    if event == "line":
        if hasattr(pydb, 'cell_info'):
            cell_info = pydb.cell_info
            if is_cell_filename(filename):
                if filename not in cell_info.cell_filename_to_cell_id_map:
                    cell_info.cache_cell_mapping(filename)

                cell_id = cell_info.cell_filename_to_cell_id_map[filename]
                if cell_id in pydb.jupyter_breakpoints:
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


def send_cell_modified_warning_once(pydb, cell_name):
    if cell_name not in pydb.warn_once_map:
        pydb.warn_once_map[cell_name] = True
        cmd = pydb.cmd_factory.make_show_warning_message("jupyter")
        pydb.writer.add_command(cmd)


def _is_inside_jupyter_cell(frame, pydb):
    while frame is not None:
        filename = frame.f_code.co_filename
        file_basename = os.path.basename(filename)
        if hasattr(pydb, 'cell_info'):
            if is_cell_filename(filename):
                if filename not in pydb.cell_info.cell_filename_to_cell_id_map:
                    send_cell_modified_warning_once(pydb, file_basename)
                    # IDE side doesn't know about the cell, so we should ignore it
                    return False
                return True
            frame = frame.f_back
    return False


def _get_ipython_safely():
    try:
        return get_ipython()
    except NameError:
        return None


def suspend(plugin, pydb, thread, frame, bp_type):
    if bp_type == 'jupyter-line' and _is_inside_jupyter_cell(frame, pydb):
        return suspend_jupyter(pydb, thread, frame)
    return None


def exception_break(plugin, pydb, frame, args, arg):
    filename = frame.f_code.co_filename
    if pydb.jupyter_exception_break and is_cell_filename(filename):
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
    if hasattr(pydb, 'cell_info') and is_cell_filename(filename):
        return pydb.cell_info.cell_filename_to_cell_id_map[filename]
    else:
        return filename


def is_cell_filename(filename):
    try:
        import linecache
        import IPython
        ipython_major_version = int(IPython.__version__[0])
        if hasattr(linecache, 'cache'):
            if ipython_major_version < 8:
                if hasattr(linecache, '_ipython_cache'):
                    if filename in linecache._ipython_cache:
                        cached_value = linecache._ipython_cache[filename]
                        is_util_code = "pydev_util_command" in cached_value[2][0]
                        return not is_util_code
            else:
                if filename in linecache.cache:
                    cached_value = linecache.cache[filename]
                    is_not_library = cached_value[1] is None
                    is_util_code = "pydev_util_command" in cached_value[2][0]
                    return is_not_library and not is_util_code
    except:
        pass
    return False


class JupyterFrame(object):
    def __init__(self, frame, pydb):
        file_name = _convert_filename(frame, pydb)
        self.f_code = FCode('<ipython cell>', file_name)
        self.f_lineno = frame.f_lineno
        self.f_back = frame
        self.f_globals = frame.f_globals
        self.f_locals = frame.f_locals
        self.f_trace = None