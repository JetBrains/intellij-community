package com.intellij.community.wintools


import com.intellij.community.wintools.ntdll.*
import com.intellij.openapi.util.SystemInfoRt
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinBase.INVALID_HANDLE_VALUE
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinNT.*
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.pathString
import kotlin.jvm.optionals.getOrNull

/**
 * @return list of processes that lock [path] (directory or file) preventing it from deletion
 */
fun getProcessLockedPath(path: Path): Result<List<ProcessHandle>> {
  assert(SystemInfoRt.isWindows) { "Do not call on **nix" }
  val fileName = path.pathString

  val kernel32 = Kernel32.INSTANCE
  val fileHandle = kernel32.CreateFile(
    fileName,
    FILE_READ_ATTRIBUTES,
    FILE_SHARE_READ,
    null,
    OPEN_EXISTING,
    if (path.isDirectory()) FILE_FLAG_BACKUP_SEMANTICS else 0, // can't open dir without this flag
    null
  )

  if (fileHandle == INVALID_HANDLE_VALUE) {
    return winFailure("Failed to open $fileName")
  }

  var numberOfProcesses = 1

  var fileInfo: FILE_PROCESS_IDS_USING_FILE_INFORMATION
  var result: Int
  try {
    do {
      fileInfo = FILE_PROCESS_IDS_USING_FILE_INFORMATION(numberOfProcesses)
      result = NtDllExt.INSTANCE.NtQueryInformationFile(
        fileHandle,
        IO_STATUS_BLOCK(),
        fileInfo,
        WinDef.ULONG(fileInfo.size().toLong()),
        FileProcessIdsUsingFileInformation
      )
      numberOfProcesses = numberOfProcesses.shl(1)
      //
    }
    while (result == STATUS_INFO_LENGTH_MISMATCH)
    assert(result == 0) { "ZwQueryInformationFile: $result" }
    val pids = fileInfo.processList.map { it.toLong() }
    val processes = pids.mapNotNull { ProcessHandle.of(it).getOrNull() }
    return Result.success(processes)
  }
  finally {
    kernel32.CloseHandle(fileHandle)
  }
}
