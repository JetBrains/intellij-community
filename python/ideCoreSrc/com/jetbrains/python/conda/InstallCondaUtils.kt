// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.conda

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.SystemProperties
import java.io.File

/**
 * @author Aleksey.Rostovskiy
 */
object InstallCondaUtils {
  private val LOG = Logger.getInstance(InstallCondaUtils::class.java)

  /**
   * @return `miniconda3` folder at home directory
   */
  @JvmStatic
  val defaultDirectoryFile: File by lazy {
    File(SystemProperties.getUserHome(), "miniconda3")
  }

  /**
   * [checkPath] should be called before running this process
   * @param path installation path
   * @param indicationFunction function to do when lines from stdout/stderr will be coming
   * @return CapturingProcessHandler for installation
   */
  @JvmStatic
  fun installationHandler(path: String,
                          indicationFunction: (String) -> Unit): CapturingProcessHandler {
    val handler = CapturingProcessHandler(getCommandLine(path))

    handler.addProcessListener(object : ProcessAdapter() {
      override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
        if (outputType === ProcessOutputTypes.STDOUT || outputType === ProcessOutputTypes.STDERR) {
          for (line in StringUtil.splitByLines(event.text)) {
            indicationFunction(line)
          }
        }
      }
    })

    return handler
  }

  /**
   * Supported couple cases for Unix-based OS:
   * * "folder" -> "$HOME/folder"
   * * "~/folder" -> $HOME/folder"
   * @return transformed folder
   */
  @JvmStatic
  fun beatifyPath(path: String): String {
    if (!SystemInfo.isWindows && !path.startsWith("/") && path.isNotBlank()) {
      val home = SystemProperties.getUserHome()
      return if (path.startsWith("~")) {
        home + path.substring(1)
      }
      else {
        File(home, path).absolutePath
      }
    }
    return path
  }

  private fun File.checkCondaWrite(): Boolean {
    return if (exists()) canWrite() else parentFile.checkCondaWrite()
  }

  /**
   * @return GeneralCommandLine with arguments for current OS
   * @throws IllegalArgumentException if OS is unknown
   * */
  private fun getCommandLine(path: String): GeneralCommandLine {
    val installerPath = PythonMinicondaLocator.getMinicondaInstallerPath()!!

    return when {
      SystemInfo.isWindows -> GeneralCommandLine(installerPath,
                                                 "/InstallationType=JustMe",
                                                 "/AddToPath=0",
                                                 "/RegisterPython=0",
                                                 "/S",
                                                 "/D=$path")
      SystemInfo.isLinux || SystemInfo.isMac -> GeneralCommandLine("bash",
                                                                   installerPath,
                                                                   "-b",
                                                                   "-p", path)
      else -> {
        LOG.error("${SystemInfo.OS_NAME} isn't expected as a operation system")
        throw IllegalArgumentException("OS ${SystemInfo.OS_NAME} isn't supported for Miniconda installation")
      }
    }
  }

  /**
   * @param path checks on
   * * being blank
   * * already exists
   * * writable permissions
   * * already existed
   * @return String with error message or null if none
   */
  @JvmStatic
  fun checkPath(path: String): String? {
    if (path.isBlank())
      return ActionsBundle.message("action.SetupMiniconda.installDirectoryMissing")

    val pathFile = File(path)
    if (pathFile.exists())
      return ActionsBundle.message("action.SetupMiniconda.installDirectoryIsNotEmpty", path)

    if (!pathFile.checkCondaWrite())
      return ActionsBundle.message("action.SetupMiniconda.canNotWriteToInstallationDirectory", path)

    if (!PythonMinicondaLocator.isInstallerExists())
      return ActionsBundle.message("action.SetupMiniconda.installerMissing")

    return null
  }
}