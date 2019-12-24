#ifndef _PY_SETTRACE_37_HPP_
#define _PY_SETTRACE_37_HPP_

#include "python.h"
#include "py_utils.hpp"

// On Python 3.7 onwards the thread state is not kept in PyThread_set_key_value (rather
// it uses PyThread_tss_set using PyThread_tss_set(&_PyRuntime.gilstate.autoTSSkey, (void *)tstate)
// and we don't have access to that key from here (thus, we can't use the previous approach which
// made CPython think that the current thread had the thread state where we wanted to set the tracing).
//
// So, the solution implemented here is not faking that change and reimplementing PyEval_SetTrace.
// The implementation is mostly the same from the one in CPython, but we have one shortcoming:
//
// When CPython sets the tracing for a thread it increments _Py_TracingPossible (actually
// _PyRuntime.ceval.tracing_possible). This implementation has one issue: it only works on
// deltas when the tracing is set (so, a settrace(func) will increase the _Py_TracingPossible global value and a
// settrace(None) will decrease it, but when a thread dies it's kept as is and is not decreased).
// -- as we don't currently have access to _PyRuntime we have to create a thread, set the tracing
// and let it die so that the count is increased (this is really hacky, but better than having
// to create a local copy of the whole _PyRuntime (defined in pystate.h with several inner structs) 
// which would need to be kept up to date for each new CPython version just to increment that variable).


struct InternalInitializeSettrace_37 {
    PyUnicode_InternFromString* pyUnicode_InternFromString;
    PyObject* pyNone;
    _PyObject_FastCallDict* pyObject_FastCallDict;
    PyTraceBack_Here* pyTraceBack_Here;
    PyEval_SetTrace* pyEval_SetTrace;
    bool isDebug;
};

/**
 * Helper information to access CPython internals.
 */
static InternalInitializeSettrace_37 *internalInitializeSettrace_37 = NULL;

/*
 * Cached interned string objects used for calling the profile and
 * trace functions.  Initialized by InternalTraceInit_37().
 */
static PyObject *InternalWhatstrings_37[8] = {NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL};


static int
InternalIsTraceInitialized_37()
{
    return internalInitializeSettrace_37 != NULL;
}


static int
InternalTraceInit_37(InternalInitializeSettrace_37 *p_internalInitializeSettrace_37)
{
    internalInitializeSettrace_37 = p_internalInitializeSettrace_37;
    static const char * const whatnames[8] = {
        "call", "exception", "line", "return",
        "c_call", "c_exception", "c_return",
        "opcode"
    };
    PyObject *name;
    int i;
    for (i = 0; i < 8; ++i) {
        if (InternalWhatstrings_37[i] == NULL) {
            name = internalInitializeSettrace_37->pyUnicode_InternFromString(whatnames[i]);
            if (name == NULL)
                return -1;
            InternalWhatstrings_37[i] = name;
        }
    }
    return 0;
}


static PyObject *
InternalCallTrampoline_37(PyObject* callback,
                PyFrameObject *frame, int what, PyObject *arg)
{
    PyObject *result;
    PyObject *stack[3];

// Note: this is commented out from CPython (we shouldn't need it and it adds a reasonable overhead).
//     if (PyFrame_FastToLocalsWithError(frame) < 0) {
//         return NULL;
//     }
// 
    stack[0] = (PyObject *)frame;
    stack[1] = InternalWhatstrings_37[what];
    stack[2] = (arg != NULL) ? arg : internalInitializeSettrace_37->pyNone;

    // call the Python-level function
    // result = _PyObject_FastCall(callback, stack, 3);
    //
    // Note that _PyObject_FastCall is actually a define:
    // #define _PyObject_FastCall(func, args, nargs) _PyObject_FastCallDict((func), (args), (nargs), NULL)
    
    result = internalInitializeSettrace_37->pyObject_FastCallDict(callback, stack, 3, NULL);


// Note: this is commented out from CPython (we shouldn't need it and it adds a reasonable overhead).
//     PyFrame_LocalsToFast(frame, 1);

    if (result == NULL) {
        internalInitializeSettrace_37->pyTraceBack_Here(frame);
    }

    return result;
}

static int
InternalTraceTrampoline_37(PyObject *self, PyFrameObject *frame,
                 int what, PyObject *arg)
{
    PyObject *callback;
    PyObject *result;

    if (what == PyTrace_CALL){
        callback = self;
    } else {
        callback = frame->f_trace;
    }
    
    if (callback == NULL){
        return 0;
    }
    
    result = InternalCallTrampoline_37(callback, frame, what, arg);
    if (result == NULL) {
        // Note: calling the original sys.settrace here.
        internalInitializeSettrace_37->pyEval_SetTrace(NULL, NULL);
        PyObject *temp_f_trace = frame->f_trace;
        frame->f_trace = NULL;
        if(temp_f_trace != NULL){
            DecRef(temp_f_trace, internalInitializeSettrace_37->isDebug);
        }
        return -1;
    }
    if (result != internalInitializeSettrace_37->pyNone) {
        PyObject *tmp = frame->f_trace;
        frame->f_trace = result;
        DecRef(tmp, internalInitializeSettrace_37->isDebug);
    }
    else {
        DecRef(result, internalInitializeSettrace_37->isDebug);
    }
    return 0;
}

void InternalPySetTrace_37(PyThreadState* curThread, PyObjectHolder* traceFunc, bool isDebug)
{
    PyThreadState_37_38* tstate = reinterpret_cast<PyThreadState_37_38*>(curThread);
    PyObject *temp = tstate->c_traceobj;
    
    // We can't increase _Py_TracingPossible. Everything else should be equal to CPython.
    // runtime->ceval.tracing_possible += (func != NULL) - (tstate->c_tracefunc != NULL);
    
    PyObject *arg = traceFunc->ToPython();
    IncRef(arg);
    tstate->c_tracefunc = NULL;
    tstate->c_traceobj = NULL;
    /* Must make sure that profiling is not ignored if 'temp' is freed */
    tstate->use_tracing = tstate->c_profilefunc != NULL;
    if(temp != NULL){
        DecRef(temp, isDebug);
    }
    tstate->c_tracefunc = InternalTraceTrampoline_37;
    tstate->c_traceobj = arg;
    /* Flag that tracing or profiling is turned on */
    tstate->use_tracing = ((InternalTraceTrampoline_37 != NULL)
                           || (tstate->c_profilefunc != NULL));
    
}


#endif //_PY_SETTRACE_37_HPP_