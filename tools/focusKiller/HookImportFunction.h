/*
Module : HookImportFunction.h
Purpose: Defines the interface for code to hook a call to any imported Win32 SDK
Created: PJN / 23-10-1999

Copyright (c) 1999 by PJ Naughter.  
All rights reserved.

*/

#ifndef __HOOKIMPORTFUNCTION_H__
#define __HOOKIMPORTFUNCTION_H__



////////////// Structures ///////////////////////////

typedef struct tag_HOOKFUNCDESC
{
  LPCSTR szFunc; // The name of the function to hook.
  PROC pProc;    // The procedure to blast in.
} HOOKFUNCDESC , * LPHOOKFUNCDESC;



////////////// Functions ////////////////////////////

PIMAGE_IMPORT_DESCRIPTOR GetNamedImportDescriptor(HMODULE hModule, LPCSTR szImportMod);
BOOL HookImportFunctionsByName(HMODULE hModule, LPCSTR szImportMod, UINT uiCount, 
                               LPHOOKFUNCDESC paHookArray, PROC* paOrigFuncs, UINT* puiHooked);


#endif //__HOOKIMPORTEDFUNCTION_H__