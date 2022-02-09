// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk

import com.google.common.hash.HashFunction
import com.google.common.hash.Hashing
import com.google.common.io.Files
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.OSProcessUtil
import com.intellij.execution.process.ProcessOutput
import com.intellij.execution.util.ExecUtil.execAndGetOutput
import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk.*
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.io.HttpRequests
import com.intellij.webcore.packaging.PackageManagementService
import com.intellij.webcore.packaging.PackagesNotificationPanel
import com.jetbrains.python.PyBundle
import com.jetbrains.python.sdk.PySdkToInstallCollector.Companion.DownloadResult
import com.jetbrains.python.sdk.PySdkToInstallCollector.Companion.InstallationResult
import com.jetbrains.python.sdk.PySdkToInstallCollector.Companion.LookupResult
import com.jetbrains.python.sdk.PySdkToInstallCollector.Companion.logSdkDownloadOnWindows
import com.jetbrains.python.sdk.PySdkToInstallCollector.Companion.logSdkInstallationOnMac
import com.jetbrains.python.sdk.PySdkToInstallCollector.Companion.logSdkInstallationOnWindows
import com.jetbrains.python.sdk.PySdkToInstallCollector.Companion.logSdkLookupOnMac
import com.jetbrains.python.sdk.PySdkToInstallCollector.Companion.logSdkLookupOnWindows
import com.jetbrains.python.sdk.flavors.MacPythonSdkFlavor
import org.jetbrains.annotations.CalledInAny
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.absoluteValue

private val LOGGER = Logger.getInstance(PySdkToInstall::class.java)

@CalledInAny
internal fun getSdksToInstall(): List<PySdkToInstall> {
  return if (SystemInfo.isWindows) listOf(getPy39ToInstallOnWindows(), getPy310ToInstallOnWindows())
  else if (SystemInfo.isMac) listOf(PySdkToInstallViaXCodeSelect())
  else emptyList()
}

@RequiresEdt
fun installSdkIfNeeded(sdk: Sdk?, module: Module?, existingSdks: List<Sdk>): Sdk? {
  return sdk.let { if (it is PySdkToInstall) it.install(module) { detectSystemWideSdks(module, existingSdks) } else it }
}

@RequiresEdt
fun installSdkIfNeeded(sdk: Sdk?, module: Module?, existingSdks: List<Sdk>, context: UserDataHolder): Sdk? {
  return sdk.let { if (it is PySdkToInstall) it.install(module) { detectSystemWideSdks(module, existingSdks, context) } else it }
}

private fun getPy39ToInstallOnWindows(): PySdkToInstallOnWindows {
  val version = "3.9"
  val name = "Python $version"
  @Suppress("DEPRECATION") val hashFunction = Hashing.md5()

  return PySdkToInstallOnWindows(
    name,
    version,
    "https://www.python.org/ftp/python/3.9.7/python-3.9.7-amd64.exe",
    28895456,
    "cc3eabc1f9d6c703d1d2a4e7c041bc1d",
    hashFunction,
    "python-3.9.7-amd64.exe"
  )
}

private fun getPy310ToInstallOnWindows(): PySdkToInstallOnWindows {
  val version = "3.10"
  val name = "Python $version"
  @Suppress("DEPRECATION") val hashFunction = Hashing.md5()

  return PySdkToInstallOnWindows(
    name,
    version,
    "https://www.python.org/ftp/python/3.10.0/python-3.10.0-amd64.exe",
    28315928,
    "c3917c08a7fe85db7203da6dcaa99a70",
    hashFunction,
    "python-3.10.0-amd64.exe"
  )
}

