// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.execService.impl.processLaunchers

import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.ProjectManager
import com.intellij.platform.eel.EelExecApi
import com.intellij.platform.eel.EelProcess
import com.intellij.platform.eel.ExecuteProcessException
import com.intellij.platform.eel.impl.base.ProcessFunctions
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.eel.provider.utils.EelPathUtils
import com.intellij.platform.eel.spawnProcess
import com.intellij.project.stateStore
import com.intellij.python.community.execService.BinOnEel
import com.intellij.python.community.execService.TtySize
import com.intellij.python.community.execService.impl.PyExecBundle
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.Exe
import com.jetbrains.python.errorProcessing.ExecErrorReason
import com.jetbrains.python.sdk.getModuleRoots
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.pathString

private val log = fileLogger()

internal suspend fun createProcessLauncherOnEel(binOnEel: BinOnEel, launchRequest: LaunchRequest): ProcessLauncher {
  val exePath: EelPath = with(binOnEel) {
    (if (path.isAbsolute) path else workDir?.resolve(binOnEel.path) ?: path.toAbsolutePath()).asEelPath()
  }
  val eel = exePath.descriptor.toEelApi()
  val args = launchRequest.args.getArgs { file ->
    EelPathUtils.transferLocalContentToRemote(
      source = file,
      target = EelPathUtils.TransferTarget.Temporary(eel.descriptor)
    ).asEelPath().toString()
  }
  return ProcessLauncher(
    exeForError = Exe.OnEel(exePath),
    args = args,
    processCommands = EelProcessCommands(launchRequest.scopeToBind, binOnEel, exePath, args, launchRequest.env, launchRequest.usePty)
  )
}

private class EelProcessCommands(
  override val scopeToBind: CoroutineScope,
  private val binOnEel: BinOnEel,
  private val path: EelPath,
  private val args: List<String>,
  private val env: Map<String, String>,
  private val tty: TtySize?,
) : ProcessCommands {
  private var eelProcess: EelProcess? = null

  override val info: ProcessCommandsInfo
    get() = ProcessCommandsInfo(
      env = env,
      cwd = binOnEel.workDir?.toRealPath()?.pathString,
      target = binOnEel.path.getEelDescriptor().name,
    )

  override val processFunctions: ProcessFunctions = ProcessFunctions(
    waitForExit = { eelProcess?.exitCode?.await() },
    killProcess = { eelProcess?.kill() }
  )

  override suspend fun start(): Result<Process, ExecErrorReason.CantStart> {
    var workDir = binOnEel.workDir
    workDir = withContext(Dispatchers.IO) { if (workDir != null && !workDir.isAbsolute) workDir.toRealPath() else workDir }

    // If project is untrusted we should not execute anything there
    val nioPathToExec = withContext(Dispatchers.IO) {
      path.asNioPath().toAbsolutePath()
    }
    val pathIsProhibited = getProhibitedPaths().any { prohibitedParent ->
      nioPathToExec.startsWith(prohibitedParent) ||
      (workDir != null && workDir.startsWith(prohibitedParent))
    }
    if (pathIsProhibited) {
      log.trace { "Prohibited exec $nioPathToExec" }
      return Result.failure(ExecErrorReason.CantStart(null, PyExecBundle.message("py.exec.error.not.trusted", nioPathToExec)))
    }

    try {
      log.trace { "Spawning $nioPathToExec" }
      val eelProcess = path.descriptor.toEelApi().exec.spawnProcess(path)
        .scope(scopeToBind)
        .args(args)
        .env(env)
        .workingDirectory(workDir?.asEelPath())
        .interactionOptions(if (tty != null) EelExecApi.Pty(tty.cols.toInt(), tty.rows.toInt()) else null)
        .eelIt()
      this.eelProcess = eelProcess
      return Result.success(eelProcess.convertToJavaProcess())
    }
    catch (e: ExecuteProcessException) {
      return Result.failure(ExecErrorReason.CantStart(e.errno, e.message))
    }
  }
}

/**
 * List of roots of all untrusted projects
 */
private suspend fun getProhibitedPaths(): List<Path> = withContext(Dispatchers.Default) {
  val untrustedProjects = ProjectManager.getInstance().openProjects.filter { !TrustedProjects.isProjectTrusted(it) }
  return@withContext untrustedProjects.flatMap { project ->
    setOf(project.stateStore.projectBasePath) + project.getModuleRoots().map { it.toNioPath() }
  }.map { it.toAbsolutePath() }
}