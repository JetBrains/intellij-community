# cython: language_level=3
from _pydevd_frame_eval.pydevd_frame_evaluator_common cimport *

cdef extern from "pystate.h":
    ctypedef PyObject* _PyFrameEvalFunction(PyThreadState *tstate, PyFrameObject *frame, int exc)
    ctypedef struct PyInterpreterState:
        PyInterpreterState *next
        PyInterpreterState *tstate_head

        PyObject *modules

        PyObject *modules_by_index
        PyObject *sysdict
        PyObject *builtins
        PyObject *importlib

        PyObject *codec_search_path
        PyObject *codec_search_cache
        PyObject *codec_error_registry
        int codecs_initialized
        int fscodec_initialized

        int dlopenflags

        PyObject *builtins_copy
        PyObject *import_func
        # Initialized to PyEval_EvalFrameDefault().
        _PyFrameEvalFunction eval_frame

    ctypedef struct PyThreadState:
        PyThreadState *prev
        PyThreadState *next
        PyInterpreterState *interp
    # ...

    PyThreadState *PyThreadState_Get()

cdef extern from "ceval.h":
    PyObject* _PyEval_EvalFrameDefault(PyThreadState *tstate, PyFrameObject *frame, int exc)
