import traceback

from _pydev_bundle.pydev_is_thread_alive import is_thread_alive
from _pydev_imps._pydev_saved_modules import threading
from _pydevd_bundle.pydevd_constants import get_thread_id, IS_IRONPYTHON, NO_FTRACE
from _pydevd_bundle.pydevd_dont_trace_files import DONT_TRACE
from _pydevd_bundle.pydevd_kill_all_pydevd_threads import kill_all_pydev_threads
from pydevd_file_utils import get_abs_path_real_path_and_base_from_frame, NORM_PATHS_AND_BASE_CONTAINER
from pydevd_tracing import SetTrace
# IFDEF CYTHON
# # In Cython, set_additional_thread_info is bundled in the file.
# from cpython.object cimport PyObject
# from cpython.ref cimport Py_INCREF, Py_XDECREF
# ELSE
from _pydevd_bundle.pydevd_additional_thread_info import set_additional_thread_info
from _pydevd_bundle.pydevd_frame import PyDBFrame
# ENDIF
from os.path import basename, splitext
from _pydevd_bundle.pydevd_breakpoints import stop_on_unhandled_exception
from _pydevd_bundle.pydevd_collect_try_except_info import collect_try_except_info

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
# ELSE
# ENDIF


def trace_dispatch(py_db, frame, event, arg):
    # IFDEF CYTHON
    # cdef str filename;
    # cdef str name;
    # cdef tuple args;
    # ENDIF

    # Note: this is always the first entry-point in the tracing for any thread.
    # After entering here we'll set a new tracing function for this thread
    # where more information is cached (and will also setup the tracing for
    # frames where we should deal with unhandled exceptions).
    thread = None
    # Cache the frame which should be traced to deal with unhandled exceptions.
    # (i.e.: thread entry-points).

    f_unhandled = frame
    # print('called at', f_unhandled.f_code.co_name, f_unhandled.f_code.co_filename, f_unhandled.f_code.co_firstlineno)
    force_only_unhandled_tracer = False
    while f_unhandled is not None:
        filename = f_unhandled.f_code.co_filename
        name = splitext(basename(filename))[0]
        if name == 'threading':
            if f_unhandled.f_code.co_name in ('__bootstrap', '_bootstrap'):
                # We need __bootstrap_inner, not __bootstrap.
                if event != 'call': frame.f_trace = NO_FTRACE
                return None

            elif f_unhandled.f_code.co_name in ('__bootstrap_inner', '_bootstrap_inner'):
                # Note: be careful not to use threading.currentThread to avoid creating a dummy thread.
                t = f_unhandled.f_locals.get('self')
                force_only_unhandled_tracer = True
                if t is not None and isinstance(t, threading.Thread):
                    thread = t
                    break

        elif name == 'pydev_monkey':
            if f_unhandled.f_code.co_name == '__call__':
                force_only_unhandled_tracer = True
                break

        elif name == 'pydevd':
            if f_unhandled.f_code.co_name in ('run', 'main'):
                # We need to get to _exec
                if event != 'call': frame.f_trace = NO_FTRACE
                return None

            if f_unhandled.f_code.co_name == '_exec':
                force_only_unhandled_tracer = True
                break

        elif f_unhandled.f_back is None:
            break

        f_unhandled = f_unhandled.f_back

    if thread is None:
        # Important: don't call threadingCurrentThread if we're in the threading module
        # to avoid creating dummy threads.
        thread = threadingCurrentThread()

    if getattr(thread, 'pydev_do_not_trace', None):
        SetTrace(None, apply_to_pydevd_thread=True)
        if event != 'call': frame.f_trace = NO_FTRACE
        return None

    try:
        additional_info = thread.additional_info
        if additional_info is None:
            raise AttributeError()
    except:
        additional_info = set_additional_thread_info(thread)

    # print('enter thread tracer', thread, get_thread_id(thread))
    args = (py_db, thread, additional_info, global_cache_skips, global_cache_frame_skips)

    if f_unhandled is not None:
        if f_unhandled.f_back is None and not force_only_unhandled_tracer:
            # Happens when we attach to a running program.
            top_level_thread_tracer = TopLevelThreadTracerNoBackFrame(ThreadTracer(args).__call__, args)
        else:
            # Stop in some internal place to report about unhandled exceptions
            top_level_thread_tracer = TopLevelThreadTracerOnlyUnhandledExceptions(args)
# IFDEF CYTHON
#         thread._top_level_thread_tracer = top_level_thread_tracer # Hack for cython to keep it alive while the thread is alive (just the method in the SetTrace is not enough).
# ELSE
# ENDIF
        # print(' --> found to trace unhandled', f_unhandled.f_code.co_name, f_unhandled.f_code.co_filename, f_unhandled.f_code.co_firstlineno)
        f_trace = top_level_thread_tracer.get_trace_dispatch_func()
        # IFDEF CYTHON
        # f_unhandled.f_trace = SafeCallWrapper(f_trace)
        # ELSE
        f_unhandled.f_trace = f_trace
        # ENDIF

        if frame is f_unhandled:
            return f_unhandled.f_trace(frame, event, arg)

    thread_tracer = ThreadTracer(args)
