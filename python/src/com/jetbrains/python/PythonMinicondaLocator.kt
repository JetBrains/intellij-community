// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import java.io.File

/**
 * @author Aleksey.Rostovskiy
 */
object PythonMinicondaLocator {
  private val LOG = Logger.getInstance(PythonMinicondaLocator::class.java)

  /**
   * @return miniconda3 installer path in installation folder
   */
  fun getMinicondaInstallerPath() = getMinicondaInstallerFile()?.absolutePath

  /**
   * @return exists miniconda3 installer in distribution or not
   */
  @JvmStatic
  fun isInstallerExists() = getMinicondaInstallerFile() != null

  private fun getMinicondaInstallerFolder() = File(PathManager.getHomePath(), "minicondaInstaller")

  private fun getMinicondaInstallerFile(): File? {
    val osName = when {
      SystemInfo.isWindows -> "Windows"
      SystemInfo.isLinux -> "Linux"
      SystemInfo.isMac -> "MacOSX"
      else -> {
        LOG.error("${SystemInfo.OS_NAME} isn't expected as an operation system")
        throw IllegalArgumentException("Wrong OS: ${SystemInfo.OS_NAME}")
      }
    }

    val installer = File(getMinicondaInstallerFolder(),
                                 "Miniconda3-latest-$osName-x86_64.${if (SystemInfo.isWindows) "exe" else "sh"}")

    return if (installer.exists()) installer else null
  }
}