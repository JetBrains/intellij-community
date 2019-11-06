#ifndef _PY_SETTRACE_HPP_
#define _PY_SETTRACE_HPP_

#include "ref_utils.hpp"
#include "py_utils.hpp"
#include "python.h"
#include "py_settrace_37.hpp"
#include <unordered_set>


#ifdef _WIN32

typedef HMODULE MODULE_TYPE;
#else // LINUX -----------------------------------------------------------------

typedef void* MODULE_TYPE;
typedef ssize_t SSIZE_T;
typedef unsigned int DWORD;

#endif

DWORD GetPythonThreadId(PythonVersion version, PyThreadState* curThread) {
    DWORD threadId = 0;
    if (PyThreadState_25_27::IsFor(version)) {
        threadId = (DWORD)((PyThreadState_25_27*)curThread)->thread_id;
    } else if (PyThreadState_30_33::IsFor(version)) {
        threadId = (DWORD)((PyThreadState_30_33*)curThread)->thread_id;
    } else if (PyThreadState_34_36::IsFor(version)) {
        threadId = (DWORD)((PyThreadState_34_36*)curThread)->thread_id;
    } else if (PyThreadState_37_38::IsFor(version)) {
        threadId = (DWORD)((PyThreadState_37_38*)curThread)->thread_id;
    }
    return threadId;
}


/**
 * This function may be called to set a tracing function to existing python threads.
 */
