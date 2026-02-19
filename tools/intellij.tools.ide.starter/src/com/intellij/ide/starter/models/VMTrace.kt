package com.intellij.ide.starter.models

import com.intellij.openapi.util.SystemInfo
import java.nio.file.Files
import java.nio.file.Path

/**
 * Holds path to libvmtrace.so on disk.
 */
object VMTrace {
  val vmTraceFile: Path

  val isSupported: Boolean
    get() = (SystemInfo.isLinux && !SystemInfo.isAarch64) || SystemInfo.isMac || SystemInfo.isWindows

  init {
    if (isSupported) {
      val resourceName = when {
        SystemInfo.isLinux && !SystemInfo.isAarch64 -> "/libvmtrace.so"
        SystemInfo.isWindows -> "/libvmtrace.dll"
        SystemInfo.isMac && !SystemInfo.isAarch64 -> "/libvmtrace.dylib"
        SystemInfo.isMac && SystemInfo.isAarch64 -> "/libvmtrace-aarch64.dylib"
        else -> throw UnsupportedOperationException("Unsupported platform for libvmtrace")
      }

      vmTraceFile = Files.createTempFile("libvmtrace", "." + getExtension(resourceName))

      val vmTraceBytes = VMOptions::class.java.getResourceAsStream(resourceName)!!
        .use { it.readAllBytes() }
      Files.write(vmTraceFile, vmTraceBytes)
    }
    else {
      vmTraceFile = Path.of("unsupported-platform-libvmtrace")
    }
  }

  private fun getExtension(fileName: String): String {
    return fileName.substringAfterLast(".", "")
  }
}