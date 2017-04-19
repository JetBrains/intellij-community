import sys
from _pydevd_bundle.pydevd_constants import STATE_RUN, PYTHON_SUSPEND
# IFDEF CYTHON
# ELSE
from _pydevd_bundle.pydevd_frame import PyDBFrame
# ENDIF

version = 2

#=======================================================================================================================
# PyDBAdditionalThreadInfo
#=======================================================================================================================
# IFDEF CYTHON
# cdef class PyDBAdditionalThreadInfo:
# ELSE
class PyDBAdditionalThreadInfo(object):
    # ENDIF

    # IFDEF CYTHON
    # cdef public int pydev_state;
    # cdef public object pydev_step_stop; # Actually, it's a frame or None
    # cdef public int pydev_step_cmd;
    # cdef public bint pydev_notify_kill;
    # cdef public object pydev_smart_step_stop; # Actually, it's a frame or None
    # cdef public bint pydev_django_resolve_frame;
    # cdef public object pydev_call_from_jinja2;
    # cdef public object pydev_call_inside_jinja2;
    # cdef public bint is_tracing;
    # cdef public tuple conditional_breakpoint_exception;
    # cdef public str pydev_message;
    # cdef public int suspend_type;
    # cdef public int pydev_next_line;
    # cdef public str pydev_func_name;
    # ELSE
    __slots__ = [
        'pydev_state',
        'pydev_step_stop',
        'pydev_step_cmd',
        'pydev_notify_kill',
        'pydev_smart_step_stop',
        'pydev_django_resolve_frame',
        'pydev_call_from_jinja2',
        'pydev_call_inside_jinja2',
        'is_tracing',
        'conditional_breakpoint_exception',
        'pydev_message',
        'suspend_type',
        'pydev_next_line',
        'pydev_func_name',
    ]
    # ENDIF

    def __init__(self):
        self.pydev_state = STATE_RUN
        self.pydev_step_stop = None
        self.pydev_step_cmd = -1 # Something as CMD_STEP_INTO, CMD_STEP_OVER, etc.
        self.pydev_notify_kill = False
        self.pydev_smart_step_stop = None
        self.pydev_django_resolve_frame = False
        self.pydev_call_from_jinja2 = None
        self.pydev_call_inside_jinja2 = None
        self.is_tracing = False
        self.conditional_breakpoint_exception = None
        self.pydev_message = ''
        self.suspend_type = PYTHON_SUSPEND
        self.pydev_next_line = -1
        self.pydev_func_name = '.invalid.' # Must match the type in cython


    def iter_frames(self, t):
        #sys._current_frames(): dictionary with thread id -> topmost frame
        current_frames = sys._current_frames()
        v = current_frames.get(t.ident)
        if v is not None:
            return [v]
        return []

    # IFDEF CYTHON
    # def create_db_frame(self, *args, **kwargs):
    #     raise AssertionError('This method should not be called on cython (PyDbFrame should be used directly).')
    # ELSE
    # just create the db frame directly
    create_db_frame = PyDBFrame
    # ENDIF

    def __str__(self):
        return 'State:%s Stop:%s Cmd: %s Kill:%s' % (
            self.pydev_state, self.pydev_step_stop, self.pydev_step_cmd, self.pydev_notify_kill)
