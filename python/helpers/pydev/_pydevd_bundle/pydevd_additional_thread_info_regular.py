import sys
from _pydevd_bundle.pydevd_constants import STATE_RUN, PYTHON_SUSPEND, IS_JYTHON
# IFDEF CYTHON
# ELSE
from _pydevd_bundle.pydevd_frame import PyDBFrame
# ENDIF

version = 7

if not hasattr(sys, '_current_frames'):

    # Some versions of Jython don't have it (but we can provide a replacement)
    if IS_JYTHON:
        from java.lang import NoSuchFieldException
        from org.python.core import ThreadStateMapping
        try:
            cachedThreadState = ThreadStateMapping.getDeclaredField('globalThreadStates') # Dev version
        except NoSuchFieldException:
            cachedThreadState = ThreadStateMapping.getDeclaredField('cachedThreadState') # Release Jython 2.7.0
        cachedThreadState.accessible = True
        thread_states = cachedThreadState.get(ThreadStateMapping)

        def _current_frames():
            as_array = thread_states.entrySet().toArray()
            ret = {}
            for thread_to_state in as_array:
                thread = thread_to_state.getKey()
                if thread is None:
                    continue
                thread_state = thread_to_state.getValue()
                if thread_state is None:
                    continue

                frame = thread_state.frame
                if frame is None:
                    continue

                ret[thread.getId()] = frame
            return ret
    else:
        raise RuntimeError('Unable to proceed (sys._current_frames not available in this Python implementation).')
else:
    _current_frames = sys._current_frames

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
        current_frames = _current_frames()
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
