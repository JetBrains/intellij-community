import dis
from _pydev_imps._pydev_saved_modules import threading
from _pydevd_bundle.pydevd_additional_thread_info import PyDBAdditionalThreadInfo
from _pydevd_bundle.pydevd_comm import get_global_debugger
from _pydevd_bundle.pydevd_dont_trace_files import DONT_TRACE
from _pydevd_frame_eval.pydevd_frame_tracing import pydev_trace_code_wrapper, update_globals_dict
from _pydevd_frame_eval.pydevd_modify_bytecode import insert_code
from pydevd_file_utils import get_abs_path_real_path_and_base_from_frame, NORM_PATHS_AND_BASE_CONTAINER

AVOID_RECURSION = [
    'pydevd_additional_thread_info_regular.py',
    'threading.py',
    '_weakrefset.py'
]

get_file_type = DONT_TRACE.get

cdef PyObject* get_bytecode_while_frame_eval(PyFrameObject *frame_obj, int exc):
    frame = <object> frame_obj
    cdef str filepath = frame.f_code.co_filename
    cdef bint skip_file = exc

    for file in AVOID_RECURSION:
        # we can't call any other function without this check, because we can get stack overflow
        if filepath.endswith(file):
            skip_file = True
            break

    if not skip_file:
        try:
            threading.currentThread()
        except:
            skip_file = True

    if not skip_file:
        t = threading.currentThread()
        try:
            additional_info = t.additional_info
            if additional_info is None:
                raise AttributeError()
        except:
            additional_info = t.additional_info = PyDBAdditionalThreadInfo()

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

        main_debugger = get_global_debugger()
        breakpoints = main_debugger.breakpoints.get(abs_path_real_path_and_base[1])
        if breakpoints:
            was_break = False
            breakpoints_to_update = []
            code_object = frame.f_code
            for offset, line in dis.findlinestarts(code_object):
                if line in breakpoints:
                    breakpoint = breakpoints[line]
                    if code_object not in breakpoint.code_objects:
                        # This check is needed for generator functions, because after each yield the new frame is created
                        # but the former code object is used
                        breakpoints_to_update.append(breakpoint)
                        new_code = insert_code(frame.f_code, pydev_trace_code_wrapper.__code__, line)
                        Py_INCREF(new_code)
                        frame_obj.f_code = <PyCodeObject *> new_code
                        was_break = True
            if was_break:
                update_globals_dict(frame.f_globals)
                for bp in breakpoints_to_update:
                    bp.code_objects.append(frame.f_code)
        else:
            if main_debugger.has_plugin_line_breaks:
                can_not_skip = main_debugger.plugin.can_not_skip(main_debugger, None, frame)
                if can_not_skip:
                    main_debugger.SetTrace(main_debugger.trace_dispatch)
                    main_debugger.set_trace_for_frame_and_parents(frame)

        additional_info.is_tracing = False
    return _PyEval_EvalFrameDefault(frame_obj, exc)

def frame_eval_func():
    cdef PyThreadState *state = PyThreadState_Get()
    state.interp.eval_frame = get_bytecode_while_frame_eval

def stop_frame_eval():
    cdef PyThreadState *state = PyThreadState_Get()
    state.interp.eval_frame = _PyEval_EvalFrameDefault
