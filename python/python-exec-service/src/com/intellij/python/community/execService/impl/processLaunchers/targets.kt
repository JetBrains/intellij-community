// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UsePlatformProcessAwaitExit")

package com.intellij.python.community.execService.impl.processLaunchers

import com.intellij.execution.ExecutionException
import com.intellij.execution.process.LocalPtyOptions
import com.intellij.execution.target.FullPathOnTarget
import com.intellij.execution.target.TargetEnvironment
import com.intellij.execution.target.TargetProgressIndicator
import com.intellij.execution.target.TargetedCommandLine
import com.intellij.execution.target.TargetedCommandLineBuilder
import com.intellij.execution.target.getTargetPaths
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.execution.target.local.LocalTargetPtyOptions
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.ProjectManager
import com.intellij.platform.eel.provider.utils.ProcessFunctions
import com.intellij.platform.eel.provider.utils.bindProcessToScopeImpl
import com.intellij.python.community.execService.BinOnTarget
import com.intellij.python.community.execService.DownloadConfig
import com.intellij.python.community.execService.ExecuteGetProcessError
import com.intellij.python.community.execService.impl.PyExecBundle
import com.intellij.python.community.execService.spi.TargetEnvironmentRequestHandler
import com.intellij.remoteServer.util.ServerRuntimeException
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.Exe
import com.jetbrains.python.errorProcessing.ExecErrorReason
import com.jetbrains.python.errorProcessing.MessageError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.io.path.pathString
import kotlin.time.Duration.Companion.milliseconds

private val logger = fileLogger()

internal suspend fun createProcessLauncherOnTarget(binOnTarget: BinOnTarget, launchRequest: LaunchRequest): Result<ProcessLauncher, ExecuteGetProcessError.EnvironmentError> = withContext(Dispatchers.IO) {
  val target = binOnTarget.target

  val request = if (target != null) {
    val projectMan = ProjectManager.getInstance() // Broken Targets API doesn't work without project
    try {
      target.createEnvironmentRequest(projectMan.openProjects.firstOrNull() ?: projectMan.defaultProject)
    }
    catch (e: ServerRuntimeException) {
      return@withContext Result.failure(ExecuteGetProcessError.EnvironmentError(MessageError(e.localizedMessage)))
    }
  }
  else LocalTargetEnvironmentRequest()

  // Broken Targets API can only upload the whole directory
  val dirsToMap = buildSet {
    addAll(launchRequest.args.localFiles.map { it.parent })
    binOnTarget.workingDir?.takeIf { it.pathString.isNotBlank() }?.also {
      add(it)
    }
  }
  val handler = TargetEnvironmentRequestHandler.getHandler(request)
  val uploadRoots = handler.mapUploadRoots(request, dirsToMap, binOnTarget.workingDir?.takeIf { it.pathString.isNotBlank() })
  request.uploadVolumes.addAll(uploadRoots)

  // Setup download roots if download is requested
  val downloadConfig = launchRequest.downloadConfig
  if (downloadConfig != null) {
    val localDirsToDownload = binOnTarget.workingDir?.takeIf { it.pathString.isNotBlank() }?.let { setOf(it) } ?: emptySet()
    val downloadRoots = handler.mapDownloadRoots(request, request.uploadVolumes, localDirsToDownload)
    request.downloadVolumes.addAll(downloadRoots)
  }

  val targetEnv = try {
    request.prepareEnvironment(TargetProgressIndicator.EMPTY)
  }
  catch (e: RuntimeException) { // some types like DockerRemoteRequest throw base RuntimeException instead of anything meaningful, need to change platform code first
    fileLogger().warn("Failed to start $target", e) // TODO: i18n
    return@withContext Result.failure(ExecuteGetProcessError.EnvironmentError(MessageError("Failed to start environment due to ${e.localizedMessage}")))
  }
  catch (e: ExecutionException) {
    fileLogger().warn("Failed to start $target", e) // TODO: i18n
    return@withContext Result.failure(ExecuteGetProcessError.EnvironmentError(MessageError("Failed to start environment due to ${e.localizedMessage}")))
  }
  targetEnv.uploadVolumes.forEach { _, volume ->
    volume.upload(".", TargetProgressIndicator.EMPTY)
  }

  val args = launchRequest.args.getArgs { localFile ->
    targetEnv.getTargetPaths(localFile.pathString).first()
  }
  val exePath: FullPathOnTarget
  val cmdLine = TargetedCommandLineBuilder(request).also { commandLineBuilder ->
    binOnTarget.configureTargetCmdLine(commandLineBuilder)
    // exe path is always fixed (pre-presolved) promise. It can't be obtained directly because of Targets API limitation
    exePath = commandLineBuilder.exePath.localValue.blockingGet(1000) ?: error("Exe path not set: $binOnTarget is broken")
    // Map working directory through upload volumes if it's a local path
    binOnTarget.workingDir?.takeIf { it.pathString.isNotBlank() }?.let { workingDir ->
      // Try to resolve through upload volumes (in case workingDir is a local path that needs mapping)
      val workingDirOnTarget = targetEnv.getTargetPaths(workingDir.pathString).firstOrNull() ?: workingDir.pathString
      commandLineBuilder.setWorkingDirectory(workingDirOnTarget)
    }
    launchRequest.usePty?.let {
      val ptyOptions = LocalPtyOptions
        .defaults()
        .builder()
        .initialRows(it.rows.toInt())
        .initialColumns(it.cols.toInt())
        .build()
      commandLineBuilder.ptyOptions = LocalTargetPtyOptions(ptyOptions)
    }

    commandLineBuilder.addParameters(args)
    for ((k, v) in launchRequest.env) {
      commandLineBuilder.addEnvironmentVariable(k, v)
    }
  }.build()
  return@withContext Result.success(ProcessLauncher(exeForError = Exe.OnTarget(exePath), args = args, processCommands = TargetProcessCommands(launchRequest.scopeToBind, exePath, targetEnv, cmdLine, downloadConfig)))
}

