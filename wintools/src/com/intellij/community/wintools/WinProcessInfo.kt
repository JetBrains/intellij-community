package com.intellij.community.wintools

import com.intellij.community.wintools.ntdll.NtDllExt
import com.intellij.community.wintools.ntdll.PEB
import com.intellij.community.wintools.ntdll.PROCESS_BASIC_INFORMATION
import com.intellij.community.wintools.ntdll.RTL_USER_PROCESS_PARAMETERS
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.SystemInfoRt
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinNT.PROCESS_QUERY_INFORMATION
import com.sun.jna.platform.win32.WinNT.PROCESS_VM_READ
import java.nio.file.Path
import kotlin.io.path.Path

/**
 * See [get]
 */
class WinProcessInfo private constructor(
  val pid: Long,
  val commandLine: @NlsSafe String,
  val executable: Path,
  val parentId: Long?,
) {
  companion object {
    /**
     * @return process information by [pid]
     */
    fun get(pid: Long): Result<WinProcessInfo> {
      assert(SystemInfoRt.isWindows) { "Do not call on **nix" }

      val hProcess = Kernel32.INSTANCE.OpenProcess(PROCESS_QUERY_INFORMATION or PROCESS_VM_READ, false, pid.toInt())
      if (hProcess == null) {
        return winFailure("Failed to open a process with the PID $pid")
      }

      try {
        val info = PROCESS_BASIC_INFORMATION()
        if (NtDllExt.INSTANCE.NtQueryInformationProcess(hProcess, 0, info, info.size(), null) != Pointer.NULL) {
          return winFailure("Failed to query the process information from the PID $pid")
        }

        val peb = PEB()
        if (!Kernel32.INSTANCE.ReadProcessMemory(hProcess, info.PebBaseAddress, peb.pointer, peb.size(), null)) {
          return winFailure("Failed to read the PEB structure from the PID $pid")
        }
        peb.read()

        val rtl = RTL_USER_PROCESS_PARAMETERS()
        if (!Kernel32.INSTANCE.ReadProcessMemory(hProcess, peb.ProcessParameters, rtl.pointer, rtl.size(), null)) {
          return winFailure("Failed to read RTL_USER_PROCESS_PARAMETERS structure from the PID $pid")
        }
        rtl.read()

        val commandLine = rtl.CommandLine.readFromProcess(hProcess)
        if (commandLine == null) {
          return winFailure("Failed to read the command line from the PID $pid")
        }

        val executable = rtl.ImagePathName.readFromProcess(hProcess)?.let { Path(it) }
        if (executable == null) {
          return winFailure("Failed to read the image path name from the PID $pid")
        }

        return Result.success(WinProcessInfo(pid, commandLine, executable, info.InheritedFromUniqueProcessId))
      }
      finally {
        Kernel32.INSTANCE.CloseHandle(hProcess)
      }
    }
  }

  override fun toString(): String {
    return "WinProcessInfo(pid=$pid, commandLine='$commandLine', executable=$executable, parentId=$parentId)"
  }
}