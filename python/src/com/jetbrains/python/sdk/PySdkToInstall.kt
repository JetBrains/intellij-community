// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk

import com.google.common.hash.HashFunction
import com.google.common.hash.Hashing
import com.google.common.io.Files
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.io.HttpRequests
import com.intellij.webcore.packaging.PackageManagementService
import com.intellij.webcore.packaging.PackagesNotificationPanel
import com.jetbrains.python.PyBundle
import com.jetbrains.python.sdk.PySdkToInstallCollector.Companion.DownloadResult
import com.jetbrains.python.sdk.PySdkToInstallCollector.Companion.InstallationResult
import com.jetbrains.python.sdk.PySdkToInstallCollector.Companion.LookupResult
import com.jetbrains.python.sdk.PySdkToInstallCollector.Companion.logSdkDownload
import com.jetbrains.python.sdk.PySdkToInstallCollector.Companion.logSdkInstallation
import org.jetbrains.annotations.CalledInAny
import org.jetbrains.annotations.CalledInAwt
import java.io.File
import java.io.IOException
import kotlin.math.absoluteValue

private val LOGGER = Logger.getInstance(PySdkToInstall::class.java)

@CalledInAny
internal fun getSdksToInstall(): List<PySdkToInstall> {
  return if (SystemInfo.isWindows) listOf(getPy37ToInstallOnWindows(), getPy38ToInstallOnWindows())
  else emptyList()
}

private fun getPy37ToInstallOnWindows(): PySdkToInstallOnWindows {
  val version = "3.7"
  val name = "Python $version"
  val hashFunction = Hashing.md5()

  return if (SystemInfo.is32Bit) {
    PySdkToInstallOnWindows(
      name,
      version,
      "https://www.python.org/ftp/python/3.7.7/python-3.7.7.exe",
      25747128,
      "e9db9cf43b4f2472d75a055380871045",
      hashFunction,
      "python-3.7.7.exe"
    )
  }
  else {
    PySdkToInstallOnWindows(
      name,
      version,
      "https://www.python.org/ftp/python/3.7.7/python-3.7.7-amd64.exe",
      26797616,
      "e0c910087459df78d827eb1554489663",
      hashFunction,
      "python-3.7.7-amd64.exe"
    )
  }
}

private fun getPy38ToInstallOnWindows(): PySdkToInstallOnWindows {
  val version = "3.8"
  val name = "Python $version"
  val hashFunction = Hashing.md5()

  return if (SystemInfo.is32Bit) {
    PySdkToInstallOnWindows(
      name,
      version,
      "https://www.python.org/ftp/python/3.8.3/python-3.8.3.exe",
      26744744,
      "452373e2c467c14220efeb10f40c231f",
      hashFunction,
      "python-3.8.3.exe"
    )
  }
  else {
    PySdkToInstallOnWindows(
      name,
      version,
      "https://www.python.org/ftp/python/3.8.3/python-3.8.3-amd64.exe",
      27805800,
      "fd2458fa0e9ead1dd9fbc2370a42853b",
      hashFunction,
      "python-3.8.3-amd64.exe"
    )
  }
}

internal abstract class PySdkToInstall internal constructor(name: String, version: String)
  : ProjectJdkImpl(name, PythonSdkType.getInstance(), null, version) {

  @CalledInAny
  abstract fun renderInList(renderer: PySdkListCellRenderer)

  @CalledInAny
  abstract fun getInstallationWarning(defaultButtonName: String): String

  @CalledInAwt
  abstract fun install(module: Module?, systemWideSdksDetector: () -> List<PyDetectedSdk>): PyDetectedSdk?
}

