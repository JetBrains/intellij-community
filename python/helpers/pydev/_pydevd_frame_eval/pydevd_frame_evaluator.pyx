import dis
from _pydev_imps._pydev_saved_modules import threading
from _pydevd_bundle.pydevd_additional_thread_info import PyDBAdditionalThreadInfo
from _pydevd_bundle.pydevd_comm import get_global_debugger
from _pydevd_bundle.pydevd_dont_trace_files import DONT_TRACE
from _pydevd_frame_eval.pydevd_frame_tracing import pydev_trace_code_wrapper, update_globals_dict, dummy_tracing_holder
from _pydevd_frame_eval.pydevd_modify_bytecode import insert_code
from pydevd_file_utils import get_abs_path_real_path_and_base_from_frame, NORM_PATHS_AND_BASE_CONTAINER

AVOID_RECURSION = [
    'pydevd_additional_thread_info_regular.py',
    'threading.py',
    '_weakrefset.py'
]

get_file_type = DONT_TRACE.get
NO_BREAKS_IN_FRAME = 1


class UseCodeExtraHolder:
    # Use this flag in order to disable co_extra field
    use_code_extra = True
    # Keep the index of co_extra in a thread-local storage
    local = threading.local()
    local.index = -1


def is_use_code_extra():
    return UseCodeExtraHolder.use_code_extra


# enable using `co_extra` field in order to cache frames without breakpoints
def enable_cache_frames_without_breaks(new_value):
    UseCodeExtraHolder.use_code_extra = new_value


cpdef dummy_trace_dispatch(frame, str event, arg):
    return None


cdef PyObject* get_bytecode_while_frame_eval(PyFrameObject *frame_obj, int exc):
    frame = <object> frame_obj
    cdef str filepath = frame.f_code.co_filename
    cdef bint skip_file = exc
    cdef void* extra = NULL
    cdef int* extra_value = NULL
    cdef int thread_index = -1

    if is_use_code_extra is None or AVOID_RECURSION is None:
        # Sometimes during process shutdown these global variables become None
        return _PyEval_EvalFrameDefault(frame_obj, exc)

    if is_use_code_extra():
        extra = PyMem_Malloc(sizeof(int))
        try:
            thread_index = UseCodeExtraHolder.local.index
        except:
            pass
        if thread_index != -1:
            _PyCode_GetExtra(<PyObject*> frame.f_code, thread_index, &extra)
            if extra is not NULL:
                extra_value = <int*> extra
                if extra_value[0] == NO_BREAKS_IN_FRAME:
                    return _PyEval_EvalFrameDefault(frame_obj, exc)

    for file in AVOID_RECURSION:
        # we can't call any other function without this check, because we can get stack overflow
        for path_separator in ('/', '\\'):
            if filepath.endswith(path_separator + file):
                skip_file = True
                break

    if not skip_file:
        try:
            t = threading.currentThread()
        except:
            skip_file = True

    if not skip_file:
        try:
            additional_info = t.additional_info
            if additional_info is None:
                raise AttributeError()
        except:
            additional_info = t.additional_info = PyDBAdditionalThreadInfo()
            # request `co_extra` inside every new thread
            thread_index = _PyEval_RequestCodeExtraIndex(PyMem_Free)
            UseCodeExtraHolder.local.index = thread_index

        if additional_info.is_tracing or getattr(t, 'pydev_do_not_trace', None):
            return _PyEval_EvalFrameDefault(frame_obj, exc)

        additional_info.is_tracing = True
        try:
            abs_path_real_path_and_base = NORM_PATHS_AND_BASE_CONTAINER[frame.f_code.co_filename]
        except:
            abs_path_real_path_and_base = get_abs_path_real_path_and_base_from_frame(frame)

        file_type = get_file_type(abs_path_real_path_and_base[-1])  #we don't want to debug anything related to pydevd
        if file_type is not None:
            additional_info.is_tracing = False
            return _PyEval_EvalFrameDefault(frame_obj, exc)

        was_break = False
        main_debugger = get_global_debugger()
        breakpoints = main_debugger.breakpoints.get(abs_path_real_path_and_base[1])
        code_object = frame.f_code
        if breakpoints:
            breakpoints_to_update = []
            for offset, line in dis.findlinestarts(code_object):
                if line in breakpoints:
                    breakpoint = breakpoints[line]
                    if code_object not in breakpoint.code_objects:
                        # This check is needed for generator functions, because after each yield the new frame is created
                        # but the former code object is used
                        success, new_code = insert_code(frame.f_code, pydev_trace_code_wrapper.__code__, line)
                        if success:
                            breakpoints_to_update.append(breakpoint)
                            Py_INCREF(new_code)
                            frame_obj.f_code = <PyCodeObject *> new_code
                            was_break = True
                        else:
                            main_debugger.set_trace_for_frame_and_parents(frame)
                            was_break = False
                            break
            if was_break:
                update_globals_dict(frame.f_globals)
                for bp in breakpoints_to_update:
                    bp.code_objects.add(frame.f_code)
        else:
            if main_debugger.has_plugin_line_breaks:
                can_not_skip = main_debugger.plugin.can_not_skip(main_debugger, None, frame)
                if can_not_skip:
                    was_break = True
                    main_debugger.SetTrace(main_debugger.trace_dispatch)
                    main_debugger.set_trace_for_frame_and_parents(frame)

        if not was_break:
            extra_value = <int*> PyMem_Malloc(sizeof(int))
            extra_value[0] = NO_BREAKS_IN_FRAME
            try:
                thread_index = UseCodeExtraHolder.local.index
            except:
                pass
            if thread_index != -1:
                _PyCode_SetExtra(<PyObject*> code_object, thread_index, extra_value)

        additional_info.is_tracing = False
    return _PyEval_EvalFrameDefault(frame_obj, exc)

def frame_eval_func():
    cdef PyThreadState *state = PyThreadState_Get()
    state.interp.eval_frame = get_bytecode_while_frame_eval
    global dummy_tracing_holder
    dummy_tracing_holder.set_trace_func(dummy_trace_dispatch)

def stop_frame_eval():
    cdef PyThreadState *state = PyThreadState_Get()
    state.interp.eval_frame = _PyEval_EvalFrameDefault
