// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.installer

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.jetbrains.python.PySdkBundle
import com.jetbrains.python.sdk.Release
import com.jetbrains.python.sdk.Resource
import com.jetbrains.python.sdk.ResourceType
import java.nio.file.Path
import kotlin.io.path.absolutePathString

/**
 * Software Release Installer for Apple Software Package (pkg) files
 */
class PkgReleaseInstaller : ResourceTypeReleaseInstaller(ResourceType.APPLE_SOFTWARE_PACKAGE) {
  override fun buildCommandLine(resource: Resource, path: Path): GeneralCommandLine {
    return ExecUtil.sudoCommand(
      GeneralCommandLine("installer", "-pkg", path.absolutePathString(), "-target", "/"),
      PySdkBundle.message("python.sdk.running.sudo.prompt", resource.fileName)
    )
  }
}

/**
 * Software Release Installer for Microsoft Window Executable (exe) files
 */
class ExeReleaseInstaller : ResourceTypeReleaseInstaller(ResourceType.MICROSOFT_WINDOWS_EXECUTABLE) {
  override fun buildCommandLine(resource: Resource, path: Path): GeneralCommandLine {
    return GeneralCommandLine(path.absolutePathString(), "/repair", "/quiet", "InstallAllUsers=0")
  }
}

/**
 * Base Release Installer with resource type specific filtering (like exe, pkg, ...)
 */
abstract class ResourceTypeReleaseInstaller(private val resourceType: ResourceType) : DownloadableReleaseInstaller() {
  abstract fun buildCommandLine(resource: Resource, path: Path): GeneralCommandLine

  @Throws(ProcessException::class)
  override fun process(release: Release, resourcePaths: Map<Resource, Path>, indicator: ProgressIndicator) {
    resourcePaths.forEach { (resource, path) ->
      processResource(resource, path, indicator)
    }
  }

  @Throws(ProcessException::class)
  private fun processResource(resource: Resource, path: Path, indicator: ProgressIndicator) {
    indicator.isIndeterminate = true
    indicator.text = PySdkBundle.message("python.sdk.running.progress.text", resource.fileName)
    indicator.text2 = PySdkBundle.message("python.sdk.running.one.minute.progress.details")

    val commandLine = buildCommandLine(resource, path)
    LOGGER.info("Running ${commandLine.commandLineString}")
    val processOutput: ProcessOutput
    try {
      processOutput = CapturingProcessHandler(commandLine).runProcessWithProgressIndicator(indicator)
    }
    catch (e: Exception) {
      throw ExecutionProcessException(commandLine, e)
    }
    processOutput.isCancelled.takeIf { it }?.let { throw CancelledProcessException(commandLine, processOutput) }
    processOutput.exitCode.takeIf { it != 0 }?.let {
      if (processOutput.stderr.contains("User cancelled", ignoreCase = true)) {
        throw CancelledProcessException(commandLine, processOutput)
      }
      throw NonZeroExitCodeProcessException(commandLine, processOutput)
    }
    processOutput.isTimeout.takeIf { it }?.let { throw TimeoutProcessException(commandLine, processOutput) }
  }

  /**
   * It can install a release only if the release contains at least single resource with corresponding resource type in the binaries.
   */
  override fun canInstall(release: Release): Boolean {
    return release.binaries?.any {
      it.isCompatible() &&
      it.resources.any { r -> r.type == resourceType }
    } ?: false
  }

  /**
   * @returns first non-empty list with resources of the selected type in any compatible binary package or empty list if nothing is found.
   */
  override fun getResourcesToDownload(release: Release): List<Resource> {
    return release.binaries
             ?.filter { it.isCompatible() }
             ?.firstNotNullOfOrNull {
               it.resources.filter { r -> r.type == resourceType }.ifEmpty { null }
             }
           ?: listOf()
  }
}