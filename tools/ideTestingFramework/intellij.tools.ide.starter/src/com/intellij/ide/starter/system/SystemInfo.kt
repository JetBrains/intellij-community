package com.intellij.ide.starter.system

import java.util.*

object SystemInfo {
  val OS_NAME: String = System.getProperty("os.name")
  val OS_VERSION = System.getProperty("os.version").lowercase()
  val OS_ARCH: String = System.getProperty("os.arch")
  val JAVA_VERSION: String = System.getProperty("java.version")
  val JAVA_RUNTIME_VERSION = getRtVersion(JAVA_VERSION)
  val JAVA_VENDOR: String = System.getProperty("java.vm.vendor", "Unknown")

  private val OS_NAME_LOWERCASED = OS_NAME.lowercase(Locale.ENGLISH)
  val isWindows = OS_NAME_LOWERCASED.startsWith("windows")
  val isMac = OS_NAME_LOWERCASED.startsWith("mac")
  val isLinux = OS_NAME_LOWERCASED.startsWith("linux")
  val isFreeBSD = OS_NAME_LOWERCASED.startsWith("freebsd")
  val isSolaris = OS_NAME_LOWERCASED.startsWith("sunos")
  val isUnix = !isWindows
  val isXWindow = isUnix && !isMac

  val isAarch64: Boolean = OS_ARCH == "aarch64"

  private fun getRtVersion(fallback: String): String? {
    val rtVersion = System.getProperty("java.runtime.version")
    return if (Character.isDigit(rtVersion[0])) rtVersion else fallback
  }

  fun getOsName(): String = if (isMac) "macOS" else OS_NAME

  fun getOsNameAndVersion(): String = getOsName() + ' ' + OS_VERSION

  fun getOsType(): OsType = when {
    isMac -> OsType.MacOS
    isWindows -> OsType.Windows
    isLinux -> OsType.Linux
    else -> OsType.Other
  }
}