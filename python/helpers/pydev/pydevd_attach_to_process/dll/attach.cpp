/* ****************************************************************************
*
* Copyright (c) Microsoft Corporation.
*
* This source code is subject to terms and conditions of the Apache License, Version 2.0. A
* copy of the license can be found in the License.html file at the root of this distribution. If
* you cannot locate the Apache License, Version 2.0, please send an email to
* vspython@microsoft.com. By using this source code in any fashion, you are agreeing to be bound
* by the terms of the Apache License, Version 2.0.
*
* You must not remove this notice, or any other, from this software.
*
* Contributor: Fabio Zadrozny
*
* Based on PyDebugAttach.cpp from PVTS. Windows only.
* Initially we did an attach completely based on shellcode which got the
* GIL called PyRun_SimpleString with the needed code and was done with it
* (so, none of this code was needed).
* Now, newer version of Python don't initialize threading by default, so,
* most of this code is done only to overcome this limitation (and as a plus,
* if there's no code running, we also pause the threads to make our code run).
*
* On Linux the approach is still the simpler one (using gdb), so, on newer
* versions of Python it may not work unless the user has some code running
* and threads are initialized.
* I.e.:
*
* The user may have to add the code below in the start of its script for
* a successful attach (if he doesn't already use threads).
*
* from threading import Thread
* Thread(target=str).start()
*
* -- this is the workaround for the fact that we can't get the gil
* if there aren't any threads (PyGILState_Ensure gives an error).
* ***************************************************************************/


// Access to std::cout and std::endl
#include <iostream>

// DECLDIR will perform an export for us
#define DLL_EXPORT

#include "attach.h"
#include "stdafx.h"
#include "python.h"

#pragma comment(lib, "kernel32.lib")
#pragma comment(lib, "user32.lib")
#pragma comment(lib, "advapi32.lib")
#pragma comment(lib, "psapi.lib")

// _Always_ is not defined for all versions, so make it a no-op if missing.
#ifndef _Always_
#define _Always_(x) x
#endif

using namespace std;

typedef int (Py_IsInitialized)();
typedef void (PyEval_Lock)(); // Acquire/Release lock
typedef void (PyThreadState_API)(PyThreadState *); // Acquire/Release lock
typedef PyInterpreterState* (PyInterpreterState_Head)();
typedef PyThreadState* (PyInterpreterState_ThreadHead)(PyInterpreterState* interp);
typedef PyThreadState* (PyThreadState_Next)(PyThreadState *tstate);
typedef PyThreadState* (PyThreadState_Swap)(PyThreadState *tstate);
typedef PyThreadState* (_PyThreadState_UncheckedGet)();
typedef int (PyRun_SimpleString)(const char *command);
typedef PyObject* (PyDict_New)();
typedef PyObject* (PyModule_New)(const char *name);
typedef PyObject* (PyModule_GetDict)(PyObject *module);
typedef PyObject* (Py_CompileString)(const char *str, const char *filename, int start);
typedef PyObject* (PyEval_EvalCode)(PyObject *co, PyObject *globals, PyObject *locals);
typedef PyObject* (PyDict_GetItemString)(PyObject *p, const char *key);
typedef PyObject* (PyObject_CallFunctionObjArgs)(PyObject *callable, ...);    // call w/ varargs, last arg should be NULL
typedef PyObject* (PyEval_GetBuiltins)();
typedef int (PyDict_SetItemString)(PyObject *dp, const char *key, PyObject *item);
typedef int (PyEval_ThreadsInitialized)();
typedef void (Py_AddPendingCall)(int (*func)(void *), void*);
typedef PyObject* (PyInt_FromLong)(long);
typedef PyObject* (PyString_FromString)(const char* s);
typedef void PyEval_SetTrace(Py_tracefunc func, PyObject *obj);
typedef void (PyErr_Restore)(PyObject *type, PyObject *value, PyObject *traceback);
typedef void (PyErr_Fetch)(PyObject **ptype, PyObject **pvalue, PyObject **ptraceback);
typedef PyObject* (PyErr_Occurred)();
typedef PyObject* (PyErr_Print)();
typedef PyObject* (PyImport_ImportModule) (const char *name);
typedef PyObject* (PyObject_GetAttrString)(PyObject *o, const char *attr_name);
typedef PyObject* (PyObject_HasAttrString)(PyObject *o, const char *attr_name);
typedef PyObject* (PyObject_SetAttrString)(PyObject *o, const char *attr_name, PyObject* value);
typedef PyObject* (PyBool_FromLong)(long v);
typedef enum { PyGILState_LOCKED, PyGILState_UNLOCKED } PyGILState_STATE;
typedef PyGILState_STATE(PyGILState_Ensure)();
typedef void (PyGILState_Release)(PyGILState_STATE);
typedef unsigned long (_PyEval_GetSwitchInterval)(void);
typedef void (_PyEval_SetSwitchInterval)(unsigned long microseconds);
typedef void* (PyThread_get_key_value)(int);
typedef int (PyThread_set_key_value)(int, void*);
typedef void (PyThread_delete_key_value)(int);
typedef PyGILState_STATE PyGILState_EnsureFunc(void);
typedef void PyGILState_ReleaseFunc(PyGILState_STATE);
typedef PyObject* PyInt_FromSize_t(size_t ival);
typedef PyThreadState *PyThreadState_NewFunc(PyInterpreterState *interp);
typedef PyObject* PyObject_Repr(PyObject*);
typedef size_t PyUnicode_AsWideChar(PyObject *unicode, wchar_t *w, size_t size);

class PyObjectHolder;
PyObject* GetPyObjectPointerNoDebugInfo(bool isDebug, PyObject* object);
void DecRef(PyObject* object, bool isDebug);
void IncRef(PyObject* object, bool isDebug);

#define MAX_INTERPRETERS 10

// Helper class so we can use RAII for freeing python objects when they go out of scope
class PyObjectHolder {
private:
    PyObject* _object;
public:
    bool _isDebug;

    PyObjectHolder(bool isDebug) {
        _object = nullptr;
        _isDebug = isDebug;
    }

    PyObjectHolder(bool isDebug, PyObject *object) {
        _object = object;
        _isDebug = isDebug;
    };

    PyObjectHolder(bool isDebug, PyObject *object, bool addRef) {
        _object = object;
        _isDebug = isDebug;
        if (_object != nullptr && addRef) {
            GetPyObjectPointerNoDebugInfo(_isDebug, _object)->ob_refcnt++;
        }
    };

    PyObject* ToPython() {
        return _object;
    }

    ~PyObjectHolder() {
        DecRef(_object, _isDebug);
    }

    PyObject* operator* () {
        return GetPyObjectPointerNoDebugInfo(_isDebug, _object);
    }
};

class InterpreterInfo {
public:
    InterpreterInfo(HMODULE module, bool debug) :
        Interpreter(module),
        CurrentThread(nullptr),
        CurrentThreadGetter(nullptr),
        NewThreadFunction(nullptr),
        PyGILState_Ensure(nullptr),
        Version(PythonVersion_Unknown),
        Call(nullptr),
        IsDebug(debug),
        SetTrace(nullptr),
        PyThreadState_New(nullptr),
        ThreadState_Swap(nullptr) {
    }

    ~InterpreterInfo() {
        if (NewThreadFunction != nullptr) {
            delete NewThreadFunction;
        }
    }

    PyObjectHolder* NewThreadFunction;
    PyThreadState** CurrentThread;
    _PyThreadState_UncheckedGet *CurrentThreadGetter;