private class TargetProcessCommands(
  override val scopeToBind: CoroutineScope,
  private val exePath: FullPathOnTarget,
  private val targetEnv: TargetEnvironment,
  private val cmdLine: TargetedCommandLine,
  private val downloadConfig: DownloadConfig?,
) : ProcessCommands {
  override val info: ProcessCommandsInfo
    get() = ProcessCommandsInfo(
      env = cmdLine.environmentVariables,
      cwd = cmdLine.workingDirectory,
      target = targetEnv.request.configuration?.displayName ?: PyExecBundle.message("py.exec.target.name.default")
    )

  private var process: Process? = null

  override val processFunctions: ProcessFunctions = ProcessFunctions(waitForExit = { // `waitForExit` seems to be broken in Targets API, hence polling
    while (process?.isAlive == true) {
      delay(100.milliseconds)
    }
    downloadAfterExecution()
    targetEnv.shutdown()
  }, killProcess = {
    process?.destroyForcibly()
    targetEnv.shutdown()
  })

  private suspend fun downloadAfterExecution() {
    if (downloadConfig == null) return

    targetEnv.downloadVolumes.forEach { (_, volume) ->
      val paths = downloadConfig.relativePaths.takeIf { it.isNotEmpty() } ?: listOf(".")
      for (path in paths) {
        coroutineToIndicator {
          try {
            volume.download(path, it)
          }
          catch (e: IOException) {
            fileLogger().warn("Could not download $path: ${e.message}")
          }
          catch (e: RuntimeException) { // TODO: Unfortunately even though download is documented to throw IOException, in practice other random exceptions are possible for SSH at least
            fileLogger().warn("Could not download $path: ${e.message}")
          }
        }
      }
    }
  }

  override suspend fun start(): Result<Process, ExecErrorReason.CantStart> {

    try {
      val process = targetEnv.createProcess(cmdLine)
      this.process = process
      scopeToBind.bindProcessToScopeImpl(logger = logger, processNameForDebug = exePath, processFunctions = processFunctions)
      return Result.success(process)
    }
    catch (e: ExecutionException) {
      return e.asCantStart()
    }
  }
}

private fun ExecutionException.asCantStart(): Result.Failure<ExecErrorReason.CantStart> = Result.failure(ExecErrorReason.CantStart(null, localizedMessage))
