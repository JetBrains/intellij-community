#include <windows.h>

// NT 4 doesn't have FlashWindowEx.
typedef BOOL (WINAPI *t_FlashWindowEx)(FLASHWINFO*);
t_FlashWindowEx p_FlashWindowEx;
#define FlashWindowEx p_FlashWindowEx

#ifdef USE_DETOURS
#include "detours.h" // see http://research.microsoft.com/sn/detours/
extern "C" {
    DETOUR_TRAMPOLINE(BOOL WINAPI Real_SetForegroundWindow(HWND hWnd), SetForegroundWindow);    
}
#else
// IAT patching hook method. See http://www.naughter.com/hookimportfunction.html
// compile with cl /LD focuskiller.cpp HookImportFunction.cpp user32.lib
#include "HookImportFunction.h"

typedef BOOL (WINAPI *t_SetForegroundWindow)(HWND);
t_SetForegroundWindow Real_SetForegroundWindow;
#endif

DWORD mypid;
   
BOOL WINAPI Mine_SetForegroundWindow(HWND hWnd)
{    
    DWORD pid;    
    HWND fg = GetForegroundWindow();
    HWND owner = GetWindow(hWnd, GW_OWNER);
    GetWindowThreadProcessId(fg, &pid);
    
#ifdef _DEBUG
    char buf[500];
    wsprintf(buf, "SetForegroundWindow(%x): owner = %x, %d <-> %d", hWnd, owner, pid, mypid);
    OutputDebugString(buf);
#endif

    // Disallow if
    //  a) another process' window is in the foreground
    //  b) the window to be put in front is a top-level window (should avoid putting one IDEA project in front of another one)
    if (mypid != pid || owner == NULL) {
        if (FlashWindowEx != NULL) {
            FLASHWINFO fw;
            fw.cbSize = sizeof(fw);
            fw.hwnd = hWnd;
            fw.uCount = 5;
            fw.dwTimeout = 0;
                    
            fw.dwFlags = FLASHW_TRAY | FLASHW_TIMERNOFG;
            FlashWindowEx(&fw);
        } else {
            FlashWindow(hWnd, TRUE);
        }
        return TRUE; // fake success
    }
    
    return Real_SetForegroundWindow(hWnd);
}

void HookFunctions(HMODULE hModule)
{
#ifdef USE_DETOURS
#ifdef _DEBUG    
    OutputDebugString("Using Detours hook...");
#endif

    DetourFunctionWithTrampoline((PBYTE)Real_SetForegroundWindow,
                                 (PBYTE)Mine_SetForegroundWindow);
#else
#ifdef _DEBUG    
    OutputDebugString("Using IAT patching hook...");
#endif

    HOOKFUNCDESC hook;
    hook.szFunc = "SetForegroundWindow";
    hook.pProc = (PROC)Mine_SetForegroundWindow;

    // hooking LoadLibrary and waiting until awt.dll is being loaded by java would be more correct but this works too
    HMODULE awtModule = LoadLibrary("awt.dll");
    
    BOOL b = HookImportFunctionsByName(awtModule, "user32.dll", 1, &hook, (PROC *)&Real_SetForegroundWindow, NULL);
    if (!b) {
        char buf[200];
        wsprintf(buf, "Hooking SetForegroundWindow failed [0x%x]", GetLastError());
        OutputDebugString(buf);
    }
#endif

#ifdef _DEBUG    
    OutputDebugString("Functions hooked");
#endif
}

BOOL WINAPI DllMain(HINSTANCE hinstDLL, DWORD fdwReason, LPVOID lpReserved)
{    
    if (fdwReason == DLL_PROCESS_ATTACH) {
#ifdef _DEBUG    
        char buf[200];
        wsprintf(buf, "DLL Attached");
        OutputDebugString(buf);
#endif
        
        mypid = GetCurrentProcessId();
        p_FlashWindowEx = (t_FlashWindowEx)GetProcAddress(GetModuleHandle("user32.dll"), "FlashWindowEx");

        DisableThreadLibraryCalls((HMODULE)hinstDLL);

        HookFunctions((HMODULE)hinstDLL);        
    }
    
    return TRUE;
}

