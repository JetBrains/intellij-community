// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DEPRECATION")

package com.intellij.terminal.backend.rpc

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.rethrowControlFlowException
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.project.findProject
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLockAbsence
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.terminal.LocalTerminalCustomizer
import org.jetbrains.plugins.terminal.LocalTerminalDirectRunner
import org.jetbrains.plugins.terminal.createEnvVariablesMap
import org.jetbrains.plugins.terminal.startup.MutableShellExecOptionsImpl
import org.jetbrains.plugins.terminal.startup.ShellExecCommand
import org.jetbrains.plugins.terminal.startup.ShellExecCommandImpl
import org.jetbrains.plugins.terminal.startup.ShellExecOptionsCustomizer
import org.jetbrains.plugins.terminal.startup.ShellExecOptionsCustomizerDisabler
import org.jetbrains.plugins.terminal.startup.TerminalExecOptionsCustomizationRemoteApi
import org.jetbrains.plugins.terminal.startup.TerminalExecOptionsCustomizationRequest
import org.jetbrains.plugins.terminal.startup.TerminalExecOptionsCustomizationResponse
import kotlin.io.path.pathString

internal class TerminalExecOptionsCustomizationRemoteApiImpl : TerminalExecOptionsCustomizationRemoteApi {
  override suspend fun customizeExecOptions(request: TerminalExecOptionsCustomizationRequest): TerminalExecOptionsCustomizationResponse {
    val project = request.projectId.findProject()
    val eelDescriptor = if (request.eelDescriptor != null) {
      request.eelDescriptor!!
    }
    else {
      // `request.eelDescriptor` is present only in the monolith.
      // In the RemDev scenario it is null because EelDescriptor is not serializable.
      // The working directory is assumed to be local to the environment where the IDE backend is running.
      val projectDescriptor = project.getEelDescriptor()
      if (projectDescriptor != LocalEelDescriptor) {
        thisLogger().warn("Expected that ${project} is located in the environment of LocalEelDescriptor but was: $projectDescriptor.\n" +
                          "Skipping exec options customization.")
        return TerminalExecOptionsCustomizationResponse(request.shellCommand, request.envVariables)
      }
      LocalEelDescriptor
    }

    val workingDirectoryEelPath = EelPath.parse(request.workingDirectory, eelDescriptor)
    return withContext(Dispatchers.IO) {
      customizeShellExecOptions(
        project = project,
        originalShellCommand = request.shellCommand,
        originalEnvVariables = request.envVariables,
        workingDirectory = workingDirectoryEelPath,
        shellIntegrationAvailable = request.shellIntegrationAvailable,
        eelDescriptor = eelDescriptor
      )
    }
  }
}

/**
 * Runs [LocalTerminalCustomizer] and [ShellExecOptionsCustomizer] extensions registered on the backend.
 *
 * Must be called on a background thread without a read action: the customizers may perform blocking operations
 * (see [ShellExecOptionsCustomizer.customizeExecOptions]).
 */
@RequiresReadLockAbsence
@RequiresBackgroundThread
private fun customizeShellExecOptions(
  project: Project,
  originalShellCommand: List<String>,
  originalEnvVariables: Map<String, String>,
  workingDirectory: EelPath,
  shellIntegrationAvailable: Boolean,
  eelDescriptor: EelDescriptor,
): TerminalExecOptionsCustomizationResponse {
  val disableCustomizers = ShellExecOptionsCustomizerDisabler.EP_NAME.extensionList.any { it.shouldDisable(project) }
  if (disableCustomizers) {
    return TerminalExecOptionsCustomizationResponse(originalShellCommand, originalEnvVariables)
  }

  val mutableEnvs = createEnvVariablesMap(eelDescriptor.osFamily, originalEnvVariables)
  var shellCommand = originalShellCommand

  if (shouldApplyLocalTerminalCustomizers(project, eelDescriptor)) {
    val workingDirectoryNioPath = workingDirectory.asNioPath().pathString
    for (customizer in LocalTerminalCustomizer.EP_NAME.extensionList) {
      try {
        shellCommand = customizer.customizeCommandAndEnvironment(project, workingDirectoryNioPath, shellCommand, mutableEnvs, eelDescriptor)
      }
      catch (t: Throwable) {
        rethrowControlFlowException(t)
        logger<LocalTerminalDirectRunner>().error("Exception during customization of the terminal session", t)
      }
    }
  }

  var shellExecCommand: ShellExecCommand = ShellExecCommandImpl(shellCommand)
  ShellExecOptionsCustomizer.EP_NAME.processWithPluginDescriptor { customizer, _ ->
    val execOptions = MutableShellExecOptionsImpl(
      _execCommand = shellExecCommand,
      workingDirectory = workingDirectory,
      mutableEnvs = mutableEnvs,
      shellIntegrationAvailable = shellIntegrationAvailable,
      requester = customizer.javaClass,
    )
    customizer.customizeExecOptions(project, execOptions)
    shellExecCommand = execOptions.execCommand
  }

  return TerminalExecOptionsCustomizationResponse(shellExecCommand.command, mutableEnvs)
}

private fun shouldApplyLocalTerminalCustomizers(project: Project, processEelDescriptor: EelDescriptor): Boolean {
  if (!LocalTerminalCustomizer.EP_NAME.hasAnyExtensions()) {
    return false
  }
  val projectEelDescriptor = project.getEelDescriptor()
  // Apply `LocalTerminalCustomizer` only if the project and the shell process
  // belong to the same environment.
  // Otherwise, `LocalTerminalCustomizer` implementations may customize wrongly,
  // like `com.intellij.python.terminal.PyVirtualEnvTerminalCustomizer` does:
  // environment variables from one environment being injected into the other, breaking the shell.
  //
  // Examples of mixed environments:
  // - Running powershell.exe (using LocalEelDescriptor) in projects under \\wsl.localhost\Ubuntu\
  // - Running "wsl.exe -d Ubuntu" (using WSL EelDescriptor) in projects under C:\
  return processEelDescriptor == projectEelDescriptor
}
