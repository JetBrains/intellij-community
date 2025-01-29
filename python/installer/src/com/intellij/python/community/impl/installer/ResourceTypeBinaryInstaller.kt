// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.installer

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.progress.ProgressIndicator
import com.jetbrains.python.PySdkBundle
import com.jetbrains.python.sdk.Binary
import com.jetbrains.python.sdk.Resource
import com.jetbrains.python.sdk.ResourceType
import com.jetbrains.python.sdk.installer.*
import java.nio.file.Path

/**
 * Base Release Installer with resource type specific filtering (like exe, pkg, ...)
 */
internal abstract class ResourceTypeBinaryInstaller(private val resourceType: ResourceType) : DownloadableBinaryInstaller() {
  abstract fun buildCommandLine(resource: Resource, path: Path): GeneralCommandLine

  @Throws(ProcessException::class)
  override fun process(resourcePaths: Map<Resource, Path>, indicator: ProgressIndicator) {
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
      indicator.checkCanceled()
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
  override fun canInstall(binary: Binary): Boolean {
    return binary.resources.any { r -> r.type == resourceType }
  }

  /**
   * @returns first non-empty list with resources of the selected type in any compatible binary package or empty list if nothing is found.
   */
  override fun getResourcesToDownload(binary: Binary): List<Resource> {
    return binary.resources.filter { it.type == resourceType }
  }
}