    HMODULE Interpreter;
    PyGILState_EnsureFunc* PyGILState_Ensure;
    PyEval_SetTrace* SetTrace;
    PyThreadState_NewFunc* PyThreadState_New;
    PyThreadState_Swap* ThreadState_Swap;

    PythonVersion GetVersion() {
        if (Version == PythonVersion_Unknown) {
            Version = ::GetPythonVersion(Interpreter);
        }
        return Version;
    }

    PyObject_CallFunctionObjArgs* GetCall() {
        if (Call == nullptr) {
            Call = (PyObject_CallFunctionObjArgs*)GetProcAddress(Interpreter, "PyObject_CallFunctionObjArgs");
        }

        return Call;
    }

    bool EnsureSetTrace() {
        if (SetTrace == nullptr) {
            auto setTrace = (PyEval_SetTrace*)(void*)GetProcAddress(Interpreter, "PyEval_SetTrace");
            SetTrace = setTrace;
        }
        return SetTrace != nullptr;
    }

    bool EnsureThreadStateSwap() {
        if (ThreadState_Swap == nullptr) {
            auto swap = (PyThreadState_Swap*)(void*)GetProcAddress(Interpreter, "PyThreadState_Swap");
            ThreadState_Swap = swap;
        }
        return ThreadState_Swap != nullptr;
    }

    bool EnsureCurrentThread() {
        if (CurrentThread == nullptr && CurrentThreadGetter == nullptr) {
            CurrentThreadGetter = (_PyThreadState_UncheckedGet*)GetProcAddress(Interpreter, "_PyThreadState_UncheckedGet");
            CurrentThread = (PyThreadState**)(void*)GetProcAddress(Interpreter, "_PyThreadState_Current");
        }

        return CurrentThread != nullptr || CurrentThreadGetter != nullptr;
    }

    PyThreadState *GetCurrentThread() {
        return CurrentThreadGetter ? CurrentThreadGetter() : *CurrentThread;
    }

private:
    PythonVersion Version;
    PyObject_CallFunctionObjArgs* Call;
    bool IsDebug;
};

DWORD _interpreterCount = 0;
InterpreterInfo* _interpreterInfo[MAX_INTERPRETERS];

void PatchIAT(PIMAGE_DOS_HEADER dosHeader, PVOID replacingFunc, LPSTR exportingDll, LPVOID newFunction) {
    if (dosHeader->e_magic != IMAGE_DOS_SIGNATURE) {
        return;
    }

    auto ntHeader = (IMAGE_NT_HEADERS*)(((BYTE*)dosHeader) + dosHeader->e_lfanew);
    if (ntHeader->Signature != IMAGE_NT_SIGNATURE) {
        return;
    }

    auto importAddr = ntHeader->OptionalHeader.DataDirectory[IMAGE_DIRECTORY_ENTRY_IMPORT].VirtualAddress;
    if (importAddr == 0) {
        return;
    }

    auto import = (PIMAGE_IMPORT_DESCRIPTOR)(importAddr + ((BYTE*)dosHeader));

    while (import->Name) {
        char* name = (char*)(import->Name + ((BYTE*)dosHeader));
        if (_stricmp(name, exportingDll) == 0) {
            auto thunkData = (PIMAGE_THUNK_DATA)((import->FirstThunk) + ((BYTE*)dosHeader));

            while (thunkData->u1.Function) {
                PVOID funcAddr = (char*)(thunkData->u1.Function);

                if (funcAddr == replacingFunc) {
                    DWORD flOldProtect;
                    if (VirtualProtect(&thunkData->u1, sizeof(SIZE_T), PAGE_READWRITE, &flOldProtect)) {
                        thunkData->u1.Function = (SIZE_T)newFunction;
                        VirtualProtect(&thunkData->u1, sizeof(SIZE_T), flOldProtect, &flOldProtect);
                    }
                }
                thunkData++;
            }
        }

        import++;
    }
}

typedef BOOL WINAPI EnumProcessModulesFunc(
    __in   HANDLE hProcess,
    __out  HMODULE *lphModule,
    __in   DWORD cb,
    __out  LPDWORD lpcbNeeded
    );

typedef __kernel_entry NTSTATUS NTAPI
    NtQueryInformationProcessFunc(
    IN HANDLE ProcessHandle,
    IN PROCESSINFOCLASS ProcessInformationClass,
    OUT PVOID ProcessInformation,
    IN ULONG ProcessInformationLength,
    OUT PULONG ReturnLength OPTIONAL
    );


// A helper version of EnumProcessModules.  On Win7 uses the real EnumProcessModules which
// lives in kernel32, and so is safe to use in DLLMain.  Pre-Win7 we use NtQueryInformationProcess
// (http://msdn.microsoft.com/en-us/library/windows/desktop/ms684280(v=vs.85).aspx) and walk the 
// LDR_DATA_TABLE_ENTRY data structures http://msdn.microsoft.com/en-us/library/windows/desktop/aa813708(v=vs.85).aspx
// which have changed in Windows 7, and may change more in the future, so we can't use them there.
__success(return) BOOL EnumProcessModulesHelper(
    __in   HANDLE hProcess,
    __out  HMODULE *lphModule,
    __in   DWORD cb,
    _Always_(__out) LPDWORD lpcbNeeded
    ) {
        if (lpcbNeeded == nullptr) {
            return FALSE;
        }
        *lpcbNeeded = 0;

        auto kernel32 = GetModuleHandle(L"kernel32.dll");
        if (kernel32 == nullptr) {
            return FALSE;
        }

        auto enumProc = (EnumProcessModulesFunc*)GetProcAddress(kernel32, "K32EnumProcessModules");
        if (enumProc == nullptr) {
            // Fallback to pre-Win7 method
            PROCESS_BASIC_INFORMATION basicInfo;
            auto ntdll = GetModuleHandle(L"ntdll.dll");
            if (ntdll == nullptr) {
                return FALSE;
            }

            // http://msdn.microsoft.com/en-us/library/windows/desktop/ms684280(v=vs.85).aspx
            NtQueryInformationProcessFunc* queryInfo = (NtQueryInformationProcessFunc*)GetProcAddress(ntdll, "NtQueryInformationProcess");
            if (queryInfo == nullptr) {
                return FALSE;
            }

            auto result = queryInfo(
                GetCurrentProcess(),
                ProcessBasicInformation,
                &basicInfo,
                sizeof(PROCESS_BASIC_INFORMATION),
                NULL
                );

            if (FAILED(result)) {
                return FALSE;
            }

            // http://msdn.microsoft.com/en-us/library/windows/desktop/aa813708(v=vs.85).aspx
            PEB* peb = basicInfo.PebBaseAddress;
            auto start = (LDR_DATA_TABLE_ENTRY*)(peb->Ldr->InMemoryOrderModuleList.Flink);

            auto cur = start;
            *lpcbNeeded = 0;

            do {
                if ((*lpcbNeeded + sizeof(SIZE_T)) <= cb) {
                    PVOID *curLink = (PVOID*)cur;
                    curLink -= 2;
                    LDR_DATA_TABLE_ENTRY* curTable = (LDR_DATA_TABLE_ENTRY*)curLink;
                    if (curTable->DllBase == nullptr) {
                        break;
                    }
                    lphModule[(*lpcbNeeded) / sizeof(SIZE_T)] = (HMODULE)curTable->DllBase;
                }

                (*lpcbNeeded) += sizeof(SIZE_T);
                cur = (LDR_DATA_TABLE_ENTRY*)((LIST_ENTRY*)cur)->Flink;
            } while (cur != start && cur != 0);

            return *lpcbNeeded <= cb;
        }

        return enumProc(hProcess, lphModule, cb, lpcbNeeded);
}

