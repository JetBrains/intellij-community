// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.execService.impl.processLaunchers

import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.execution.target.FullPathOnTarget
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.ProjectManager
import com.intellij.platform.eel.EelExecApi
import com.intellij.platform.eel.EelProcess
import com.intellij.platform.eel.ExecuteProcessException
import com.intellij.platform.eel.convertToJVMProcess
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
import com.intellij.python.community.execService.impl.PathMapper
import com.intellij.python.community.execService.impl.PyExecBundle
import com.intellij.python.community.execService.impl.Uploader
import com.intellij.python.community.execService.resolveAgainst
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.Exe
import com.jetbrains.python.errorProcessing.ExecErrorReason
import com.jetbrains.python.sdk.getModuleRoots
import com.jetbrains.python.venvReader.Directory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Collections
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.pathString
import kotlin.time.Duration.Companion.minutes
import kotlin.time.measureTime
import kotlin.time.toJavaDuration

private val log = fileLogger()

internal suspend fun createProcessLauncherOnEel(binOnEel: BinOnEel, launchRequest: LaunchRequest): ProcessLauncher {
  val exePath: EelPath = with(binOnEel) {
    if (path.isAbsolute) {
      path.asEelPath()
    }
    else {
      // A relative binary sits in the work directory. Append the parts one by one, because the separator of a local
      // path is not always the separator of the eel.
      workDir?.let { dir -> path.fold(dir) { parent, part -> parent.getChild(part.pathString) } }
      ?: path.toAbsolutePath().asEelPath()
    }
  }
  val eel = exePath.descriptor.toEelApi()


  val (args, env) = launchRequest.args.getArgsAndEnv(object : Uploader {
    override suspend fun uploadFile(localFile: Path): FullPathOnTarget = withContext(Dispatchers.IO) {
      EelPathUtils.transferLocalContentToRemote(
        source = localFile,
        target = EelPathUtils.TransferTarget.Temporary(eel.descriptor)
      ).asEelPath().toString()
    }


    override suspend fun uploadDir(localDir: Directory): PathMapper {
      val remoteDir = if (eel.descriptor == localDir.getEelDescriptor()) {
        // In persistent mode, `transferLocalContentToRemote` copies the files also when the eel is local.
        // Do this check to prevent a copy from local to local.
        localDir
      }
      else {
        val build = ApplicationInfo.getInstance().build.toString()
        val home = eel.userInfo.home.resolve(".pycharm").resolve(build)
        val remoteDir = home.resolve(localDir.toHash()).asNioPath()
        withContext(Dispatchers.IO) {
          // Two concurrent uploads to the same directory are not necessary.
          // A mutex for each eel or directory is possible, but usually there is only one. Thus, one global mutex is sufficient.
          uploadMutex.withLock {
            val pathDirKey = remoteDir.pathString
            if (pathDirKey !in remoteDirCheck || !remoteDir.exists()) {
              remoteDir.createDirectories()
              val time = measureTime {
                EelPathUtils.transferLocalContentToRemote(
                  source = localDir,
                  target = EelPathUtils.TransferTarget.Explicit(remoteDir)
                )
              }
              log.debug { "Uploaded $localDir to $remoteDir in $time" }
              remoteDirCheck.add(pathDirKey)
            }
            remoteDir
          }
        }
      }
      log.debug { "$localDir mapped to $remoteDir" }
      return PathMapper {
        it.resolveAgainst(remoteDir).asEelPath().toString()
      }
    }
  })
  return ProcessLauncher(
    exeForError = Exe.OnEel(exePath),
    args = args,
    processCommands = EelProcessCommands(launchRequest.scopeToBind,
                                         binOnEel,
                                         exePath,
                                         args,
                                         env = launchRequest.getEnvMergingWithPathVars(env, binOnEel.path.getEelDescriptor().osFamily),
                                         tty = launchRequest.usePty)
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

  /**
   * This must not read the file system. The work directory can disappear while the process runs. The caller reads
   * [info] outside of a try block, so an [java.io.IOException] here escapes the whole exec call.
   */
  override val info: ProcessCommandsInfo
    get() = ProcessCommandsInfo(
      env = env,
      cwd = binOnEel.workDir?.toString(),
      target = binOnEel.path.getEelDescriptor().name,
    )

  override val processFunctions: ProcessFunctions = ProcessFunctions(
    waitForExit = { eelProcess?.exitCode?.await() },
    killProcess = { eelProcess?.kill() }
  )

  override suspend fun start(): Result<Process, ExecErrorReason.CantStart> {
    val workDir = binOnEel.workDir

    // If project is untrusted we should not execute anything there
    val (nioPathToExec, nioWorkDir) = withContext(Dispatchers.IO) {
      Pair(path.asNioPath().toAbsolutePath(), workDir?.asNioPath())
    }
    val pathIsProhibited = getProhibitedPaths().any { prohibitedParent ->
      nioPathToExec.startsWith(prohibitedParent) ||
      (nioWorkDir != null && nioWorkDir.startsWith(prohibitedParent))
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
        .workingDirectory(workDir)
        .interactionOptions(if (tty != null) EelExecApi.Pty(tty.cols.toInt(), tty.rows.toInt()) else null)
        .eelIt()
      this.eelProcess = eelProcess
      return Result.success(eelProcess.convertToJVMProcess())
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

private suspend fun Directory.toHash(): String = withContext(Dispatchers.Default) {
  val digest = MessageDigest.getInstance("SHA-256")
  digest.digest(pathString.encodeToByteArray()).toHexString()
}

/**
 * Prevents two coroutines from uploading to the same directory at the same time.
 */
private val uploadMutex = Mutex()

/**
 * Remote directories that we uploaded recently.
 * A remote directory can be old or incomplete (for example, after a failed upload or after a helpers change).
 * Thus, we upload each directory again once after startup, and again when its entry expires.
 */
private val remoteDirCheck = Collections.newSetFromMap(Caffeine.newBuilder()
                                                         .maximumSize(50)
                                                         .expireAfterWrite(30.minutes.toJavaDuration())
                                                         .build<String, Boolean>()
                                                         .asMap())