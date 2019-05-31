import traceback

from _pydev_bundle.pydev_is_thread_alive import is_thread_alive
from _pydev_imps._pydev_saved_modules import threading
from _pydevd_bundle.pydevd_constants import get_thread_id, IS_IRONPYTHON
from _pydevd_bundle.pydevd_dont_trace_files import DONT_TRACE
from _pydevd_bundle.pydevd_kill_all_pydevd_threads import kill_all_pydev_threads
from pydevd_file_utils import get_abs_path_real_path_and_base_from_frame, NORM_PATHS_AND_BASE_CONTAINER
from _pydevd_bundle.pydevd_tracing import SetTrace
# IFDEF CYTHON
# # In Cython, PyDBAdditionalThreadInfo is bundled in the file.
# from cpython.object cimport PyObject
# from cpython.ref cimport Py_INCREF, Py_XDECREF
# ELSE
from _pydevd_bundle.pydevd_additional_thread_info import PyDBAdditionalThreadInfo
from _pydevd_bundle.pydevd_frame import PyDBFrame

# ENDIF

threadingCurrentThread = threading.currentThread
get_file_type = DONT_TRACE.get

# IFDEF CYTHON -- DONT EDIT THIS FILE (it is automatically generated)
# cdef dict global_cache_skips
# cdef dict global_cache_frame_skips
# ELSE
# ENDIF


# Cache where we should keep that we completely skipped entering some context.
# It needs to be invalidated when:
# - Breakpoints are changed
# It can be used when running regularly (without step over/step in/step return)
global_cache_skips = {}
global_cache_frame_skips = {}


def trace_dispatch(py_db, frame, event, arg):
    t = threadingCurrentThread()

    if getattr(t, 'pydev_do_not_trace', None):
        return None

    try:
        additional_info = t.additional_info
        if additional_info is None:
            raise AttributeError()
    except:
        additional_info = t.additional_info = PyDBAdditionalThreadInfo()

    thread_tracer = ThreadTracer((py_db, t, additional_info, global_cache_skips, global_cache_frame_skips))
# IFDEF CYTHON
#     t._tracer = thread_tracer # Hack for cython to keep it alive while the thread is alive (just the method in the SetTrace is not enough).
# ELSE
# ENDIF
    SetTrace(thread_tracer.__call__)
    return thread_tracer.__call__(frame, event, arg)


