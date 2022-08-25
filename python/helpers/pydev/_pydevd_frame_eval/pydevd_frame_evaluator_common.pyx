from __future__ import print_function
import dis
from _pydev_imps._pydev_saved_modules import threading
from _pydevd_bundle.pydevd_comm import GlobalDebuggerHolder
from _pydevd_bundle.pydevd_dont_trace_files import DONT_TRACE
from _pydevd_frame_eval.pydevd_frame_tracing import create_pydev_trace_code_wrapper
from _pydevd_frame_eval.pydevd_modify_bytecode import insert_code
from pydevd_file_utils import get_abs_path_real_path_and_base_from_file, NORM_PATHS_AND_BASE_CONTAINER

from _pydevd_bundle.pydevd_additional_thread_info import _set_additional_thread_info_lock
from _pydevd_bundle.pydevd_cython cimport PyDBAdditionalThreadInfo

get_file_type = DONT_TRACE.get

_thread_local_info = threading.local()

cdef class ThreadInfo:

    def __init__(self):
        self.additional_info = None
        self.is_pydevd_thread = False
        self.inside_frame_eval = 0
        self.fully_initialized = False
        self.thread_trace_func = None

    def initialize_if_possible(self):
        # Don't call threading.current_thread because if we're too early in the process
        # we may create a dummy thread.
        self.inside_frame_eval += 1

        try:
            thread_ident = threading.get_ident()  # Note this is py3 only, if py2 needed to be supported, _get_ident would be needed.
            t = threading._active.get(thread_ident)
            if t is None:
                return  # Cannot initialize until thread becomes active.

            for thread in threading.enumerate():
                if isinstance(t, threading._DummyThread) and t is thread and t.ident != thread.ident:
                    t = thread
                    break

            if getattr(t, 'is_pydev_daemon_thread', False):
                self.is_pydevd_thread = True
                self.fully_initialized = True
            else:
                try:
                    additional_info = t.additional_info
                    if additional_info is None:
                        raise AttributeError()
                except:
                    with _set_additional_thread_info_lock:
                        # If it's not there, set it within a lock to avoid any racing
                        # conditions.
                        additional_info = getattr(thread, 'additional_info', None)
                        if additional_info is None:
                            additional_info = PyDBAdditionalThreadInfo()
                        t.additional_info = additional_info
                self.additional_info = additional_info
                self.fully_initialized = True
        finally:
            self.inside_frame_eval -= 1


cdef class FuncCodeInfo:

    def __init__(self):
        self.co_filename = ''
        self.real_path = ''
        self.always_skip_code = False

        # If breakpoints are found but new_code is None,
        # this means we weren't able to actually add the code
        # where needed, so, fallback to tracing.
        self.breakpoint_found = False
        self.new_code = None
        self.breakpoints_created = set()
        self.breakpoints_mtime = -1


_code_extra_index: Py_SIZE = -1


cdef ThreadInfo get_thread_info():
    '''
    Provides thread-related info.

    May return None if the thread is still not active.
    '''
    cdef ThreadInfo thread_info
    try:
        # Note: changing to a `dict[thread.ident] = thread_info` had almost no
        # effect in the performance.
        thread_info = _thread_local_info.thread_info
    except:
        thread_info = ThreadInfo()
        thread_info.inside_frame_eval += 1
        try:
            _thread_local_info.thread_info = thread_info

            # Note: _code_extra_index is not actually thread-related,
            # but this is a good point to initialize it.
            global _code_extra_index
            if _code_extra_index == -1:
                _code_extra_index = _PyEval_RequestCodeExtraIndex(release_co_extra)

            thread_info.initialize_if_possible()
        finally:
            thread_info.inside_frame_eval -= 1

    return thread_info


cdef clear_thread_local_info():
    global _thread_local_info
    _thread_local_info = threading.local()


cdef FuncCodeInfo get_func_code_info(PyCodeObject * code_obj):
    '''
    Provides code-object related info.

    Stores the gathered info in a cache in the code object itself. Note that
    multiple threads can get the same info.

    get_thread_info() *must* be called at least once before get_func_code_info()
    to initialize _code_extra_index.
    '''
    # f_code = <object> code_obj
    # DEBUG = f_code.co_filename.endswith('_debugger_case_multiprocessing.py')
    # if DEBUG:
    #     print('get_func_code_info', f_code.co_name, f_code.co_filename)

    cdef object main_debugger = GlobalDebuggerHolder.global_dbg

    cdef void * extra
    _PyCode_GetExtra(<PyObject *> code_obj, _code_extra_index, & extra)
    if extra is not NULL:
        extra_obj = <PyObject *> extra
        if extra_obj is not NULL:
            func_code_info_obj = <FuncCodeInfo> extra_obj
            if func_code_info_obj.breakpoints_mtime == main_debugger.mtime:
                # if DEBUG:
                #     print('get_func_code_info: matched mtime', f_code.co_name, f_code.co_filename)

                return func_code_info_obj

    cdef str co_filename = <str> code_obj.co_filename
    cdef str co_name = <str> code_obj.co_name

    func_code_info = FuncCodeInfo()
    func_code_info.breakpoints_mtime = main_debugger.mtime

    func_code_info.co_filename = co_filename

    if not func_code_info.always_skip_code:
        try:
            abs_path_real_path_and_base = NORM_PATHS_AND_BASE_CONTAINER[co_filename]
        except:
            abs_path_real_path_and_base = get_abs_path_real_path_and_base_from_file(co_filename)

        func_code_info.real_path = abs_path_real_path_and_base[1]

        file_type = get_file_type(abs_path_real_path_and_base[-1])  # we don't want to debug anything related to pydevd
        if file_type is not None:
            func_code_info.always_skip_code = True

    if not func_code_info.always_skip_code:
        was_break: bool = False
        if main_debugger is not None:
            breakpoints: dict = main_debugger.breakpoints.get(func_code_info.real_path)
            # print('\n---')
            # print(main_debugger.breakpoints)
            # print(func_code_info.real_path)
            # print(main_debugger.breakpoints.get(func_code_info.real_path))
            code_obj_py: object = <object> code_obj
            if breakpoints:
                # if DEBUG:
                #    print('found breakpoints', code_obj_py.co_name, breakpoints)
                for offset, line in dis.findlinestarts(code_obj_py):
                    if line in breakpoints:
                        breakpoint = breakpoints[line]
                        # if DEBUG:
                        #    print('created breakpoint', code_obj_py.co_name, line)
                        func_code_info.breakpoints_created.add(line)
                        func_code_info.breakpoint_found = True

                        success, new_code = insert_code(
                            code_obj_py, create_pydev_trace_code_wrapper(line), line)

                        if success:
                            func_code_info.new_code = new_code
                            code_obj_py = new_code
                        else:
                            func_code_info.new_code = None
                            break

    Py_INCREF(func_code_info)
    _PyCode_SetExtra(<PyObject *> code_obj, _code_extra_index, <PyObject *> func_code_info)

    return func_code_info
