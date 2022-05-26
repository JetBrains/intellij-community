package com.intellij.ide.starter.system

import java.util.*

object SystemInfo {
  val OS_NAME = System.getProperty("os.name")
  val OS_VERSION = System.getProperty("os.version").lowercase()
  val OS_ARCH = System.getProperty("os.arch")
  val JAVA_VERSION = System.getProperty("java.version")
  val JAVA_RUNTIME_VERSION = getRtVersion(JAVA_VERSION)
  val JAVA_VENDOR = System.getProperty("java.vm.vendor", "Unknown")

  private val _OS_NAME = OS_NAME.lowercase(Locale.ENGLISH);
  val isWindows = _OS_NAME.startsWith("windows");
  val isMac = _OS_NAME.startsWith("mac");
  val isLinux = _OS_NAME.startsWith("linux");
  val isFreeBSD = _OS_NAME.startsWith("freebsd");
  val isSolaris = _OS_NAME.startsWith("sunos");
  val isUnix = !isWindows;
  val isXWindow = isUnix && !isMac;

  private fun getRtVersion(fallback: String): String? {
    val rtVersion = System.getProperty("java.runtime.version")
    return if (Character.isDigit(rtVersion[0])) rtVersion else fallback
  }

  fun getOsNameAndVersion(): String = (if (isMac) "macOS" else OS_NAME) + ' ' + OS_VERSION

  fun getOsType(): OsType = when {
    isMac -> OsType.MacOS
    isWindows -> OsType.Windows
    isLinux -> OsType.Linux
    else -> OsType.Other
  }
}