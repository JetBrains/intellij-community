// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.black

import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.target.TargetedCommandLineBuilder
import com.intellij.execution.target.local.LocalTargetEnvironment
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Version
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.util.VersionUtil
import com.jetbrains.python.PyBundle
import com.jetbrains.python.black.BlackFormatterUtil.Companion.PACKAGE_NAME
import com.jetbrains.python.black.configuration.BlackFormatterConfiguration
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonPackageManagementListener
import com.jetbrains.python.packaging.management.PythonPackageManager
import kotlinx.coroutines.*
import java.util.regex.Pattern

@Service(Service.Level.PROJECT)
class BlackFormatterVersionService(private val project: Project, val serviceScope: CoroutineScope) : Disposable {
  companion object {

    private val PATTERN = Pattern.compile("(?<=black, )(([\\d]+\\.[\\d]+\\.?[\\d]*).*)(?= \\(compiled: )")

    val UNKNOWN_VERSION: Version = Version(0, 0, 0)

    @JvmStatic
    fun getInstance(project: Project): BlackFormatterVersionService = project.service()

    suspend fun getVersion(project: Project): Version = getInstance(project).getVersion()

    private fun parseVersionString(versionString: String): Version {

      val parts = versionString
        .split(".", "b")
        .mapNotNull(String::toIntOrNull)
        .filter { it >= 0 }

      if (parts.size < 3) {
        return UNKNOWN_VERSION
      }

      return Version(parts[0], parts[1], parts[2])
    }
  }

  private var version: Version = UNKNOWN_VERSION

  private var configuration: BlackFormatterConfiguration

  init {
    configuration = BlackFormatterConfiguration.getBlackConfiguration(project).copy()
    version = runBlockingCancellable { getVersionFromSdkOrBinary() }
    subscribeOnChanges()
  }

  suspend fun getVersionForExecutable(pathToExecutable: String): Version {
    val targetEnvRequest = LocalTargetEnvironmentRequest()
    val targetEnvironment = LocalTargetEnvironment(LocalTargetEnvironmentRequest())

    val commandLineBuilder = TargetedCommandLineBuilder(targetEnvRequest)
    commandLineBuilder.setExePath(pathToExecutable)
    commandLineBuilder.addParameters("--version")

    val targetCMD = commandLineBuilder.build()

    val process = targetEnvironment.createProcess(targetCMD)

    return runCatching {
      withContext(Dispatchers.IO) {
        coroutineToIndicator {
          val processHandler = CapturingProcessHandler(process, targetCMD.charset, targetCMD.getCommandPresentation(targetEnvironment))
          val processOutput = processHandler.runProcess(5000, true).stdout
          VersionUtil.parseVersion(processOutput, PATTERN) ?: UNKNOWN_VERSION
        }
      }
    }.getOrDefault(UNKNOWN_VERSION)
  }

  private suspend fun getVersion(): Version {
    if (BlackFormatterConfiguration.getBlackConfiguration(project) != configuration) {
      configuration = BlackFormatterConfiguration.getBlackConfiguration(project).copy()
      version = getVersionFromSdkOrBinary()
    }
    return version
  }

  private suspend fun getVersionFromSdkOrBinary(): Version {
    return when (configuration.executionMode) {
      BlackFormatterConfiguration.ExecutionMode.BINARY -> {
        configuration.pathToExecutable?.let {
          getVersionForExecutable(it)
        } ?: UNKNOWN_VERSION
      }
      BlackFormatterConfiguration.ExecutionMode.PACKAGE -> {
        getVersionForPackage(configuration.getSdk(), project)
      }
    }
  }

  fun getVersionForPackage(sdk: Sdk?, project: Project): Version {
    return getBlackFormatterPackageInfo(sdk, project)?.let { pythonPackage ->
      parseVersionString(pythonPackage.version)
    } ?: UNKNOWN_VERSION
  }

  private fun getBlackFormatterPackageInfo(sdk: Sdk?, project: Project): PythonPackage? {
    val packageManager = sdk?.let { PythonPackageManager.forSdk(project, sdk) } ?: return null
    if (packageManager.installedPackages.isEmpty()) {
      runBlockingCancellable {
        withBackgroundProgress(project, PyBundle.message("black.getting.black.version"), cancellable = true) {
          packageManager.reloadPackages()
        }
      }
    }
    return packageManager.let {
      it.installedPackages.firstOrNull { pyPackage -> pyPackage.name == PACKAGE_NAME }
    }
  }

  private fun subscribeOnChanges() {
    val connection = project.messageBus.connect(this)
    connection.subscribe(PythonPackageManager.PACKAGE_MANAGEMENT_TOPIC, object : PythonPackageManagementListener {
      override fun packagesChanged(sdk: Sdk) {
        runBlockingCancellable {
          serviceScope.launch {
            version = getVersionFromSdkOrBinary()
          }
        }
      }
    })
  }

  override fun dispose() {
    serviceScope.cancel()
  }
}