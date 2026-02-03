package com.intellij.community.wintools.ntdll

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.NtDll
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinNT.HANDLE
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.W32APIOptions

@Suppress("LocalVariableName", "FunctionName")
internal interface NtDllExt : NtDll {
  companion object {
    val INSTANCE: NtDllExt = Native.load("NtDll", NtDllExt::class.java, W32APIOptions.DEFAULT_OPTIONS)
  }

  fun NtQueryInformationFile(
    file: HANDLE,
    statusBlock: IO_STATUS_BLOCK,
    fileInfo: FILE_PROCESS_IDS_USING_FILE_INFORMATION?,
    fileInfoSize: WinDef.ULONG,
    fileInformationClass: Int,
  ): Int

  fun NtQueryInformationProcess(
    ProcessHandle: HANDLE,
    ProcessInformationClass: Int,
    ProcessInformation: PROCESS_BASIC_INFORMATION,
    ProcessInformationLength: Int,
    ReturnLength: IntByReference?,
  ): Pointer
}