// This function will work with Win7 and later versions of the OS and is safe to call under
// the loader lock (all APIs used are in kernel32).
BOOL PatchFunction(LPSTR exportingDll, PVOID replacingFunc, LPVOID newFunction) {
    HANDLE hProcess = GetCurrentProcess();
    DWORD modSize = sizeof(HMODULE) * 1024;
    HMODULE* hMods = (HMODULE*)_malloca(modSize);
    DWORD modsNeeded = 0;
    if (hMods == nullptr) {
        modsNeeded = 0;
        return FALSE;
    }

    while (!EnumProcessModulesHelper(hProcess, hMods, modSize, &modsNeeded)) {
        // try again w/ more space...
        _freea(hMods);
        hMods = (HMODULE*)_malloca(modsNeeded);
        if (hMods == nullptr) {
            modsNeeded = 0;
            break;
        }
        modSize = modsNeeded;
    }

    for (DWORD tmp = 0; tmp < modsNeeded / sizeof(HMODULE); tmp++) {
        PIMAGE_DOS_HEADER dosHeader = (PIMAGE_DOS_HEADER)hMods[tmp];

        PatchIAT(dosHeader, replacingFunc, exportingDll, newFunction);
    }

    return TRUE;
}

wstring GetCurrentModuleFilename() {
    HMODULE hModule = NULL;
    if (GetModuleHandleEx(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS | GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT, (LPCTSTR)GetCurrentModuleFilename, &hModule) != 0) {
        wchar_t filename[MAX_PATH];
        GetModuleFileName(hModule, filename, MAX_PATH);
        return filename;
    }
    return wstring();
}

struct AttachInfo {
    PyEval_Lock* InitThreads;
    HANDLE Event;
};

HANDLE g_initedEvent;
int AttachCallback(void *initThreads) {
    // initialize us for threading, this will acquire the GIL if not already created, and is a nop if the GIL is created.
    // This leaves us in the proper state when we return back to the runtime whether the GIL was created or not before
    // we were called.
    ((PyEval_Lock*)initThreads)();
    SetEvent(g_initedEvent);
    return 0;
}

char* ReadCodeFromFile(wchar_t* filePath) {
    ifstream filestr;
    filestr.open(filePath, ios::binary);
    if (filestr.fail()) {
        return nullptr;
    }

    // get length of file:
    filestr.seekg(0, ios::end);
    auto length = filestr.tellg();
    filestr.seekg(0, ios::beg);

    int len = (int)length;
    char* buffer = new char[len + 1];
    filestr.read(buffer, len);
    buffer[len] = 0;

    // remove carriage returns, copy zero byte
    for (int read = 0, write = 0; read <= len; read++) {
        if (buffer[read] == '\r') {
            continue;
        } else if (write != read) {
            buffer[write] = buffer[read];
        }
        write++;
    }

    return buffer;
}

// create a custom heap for our unordered map.  This is necessary because if we suspend a thread while in a heap function
// then we could deadlock here.  We need to be VERY careful about what we do while the threads are suspended.
static HANDLE g_heap = 0;

template<typename T>
class PrivateHeapAllocator {
public:
    typedef size_t    size_type;
    typedef ptrdiff_t difference_type;
    typedef T*        pointer;
    typedef const T*  const_pointer;
    typedef T&        reference;
    typedef const T&  const_reference;
    typedef T         value_type;

    template<class U>
    struct rebind {
        typedef PrivateHeapAllocator<U> other;
    };

    explicit PrivateHeapAllocator() {}

    PrivateHeapAllocator(PrivateHeapAllocator const&) {}

    ~PrivateHeapAllocator() {}

    template<typename U>
    PrivateHeapAllocator(PrivateHeapAllocator<U> const&) {}

    pointer allocate(size_type size, allocator<void>::const_pointer hint = 0) {
        UNREFERENCED_PARAMETER(hint);

        if (g_heap == nullptr) {
            g_heap = HeapCreate(0, 0, 0);
        }
        auto mem = HeapAlloc(g_heap, 0, size * sizeof(T));
        return static_cast<pointer>(mem);
    }

    void deallocate(pointer p, size_type n) {
        UNREFERENCED_PARAMETER(n);

        HeapFree(g_heap, 0, p);
    }

    size_type max_size() const {
        return (std::numeric_limits<size_type>::max)() / sizeof(T);
    }

    void construct(pointer p, const T& t) {
        new(p) T(t);
    }

    void destroy(pointer p) {
        p->~T();
    }
};

typedef unordered_map<DWORD, HANDLE, std::hash<DWORD>, std::equal_to<DWORD>, PrivateHeapAllocator<pair<DWORD, HANDLE>>> ThreadMap;

void ResumeThreads(ThreadMap &suspendedThreads) {
    for (auto start = suspendedThreads.begin();  start != suspendedThreads.end(); start++) {
        ResumeThread((*start).second);
        CloseHandle((*start).second);
    }
    suspendedThreads.clear();
}

// Suspends all threads ensuring that they are not currently in a call to Py_AddPendingCall.
void SuspendThreads(ThreadMap &suspendedThreads, Py_AddPendingCall* addPendingCall, PyEval_ThreadsInitialized* threadsInited) {
    DWORD curThreadId = GetCurrentThreadId();
    DWORD curProcess = GetCurrentProcessId();
    // suspend all the threads in the process so we can do things safely...
    bool suspended;

    do {
        suspended = false;
        HANDLE h = CreateToolhelp32Snapshot(TH32CS_SNAPTHREAD, 0);
        if (h != INVALID_HANDLE_VALUE) {

            THREADENTRY32 te;
            te.dwSize = sizeof(te);
            if (Thread32First(h, &te)) {
                do {
                    if (te.dwSize >= FIELD_OFFSET(THREADENTRY32, th32OwnerProcessID) + sizeof(te.th32OwnerProcessID) && te.th32OwnerProcessID == curProcess) {


                        if (te.th32ThreadID != curThreadId && suspendedThreads.find(te.th32ThreadID) == suspendedThreads.end()) {
                            auto hThread = OpenThread(THREAD_ALL_ACCESS, FALSE, te.th32ThreadID);
                            if (hThread != nullptr) {
                                SuspendThread(hThread);

                                bool addingPendingCall = false;

                                CONTEXT context;
                                memset(&context, 0x00, sizeof(CONTEXT));
                                context.ContextFlags = CONTEXT_ALL;
                                GetThreadContext(hThread, &context);

#if defined(_X86_)
                                if(context.Eip >= *((DWORD*)addPendingCall) && context.Eip <= (*((DWORD*)addPendingCall)) + 0x100) {
                                    addingPendingCall = true;
                                }
#elif defined(_AMD64_)
                                if (context.Rip >= *((DWORD64*)addPendingCall) && context.Rip <= *((DWORD64*)addPendingCall + 0x100)) {
                                    addingPendingCall = true;
                                }
#endif

                                if (addingPendingCall) {
                                    // we appear to be adding a pending call via this thread - wait for this to finish so we can add our own pending call...
                                    ResumeThread(hThread);
                                    SwitchToThread();   // yield to the resumed thread if it's on our CPU...
                                    CloseHandle(hThread);
                                } else {
                                    suspendedThreads[te.th32ThreadID] = hThread;
                                }
                                suspended = true;
                            }
                        }
                    }

                    te.dwSize = sizeof(te);
                } while (Thread32Next(h, &te) && !threadsInited());
            }
            CloseHandle(h);
        }
    } while (suspended && !threadsInited());
}