abstract class PySdkToInstall internal constructor(name: String, version: String)
  : ProjectJdkImpl(name, PythonSdkType.getInstance(), null, version) {

  @CalledInAny
  abstract fun renderInList(renderer: PySdkListCellRenderer)

  @CalledInAny
  @NlsContexts.DialogMessage
  abstract fun getInstallationWarning(@NlsContexts.Button defaultButtonName: String): String

  @RequiresEdt
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
    renderer.append(" $url", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)  // NON-NLS
    renderer.icon = AllIcons.Actions.Download
  }

  @NlsContexts.DialogMessage
  override fun getInstallationWarning(@NlsContexts.Button defaultButtonName: String): String {
    val fileSize = StringUtil.formatFileSize(size)
    return HtmlBuilder()
      .append(PyBundle.message("python.sdk.executable.not.found.header"))
      .append(tag("ul").children(
        tag("li").children(raw(PyBundle.message("python.sdk.executable.not.found.option.specify.path", text("...").bold(), "python.exe"))),
        tag("li").children(raw(PyBundle.message("python.sdk.executable.not.found.option.download.and.install",
                                                text(defaultButtonName).bold(), fileSize)))
      )).toString()
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
        logSdkDownloadOnWindows(project, version, DownloadResult.CANCELLED)
        return null
      }
      downloadInstaller(project, targetFile, indicator)

      if (indicator.isCanceled) {
        logSdkDownloadOnWindows(project, version, DownloadResult.CANCELLED)
        return null
      }
      checkInstallerConsistency(project, targetFile)

      logSdkDownloadOnWindows(project, version, DownloadResult.OK)

      indicator.text = PyBundle.message("python.sdk.running", targetFileName)
      indicator.text2 = PyBundle.message("python.sdk.installing.windows.warning")
      indicator.isIndeterminate = true

      if (indicator.isCanceled) {
        logSdkInstallationOnWindows(project, version, InstallationResult.CANCELLED)
        return null
      }
      runInstaller(project, targetFile, indicator)

      logSdkInstallationOnWindows(project, version, InstallationResult.OK)

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
      logSdkDownloadOnWindows(project, version, DownloadResult.EXCEPTION)
      throw IOException("Failed to download $url to $targetFile.", e)
    }
    catch (e: ProcessCanceledException) {
      logSdkDownloadOnWindows(project, version, DownloadResult.CANCELLED)
      throw e
    }
  }

  private fun checkInstallerConsistency(project: Project?, installer: File) {
    LOGGER.debug("Checking installer size")
    val sizeDiff = installer.length() - size
    if (sizeDiff != 0L) {
      logSdkDownloadOnWindows(project, version, DownloadResult.SIZE)
      throw IOException("Downloaded $installer has incorrect size, difference is ${sizeDiff.absoluteValue} bytes.")
    }

    LOGGER.debug("Checking installer checksum")
    val actualHashCode = Files.asByteSource(installer).hash(hashFunction).toString()
    if (!actualHashCode.equals(hash, ignoreCase = true)) {
      logSdkDownloadOnWindows(project, version, DownloadResult.CHECKSUM)
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
          PyBundle.message("python.sdk.try.to.install.python.manually")
        )
      )
    }
  }

  private fun runInstaller(project: Project?, installer: File, indicator: ProgressIndicator) {
    val commandLine = GeneralCommandLine(installer.absolutePath, "/quiet")
    LOGGER.info("Running ${commandLine.commandLineString}")

    val output = runInstaller(project, commandLine, indicator)

    if (output.isCancelled) logSdkInstallationOnWindows(project, version, InstallationResult.CANCELLED)
    if (output.exitCode != 0) logSdkInstallationOnWindows(project, version, InstallationResult.EXIT_CODE)
    if (output.isTimeout) logSdkInstallationOnWindows(project, version, InstallationResult.TIMEOUT)

    if (output.exitCode != 0 || output.isTimeout) throw PyInstallationException(commandLine, output)
  }

  private fun handleInstallationException(e: PyInstallationException) {
    val processOutput = e.output
    processOutput.checkSuccess(LOGGER)

    if (processOutput.isCancelled) {
      PackagesNotificationPanel.showError(
        PyBundle.message("python.sdk.installation.has.been.cancelled.title", name),
        PackageManagementService.ErrorDescription(
          PyBundle.message("python.sdk.some.installed.python.components.might.get.inconsistent.after.cancellation"),
          e.commandLine.commandLineString,
          listOf(processOutput.stderr, processOutput.stdout).firstOrNull { it.isNotBlank() },
          PyBundle.message("python.sdk.consider.installing.python.manually")
        )
      )
    }
    else {
      PackagesNotificationPanel.showError(
        PyBundle.message("python.sdk.failed.to.install.title", name),
        PackageManagementService.ErrorDescription(
          if (processOutput.isTimeout) PyBundle.message("python.sdk.failed.to.install.timed.out")
          else PyBundle.message("python.sdk.failed.to.install.exit.code", processOutput.exitCode),
          e.commandLine.commandLineString,
          listOf(processOutput.stderr, processOutput.stdout).firstOrNull { it.isNotBlank() },
          PyBundle.message("python.sdk.try.to.install.python.manually")
        )
      )
    }
  }

  private fun runInstaller(project: Project?, commandLine: GeneralCommandLine, indicator: ProgressIndicator): ProcessOutput {
    try {
      return CapturingProcessHandler(commandLine).runProcessWithProgressIndicator(indicator)
    }
    catch (e: ExecutionException) {
      logSdkInstallationOnWindows(project, version, InstallationResult.EXCEPTION)
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
          PyBundle.message("python.sdk.try.to.install.python.manually")
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
        logSdkLookupOnWindows(
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

private class PySdkToInstallViaXCodeSelect : PySdkToInstall("Python", "") {

  override fun renderInList(renderer: PySdkListCellRenderer) {
    renderer.append(name)
    renderer.append(" ")
    renderer.append(PyBundle.message("python.cldt.installing.suggestion"), SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
  }

  @NlsContexts.DialogMessage
  override fun getInstallationWarning(defaultButtonName: String): String {
    val commandChunk = text(MacPythonSdkFlavor.getXCodeSelectInstallCommand().commandLineString)

    return HtmlBuilder()
      .append(PyBundle.message("python.sdk.executable.not.found.header"))
      .append(tag("ul").children(
        tag("li").children(raw(PyBundle.message("python.sdk.executable.not.found.option.specify.path", text("...").bold(), "python"))),
        tag("li").children(raw(
          PyBundle.message("python.sdk.executable.not.found.option.install.with.cldt", text(defaultButtonName).bold(), commandChunk.code())
        )),
        tag("li").children(text(PyBundle.message("python.sdk.executable.not.found.option.install.or.brew")))
      )).toString()
  }

  override fun install(module: Module?, systemWideSdksDetector: () -> List<PyDetectedSdk>): PyDetectedSdk? {
    val project = module?.project
    return ProgressManager.getInstance().run(
      object : Task.WithResult<PyDetectedSdk?, Exception>(project, PyBundle.message("python.cldt.installing.title"), true) {
        override fun compute(indicator: ProgressIndicator): PyDetectedSdk? {
          @Suppress("DialogTitleCapitalization")
          indicator.text = PyBundle.message("python.cldt.installing.indicator")
          indicator.text2 = PyBundle.message("python.cldt.installing.skip")

          runXCodeSelectInstall(project)

          while (!MacPythonSdkFlavor.areCommandLineDeveloperToolsAvailable() && isInstallCommandLineDeveloperToolsAppRunning()) {
            if (indicator.isCanceled) {
              logSdkInstallationOnMac(project, InstallationResult.CANCELLED)
              return null
            }

            Thread.sleep(TimeUnit.SECONDS.toMillis(5))
          }

          logSdkInstallationOnMac(
            project,
            if (MacPythonSdkFlavor.areCommandLineDeveloperToolsAvailable()) InstallationResult.OK else InstallationResult.EXIT_CODE
          )

          LOGGER.debug("Resetting system-wide sdks detectors")
          resetSystemWideSdksDetectors()

          return systemWideSdksDetector()
            .also { sdks ->
              LOGGER.debug { sdks.joinToString(prefix = "Detected system-wide sdks: ") { it.homePath ?: it.name } }
            }
            .also {
              logSdkLookupOnMac(
                project,
                if (it.isEmpty()) LookupResult.NOT_FOUND else LookupResult.FOUND
              )
            }
            .singleOrNull()
        }
      }
        .also {
          it.cancelText = IdeBundle.message("button.skip")
          it.cancelTooltipText = IdeBundle.message("button.skip")
        }
    )
  }

  private fun runXCodeSelectInstall(project: Project?) {
    val commandLine = MacPythonSdkFlavor.getXCodeSelectInstallCommand()
    try {
      execAndGetOutput(commandLine)
        .also {
          if (LOGGER.isDebugEnabled) {
            LOGGER.debug("Result of '${commandLine.commandLineString}':\n$it")
          }
        }
    }
    catch (e: ExecutionException) {
      logSdkInstallationOnMac(project, InstallationResult.EXCEPTION)
      LOGGER.warn("Exception during '${commandLine.commandLineString}'", e)
    }
  }

  private fun isInstallCommandLineDeveloperToolsAppRunning(): Boolean {
    val appName = "Install Command Line Developer Tools.app"

    return OSProcessUtil
      .getProcessList()
      .any { it.commandLine.contains(appName) }
      .also {
        if (LOGGER.isDebugEnabled) {
          LOGGER.debug("'$appName' is${if (it) "" else " not"} running")
        }
      }
  }
}
