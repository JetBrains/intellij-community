package com.intellij.ide.starter.models

import com.intellij.ide.starter.system.SystemInfo
import com.intellij.openapi.util.io.FileUtilRt
import java.nio.file.Files
import java.nio.file.Path

/**
 * Holds path to libvmtrace.so on disk.
 */
object VMTrace {
  val vmTraceFile: Path

  val isSupported: Boolean
    get() = SystemInfo.isLinux || SystemInfo.isMac || SystemInfo.isWindows

  init {
    if (isSupported) {
      val resourceName = when {
        SystemInfo.isLinux -> "/libvmtrace.so"
        SystemInfo.isWindows -> "/libvmtrace.dll"
        SystemInfo.isMac && !SystemInfo.isAarch64 -> "/libvmtrace.dylib"
        SystemInfo.isMac && SystemInfo.isAarch64 -> "/libvmtrace-aarch64.dylib"
        else -> throw UnsupportedOperationException("Unsupported platform for libvmtrace")
      }

      vmTraceFile = Files.createTempFile("libvmtrace", "." + FileUtilRt.getExtension(resourceName))

      val vmTraceBytes = VMOptions::class.java.getResourceAsStream(resourceName)!!
        .use { it.readAllBytes() }
      Files.write(vmTraceFile, vmTraceBytes)
    }
    else {
      vmTraceFile = Path.of("unsupported-platform-libvmtrace")
    }
  }
}