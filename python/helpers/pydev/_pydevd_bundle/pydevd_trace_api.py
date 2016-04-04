def add_line_breakpoint(plugin, pydb, type, file, line, condition, expression, func_name):
    return None

def add_exception_breakpoint(plugin, pydb, type, exception):
    return False

def remove_exception_breakpoint(plugin, pydb, type, exception):
    return False

def get_breakpoints(plugin, pydb):
    return None

def can_not_skip(plugin, pydb, pydb_frame, frame):
    return False

def has_exception_breaks(plugin):
    return False

def has_line_breaks(plugin):
    return False

def cmd_step_into(plugin, pydb, frame, event, args, stop_info, stop):
    return False

def cmd_step_over(plugin, pydb, frame, event, args, stop_info, stop):
    return False

def stop(plugin, pydb, frame, event, args, stop_info, arg, step_cmd):
    return False

def get_breakpoint(plugin, pydb, pydb_frame, frame, event, args):
    return None

def suspend(plugin, pydb, thread, frame):
    return None

def exception_break(plugin, pydb, pydb_frame, frame, args, arg):
    return None

def change_variable(plugin, frame, attr, expression):
    return False
