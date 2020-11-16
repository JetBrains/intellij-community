from __future__ import print_function
from _pydev_imps._pydev_saved_modules import threading
from _pydevd_bundle.pydevd_comm import GlobalDebuggerHolder
from _pydevd_frame_eval.pydevd_frame_tracing import update_globals_dict, dummy_tracing_holder
from _pydevd_bundle.pydevd_trace_dispatch import fix_top_level_trace_and_get_trace_func

from _pydevd_bundle.pydevd_cython cimport PyDBAdditionalThreadInfo

from _pydevd_frame_eval.pydevd_frame_evaluator_common cimport ThreadInfo, FuncCodeInfo, get_thread_info, get_func_code_info, \
    clear_thread_local_info
from _pydevd_frame_eval.pydevd_frame_evaluator_common import _thread_local_info


def get_thread_info_py() -> ThreadInfo:
    return get_thread_info()


def clear_thread_local_info_py():
    clear_thread_local_info()


def dummy_trace_dispatch(frame, str event, arg):
    if event == 'call':
        if frame.f_trace is not None:
            return frame.f_trace(frame, event, arg)
    return None


def decref_py(obj):
    '''
    Helper to be called from Python.
    '''
    Py_DECREF(obj)


def get_func_code_info_py(code_obj) -> FuncCodeInfo:
    '''
    Helper to be called from Python.
    '''
    return get_func_code_info(<PyCodeObject *> code_obj)


cdef PyObject * get_bytecode_while_frame_eval(PyFrameObject * frame_obj, int exc):
    '''
    This function makes the actual evaluation and changes the bytecode to a version
    where programmatic breakpoints are added.
    '''
    if GlobalDebuggerHolder is None or _thread_local_info is None or exc:
        # Sometimes during process shutdown these global variables become None
        return _PyEval_EvalFrameDefault(frame_obj, exc)

    # co_filename: str = <str>frame_obj.f_code.co_filename
    # if co_filename.endswith('threading.py'):
    #     return _PyEval_EvalFrameDefault(frame_obj, exc)

    cdef ThreadInfo thread_info
    cdef int STATE_SUSPEND = 2
    cdef int CMD_STEP_INTO = 107
    cdef int CMD_STEP_OVER = 108
    cdef int CMD_STEP_INTO_MY_CODE = 144
    cdef int CMD_SMART_STEP_INTO = 128
    cdef bint can_skip = True
    try:
        thread_info = _thread_local_info.thread_info
    except:
        thread_info = get_thread_info()
        if thread_info is None:
            return _PyEval_EvalFrameDefault(frame_obj, exc)

    if thread_info.inside_frame_eval:
        return _PyEval_EvalFrameDefault(frame_obj, exc)

    if not thread_info.fully_initialized:
        thread_info.initialize_if_possible()
        if not thread_info.fully_initialized:
            return _PyEval_EvalFrameDefault(frame_obj, exc)

    # Can only get additional_info when fully initialized.
    cdef PyDBAdditionalThreadInfo additional_info = thread_info.additional_info
    if thread_info.is_pydevd_thread or additional_info.is_tracing:
        # Make sure that we don't trace pydevd threads or inside our own calls.
        return _PyEval_EvalFrameDefault(frame_obj, exc)

    # frame = <object> frame_obj
    # DEBUG = frame.f_code.co_filename.endswith('_debugger_case_multiprocessing.py')
    # if DEBUG:
    #     print('get_bytecode_while_frame_eval', frame.f_lineno, frame.f_code.co_name, frame.f_code.co_filename)

    thread_info.inside_frame_eval += 1
    additional_info.is_tracing = True
    try:
        main_debugger: object = GlobalDebuggerHolder.global_dbg

        if main_debugger is None or \
                not hasattr(main_debugger, "break_on_caught_exceptions") or \
                not hasattr(main_debugger, "has_plugin_exception_breaks") or \
                not hasattr(main_debugger, "stop_on_failed_tests") or \
                not hasattr(main_debugger, "signature_factory"):
            # Debugger isn't fully initialized here yet
            return _PyEval_EvalFrameDefault(frame_obj, exc)
        frame = <object> frame_obj

        if thread_info.thread_trace_func is None:
            trace_func, apply_to_global = fix_top_level_trace_and_get_trace_func(main_debugger, frame)
            if apply_to_global:
                thread_info.thread_trace_func = trace_func  # ThreadTracer.__call__

        if additional_info.pydev_step_cmd in (CMD_STEP_INTO, CMD_STEP_INTO_MY_CODE, CMD_SMART_STEP_INTO) or \
                main_debugger.break_on_caught_exceptions or \
                main_debugger.has_plugin_exception_breaks or \
                main_debugger.signature_factory or \
                additional_info.pydev_step_cmd == CMD_STEP_OVER and main_debugger.show_return_values and frame.f_back is additional_info.pydev_step_stop:

            if thread_info.thread_trace_func is not None:
                frame.f_trace = thread_info.thread_trace_func
            else:
                frame.f_trace = <object> main_debugger.trace_dispatch
        else:
            func_code_info: FuncCodeInfo = get_func_code_info(frame_obj.f_code)
            # if DEBUG:
            #     print('get_bytecode_while_frame_eval always skip', func_code_info.always_skip_code)
            if not func_code_info.always_skip_code:

                if main_debugger.has_plugin_line_breaks:
                    can_skip = not main_debugger.plugin.can_not_skip(main_debugger, None, <object> frame_obj, None)

                    if not can_skip:
                        # if DEBUG:
                        #     print('get_bytecode_while_frame_eval not can_skip')
                        if thread_info.thread_trace_func is not None:
                            frame.f_trace = thread_info.thread_trace_func
                        else:
                            frame.f_trace = <object> main_debugger.trace_dispatch

                if (can_skip and func_code_info.breakpoint_found) or main_debugger.stop_on_failed_tests:
                    # if DEBUG:
                    #     print('get_bytecode_while_frame_eval new_code', func_code_info.new_code)

                    # If breakpoints are found but new_code is None,
                    # this means we weren't able to actually add the code
                    # where needed, so, fallback to tracing.
                    if func_code_info.new_code is None:
                        if thread_info.thread_trace_func is not None:
                            frame.f_trace = thread_info.thread_trace_func
                        else:
                            frame.f_trace = <object> main_debugger.trace_dispatch
                    else:
                        # print('Using frame eval break for', <object> frame_obj.f_code.co_name)
                        update_globals_dict(<object> frame_obj.f_globals)
                        Py_INCREF(func_code_info.new_code)
                        old = <object> frame_obj.f_code
                        frame_obj.f_code = <PyCodeObject *> func_code_info.new_code
                        Py_DECREF(old)

    finally:
        thread_info.inside_frame_eval -= 1
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
