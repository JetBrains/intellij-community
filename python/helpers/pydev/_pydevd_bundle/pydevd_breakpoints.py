from _pydevd_bundle.pydevd_constants import dict_iter_values, IS_PY24
from _pydev_bundle import pydev_log
from _pydevd_bundle import pydevd_import_class
from _pydevd_bundle.pydevd_frame_utils import add_exception_to_frame
from _pydev_imps._pydev_saved_modules import threading


class ExceptionBreakpoint(object):

    def __init__(
            self,
            qname,
            condition,
            expression,
            notify_on_handled_exceptions,
            notify_on_unhandled_exceptions,
            notify_on_first_raise_only,
            ignore_libraries
    ):
        exctype = get_exception_class(qname)
        self.qname = qname
        if exctype is not None:
            self.name = exctype.__name__
        else:
            self.name = None

        self.condition = condition
        self.expression = expression
        self.notify_on_unhandled_exceptions = notify_on_unhandled_exceptions
        self.notify_on_handled_exceptions = notify_on_handled_exceptions
        self.notify_on_first_raise_only = notify_on_first_raise_only
        self.ignore_libraries = ignore_libraries

        self.type = exctype

    def __str__(self):
        return self.qname

    @property
    def has_condition(self):
        return self.condition is not None

    def handle_hit_condition(self, frame):
        return False


class LineBreakpoint(object):

    def __init__(self, line, condition, func_name, expression, suspend_policy="NONE", hit_condition=None, is_logpoint=False):
        self.line = line
        self.condition = condition
        self.func_name = func_name
        self.expression = expression
        self.suspend_policy = suspend_policy
        self.hit_condition = hit_condition
        self._hit_count = 0
        self._hit_condition_lock = threading.Lock()
        self.is_logpoint = is_logpoint

    @property
    def has_condition(self):
        return self.condition is not None or self.hit_condition is not None

    def handle_hit_condition(self, frame):
        if self.hit_condition is None:
            return False
        ret = False
        with self._hit_condition_lock:
            self._hit_count += 1
            expr = self.hit_condition.replace('@HIT@', str(self._hit_count))
            try:
                ret = bool(eval(expr, frame.f_globals, frame.f_locals))
            except Exception:
                ret = False
        return ret


def get_exception_full_qname(exctype):
    if not exctype:
        return None
    return str(exctype.__module__) + '.' + exctype.__name__


def get_exception_name(exctype):
    if not exctype:
        return None
    return exctype.__name__


def get_exception_breakpoint(exctype, exceptions):
    exception_full_qname = get_exception_full_qname(exctype)

    exc = None
    if exceptions is not None:
        try:
            return exceptions[exception_full_qname]
        except KeyError:
            for exception_breakpoint in dict_iter_values(exceptions):
                if exception_breakpoint.type is not None and issubclass(exctype, exception_breakpoint.type):
                    if exc is None or issubclass(exception_breakpoint.type, exc.type):
                        exc = exception_breakpoint
    return exc


def stop_on_unhandled_exception(py_db, thread, additional_info, arg):
    from _pydevd_bundle.pydevd_frame import handle_breakpoint_condition, handle_breakpoint_expression
    exctype, value, tb = arg
    break_on_uncaught_exceptions = py_db.break_on_uncaught_exceptions
    if break_on_uncaught_exceptions:
        exception_breakpoint = get_exception_breakpoint(exctype, break_on_uncaught_exceptions)
    else:
        exception_breakpoint = None

    if not exception_breakpoint:
        return

    if tb is None:  # sometimes it can be None, e.g. with GTK
        return

    if exctype in (KeyboardInterrupt, SystemExit):
        return

    frames = []
    user_frame = None

    while tb:
        frame = tb.tb_frame
        if exception_breakpoint.ignore_libraries and py_db.in_project_scope(frame.f_code.co_filename):
            user_frame = tb.tb_frame
        frames.append(tb.tb_frame)
        tb = tb.tb_next

    frames_byid = dict([(id(frame), frame) for frame in frames])
    if exception_breakpoint.ignore_libraries and user_frame is not None:
        frame = user_frame
    else:
        frame = frames[-1]
    add_exception_to_frame(frame, arg)
    if exception_breakpoint.condition is not None:
        eval_result = handle_breakpoint_condition(py_db, additional_info, exception_breakpoint, frame)
        if not eval_result:
            return

    if exception_breakpoint.expression is not None:
        handle_breakpoint_expression(exception_breakpoint, additional_info, frame)

    try:
        additional_info.pydev_message = exception_breakpoint.qname
    except:
        additional_info.pydev_message = exception_breakpoint.qname.encode('utf-8')

    additional_info.pydev_message = 'python-%s' % additional_info.pydev_message

    pydev_log.debug('Handling post-mortem stop on exception breakpoint %s' % (exception_breakpoint.qname,))

    py_db.stop_on_unhandled_exception(thread, frame, frames_byid, arg)


def get_exception_class(kls):
    if IS_PY24 and "BaseException" == kls:
        kls = "Exception"

    try:
        return eval(kls)
    except:
        return pydevd_import_class.import_name(kls)
