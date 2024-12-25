package com.intellij.python.junit5Tests.framework.winLockedFile.impl


import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.SystemInfoRt
import com.jetbrains.python.Result
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Structure
import com.sun.jna.Union
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinBase.INVALID_HANDLE_VALUE
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinNT.*
import com.sun.jna.win32.W32APIOptions
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.pathString
import kotlin.jvm.optionals.getOrNull


internal fun getProcessLockedFileImpl(file: Path): Result<List<ProcessHandle>, @NlsSafe String> {
  assert(SystemInfoRt.isWindows) { "Do not call on **nix" }
  val fileName = file.pathString

  val kernel32 = Kernel32.INSTANCE
  val fileHandle = kernel32.CreateFile(
    fileName,
    FILE_READ_ATTRIBUTES,
    FILE_SHARE_READ,
    null,
    OPEN_EXISTING,
    if (file.isDirectory()) FILE_FLAG_BACKUP_SEMANTICS else 0, // can't open dir without this flag
    null
  )

  if (fileHandle == INVALID_HANDLE_VALUE) {
    return Result.failure("Failed to open $fileName : error ${kernel32.GetLastError()}")
  }

  var numberOfProcesses = 1

  var fileInfo: FileProcessIdsUsingInformation
  var result: Int
  try {
    val ntdll = MyNtDll.instance
    do {
      fileInfo = FileProcessIdsUsingInformation(numberOfProcesses)
      result = ntdll.ZwQueryInformationFile(
        fileHandle,
        IoStatusBlock(),
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
///////// impl part ////////////

/**
 * Type of info we need. See `wdm.h`
 */
private const val FileProcessIdsUsingFileInformation = 47

// Not enough space in info
private const val STATUS_INFO_LENGTH_MISMATCH = -1073741820

// Whatever
@Suppress("unused")
internal class IoStatusBlock : Structure() {
  class IoStatusBlockUnion : Union() {
    @JvmField
    var status = WinDef.LONG(0)

    @JvmField
    var pointer = WinDef.PVOID()
  }

  @JvmField
  var union = IoStatusBlockUnion()

  @JvmField
  var information = WinDef.LONG()

  override fun getFieldOrder(): List<String> = listOf("union", "information")
}

/**
 * List of processes that access file
 */
internal class FileProcessIdsUsingInformation(maxNumberOfPids: Int) : Structure() {
  @Suppress("unused")
  @JvmField
  var numberOfProcess = WinDef.ULONG()

  // PIDs
  @JvmField
  var processList = Array<WinDef.ULONGLONG>(maxNumberOfPids) {
    WinDef.ULONGLONG(0)
  }


  override fun getFieldOrder(): List<String> = listOf("numberOfProcess", "processList")
}

@Suppress("TestFunctionName")
private interface MyNtDll : Library {
  companion object {
    val instance: MyNtDll = Native.load<MyNtDll>("NtDll", MyNtDll::class.java, W32APIOptions.DEFAULT_OPTIONS)
  }

  fun ZwQueryInformationFile(
    file: HANDLE,
    statusBlock: IoStatusBlock,
    fileInfo: FileProcessIdsUsingInformation?,
    fileInfoSize: WinDef.ULONG,
    fileInformationClass: Int,
  ): Int
}
