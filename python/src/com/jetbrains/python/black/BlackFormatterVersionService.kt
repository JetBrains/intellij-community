// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.black

import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.target.TargetedCommandLineBuilder
import com.intellij.execution.target.local.LocalTargetEnvironment
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Version
import com.intellij.util.VersionUtil
import com.jetbrains.python.black.configuration.BlackFormatterConfiguration
import com.jetbrains.python.packaging.PyPackage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

@Service(Service.Level.PROJECT)
class BlackFormatterVersionService(private val project: Project) {
  companion object {

    private val PATTERN = Pattern.compile("(?<=black, )(([\\d]+\\.[\\d]+\\.?[\\d]*).*)(?= \\(compiled: )")

    val UNKNOWN_VERSION = Version(0, 0, 0)

    private fun getInstance(project: Project): BlackFormatterVersionService = project.service()

    fun getVersion(project: Project): Version = getInstance(project).getVersion()
  }

  private var version: Version

  private var configuration: BlackFormatterConfiguration

  init {
    configuration = BlackFormatterConfiguration.getBlackConfiguration(project).copy()
    version = getVersionFromSdkOrBinary()
  }

  @Synchronized
  private fun getVersion(): Version {
    if (BlackFormatterConfiguration.getBlackConfiguration(project) != configuration) {
      configuration = BlackFormatterConfiguration.getBlackConfiguration(project).copy()
      version = getVersionFromSdkOrBinary()
    }
    return version
  }

  private fun getVersionFromSdkOrBinary(): Version {
    return when (configuration.executionMode) {
      BlackFormatterConfiguration.ExecutionMode.BINARY -> {
        configuration.pathToExecutable?.let {
          getVersionForExecutable(it)
        } ?: UNKNOWN_VERSION
      }
      BlackFormatterConfiguration.ExecutionMode.PACKAGE -> {
        BlackFormatterUtil.getBlackFormatterPackageInfo(configuration.getSdk())?.let {
          getVersionForPackage(it)
        } ?: UNKNOWN_VERSION
      }
    }
  }

  private fun getVersionForExecutable(pathToExecutable: String): Version {
    val targetEnvRequest = LocalTargetEnvironmentRequest()
    val targetEnvironment = LocalTargetEnvironment(LocalTargetEnvironmentRequest())

    val commandLineBuilder = TargetedCommandLineBuilder(targetEnvRequest)
    commandLineBuilder.setExePath(pathToExecutable)
    commandLineBuilder.addParameters("--version")

    val targetCMD = commandLineBuilder.build()

    val process = targetEnvironment.createProcess(targetCMD)

    return runBlockingMaybeCancellable {
      runCatching {
        withContext(Dispatchers.IO) {
          val processHandler = CapturingProcessHandler(process, targetCMD.charset, targetCMD.getCommandPresentation(targetEnvironment))
          val processOutput = processHandler.runProcess(5000, true).stdout
          return@withContext VersionUtil.parseVersion(processOutput, PATTERN) ?: UNKNOWN_VERSION
        }
      }.getOrDefault(UNKNOWN_VERSION)
    }
  }

  private fun getVersionForPackage(pythonPackage: PyPackage): Version =
    parseVersionString(pythonPackage.version)

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