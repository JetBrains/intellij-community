cdef class PyDBAdditionalThreadInfo:
    cdef public int pydev_state;
    cdef public object pydev_step_stop; # Actually, it's a frame or None
    cdef public int pydev_step_cmd;
    cdef public bint pydev_notify_kill;
    cdef public object pydev_smart_step_stop; # Actually, it's a frame or None
    cdef public bint pydev_django_resolve_frame;
    cdef public object pydev_call_from_jinja2;
    cdef public object pydev_call_inside_jinja2;
    cdef public bint is_tracing;
    cdef public tuple conditional_breakpoint_exception;
    cdef public str pydev_message;
    cdef public int suspend_type;
    cdef public int pydev_next_line;
    cdef public str pydev_func_name;
    cdef public bint suspended_at_unhandled;
    cdef public str trace_suspend_type;