PyObject* GetPyObjectPointerNoDebugInfo(bool isDebug, PyObject* object) {
    if (object != nullptr && isDebug) {
        // debug builds have 2 extra pointers at the front that we don't care about
        return (PyObject*)((size_t*)object + 2);
    }
    return object;
}

void DecRef(PyObject* object, bool isDebug) {
    auto noDebug = GetPyObjectPointerNoDebugInfo(isDebug, object);

    if (noDebug != nullptr && --noDebug->ob_refcnt == 0) {
        ((PyTypeObject*)GetPyObjectPointerNoDebugInfo(isDebug, noDebug->ob_type))->tp_dealloc(object);
    }
}

void IncRef(PyObject* object) {
    object->ob_refcnt++;
}


// Ensures handles are closed when they go out of scope
class HandleHolder {
    HANDLE _handle;
public:
    HandleHolder(HANDLE handle) : _handle(handle) {
    }

    ~HandleHolder() {
        CloseHandle(_handle);
    }
};

DWORD GetPythonThreadId(PythonVersion version, PyThreadState* curThread) {
    DWORD threadId = 0;
    if (PyThreadState_25_27::IsFor(version)) {
        threadId = (DWORD)((PyThreadState_25_27*)curThread)->thread_id;
    } else if (PyThreadState_30_33::IsFor(version)) {
        threadId = (DWORD)((PyThreadState_30_33*)curThread)->thread_id;
    } else if (PyThreadState_34_36::IsFor(version)) {
        threadId = (DWORD)((PyThreadState_34_36*)curThread)->thread_id;
    } else if (PyThreadState_37::IsFor(version)) {
        threadId = (DWORD)((PyThreadState_37*)curThread)->thread_id;
    }
    return threadId;
}

// holder to ensure we release the GIL even in error conditions
class GilHolder {
    PyGILState_STATE _gilState;
    PyGILState_Release* _release;
public:
    GilHolder(PyGILState_Ensure* acquire, PyGILState_Release* release) {
        _gilState = acquire();
        _release = release;
    }

    ~GilHolder() {
        _release(_gilState);
    }
};

bool LoadAndEvaluateCode(
    wchar_t* filePath, const char* fileName, bool isDebug, PyObject* globalsDict,
    Py_CompileString* pyCompileString, PyDict_SetItemString* dictSetItem,
    PyEval_EvalCode* pyEvalCode, PyString_FromString* strFromString, PyEval_GetBuiltins* getBuiltins,
    PyErr_Print pyErrPrint
 ) {
    auto debuggerCode = ReadCodeFromFile(filePath);
    if (debuggerCode == nullptr) {
        return false;
    }

    auto code = PyObjectHolder(isDebug, pyCompileString(debuggerCode, fileName, 257 /*Py_file_input*/));
    delete[] debuggerCode;

    if (*code == nullptr) {
        return false;
    }

    dictSetItem(globalsDict, "__builtins__", getBuiltins());
    auto size = WideCharToMultiByte(CP_UTF8, 0, filePath, (DWORD)wcslen(filePath), NULL, 0, NULL, NULL);
    char* filenameBuffer = new char[size + 1];
    if (WideCharToMultiByte(CP_UTF8, 0, filePath, (DWORD)wcslen(filePath), filenameBuffer, size, NULL, NULL) != 0) {
        filenameBuffer[size] = 0;
        dictSetItem(globalsDict, "__file__", strFromString(filenameBuffer));
    }

    auto evalResult = PyObjectHolder(isDebug, pyEvalCode(code.ToPython(), globalsDict, globalsDict));
#if !NDEBUG
    if (*evalResult == nullptr) {
        pyErrPrint();
    }
#else
    UNREFERENCED_PARAMETER(pyErrPrint);
#endif

    return true;
}

// Checks to see if the specified module is likely a Python interpreter.
bool IsPythonModule(HMODULE module, bool &isDebug) {
    wchar_t mod_name[MAX_PATH];
    isDebug = false;
    if (GetModuleBaseName(GetCurrentProcess(), module, mod_name, MAX_PATH)) {
        if (_wcsnicmp(mod_name, L"python", 6) == 0) {
            if (wcslen(mod_name) >= 10 && _wcsnicmp(mod_name + 8, L"_d", 2) == 0) {
                isDebug = true;
            }
            return true;
        }
    }
    return false;
}

