import dis
from _pydevd_bundle.pydevd_comm import get_global_debugger
from _pydevd_frame_eval.pydevd_frame_tracing import pydev_trace_code_wrapper, update_globals_dict
from _pydevd_frame_eval.pydevd_modify_bytecode import insert_code

ignore_list = ['pydevd.py', 'pydevd_comm.py']

def get_breakpoints_for_file(filename):
    main_debugger = get_global_debugger()
    breakpoints_for_file = main_debugger.breakpoints.get(filename)
    return breakpoints_for_file

cdef PyObject*get_bytecode_while_frame_eval(PyFrameObject *frame, int exc):
    filepath = str(<object> frame.f_code.co_filename)
    skip_file = False
    breakpoints = None
    for file in ignore_list:
        if filepath.endswith(file):
            skip_file = True

    if not skip_file:
        breakpoints = get_breakpoints_for_file(filepath)
        if breakpoints:
            was_break = False
            for offset, line in dis.findlinestarts(<object> frame.f_code):
                if line in breakpoints:
                    was_break = True
                    new_code = insert_code(<object> frame.f_code, pydev_trace_code_wrapper.__code__, line)
                    frame.f_code = <PyCodeObject *> new_code
            if was_break:
                update_globals_dict(<object> frame.f_globals)
    return _PyEval_EvalFrameDefault(frame, exc)

def set_frame_eval():
    cdef PyThreadState *state = PyThreadState_Get()
    state.interp.eval_frame = get_bytecode_while_frame_eval

def stop_frame_eval():
    cdef PyThreadState *state = PyThreadState_Get()
    state.interp.eval_frame = _PyEval_EvalFrameDefault