# IFDEF CYTHON
# cdef class SafeCallWrapper:
#   cdef method_object
#   def __init__(self, method_object):
#       self.method_object = method_object
#   def  __call__(self, *args):
#       #Cannot use 'self' once inside the delegate call since we are borrowing the self reference f_trace field
#       #in the frame, and that reference might get destroyed by set trace on frame and parents
#       cdef PyObject* method_obj = <PyObject*> self.method_object
#       Py_INCREF(<object>method_obj)
#       ret = (<object>method_obj)(*args)
#       Py_XDECREF (method_obj)
#       return SafeCallWrapper(ret) if ret is not None else None
# cdef class ThreadTracer:
#     cdef public tuple _args;
#     def __init__(self, tuple args):
#         self._args = args
# ELSE
class ThreadTracer:
    def __init__(self, args):
        self._args = args

    # ENDIF

    def __call__(self, frame, event, arg):
        ''' This is the callback used when we enter some context in the debugger.

        We also decorate the thread we are in with info about the debugging.
        The attributes added are:
            pydev_state
            pydev_step_stop
            pydev_step_cmd
            pydev_notify_kill

        :param PyDB py_db:
            This is the global debugger (this method should actually be added as a method to it).
        '''
        # IFDEF CYTHON
        # cdef str filename;
        # cdef str base;
        # cdef int pydev_step_cmd;
        # cdef tuple cache_key;
        # cdef dict cache_skips;
        # cdef bint is_stepping;
        # cdef tuple abs_path_real_path_and_base;
        # cdef PyDBAdditionalThreadInfo additional_info;
        # ENDIF
        # print('ENTER: trace_dispatch', frame.f_code.co_filename, frame.f_lineno, event, frame.f_code.co_name)
        py_db, t, additional_info, cache_skips, frame_skips_cache = self._args
        pydev_step_cmd = additional_info.pydev_step_cmd
        is_stepping = pydev_step_cmd != -1

        try:
            if py_db._finish_debugging_session:
                if not py_db._termination_event_set:
                    # that was not working very well because jython gave some socket errors
                    try:
                        if py_db.output_checker is None:
                            kill_all_pydev_threads()
                    except:
                        traceback.print_exc()
                    py_db._termination_event_set = True
                return None

            # if thread is not alive, cancel trace_dispatch processing
            if not is_thread_alive(t):
                py_db._process_thread_not_alive(get_thread_id(t))
                return None  # suspend tracing

            try:
                # Make fast path faster!
                abs_path_real_path_and_base = NORM_PATHS_AND_BASE_CONTAINER[frame.f_code.co_filename]
            except:
                abs_path_real_path_and_base = get_abs_path_real_path_and_base_from_frame(frame)

            if py_db.thread_analyser is not None:
                py_db.thread_analyser.log_event(frame)

            if py_db.asyncio_analyser is not None:
                py_db.asyncio_analyser.log_event(frame)

            filename = abs_path_real_path_and_base[1]
            # Note: it's important that the context name is also given because we may hit something once
            # in the global context and another in the local context.
            cache_key = (frame.f_lineno, frame.f_code.co_name, filename)
            if not is_stepping and cache_key in cache_skips:
                # print('skipped: trace_dispatch (cache hit)', cache_key, frame.f_lineno, event, frame.f_code.co_name)
                return None

            file_type = get_file_type(
                abs_path_real_path_and_base[-1])  # we don't want to debug threading or anything related to pydevd

            if file_type is not None:
                if file_type == 1:  # inlining LIB_FILE = 1
                    if py_db.not_in_scope(filename):
                        # print('skipped: trace_dispatch (not in scope)', abs_path_real_path_and_base[-1], frame.f_lineno, event, frame.f_code.co_name, file_type)
                        cache_skips[cache_key] = 1
                        return None
                else:
                    # print('skipped: trace_dispatch', abs_path_real_path_and_base[-1], frame.f_lineno, event, frame.f_code.co_name, file_type)
                    cache_skips[cache_key] = 1
                    return None

            if is_stepping:
                if py_db.is_filter_enabled and py_db.is_ignored_by_filters(filename):
                    # ignore files matching stepping filters
                    return None
                if py_db.is_filter_libraries and py_db.not_in_scope(filename):
                    # ignore library files while stepping
                    return None

            # print('trace_dispatch', base, frame.f_lineno, event, frame.f_code.co_name, file_type)
            if additional_info.is_tracing:
                return None  # we don't wan't to trace code invoked from pydevd_frame.trace_dispatch

            # Just create PyDBFrame directly (removed support for Python versions < 2.5, which required keeping a weak
            # reference to the frame).
            ret = PyDBFrame((py_db, filename, additional_info, t, frame_skips_cache,
                             (frame.f_code.co_name, frame.f_code.co_firstlineno, filename))).trace_dispatch(frame,
                                                                                                            event, arg)
            if ret is None:
                cache_skips[cache_key] = 1
                return None

            # IFDEF CYTHON
            # return SafeCallWrapper(ret)
            # ELSE
            return ret
            # ENDIF

        except SystemExit:
            return None

        except Exception:
            if py_db._finish_debugging_session:
                return None  # Don't log errors when we're shutting down.
            # Log it
            try:
                if traceback is not None:
                    # This can actually happen during the interpreter shutdown in Python 2.7
                    traceback.print_exc()
            except:
                # Error logging? We're really in the interpreter shutdown...
                # (https://github.com/fabioz/PyDev.Debugger/issues/8)
                pass
            return None


if IS_IRONPYTHON:
    # This is far from ideal, as we'll leak frames (we'll always have the last created frame, not really
    # the last topmost frame saved -- this should be Ok for our usage, but it may leak frames and things
    # may live longer... as IronPython is garbage-collected, things should live longer anyways, so, it
    # shouldn't be an issue as big as it's in CPython -- it may still be annoying, but this should
    # be a reasonable workaround until IronPython itself is able to provide that functionality).
    #
    # See: https://github.com/IronLanguages/main/issues/1630
    from _pydevd_bundle.pydevd_additional_thread_info_regular import _tid_to_last_frame

    _original_call = ThreadTracer.__call__


    def __call__(self, frame, event, arg):
        _tid_to_last_frame[self._args[1].ident] = frame
        return _original_call(self, frame, event, arg)


    ThreadTracer.__call__ = __call__