# IFDEF CYTHON
#     thread._tracer = thread_tracer # Hack for cython to keep it alive while the thread is alive (just the method in the SetTrace is not enough).
# ELSE
# ENDIF

    SetTrace(thread_tracer.__call__)
    return thread_tracer.__call__(frame, event, arg)


# IFDEF CYTHON
# cdef class TopLevelThreadTracerOnlyUnhandledExceptions:
#     cdef public tuple _args;
#     def __init__(self, tuple args):
#         self._args = args
# ELSE
class TopLevelThreadTracerOnlyUnhandledExceptions:

    def __init__(self, args):
        self._args = args
# ENDIF

    def trace_unhandled_exceptions(self, frame, event, arg):
        # Note that we ignore the frame as this tracing method should only be put in topmost frames already.
        # print('trace_unhandled_exceptions', event, frame.f_code.co_name, frame.f_code.co_filename, frame.f_code.co_firstlineno)
        if event == 'exception' and arg is not None:
            py_db, t, additional_info = self._args[0:3]
            if arg is not None:
                if not additional_info.suspended_at_unhandled:
                    additional_info.suspended_at_unhandled = True

                    stop_on_unhandled_exception(py_db, t, additional_info, arg)

        # No need to reset frame.f_trace to keep the same trace function.
        # IFDEF CYTHON
        # return SafeCallWrapper(self.trace_unhandled_exceptions)
        # ELSE
        return self.trace_unhandled_exceptions
        # ENDIF

    def get_trace_dispatch_func(self):
        return self.trace_unhandled_exceptions


# IFDEF CYTHON
# cdef class TopLevelThreadTracerNoBackFrame:
#
#     cdef public object _frame_trace_dispatch;
#     cdef public tuple _args;
#     cdef public object _try_except_info;
#     cdef public object _last_exc_arg;
#     cdef public set _raise_lines;
#     cdef public int _last_raise_line;
#
#     def __init__(self, frame_trace_dispatch, tuple args):
#         self._frame_trace_dispatch = frame_trace_dispatch
#         self._args = args
#         self._try_except_info = None
#         self._last_exc_arg = None
#         self._raise_lines = set()
#         self._last_raise_line = -1
# ELSE
class TopLevelThreadTracerNoBackFrame:
    '''
    This tracer is pretty special in that it's dealing with a frame without f_back (i.e.: top frame
    on remote attach or QThread).

    This means that we have to carefully inspect exceptions to discover whether the exception will
    be unhandled or not (if we're dealing with an unhandled exception we need to stop as unhandled,
    otherwise we need to use the regular tracer -- unfortunately the debugger has little info to
    work with in the tracing -- see: https://bugs.python.org/issue34099, so, we inspect bytecode to
    determine if some exception will be traced or not... note that if this is not available -- such
    as on Jython -- we consider any top-level exception to be unnhandled).
    '''

    def __init__(self, frame_trace_dispatch, args):
        self._frame_trace_dispatch = frame_trace_dispatch
        self._args = args
        self._try_except_info = None
        self._last_exc_arg = None
        self._raise_lines = set()
        self._last_raise_line = -1
# ENDIF

    def trace_dispatch_and_unhandled_exceptions(self, frame, event, arg):
        # print('trace_dispatch_and_unhandled_exceptions', event, frame.f_code.co_name, frame.f_code.co_filename, frame.f_code.co_firstlineno)
        if self._frame_trace_dispatch is not None:
            self._frame_trace_dispatch = self._frame_trace_dispatch(frame, event, arg)

        if event == 'exception':
            self._last_exc_arg = arg
            self._raise_lines.add(frame.f_lineno)
            self._last_raise_line = frame.f_lineno

        elif event == 'return' and self._last_exc_arg is not None:
            # For unhandled exceptions we actually track the return when at the topmost level.
            try:
                py_db, t, additional_info = self._args[0:3]
                if not additional_info.suspended_at_unhandled:  # Note: only check it here, don't set.
                    if frame.f_lineno in self._raise_lines:
                        stop_on_unhandled_exception(py_db, t, additional_info, self._last_exc_arg)

                    else:
                        if self._try_except_info is None:
                            self._try_except_info = collect_try_except_info(frame.f_code)
                        if not self._try_except_info:
                            # Consider the last exception as unhandled because there's no try..except in it.
                            stop_on_unhandled_exception(py_db, t, additional_info, self._last_exc_arg)
                        else:
                            # Now, consider only the try..except for the raise
                            valid_try_except_infos = []
                            for try_except_info in self._try_except_info:
                                if try_except_info.is_line_in_try_block(self._last_raise_line):
                                    valid_try_except_infos.append(try_except_info)

                            if not valid_try_except_infos:
                                stop_on_unhandled_exception(py_db, t, additional_info, self._last_exc_arg)

                            else:
                                # Note: check all, not only the "valid" ones to cover the case
                                # in "tests_python.test_tracing_on_top_level.raise_unhandled10"
                                # where one try..except is inside the other with only a raise
                                # and it's gotten in the except line.
                                for try_except_info in self._try_except_info:
                                    if try_except_info.is_line_in_except_block(frame.f_lineno):
                                        if (
                                                frame.f_lineno == try_except_info.except_line or
                                                frame.f_lineno in try_except_info.raise_lines_in_except
                                            ):
                                            # In a raise inside a try..except block or some except which doesn't
                                            # match the raised exception.
                                            stop_on_unhandled_exception(py_db, t, additional_info, self._last_exc_arg)
                                            break
                                        else:
                                            break  # exited during the except block (no exception raised)
            finally:
                # Remove reference to exception after handling it.
                self._last_exc_arg = None

        # IFDEF CYTHON
        # ret = SafeCallWrapper(self.trace_dispatch_and_unhandled_exceptions)
        # ELSE
        ret = self.trace_dispatch_and_unhandled_exceptions
        # ENDIF
        
        # Need to reset (the call to _frame_trace_dispatch may have changed it).
        frame.f_trace = ret
        return ret

    def get_trace_dispatch_func(self):
        return self.trace_dispatch_and_unhandled_exceptions