private class PySdkToInstallOnWindows(name: String,
                                      private val version: String,
                                      private val url: String,
                                      private val size: Long,
                                      private val hash: String,
                                      private val hashFunction: HashFunction,
                                      private val targetFileName: String) : PySdkToInstall(name, version) {

  override fun renderInList(renderer: PySdkListCellRenderer) {
    renderer.append(name)
    renderer.append(" $url", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
    renderer.icon = AllIcons.Actions.Download
  }

  override fun getInstallationWarning(defaultButtonName: String): String {
    val header = "Python executable is not found. Choose one of the following options:"

    val browseButtonName = "..." // ComponentWithBrowseButton
    val firstOption = "Click <strong>$browseButtonName</strong> to specify a path to python.exe in your file system"

    val size = StringUtil.formatFileSize(size)
    val secondOption = "Click <strong>$defaultButtonName</strong> to download and install Python from python.org ($size)"

    return "$header<ul><li>$firstOption</li><li>$secondOption</li></ul>"
  }

  override fun install(module: Module?, systemWideSdksDetector: () -> List<PyDetectedSdk>): PyDetectedSdk? {
    try {
      val project = module?.project
      return ProgressManager.getInstance().run(
        object : Task.WithResult<PyDetectedSdk?, Exception>(project, PyBundle.message("python.sdk.installing", name), true) {
          override fun compute(indicator: ProgressIndicator): PyDetectedSdk? = install(project, systemWideSdksDetector, indicator)
        }
      )
    }
    catch (e: IOException) {
      handleIOException(e)
    }
    catch (e: PyInstallationExecutionException) {
      handleExecutionException(e)
    }
    catch (e: PyInstallationException) {
      handleInstallationException(e)
    }

    return null
  }

  private fun install(project: Project?, systemWideSdksDetector: () -> List<PyDetectedSdk>, indicator: ProgressIndicator): PyDetectedSdk? {
    val targetFile = File(PathManager.getTempPath(), targetFileName)

    try {
      indicator.text = PyBundle.message("python.sdk.downloading", targetFileName)

      if (indicator.isCanceled) {
        logSdkDownload(project, version, DownloadResult.CANCELLED)
        return null
      }
      downloadInstaller(project, targetFile, indicator)

      if (indicator.isCanceled) {
        logSdkDownload(project, version, DownloadResult.CANCELLED)
        return null
      }
      checkInstallerConsistency(project, targetFile)

      logSdkDownload(project, version, DownloadResult.OK)

      indicator.text = PyBundle.message("python.sdk.running", targetFileName)
      indicator.text2 = PyBundle.message("python.sdk.installing.windows.warning")
      indicator.isIndeterminate = true

      if (indicator.isCanceled) {
        logSdkInstallation(project, version, InstallationResult.CANCELLED)
        return null
      }
      runInstaller(project, targetFile, indicator)

      logSdkInstallation(project, version, InstallationResult.OK)

      return findInstalledSdk(project, systemWideSdksDetector)
    }
    finally {
      FileUtil.delete(targetFile)
    }
  }

  private fun downloadInstaller(project: Project?, targetFile: File, indicator: ProgressIndicator) {
    LOGGER.info("Downloading $url to $targetFile")

    return try {
      HttpRequests.request(url).saveToFile(targetFile, indicator)
    }
    catch (e: IOException) {
      logSdkDownload(project, version, DownloadResult.EXCEPTION)
      throw IOException("Failed to download $url to $targetFile.", e)
    }
    catch (e: ProcessCanceledException) {
      logSdkDownload(project, version, DownloadResult.CANCELLED)
      throw e
    }
  }

  private fun checkInstallerConsistency(project: Project?, installer: File) {
    LOGGER.debug("Checking installer size")
    val sizeDiff = installer.length() - size
    if (sizeDiff != 0L) {
      logSdkDownload(project, version, DownloadResult.SIZE)
      throw IOException("Downloaded $installer has incorrect size, difference is ${sizeDiff.absoluteValue} bytes.")
    }

    LOGGER.debug("Checking installer checksum")
    val actualHashCode = Files.asByteSource(installer).hash(hashFunction).toString()
    if (!actualHashCode.equals(hash, ignoreCase = true)) {
      logSdkDownload(project, version, DownloadResult.CHECKSUM)
      throw IOException("Checksums for $installer does not match. Actual value is $actualHashCode, expected $hash.")
    }
  }

  private fun handleIOException(e: IOException) {
    LOGGER.info(e)

    e.message?.let {
      PackagesNotificationPanel.showError(
        PyBundle.message("python.sdk.failed.to.install.title", name),
        PackageManagementService.ErrorDescription(
          it,
          null,
          e.cause?.message,
          "Try to install Python from https://www.python.org manually."
        )
      )
    }
  }

  private fun runInstaller(project: Project?, installer: File, indicator: ProgressIndicator) {
    val commandLine = GeneralCommandLine(installer.absolutePath, "/quiet")
    LOGGER.info("Running ${commandLine.commandLineString}")

    val output = runInstaller(project, commandLine, indicator)

    if (output.isCancelled) logSdkInstallation(project, version, InstallationResult.CANCELLED)
    if (output.exitCode != 0) logSdkInstallation(project, version, InstallationResult.EXIT_CODE)
    if (output.isTimeout) logSdkInstallation(project, version, InstallationResult.TIMEOUT)

    if (output.exitCode != 0 || output.isTimeout) throw PyInstallationException(commandLine, output)
  }

  private fun handleInstallationException(e: PyInstallationException) {
    val processOutput = e.output
    processOutput.checkSuccess(LOGGER)

    if (processOutput.isCancelled) {
      PackagesNotificationPanel.showError(
        PyBundle.message("python.sdk.installation.has.been.cancelled.title", name),
        PackageManagementService.ErrorDescription(
          "Some Python components that have been installed might get inconsistent after cancellation.",
          e.commandLine.commandLineString,
          listOf(processOutput.stderr, processOutput.stdout).firstOrNull { it.isNotBlank() },
          "Consider installing Python from https://www.python.org manually."
        )
      )
    }
    else {
      PackagesNotificationPanel.showError(
        PyBundle.message("python.sdk.failed.to.install.title", name),
        PackageManagementService.ErrorDescription(
          if (processOutput.isTimeout) "Timed out" else "Exit code ${processOutput.exitCode}",
          e.commandLine.commandLineString,
          listOf(processOutput.stderr, processOutput.stdout).firstOrNull { it.isNotBlank() },
          "Try to install Python from https://www.python.org manually."
        )
      )
    }
  }

  private fun runInstaller(project: Project?, commandLine: GeneralCommandLine, indicator: ProgressIndicator): ProcessOutput {
    try {
      return CapturingProcessHandler(commandLine).runProcessWithProgressIndicator(indicator)
    }
    catch (e: ExecutionException) {
      logSdkInstallation(project, version, InstallationResult.EXCEPTION)
      throw PyInstallationExecutionException(commandLine, e)
    }
  }

  private fun handleExecutionException(e: PyInstallationExecutionException) {
    LOGGER.info(e)

    e.cause.message?.let {
      PackagesNotificationPanel.showError(
        PyBundle.message("python.sdk.failed.to.install.title", name),
        PackageManagementService.ErrorDescription(
          it,
          e.commandLine.commandLineString,
          null,
          "Try to install Python from https://www.python.org manually."
        )
      )
    }
  }

  private fun findInstalledSdk(project: Project?, systemWideSdksDetector: () -> List<PyDetectedSdk>): PyDetectedSdk? {
    LOGGER.debug("Resetting system-wide sdks detectors")
    resetSystemWideSdksDetectors()

    return systemWideSdksDetector()
      .also { sdks ->
        LOGGER.debug { sdks.joinToString(prefix = "Detected system-wide sdks: ") { it.homePath ?: it.name } }
      }
      .also {
        PySdkToInstallCollector.logSdkLookup(
          project,
          version,
          if (it.isEmpty()) LookupResult.NOT_FOUND else LookupResult.FOUND
        )
      }
      .singleOrNull()
  }

  private class PyInstallationException(val commandLine: GeneralCommandLine, val output: ProcessOutput) : Exception()
  private class PyInstallationExecutionException(val commandLine: GeneralCommandLine, override val cause: ExecutionException) : Exception()
}