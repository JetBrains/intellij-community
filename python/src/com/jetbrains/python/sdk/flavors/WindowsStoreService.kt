// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.flavors

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileSystem
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Powershell may not exist under PATH, so we first look for it in well-known location
 */
private fun findPowerShell(): VirtualFile? {
  val fs = LocalFileSystem.getInstance()
  val winDir = System.getenv("WINDIR") ?: return null
  val winDirFile = fs.findFileByPath(winDir) ?: return null
  return winDirFile.findFileByRelativePath("/System32/WindowsPowerShell/v1.0/powershell.exe")
}

/**
 * On Win10 uses `Get-AppxPackage` cmdlet to fetch installation location of package by name.
 * To be used to find location of tools installed with Windows Store
 */
fun findInstallLocationForPackage(packageName: String, fs:VirtualFileSystem): VirtualFile? {
  if (!SystemInfo.isWin10OrNewer) {
    return null
  }
  val alphaNumeric = Regex("^[a-zA-Z0-9]+$")
  val splitLine = Regex("""^[$]_.InstallLocation\s*:\s*(.+)$""")
  val logger = Logger.getFactory().getLoggerInstance("findPackage")

  assert(packageName.isNotBlank() && packageName.matches(alphaNumeric)) { "Only alphanumeric packages are supported" }
  val powershell = findPowerShell()?.path ?: "powershell.exe"
  val command = "\"Get-AppxPackage | Where-Object {\$_.Name -like '*$packageName*'} | Select-Object {\$_.InstallLocation} | Format-List\""
  val process: Process
  try {
    process = Runtime.getRuntime().exec(arrayOf(powershell, "-Command", command))
  }
  catch (e: IOException) {
    logger.warn(e)
    return null
  }
  val result = process.waitFor(5, TimeUnit.SECONDS)
  if (!result) {
    reportError(command, "Process still runs after timeout", process, logger)
    return null
  }
  val exitValue = process.exitValue()
  if (exitValue != 0) {
    reportError(command, "Process exited $exitValue", process, logger)
    return null
  }
  val line = process.inputStream.bufferedReader().lines().filter { it.isNotBlank() }.findFirst().orElse(null) ?: return null
  val groupValues = splitLine.find(line)?.groupValues ?: return null
  if (groupValues.size != 2) {
    logger.warn("Strange output: $line")
    return null
  }
  return fs.refreshAndFindFileByPath(groupValues[1])
}

private fun reportError(command: String, error: String, process: Process, logger: Logger) {
  logger.warn(error)
  logger.warn(command)
  logger.warn(process.errorStream.bufferedReader().readText())
  logger.warn(process.inputStream.bufferedReader().readText())
}