extern "C"
{

    /**
     * The returned value signals the error that happened!
     *
     * Return codes:
     * 0 = all OK.
     * 1 = Py_IsInitialized not found
     * 2 = Py_IsInitialized returned false
     * 3 = Missing Python API
     * 4 = Interpreter not initialized
     * 5 = Python version unknown
     * 6 = Connect timeout
     **/
	int DoAttach(HMODULE module, bool isDebug, const char *command, bool showDebugInfo )
	{
        auto isInit = (Py_IsInitialized*)GetProcAddress(module, "Py_IsInitialized");

        if (isInit == nullptr) {
            if(showDebugInfo){
                std::cout << "Py_IsInitialized not found. " << std::endl << std::flush;
            }
            return 1;
        }
        if (!isInit()) {
            if(showDebugInfo){
                std::cout << "Py_IsInitialized returned false. " << std::endl << std::flush;
            }
            return 2;
        }

        auto version = GetPythonVersion(module);

        // found initialized Python runtime, gather and check the APIs we need for a successful attach...
        auto addPendingCall = (Py_AddPendingCall*)GetProcAddress(module, "Py_AddPendingCall");
        auto interpHead = (PyInterpreterState_Head*)GetProcAddress(module, "PyInterpreterState_Head");
        auto gilEnsure = (PyGILState_Ensure*)GetProcAddress(module, "PyGILState_Ensure");
        auto gilRelease = (PyGILState_Release*)GetProcAddress(module, "PyGILState_Release");
        auto threadHead = (PyInterpreterState_ThreadHead*)GetProcAddress(module, "PyInterpreterState_ThreadHead");
        auto initThreads = (PyEval_Lock*)GetProcAddress(module, "PyEval_InitThreads");
        auto releaseLock = (PyEval_Lock*)GetProcAddress(module, "PyEval_ReleaseLock");
        auto threadsInited = (PyEval_ThreadsInitialized*)GetProcAddress(module, "PyEval_ThreadsInitialized");
        auto threadNext = (PyThreadState_Next*)GetProcAddress(module, "PyThreadState_Next");
        auto threadSwap = (PyThreadState_Swap*)GetProcAddress(module, "PyThreadState_Swap");
        auto pyDictNew = (PyDict_New*)GetProcAddress(module, "PyDict_New");
        auto pyCompileString = (Py_CompileString*)GetProcAddress(module, "Py_CompileString");
        auto pyEvalCode = (PyEval_EvalCode*)GetProcAddress(module, "PyEval_EvalCode");
        auto getDictItem = (PyDict_GetItemString*)GetProcAddress(module, "PyDict_GetItemString");
        auto call = (PyObject_CallFunctionObjArgs*)GetProcAddress(module, "PyObject_CallFunctionObjArgs");
        auto getBuiltins = (PyEval_GetBuiltins*)GetProcAddress(module, "PyEval_GetBuiltins");
        auto dictSetItem = (PyDict_SetItemString*)GetProcAddress(module, "PyDict_SetItemString");
        PyInt_FromLong* intFromLong;
        PyString_FromString* strFromString;
        PyInt_FromSize_t* intFromSizeT;
        if (version >= PythonVersion_30) {
            intFromLong = (PyInt_FromLong*)GetProcAddress(module, "PyLong_FromLong");
            intFromSizeT = (PyInt_FromSize_t*)GetProcAddress(module, "PyLong_FromSize_t");
            if (version >= PythonVersion_33) {
                strFromString = (PyString_FromString*)GetProcAddress(module, "PyUnicode_FromString");
            } else {
                strFromString = (PyString_FromString*)GetProcAddress(module, "PyUnicodeUCS2_FromString");
            }
        } else {
            intFromLong = (PyInt_FromLong*)GetProcAddress(module, "PyInt_FromLong");
            strFromString = (PyString_FromString*)GetProcAddress(module, "PyString_FromString");
            intFromSizeT = (PyInt_FromSize_t*)GetProcAddress(module, "PyInt_FromSize_t");
        }
        auto errOccurred = (PyErr_Occurred*)GetProcAddress(module, "PyErr_Occurred");
        auto pyErrFetch = (PyErr_Fetch*)GetProcAddress(module, "PyErr_Fetch");
        auto pyErrRestore = (PyErr_Restore*)GetProcAddress(module, "PyErr_Restore");
        auto pyErrPrint = (PyErr_Print*)GetProcAddress(module, "PyErr_Print");
        auto pyImportMod = (PyImport_ImportModule*) GetProcAddress(module, "PyImport_ImportModule");
        auto pyGetAttr = (PyObject_GetAttrString*)GetProcAddress(module, "PyObject_GetAttrString");
        auto pySetAttr = (PyObject_SetAttrString*)GetProcAddress(module, "PyObject_SetAttrString");
        auto pyNone = (PyObject*)GetProcAddress(module, "_Py_NoneStruct");

        auto boolFromLong = (PyBool_FromLong*)GetProcAddress(module, "PyBool_FromLong");
        auto getThreadTls = (PyThread_get_key_value*)GetProcAddress(module, "PyThread_get_key_value");
        auto setThreadTls = (PyThread_set_key_value*)GetProcAddress(module, "PyThread_set_key_value");
        auto delThreadTls = (PyThread_delete_key_value*)GetProcAddress(module, "PyThread_delete_key_value");
        auto PyCFrame_Type = (PyTypeObject*)GetProcAddress(module, "PyCFrame_Type");
        auto pyRun_SimpleString = (PyRun_SimpleString*)GetProcAddress(module, "PyRun_SimpleString");
        auto pyObjectRepr = (PyObject_Repr*)GetProcAddress(module, "PyObject_Repr");
        auto pyUnicodeAsWideChar = (PyUnicode_AsWideChar*)GetProcAddress(module,
            version < PythonVersion_33 ? "PyUnicodeUCS2_AsWideChar" : "PyUnicode_AsWideChar");

        // Either _PyThreadState_Current or _PyThreadState_UncheckedGet are required
        auto curPythonThread = (PyThreadState**)(void*)GetProcAddress(module, "_PyThreadState_Current");
        auto getPythonThread = (_PyThreadState_UncheckedGet*)GetProcAddress(module, "_PyThreadState_UncheckedGet");

        // Either _Py_CheckInterval or _PyEval_[GS]etSwitchInterval are useful, but not required
        auto intervalCheck = (int*)GetProcAddress(module, "_Py_CheckInterval");
        auto getSwitchInterval = (_PyEval_GetSwitchInterval*)GetProcAddress(module, "_PyEval_GetSwitchInterval");
        auto setSwitchInterval = (_PyEval_SetSwitchInterval*)GetProcAddress(module, "_PyEval_SetSwitchInterval");

        if (addPendingCall == nullptr || interpHead == nullptr || gilEnsure == nullptr || gilRelease == nullptr || threadHead == nullptr ||
            initThreads == nullptr || releaseLock == nullptr || threadsInited == nullptr || threadNext == nullptr || threadSwap == nullptr ||
            pyDictNew == nullptr || pyCompileString == nullptr || pyEvalCode == nullptr || getDictItem == nullptr || call == nullptr ||
            getBuiltins == nullptr || dictSetItem == nullptr || intFromLong == nullptr || pyErrRestore == nullptr || pyErrFetch == nullptr ||
            errOccurred == nullptr || pyImportMod == nullptr || pyGetAttr == nullptr || pyNone == nullptr || pySetAttr == nullptr || boolFromLong == nullptr ||
            getThreadTls == nullptr || setThreadTls == nullptr || delThreadTls == nullptr || pyObjectRepr == nullptr || pyUnicodeAsWideChar == nullptr ||
            pyRun_SimpleString == nullptr ||
            (curPythonThread == nullptr && getPythonThread == nullptr)) {
                // we're missing some APIs, we cannot attach.
                if(showDebugInfo){
                    std::cout << "Error, missing Python API!! " << std::endl << std::flush;
                }
                return 3;
        }

        auto head = interpHead();
        if (head == nullptr) {
            // this interpreter is loaded but not initialized.
            if(showDebugInfo){
                std::cout << "Interpreter not initialized! " << std::endl << std::flush;
            }
            return 4;
        }

        bool threadSafeAddPendingCall = false;

        // check that we're a supported version
        if (version == PythonVersion_Unknown) {
            if(showDebugInfo){
                std::cout << "Python version unknown! " << std::endl << std::flush;
            }
            return 5;
        } else if (version >= PythonVersion_27 && version != PythonVersion_30) {
            threadSafeAddPendingCall = true;
        }






        if (!threadsInited()) {
            int saveIntervalCheck;
            unsigned long saveLongIntervalCheck;
            if (intervalCheck != nullptr) {
                // not available on 3.2
                saveIntervalCheck = *intervalCheck;
                *intervalCheck = -1;    // lower the interval check so pending calls are processed faster
                saveLongIntervalCheck = 0; // prevent compiler warning
            } else if (getSwitchInterval != nullptr && setSwitchInterval != nullptr) {
                saveLongIntervalCheck = getSwitchInterval();
                setSwitchInterval(0);
                saveIntervalCheck = 0; // prevent compiler warning
            }
            else {
                saveIntervalCheck = 0; // prevent compiler warning
                saveLongIntervalCheck = 0; // prevent compiler warning
            }

            //
            // Multiple thread support has not been initialized in the interpreter.   We need multi threading support
            // to block any actively running threads and setup the debugger attach state.
            //
            // We need to initialize multiple threading support but we need to do so safely.  One option is to call
            // Py_AddPendingCall and have our callback then initialize multi threading.  This is completely safe on 2.7
            // and up.  Unfortunately that doesn't work if we're not actively running code on the main thread (blocked on a lock
            // or reading input).  It's also not thread safe pre-2.7 so we need to make sure it's safe to call on down-level
            // interpreters.
            //
            // Another option is to make sure no code is running - if there is no active thread then we can safely call
            // PyEval_InitThreads and we're in business.  But to know this is safe we need to first suspend all the other
            // threads in the process and then inspect if any code is running.
            //
            // Finally if code is running after we've suspended the threads then we can go ahead and do Py_AddPendingCall
            // on down-level interpreters as long as we're sure no one else is making a call to Py_AddPendingCall at the same
            // time.
            //
            // Therefore our strategy becomes: Make the Py_AddPendingCall on interpreters where it's thread safe.  Then suspend
            // all threads - if a threads IP is in Py_AddPendingCall resume and try again.  Once we've got all of the threads
            // stopped and not in Py_AddPendingCall (which calls no functions its self, you can see this and it's size in the
            // debugger) then see if we have a current thread.   If not go ahead and initialize multiple threading (it's now safe,
            // no Python code is running).  Otherwise add the pending call and repeat.  If at any point during this process
            // threading becomes initialized (due to our pending call or the Python code creating a new thread)  then we're done
            // and we just resume all of the presently suspended threads.

            ThreadMap suspendedThreads;

            g_initedEvent = CreateEvent(NULL, TRUE, FALSE, NULL);
            HandleHolder holder(g_initedEvent);

            bool addedPendingCall = false;
            if (addPendingCall != nullptr && threadSafeAddPendingCall) {
                // we're on a thread safe Python version, go ahead and pend our call to initialize threading.
                addPendingCall(&AttachCallback, initThreads);
                addedPendingCall = true;
            }

 #define TICKS_DIFF(prev, cur) ((cur) >= (prev)) ? ((cur)-(prev)) : ((0xFFFFFFFF-(prev))+(cur))
             const DWORD ticksPerSecond = 1000;

            DWORD startTickCount = GetTickCount();
            do {
                SuspendThreads(suspendedThreads, addPendingCall, threadsInited);

                 if (!threadsInited()) {
                    auto curPyThread = getPythonThread ? getPythonThread() : *curPythonThread;
                    
                    if (curPyThread == nullptr) {
                         // no threads are currently running, it is safe to initialize multi threading.
                         PyGILState_STATE gilState;
                         if (version >= PythonVersion_34) {
                             // in 3.4 due to http://bugs.python.org/issue20891,
                             // we need to create our thread state manually
                             // before we can call PyGILState_Ensure() before we
                             // can call PyEval_InitThreads().

                             // Don't require this function unless we need it.
                             auto threadNew = (PyThreadState_NewFunc*)GetProcAddress(module, "PyThreadState_New");
                             if (threadNew != nullptr) {
                                 threadNew(head);
                             }
                         }

                         if (version >= PythonVersion_32) {
                             // in 3.2 due to the new GIL and later we can't call Py_InitThreads
                             // without a thread being initialized.
                             // So we use PyGilState_Ensure here to first
                             // initialize the current thread, and then we use
                             // Py_InitThreads to bring up multi-threading.
                             // Some context here: http://bugs.python.org/issue11329
                             // http://pytools.codeplex.com/workitem/834
                            gilState = gilEnsure();
                        }
                        else {
                            gilState = PyGILState_LOCKED; // prevent compiler warning
                         }

                         initThreads();

                         if (version >= PythonVersion_32) {
                             // we will release the GIL here
                            gilRelease(gilState);
                         } else {
                             releaseLock();
                         }
                    } else if (!addedPendingCall) {
                        // someone holds the GIL but no one is actively adding any pending calls.  We can pend our call
                        // and initialize threads.
                        addPendingCall(&AttachCallback, initThreads);
                        addedPendingCall = true;
                    }
                }
                ResumeThreads(suspendedThreads);
            } while (!threadsInited() &&
                (TICKS_DIFF(startTickCount, GetTickCount())) < (ticksPerSecond * 20) &&
                !addedPendingCall);

            if (!threadsInited()) {
                if (addedPendingCall) {
                    // we've added our call to initialize multi-threading, we can now wait
                    // until Python code actually starts running.
                    if(showDebugInfo){
                        std::cout << "Waiting for threads to be initialized! " << std::endl << std::flush;
                    }
                    
                    ::WaitForSingleObject(g_initedEvent, INFINITE);
                } else {
                    if(showDebugInfo){
                        std::cout << "Connect timeout! " << std::endl << std::flush;
                    }
                    return 6;
                }
            } else {
                if(showDebugInfo){
                    std::cout << "Threads initialized! " << std::endl << std::flush;
                }
            }

             if (intervalCheck != nullptr) {
                 *intervalCheck = saveIntervalCheck;
             } else if (setSwitchInterval != nullptr) {
                 setSwitchInterval(saveLongIntervalCheck);
             }
        } else {
            if(showDebugInfo){
                std::cout << "Threads already initialized! " << std::endl << std::flush;
            }
        }

        if (g_heap != nullptr) {
            HeapDestroy(g_heap);
            g_heap = nullptr;
        }


        GilHolder gilLock(gilEnsure, gilRelease);   // acquire and hold the GIL until done...
        
        pyRun_SimpleString(command);
        return 0;

    }




    int SetSysTraceFunc(HMODULE module, bool isDebug, bool showDebugInfo)
    {
    
        if(showDebugInfo){
            std::cout << "SetSysTraceFunc started. " << std::endl << std::flush;
        }
        auto isInit = (Py_IsInitialized*)GetProcAddress(module, "Py_IsInitialized");

        if (isInit == nullptr) {
            if(showDebugInfo){
                std::cout << "Py_IsInitialized not found. " << std::endl << std::flush;
            }
            return 51;
        }
        if (!isInit()) {
            if(showDebugInfo){
                std::cout << "Py_IsInitialized returned false. " << std::endl << std::flush;
            }
            return 52;
        }

        auto version = GetPythonVersion(module);

        // found initialized Python runtime, gather and check the APIs we need for a successful attach...
        auto addPendingCall = (Py_AddPendingCall*)GetProcAddress(module, "Py_AddPendingCall");
        auto curPythonThread = (PyThreadState**)(void*)GetProcAddress(module, "_PyThreadState_Current");
        auto interpHead = (PyInterpreterState_Head*)GetProcAddress(module, "PyInterpreterState_Head");
        auto gilEnsure = (PyGILState_Ensure*)GetProcAddress(module, "PyGILState_Ensure");
        auto gilRelease = (PyGILState_Release*)GetProcAddress(module, "PyGILState_Release");
        auto threadHead = (PyInterpreterState_ThreadHead*)GetProcAddress(module, "PyInterpreterState_ThreadHead");
        auto initThreads = (PyEval_Lock*)GetProcAddress(module, "PyEval_InitThreads");
        auto releaseLock = (PyEval_Lock*)GetProcAddress(module, "PyEval_ReleaseLock");
        auto threadsInited = (PyEval_ThreadsInitialized*)GetProcAddress(module, "PyEval_ThreadsInitialized");
        auto threadNext = (PyThreadState_Next*)GetProcAddress(module, "PyThreadState_Next");
        auto threadSwap = (PyThreadState_Swap*)GetProcAddress(module, "PyThreadState_Swap");
        auto pyDictNew = (PyDict_New*)GetProcAddress(module, "PyDict_New");
        auto pyCompileString = (Py_CompileString*)GetProcAddress(module, "Py_CompileString");
        auto pyEvalCode = (PyEval_EvalCode*)GetProcAddress(module, "PyEval_EvalCode");
        auto getDictItem = (PyDict_GetItemString*)GetProcAddress(module, "PyDict_GetItemString");
        auto call = (PyObject_CallFunctionObjArgs*)GetProcAddress(module, "PyObject_CallFunctionObjArgs");
        auto getBuiltins = (PyEval_GetBuiltins*)GetProcAddress(module, "PyEval_GetBuiltins");
        auto dictSetItem = (PyDict_SetItemString*)GetProcAddress(module, "PyDict_SetItemString");
        PyInt_FromLong* intFromLong;
        PyString_FromString* strFromString;
        PyInt_FromSize_t* intFromSizeT;
        if (version >= PythonVersion_30) {
            intFromLong = (PyInt_FromLong*)GetProcAddress(module, "PyLong_FromLong");
            intFromSizeT = (PyInt_FromSize_t*)GetProcAddress(module, "PyLong_FromSize_t");
            if (version >= PythonVersion_33) {
                strFromString = (PyString_FromString*)GetProcAddress(module, "PyUnicode_FromString");
            } else {
                strFromString = (PyString_FromString*)GetProcAddress(module, "PyUnicodeUCS2_FromString");
            }
        } else {
            intFromLong = (PyInt_FromLong*)GetProcAddress(module, "PyInt_FromLong");
            strFromString = (PyString_FromString*)GetProcAddress(module, "PyString_FromString");
            intFromSizeT = (PyInt_FromSize_t*)GetProcAddress(module, "PyInt_FromSize_t");
        }
        auto intervalCheck = (int*)GetProcAddress(module, "_Py_CheckInterval");
        auto errOccurred = (PyErr_Occurred*)GetProcAddress(module, "PyErr_Occurred");
        auto pyErrFetch = (PyErr_Fetch*)GetProcAddress(module, "PyErr_Fetch");
        auto pyErrRestore = (PyErr_Restore*)GetProcAddress(module, "PyErr_Restore");
        auto pyErrPrint = (PyErr_Print*)GetProcAddress(module, "PyErr_Print");
        auto pyImportMod = (PyImport_ImportModule*) GetProcAddress(module, "PyImport_ImportModule");
        auto pyGetAttr = (PyObject_GetAttrString*)GetProcAddress(module, "PyObject_GetAttrString");
        auto pySetAttr = (PyObject_SetAttrString*)GetProcAddress(module, "PyObject_SetAttrString");
        auto pyHasAttr = (PyObject_HasAttrString*)GetProcAddress(module, "PyObject_HasAttrString");
        auto pyNone = (PyObject*)GetProcAddress(module, "_Py_NoneStruct");
        auto getSwitchInterval = (_PyEval_GetSwitchInterval*)GetProcAddress(module, "_PyEval_GetSwitchInterval");
        auto setSwitchInterval = (_PyEval_SetSwitchInterval*)GetProcAddress(module, "_PyEval_SetSwitchInterval");
        auto boolFromLong = (PyBool_FromLong*)GetProcAddress(module, "PyBool_FromLong");
        auto getThreadTls = (PyThread_get_key_value*)GetProcAddress(module, "PyThread_get_key_value");
        auto setThreadTls = (PyThread_set_key_value*)GetProcAddress(module, "PyThread_set_key_value");
        auto delThreadTls = (PyThread_delete_key_value*)GetProcAddress(module, "PyThread_delete_key_value");
        auto PyCFrame_Type = (PyTypeObject*)GetProcAddress(module, "PyCFrame_Type");
        auto getPythonThread = (_PyThreadState_UncheckedGet*)GetProcAddress(module, "_PyThreadState_UncheckedGet");

        if (addPendingCall == nullptr || interpHead == nullptr || gilEnsure == nullptr || gilRelease == nullptr || threadHead == nullptr ||
            initThreads == nullptr || releaseLock == nullptr || threadsInited == nullptr || threadNext == nullptr || threadSwap == nullptr ||
            pyDictNew == nullptr || pyCompileString == nullptr || pyEvalCode == nullptr || getDictItem == nullptr || call == nullptr ||
            getBuiltins == nullptr || dictSetItem == nullptr || intFromLong == nullptr || pyErrRestore == nullptr || pyErrFetch == nullptr ||
            errOccurred == nullptr || pyImportMod == nullptr || pyGetAttr == nullptr || pyNone == nullptr || pySetAttr == nullptr || boolFromLong == nullptr ||
            getThreadTls == nullptr || setThreadTls == nullptr || delThreadTls == nullptr || releaseLock == nullptr ||
            (curPythonThread == nullptr && getPythonThread == nullptr)) {
                // we're missing some APIs, we cannot attach.
                if(showDebugInfo){
                    std::cout << "Error, missing Python API!! " << std::endl << std::flush;
                }
                return 53;
        }
        
        auto head = interpHead();
        if (head == nullptr) {
            // this interpreter is loaded but not initialized.
            if(showDebugInfo){
                std::cout << "Interpreter not initialized! " << std::endl << std::flush;
            }
            return 54;
        }

        GilHolder gilLock(gilEnsure, gilRelease);   // acquire and hold the GIL until done...
        
        auto pyTrue = boolFromLong(1);
        auto pyFalse = boolFromLong(0);

        
        auto pydevdTracingMod = PyObjectHolder(isDebug, pyImportMod("pydevd_tracing"));
        if (*pydevdTracingMod == nullptr) {
            if(showDebugInfo){
                std::cout << "pydevd_tracing module null! " << std::endl << std::flush;
            }
            return 57;
        }

        if(!pyHasAttr(pydevdTracingMod.ToPython(), "_original_settrace")){
            if(showDebugInfo){
                std::cout << "pydevd_tracing module has no _original_settrace! " << std::endl << std::flush;
            }
            return 58;
        }
        
        auto settrace = PyObjectHolder(isDebug, pyGetAttr(pydevdTracingMod.ToPython(), "_original_settrace"));
        if (*settrace == nullptr) {
            if(showDebugInfo){
                std::cout << "pydevd_tracing._original_settrace null! " << std::endl << std::flush;
            }
            return 59;
        }
        
        auto pydevdMod = PyObjectHolder(isDebug, pyImportMod("pydevd"));
        if (*pydevdMod == nullptr) {
            if(showDebugInfo){
                std::cout << "pydevd module null! " << std::endl << std::flush;
            }
            return 60;
        }
        
        auto getGlobalDebugger = PyObjectHolder(isDebug, pyGetAttr(pydevdMod.ToPython(), "get_global_debugger"));
        if (*getGlobalDebugger == nullptr) {
            if(showDebugInfo){
                std::cout << "pydevd.get_global_debugger null! " << std::endl << std::flush;
            }
            return 61;
        }
        
        auto globalDbg = PyObjectHolder(isDebug, call(getGlobalDebugger.ToPython(), NULL));
        if (*globalDbg == nullptr) {
            if(showDebugInfo){
                std::cout << "pydevd.get_global_debugger() returned null! " << std::endl << std::flush;
            }
            return 62;
        }
        
        if(!pyHasAttr(globalDbg.ToPython(), "trace_dispatch")){
            if(showDebugInfo){
                std::cout << "pydevd.get_global_debugger() has no attribute trace_dispatch! " << std::endl << std::flush;
            }
            return 63;
        }
        
        auto traceFunc = PyObjectHolder(isDebug, pyGetAttr(globalDbg.ToPython(), "trace_dispatch"));
        if (*traceFunc == nullptr) {
            if(showDebugInfo){
                std::cout << "pydevd.get_global_debugger().trace_dispatch returned null! " << std::endl << std::flush;
            }
            return 64;
        }



        // we need to walk the thread list each time after we've initialized a thread so that we are always
        // dealing w/ a valid thread list (threads can exit when we run code and therefore the current thread
        // could be corrupt).  We also don't care about newly created threads as our start_new_thread wrapper
        // will handle those.  So we collect the initial set of threads first here so that we don't keep iterating
        // if the program is spawning large numbers of threads.
        unordered_set<PyThreadState*> initialThreads;
        for (auto curThread = threadHead(head); curThread != nullptr; curThread = threadNext(curThread)) {
            initialThreads.insert(curThread);
        }

        int retVal = 0;
        unordered_set<PyThreadState*> seenThreads;
        {
            // find what index is holding onto the thread state...
            auto curPyThread = getPythonThread ? getPythonThread() : *curPythonThread;
            int threadStateIndex = -1;
            for (int i = 0; i < 100000; i++) {
                void* value = getThreadTls(i);
                if (value == curPyThread) {
                    threadStateIndex = i;
                    break;
                }
            }

            bool foundThread;
            int processedThreads = 0;
            do {
                foundThread = false;
                for (auto curThread = threadHead(head); curThread != nullptr; curThread = threadNext(curThread)) {
                    if (initialThreads.find(curThread) == initialThreads.end() ||
                        seenThreads.insert(curThread).second == false) {
                            continue;
                    }
                    foundThread = true;
                    processedThreads++;

                    DWORD threadId = GetPythonThreadId(version, curThread);
                    // skip this thread - it doesn't really have any Python code on it...
                    if (threadId != GetCurrentThreadId()) {
                        // create new debugger Thread object on our injected thread
                        auto pyThreadId = PyObjectHolder(isDebug, intFromLong(threadId));
                        PyFrameObject* frame;
                        // update all of the frames so they have our trace func
                        if (PyThreadState_25_27::IsFor(version)) {
                            frame = ((PyThreadState_25_27*)curThread)->frame;
                        } else if (PyThreadState_30_33::IsFor(version)) {
                            frame = ((PyThreadState_30_33*)curThread)->frame;
                        } else if (PyThreadState_34_36::IsFor(version)) {
                            frame = ((PyThreadState_34_36*)curThread)->frame;
                        } else if (PyThreadState_37::IsFor(version)) {
                            frame = ((PyThreadState_37*)curThread)->frame;
                        }else{
                            if(showDebugInfo){
                                std::cout << "Python version not handled! " << version << std::endl << std::flush;
                            }
                            retVal = 65;
                            break;
                        }

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
                        }

                        if(showDebugInfo){
                            std::cout << "setting trace for thread: " << threadId << std::endl << std::flush;
                        }

                        DecRef(call(settrace.ToPython(), traceFunc.ToPython(), NULL), isDebug);

                        if (errOccured) {
                            pyErrRestore(type, value, traceback);
                        }

                        // update all of the frames so they have our trace func
                        auto curFrame = (PyFrameObject*)GetPyObjectPointerNoDebugInfo(isDebug, frame);
                        while (curFrame != nullptr) {
                            // Special case for CFrame objects
                            // Stackless CFrame does not have a trace function
                            // This will just prevent a crash on attach.
                            if (((PyObject*)curFrame)->ob_type != PyCFrame_Type) {
                                DecRef(curFrame->f_trace, isDebug);
                                IncRef(*traceFunc);
                                curFrame->f_trace = traceFunc.ToPython();
                            }
                            curFrame = (PyFrameObject*)GetPyObjectPointerNoDebugInfo(isDebug, curFrame->f_back);
                        }

                        delThreadTls(threadStateIndex);
                        setThreadTls(threadStateIndex, prevThread);
                        threadSwap(prevThread);
                    }
                    break;
                }
            } while (foundThread);
        }


        
        return retVal;

	}



    /**
     * Return codes:
     *
     * -2 = could not allocate memory
     * -3 = could not allocate memory to enumerate processes
     *
     * 0 = all OK.
     * 1 = Py_IsInitialized not found
     * 2 = Py_IsInitialized returned false
     * 3 = Missing Python API
     * 4 = Interpreter not initialized
     * 5 = Python version unknown
     * 6 = Connect timeout
     *
     * result[0] should have the same result from the return function
     * result[0] is also used to set the startup info (on whether to show debug info
     * and if the debugger tracing should be set).
     **/
    DECLDIR int AttachAndRunPythonCode(const char *command, int *result )
    {
        
        int SHOW_DEBUG_INFO = 1;
        int CONNECT_DEBUGGER = 2;
        
        bool showDebugInfo = (result[0] & SHOW_DEBUG_INFO) != 0;
            
        if(showDebugInfo){
            std::cout << "AttachAndRunPythonCode started (showing debug info). " << std::endl << std::flush;
        }
        
        bool connectDebuggerTracing = (result[0] & CONNECT_DEBUGGER) != 0;
        if(showDebugInfo){
            std::cout << "connectDebuggerTracing: " << connectDebuggerTracing << std::endl << std::flush;
        }
        
        HANDLE hProcess = GetCurrentProcess();
        DWORD modSize = sizeof(HMODULE) * 1024;
        HMODULE* hMods = (HMODULE*)_malloca(modSize);
        if (hMods == nullptr) {
            result[0] = -2;
            return result[0];
        }

        DWORD modsNeeded;
        while (!EnumProcessModules(hProcess, hMods, modSize, &modsNeeded)) {
            // try again w/ more space...
            _freea(hMods);
            hMods = (HMODULE*)_malloca(modsNeeded);
            if (hMods == nullptr) {
                result[0] = -3;
                return result[0];
            }
            modSize = modsNeeded;
        }
        int attached = -1;
        {
             bool pythonFound = false;
             for (size_t i = 0; i < modsNeeded / sizeof(HMODULE); i++) {
                 bool isDebug;
                 if (IsPythonModule(hMods[i], isDebug)) {
                     pythonFound = true;
                     int temp = DoAttach(hMods[i], isDebug, command, showDebugInfo);
                     if (temp == 0) {
                         // we've successfully attached the debugger
                         attached = 0;
                         if(connectDebuggerTracing){
                            if(showDebugInfo){
                                std::cout << "SetSysTraceFunc " << std::endl << std::flush;
                            }
                            attached = SetSysTraceFunc(hMods[i], isDebug, showDebugInfo);
                         }
                         break;
                     }else{
                        if(temp > attached){
                            //I.e.: the higher the value the more significant it is.
                            attached = temp;
                         }
                     }
                 }
             }
        }

        if(showDebugInfo){
            std::cout << "Result: " << attached << std::endl << std::flush;
        }
        result[0] = attached;
        return result[0];
    }
    
    


    
    /**
     *
     *
     *
     *
     *
     **/
    DECLDIR int AttachDebuggerTracing(bool showDebugInfo)
    {
        HANDLE hProcess = GetCurrentProcess();
        DWORD modSize = sizeof(HMODULE) * 1024;
        HMODULE* hMods = (HMODULE*)_malloca(modSize);
        if (hMods == nullptr) {
            if(showDebugInfo){
                std::cout << "hmods not allocated! " << std::endl << std::flush;
            }
            return -2;
        }

        DWORD modsNeeded;
        while (!EnumProcessModules(hProcess, hMods, modSize, &modsNeeded)) {
            // try again w/ more space...
            _freea(hMods);
            hMods = (HMODULE*)_malloca(modsNeeded);
            if (hMods == nullptr) {
                if(showDebugInfo){
                    std::cout << "hmods not allocated (2)! " << std::endl << std::flush;
                }
                return -3;
            }
            modSize = modsNeeded;
        }
        int attached = -1;
        {
            bool pythonFound = false;
            for (size_t i = 0; i < modsNeeded / sizeof(HMODULE); i++) {
                bool isDebug;
                if (IsPythonModule(hMods[i], isDebug)) {
                    pythonFound = true;
                    if(showDebugInfo){
                        std::cout << "setting sys trace! " << std::endl << std::flush;
                    }
                    int temp = SetSysTraceFunc(hMods[i], isDebug, showDebugInfo);
                    if (temp == 0) {
                        // we've successfully attached the debugger
                        attached = 0;
                        break;
                    }else{
                       if(temp > attached){
                           //I.e.: the higher the value the more significant it is.
                           attached = temp;
                        }
                    }
                }
            }
        }


        return attached;
    }

}