# IFDEF CYTHON
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
        # cdef tuple frame_cache_key;
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
                        if py_db.output_checker_thread is None:
                            kill_all_pydev_threads()
                    except:
                        traceback.print_exc()
                    py_db._termination_event_set = True
                if event != 'call': frame.f_trace = NO_FTRACE
                return None

            # if thread is not alive, cancel trace_dispatch processing
            if not is_thread_alive(t):
                py_db.notify_thread_not_alive(get_thread_id(t))
                if event != 'call': frame.f_trace = NO_FTRACE
                return None  # suspend tracing

            if py_db.thread_analyser is not None:
                py_db.thread_analyser.log_event(frame)

            if py_db.asyncio_analyser is not None:
                py_db.asyncio_analyser.log_event(frame)

            # Note: it's important that the context name is also given because we may hit something once
            # in the global context and another in the local context.
            frame_cache_key = (frame.f_code.co_firstlineno, frame.f_code.co_name, frame.f_code.co_filename)
            if not is_stepping and frame_cache_key in cache_skips:
                # print('skipped: trace_dispatch (cache hit)', frame_cache_key, frame.f_lineno, event, frame.f_code.co_name)
                if event != 'call': frame.f_trace = NO_FTRACE
                return None

            try:
                # Make fast path faster!
                abs_path_real_path_and_base = NORM_PATHS_AND_BASE_CONTAINER[frame.f_code.co_filename]
            except:
                abs_path_real_path_and_base = get_abs_path_real_path_and_base_from_frame(frame)

            filename = abs_path_real_path_and_base[1]
            file_type = get_file_type(abs_path_real_path_and_base[-1])  # we don't want to debug threading or anything related to pydevd

            if file_type is not None:
                if file_type == 1:  # inlining LIB_FILE = 1
                    if not py_db.in_project_scope(filename):
                        # print('skipped: trace_dispatch (not in scope)', abs_path_real_path_and_base[-1], frame.f_lineno, event, frame.f_code.co_name, file_type)
                        cache_skips[frame_cache_key] = 1
                        if event != 'call': frame.f_trace = NO_FTRACE
                        return None
                else:
                    # print('skipped: trace_dispatch', abs_path_real_path_and_base[-1], frame.f_lineno, event, frame.f_code.co_name, file_type)
                    cache_skips[frame_cache_key] = 1
                    if event != 'call': frame.f_trace = NO_FTRACE
                    return None

            if is_stepping:
                if py_db.is_filter_enabled and py_db.is_ignored_by_filters(filename):
                    # ignore files matching stepping filters
                    if event != 'call': frame.f_trace = NO_FTRACE
                    return None
                if py_db.is_filter_libraries and not py_db.in_project_scope(filename):
                    # ignore library files while stepping
                    if event != 'call': frame.f_trace = NO_FTRACE
                    return None

            # print('trace_dispatch', base, frame.f_lineno, event, frame.f_code.co_name, file_type)
            if additional_info.is_tracing:
                if event != 'call': frame.f_trace = NO_FTRACE
                return None  # we don't wan't to trace code invoked from pydevd_frame.trace_dispatch

            # Just create PyDBFrame directly (removed support for Python versions < 2.5, which required keeping a weak
            # reference to the frame).
            ret = PyDBFrame(
                (
                    py_db, filename, additional_info, t, frame_skips_cache, frame_cache_key,
                )
            ).trace_dispatch(frame, event, arg)
            if ret is None:
                cache_skips[frame_cache_key] = 1
                if event != 'call': frame.f_trace = NO_FTRACE
                return None

            # IFDEF CYTHON
            # ret = SafeCallWrapper(ret)
            # ENDIF
            frame.f_trace = ret  # Make sure we keep the returned tracer.
            return ret

        except SystemExit:
            if event != 'call': frame.f_trace = NO_FTRACE
            return None

        except Exception:
            if py_db._finish_debugging_session:
                if event != 'call': frame.f_trace = NO_FTRACE
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
            if event != 'call': frame.f_trace = NO_FTRACE
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