int InternalSetSysTraceFunc(
    MODULE_TYPE module,
    bool isDebug,
    bool showDebugInfo,
    PyObjectHolder* traceFunc,
    PyObjectHolder* setTraceFunc,
    unsigned int threadId,
    PyObjectHolder* pyNone)
{
    
    if(showDebugInfo){
        PRINT("InternalSetSysTraceFunc started.");
    }
    
    DEFINE_PROC(isInit, Py_IsInitialized*, "Py_IsInitialized", 100);
    if (!isInit()) {
        PRINT("Py_IsInitialized returned false.");
        return 110;
    }

    auto version = GetPythonVersion(module);

    // found initialized Python runtime, gather and check the APIs we need for a successful attach...
    
    DEFINE_PROC(interpHead, PyInterpreterState_Head*, "PyInterpreterState_Head", 120);
    DEFINE_PROC(gilEnsure, PyGILState_Ensure*, "PyGILState_Ensure", 130);
    DEFINE_PROC(gilRelease, PyGILState_Release*, "PyGILState_Release", 140);
    DEFINE_PROC(threadHead, PyInterpreterState_ThreadHead*, "PyInterpreterState_ThreadHead", 150);
    DEFINE_PROC(threadNext, PyThreadState_Next*, "PyThreadState_Next", 160);
    DEFINE_PROC(threadSwap, PyThreadState_Swap*, "PyThreadState_Swap", 170);
    DEFINE_PROC(call, PyObject_CallFunctionObjArgs*, "PyObject_CallFunctionObjArgs", 180);
    
    PyInt_FromLong* intFromLong;
    
    if (version >= PythonVersion_30) {
        DEFINE_PROC(intFromLongPy3, PyInt_FromLong*, "PyLong_FromLong", 190);
        intFromLong = intFromLongPy3;
    } else {
        DEFINE_PROC(intFromLongPy2, PyInt_FromLong*, "PyInt_FromLong", 200);
        intFromLong = intFromLongPy2;
    }
    
    DEFINE_PROC(pyGetAttr, PyObject_GetAttrString*, "PyObject_GetAttrString", 250);
    DEFINE_PROC(pyHasAttr, PyObject_HasAttrString*, "PyObject_HasAttrString", 260);
    DEFINE_PROC_NO_CHECK(PyCFrame_Type, PyTypeObject*, "PyCFrame_Type", 300);  // optional
    
    DEFINE_PROC_NO_CHECK(curPythonThread, PyThreadState**, "_PyThreadState_Current", 310);  // optional
    DEFINE_PROC_NO_CHECK(getPythonThread, _PyThreadState_UncheckedGet*, "_PyThreadState_UncheckedGet", 320);  // optional

    if (curPythonThread == nullptr && getPythonThread == nullptr) {
        // we're missing some APIs, we cannot attach.
        PRINT("Error, missing Python threading API!!");
        return 330;
    }
    
    auto head = interpHead();
    if (head == nullptr) {
        // this interpreter is loaded but not initialized.
        PRINT("Interpreter not initialized!");
        return 340;
    }

    GilHolder gilLock(gilEnsure, gilRelease);   // acquire and hold the GIL until done...
    
    
    int retVal = 0;
    // find what index is holding onto the thread state...
    auto curPyThread = getPythonThread ? getPythonThread() : *curPythonThread;
    
    if(curPyThread == nullptr){
        PRINT("Getting the current python thread returned nullptr.");
        return 345;
    }

    
    if (version < PythonVersion_37) 
    {
        DEFINE_PROC(errOccurred, PyErr_Occurred*, "PyErr_Occurred", 210);
        DEFINE_PROC(pyErrFetch, PyErr_Fetch*, "PyErr_Fetch", 220);
        DEFINE_PROC(pyErrRestore, PyErr_Restore*, "PyErr_Restore", 230);
        DEFINE_PROC(getThreadTls, PyThread_get_key_value*, "PyThread_get_key_value", 270);
        DEFINE_PROC(setThreadTls, PyThread_set_key_value*, "PyThread_set_key_value", 280);
        DEFINE_PROC(delThreadTls, PyThread_delete_key_value*, "PyThread_delete_key_value", 290);
        int threadStateIndex = -1;
        for (int i = 0; i < 100000; i++) {
            void* value = getThreadTls(i);
            if (value == curPyThread) {
                threadStateIndex = i;
                break;
            }
        }
        
        if(threadStateIndex == -1){
            printf("Unable to find threadStateIndex for the current thread. curPyThread: %p\n", curPyThread);
            return 350;
        }
    
        
        bool found = false;
        for (auto curThread = threadHead(head); curThread != nullptr; curThread = threadNext(curThread)) {
            if (GetPythonThreadId(version, curThread) != threadId) {
                continue;
            }
            found = true;
            
    
            // switch to our new thread so we can call sys.settrace on it...
            // all of the work here needs to be minimal - in particular we shouldn't
            // ever evaluate user defined code as we could end up switching to this
            // thread on the main thread and corrupting state.
            delThreadTls(threadStateIndex);
            setThreadTls(threadStateIndex, curThread);
            auto prevThread = threadSwap(curThread);
    
            // save and restore the error in case something funky happens...
            auto errOccured = errOccurred();
            PyObject* type = nullptr;
            PyObject* value = nullptr;
            PyObject* traceback = nullptr;
            if (errOccured) {
                pyErrFetch(&type, &value, &traceback);
                retVal = 1;
            }
    
            if(showDebugInfo){
                printf("setting trace for thread: %d\n", threadId);
            }
    
            DecRef(call(setTraceFunc->ToPython(), traceFunc->ToPython(), nullptr), isDebug);
    
            if (errOccured) {
                pyErrRestore(type, value, traceback);
            }
    
            delThreadTls(threadStateIndex);
            setThreadTls(threadStateIndex, prevThread);
            threadSwap(prevThread);
            break;
        }
        
        if(!found) {
            retVal = 500;
        }
    } 
    else  
    {
        // See comments on py_settrace_37.hpp for why we need a different implementation in Python 3.7 onwards.
        DEFINE_PROC(pyUnicode_InternFromString, PyUnicode_InternFromString*, "PyUnicode_InternFromString", 520);
        DEFINE_PROC(pyObject_FastCallDict, _PyObject_FastCallDict*, "_PyObject_FastCallDict", 530);
        DEFINE_PROC(pyTraceBack_Here, PyTraceBack_Here*, "PyTraceBack_Here", 540);
        DEFINE_PROC(pyEval_SetTrace, PyEval_SetTrace*, "PyEval_SetTrace", 550);
        
        bool found = false;
        for (PyThreadState* curThread = threadHead(head); curThread != nullptr; curThread = threadNext(curThread)) {
            if (GetPythonThreadId(version, curThread) != threadId) {
                continue;
            }
            found = true;
            
            if(showDebugInfo){
                printf("setting trace for thread: %d\n", threadId);
            }
            
            if(!InternalIsTraceInitialized_37())
            {
                InternalInitializeSettrace_37 *internalInitializeSettrace_37 = new InternalInitializeSettrace_37();
                
                IncRef(pyNone->ToPython());
                internalInitializeSettrace_37->pyNone = pyNone->ToPython();
                
                internalInitializeSettrace_37->pyUnicode_InternFromString = pyUnicode_InternFromString;
                internalInitializeSettrace_37->pyObject_FastCallDict = pyObject_FastCallDict;
                internalInitializeSettrace_37->isDebug = isDebug;
                internalInitializeSettrace_37->pyTraceBack_Here = pyTraceBack_Here;
                internalInitializeSettrace_37->pyEval_SetTrace = pyEval_SetTrace;
                
                InternalTraceInit_37(internalInitializeSettrace_37);
            }
            InternalPySetTrace_37(curThread, traceFunc, isDebug);
            break;
        }
        if(!found) {
            retVal = 501;
        }
    }

    return retVal;

}

#endif // _PY_SETTRACE_HPP_
