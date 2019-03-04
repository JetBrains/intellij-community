// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.flavors

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.util.concurrent.TimeUnit


/**
 * On Win10 uses `Get-AppxPackage` cmdlet to fetch installation location of package by name.
 * To be used to find location of tools installed with Windows Store
 */
fun findInstallLocationForPackage(packageName: String): VirtualFile? {
  if (!SystemInfo.isWin10OrNewer) {
    return null
  }
  val alphaNumeric = Regex("^[a-zA-Z0-9]+$")
  val splitLine = Regex("""^[$]_.InstallLocation\s*:\s*(.+)$""")
  val logger = Logger.getFactory().getLoggerInstance("findPackage")

  assert(packageName.isNotBlank() && packageName.matches(alphaNumeric)) { "Only alphanumeric packages are supported" }
  val command = "powershell.exe -Command \"Get-AppxPackage | Where-Object {\$_.Name -like '*$packageName*'} | Select-Object {\$_.InstallLocation} | Format-List\""

  val process = Runtime.getRuntime().exec(command)
  val result = process.waitFor(5, TimeUnit.SECONDS)
  if (!result) {
    logger.warn("Error ${process.exitValue()} for command $command")
    logger.warn(process.errorStream.bufferedReader().readText())
    return null
  }
  val line = process.inputStream.bufferedReader().lines().filter { it.isNotBlank() }.findFirst().orElse(null) ?: return null
  val groupValues = splitLine.find(line)?.groupValues ?: return null
  if (groupValues.size != 2) {
    logger.warn("Strange output: $line")
    return null
  }
  return LocalFileSystem.getInstance().findFileByPath(groupValues[1])
}