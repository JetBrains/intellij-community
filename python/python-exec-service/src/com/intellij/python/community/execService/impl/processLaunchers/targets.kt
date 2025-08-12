// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UsePlatformProcessAwaitExit")

package com.intellij.python.community.execService.impl.processLaunchers

import com.intellij.execution.ExecutionException
import com.intellij.execution.process.LocalPtyOptions
import com.intellij.execution.target.*
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.execution.target.local.LocalTargetPtyOptions
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.project.ProjectManager
import com.intellij.platform.eel.provider.utils.ProcessFunctions
import com.intellij.platform.eel.provider.utils.bindProcessToScopeImpl
import com.intellij.python.community.execService.BinOnTarget
import com.intellij.python.community.execService.ExecuteGetProcessError
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.Exe
import com.jetbrains.python.errorProcessing.ExecErrorReason
import com.jetbrains.python.errorProcessing.MessageError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.io.path.pathString
import kotlin.time.Duration.Companion.milliseconds

private val logger = fileLogger()

internal suspend fun createProcessLauncherOnTarget(binOnTarget: BinOnTarget, launchRequest: LaunchRequest): Result<ProcessLauncher, ExecuteGetProcessError.EnvironmentError> = withContext(Dispatchers.IO) {
  val target = binOnTarget.target

  val request = if (target != null) {
    val projectMan = ProjectManager.getInstance() // Broken Targets API doesn't work without project
    target.createEnvironmentRequest(projectMan.openProjects.firstOrNull() ?: projectMan.defaultProject)
  }
  else LocalTargetEnvironmentRequest()

  // Broken Targets API can only upload the whole directory
  val dirsToMap = launchRequest.args.localFiles.map { it.parent }.toSet()
  for (localDir in dirsToMap) {
    request.uploadVolumes.add(TargetEnvironment.UploadRoot(localDir, TargetEnvironment.TargetPath.Temporary(), removeAtShutdown = true))
  }
  val targetEnv = try {
    request.prepareEnvironment(TargetProgressIndicator.EMPTY)
  }
  catch (e: ExecutionException) {
    fileLogger().warn("Failed to start $target", e) // TODO: i18n
    return@withContext Result.failure(ExecuteGetProcessError.EnvironmentError(MessageError("Failed to start environment due to ${e.localizedMessage}")))
  }
  val args = launchRequest.args.getArgs { localFile ->
    targetEnv.getTargetPaths(localFile.pathString).first()
  }
  val exePath: FullPathOnTarget
  val cmdLine = TargetedCommandLineBuilder(request).also { commandLineBuilder ->
    binOnTarget.configureTargetCmdLine(commandLineBuilder)
    // exe path is always fixed (pre-presolved) promise. It can't be obtained directly because of Targets API limitation
    exePath = commandLineBuilder.exePath.localValue.blockingGet(1000) ?: error("Exe path not set: $binOnTarget is broken")
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
  return@withContext Result.success(ProcessLauncher(exeForError = Exe.OnTarget(exePath), args = args, processCommands = TargetProcessCommands(launchRequest.scopeToBind, exePath, targetEnv, cmdLine)))
}

private class TargetProcessCommands(
  private val scopeToBind: CoroutineScope,
  private val exePath: FullPathOnTarget,
  private val targetEnv: TargetEnvironment,
  private val cmdLine: TargetedCommandLine,
) : ProcessCommands {

  private var process: Process? = null

  override val processFunctions: ProcessFunctions = ProcessFunctions(waitForExit = { // `waitForExit` seems to be broken in Targets API, hence polling
    while (process?.isAlive == true) {
      delay(100.milliseconds)
    }
    targetEnv.shutdown()
  }, killProcess = {
    process?.destroyForcibly();
    targetEnv.shutdown()
  })

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