import dis
from _pydev_imps._pydev_saved_modules import threading
from _pydevd_bundle.pydevd_additional_thread_info import PyDBAdditionalThreadInfo
from _pydevd_bundle.pydevd_comm import get_global_debugger
from _pydevd_bundle.pydevd_dont_trace_files import DONT_TRACE
from _pydevd_frame_eval.pydevd_frame_tracing import pydev_trace_code_wrapper, update_globals_dict
from _pydevd_frame_eval.pydevd_modify_bytecode import insert_code
from pydevd_file_utils import get_abs_path_real_path_and_base_from_frame, NORM_PATHS_AND_BASE_CONTAINER


def get_breakpoints_for_file(filename):
    main_debugger = get_global_debugger()
    breakpoints_for_file = main_debugger.breakpoints.get(filename)
    return breakpoints_for_file

cdef PyObject* get_bytecode_while_frame_eval(PyFrameObject *frame, int exc):
    filepath = str(<object> frame.f_code.co_filename)
    skip_file = False
    breakpoints = None
    for file in DONT_TRACE.keys():
        if filepath.endswith(file):
            skip_file = True
            break

    if not skip_file:
        t = threading.currentThread()
        try:
            additional_info = t.additional_info
            if additional_info is None:
                raise AttributeError()
        except:
            additional_info = t.additional_info = PyDBAdditionalThreadInfo()

        if not additional_info.is_tracing:
            additional_info.is_tracing = True
            try:
                abs_path_real_path_and_base = NORM_PATHS_AND_BASE_CONTAINER[<object> frame.f_code.co_filename]
            except:
                abs_path_real_path_and_base = get_abs_path_real_path_and_base_from_frame(<object> frame)

            filepath = abs_path_real_path_and_base[1]
            breakpoints = get_breakpoints_for_file(filepath)
            if breakpoints:
                was_break = False
                breakpoints_to_update = []
                code_object = <object> frame.f_code
                for offset, line in dis.findlinestarts(code_object):
                    if line in breakpoints:
                        breakpoint = breakpoints[line]
                        if code_object not in breakpoint.code_objects:
                            # This check is needed for generator functions, because after each yield the new frame is created
                            # but the former code object is used
                            breakpoints_to_update.append(breakpoint)
                            new_code = insert_code(<object> frame.f_code, pydev_trace_code_wrapper.__code__, line)
                            Py_INCREF(new_code)
                            frame.f_code = <PyCodeObject *> new_code
                            was_break = True
                if was_break:
                    update_globals_dict(<object> frame.f_globals)
                    for bp in breakpoints_to_update:
                        bp.code_objects.append(<object> frame.f_code)
            additional_info.is_tracing = False
    return _PyEval_EvalFrameDefault(frame, exc)

def frame_eval_func():
    cdef PyThreadState *state = PyThreadState_Get()
    state.interp.eval_frame = get_bytecode_while_frame_eval

def stop_frame_eval():
    cdef PyThreadState *state = PyThreadState_Get()
    state.interp.eval_frame = _PyEval_EvalFrameDefault
