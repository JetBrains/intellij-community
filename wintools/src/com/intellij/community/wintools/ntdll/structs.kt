package com.intellij.community.wintools.ntdll

import com.sun.jna.Memory
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.Structure.FieldOrder
import com.sun.jna.Union
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinNT


/**
 * Type of info we need. See `wdm.h`
 */
internal const val FileProcessIdsUsingFileInformation = 47


// Not enough space in info
internal const val STATUS_INFO_LENGTH_MISMATCH = -1073741820


// Whatever
@Suppress("unused")
internal class IO_STATUS_BLOCK : Structure() {
  class IO_STATUS_BLOCK_UNION : Union() {
    @JvmField
    var status = WinDef.LONG(0)

    @JvmField
    var pointer = WinDef.PVOID()
  }

  @JvmField
  var union = IO_STATUS_BLOCK_UNION()

  @JvmField
  var information = WinDef.LONG()

  override fun getFieldOrder(): List<String> = listOf("union", "information")
}

/**
 * List of processes that access file
 */
internal class FILE_PROCESS_IDS_USING_FILE_INFORMATION(maxNumberOfPids: Int) : Structure() {
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


@FieldOrder("ExitStatus", "PebBaseAddress", "AffinityMask", "BasePriority", "UniqueProcessId", "InheritedFromUniqueProcessId")
internal class PROCESS_BASIC_INFORMATION : Structure() {
  @JvmField
  var ExitStatus: Int = 0

  @JvmField
  var PebBaseAddress: Pointer? = null

  @JvmField
  var AffinityMask: Pointer? = null

  @JvmField
  var BasePriority: Int = 0

  @JvmField
  var UniqueProcessId: Long? = null

  @JvmField
  var InheritedFromUniqueProcessId: Long? = null
}

@FieldOrder("Reserved1", "BeingDebugged", "Reserved2", "Reserved3", "Ldr", "ProcessParameters")
internal class PEB : Structure() {
  @JvmField
  var Reserved1 = ByteArray(2)

  @JvmField
  var BeingDebugged: Byte = 0

  @JvmField
  var Reserved2 = ByteArray(1)

  @JvmField
  var Reserved3 = arrayOfNulls<Pointer>(2)

  @JvmField
  var Ldr: Pointer? = null

  @JvmField
  var ProcessParameters: Pointer? = null
}

@FieldOrder("Reserved1", "Reserved2", "ImagePathName", "CommandLine")
internal class RTL_USER_PROCESS_PARAMETERS : Structure() {
  @JvmField
  var Reserved1 = ByteArray(16)

  @JvmField
  var Reserved2 = arrayOfNulls<Pointer>(10)

  @JvmField
  var ImagePathName: UNICODE_STRING = UNICODE_STRING()

  @JvmField
  var CommandLine: UNICODE_STRING = UNICODE_STRING()
}

@FieldOrder("Length", "MaximumLength", "Buffer")
internal class UNICODE_STRING : Structure() {
  @JvmField
  var Length: Short = 0

  @JvmField
  var MaximumLength: Short = 0

  @JvmField
  var Buffer: Pointer? = null

  fun readFromProcess(processHandle: WinNT.HANDLE): String? {
    val byteBuffer = Memory(Length.toLong())
    val ok = Kernel32.INSTANCE.ReadProcessMemory(processHandle, Buffer, byteBuffer, Length.toInt(), null)

    if (!ok) return null

    return byteBuffer.getByteArray(0, Length.toInt()).toString(Charsets.UTF_16LE)
  }
}
