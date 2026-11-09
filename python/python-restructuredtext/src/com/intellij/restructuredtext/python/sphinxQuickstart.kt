// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("SphinxQuickstart")

package com.intellij.restructuredtext.python

import com.intellij.execution.process.KillableProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.python.community.execService.Args
import com.intellij.python.community.execService.ExecOptions
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.HowToReportFile
import com.intellij.python.community.execService.ProcessInteractiveHandler
import com.intellij.python.community.execService.toEnvVars
import com.intellij.python.pyproject.model.evolution.findEvoPyProject
import com.intellij.python.pyproject.model.evolution.findMainEvoPyProject
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.util.io.awaitExit
import com.jetbrains.python.PYTHONIOENCODING
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.errorProcessing.MessageError
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.errorProcessing.emit
import com.jetbrains.python.sdk.PythonEnvUtil
import com.jetbrains.python.sdk.asBinToExecute
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.time.Duration

/**
 * Runs `sphinx-quickstart` of [interpreter] for the documentation in [docsDir], in the interactive mode of [ExecService].
 * The process asks the user its questions, so [showConsole] shows its handler in a console. [showConsole] runs on the EDT
 * when the process starts. The function returns the exit code when the process ends.
 * The process waits for the user, so it has no timeout.
 *
 * The process writes into an empty temporary directory, not into [docsDir]. When the process ends with exit code 0, the
 * function copies into [docsDir] each file that [docsDir] does not have yet, and tells in the console which files it
 * kept. So a remote interpreter gets no copy of [docsDir], and the run changes or deletes no file that is already in
 * [docsDir]. After another exit code, for example after a stop, the function copies no file, because the process can
 * stop during a write. After a copy, the console tells that a rerun needs a directory without the new `conf.py`,
 * because a rerun stops at that file.
 * An I/O error of the temporary directory or of the copy comes back as an error.
 */
@ApiStatus.Internal
@OptIn(ExperimentalPathApi::class)
suspend fun runSphinxQuickstart(
  interpreter: PythonInterpreter,
  docsDir: Path,
  showConsole: suspend (ProcessHandler) -> Unit,
): PyResult<Int> {
  val binary = interpreter.asBinToExecute().getOr { return it }
  val outputDir = try {
    withContext(Dispatchers.IO) { Files.createTempDirectory("sphinx-quickstart") }
  }
  catch (e: IOException) {
    return Result.localizedError(PythonRestBundle.message("python.rest.sphinx.quickstart.temp.dir.failed", e.toString()))
  }
  try {
    val args = Args("-m", "sphinx.cmd.quickstart").addLocalDir(outputDir) {
      reportDir { it to HowToReportFile.AnArgument }
      downloadAfterExecution()
    }
    val env = mapOf(PYTHONIOENCODING to Charsets.UTF_8.name(), PythonEnvUtil.PYTHONUNBUFFERED to "1").toEnvVars()
    val options = ExecOptions(env = env, timeout = Duration.INFINITE)
    var console: ProcessHandler? = null
    val result = ExecService().executeAdvanced(binary, args, options, ProcessInteractiveHandler { _, _, process ->
      val handler = KillableProcessHandler(process, "sphinx-quickstart", Charsets.UTF_8)
      console = handler
      ProcessTerminatedListener.attach(handler)
      withContext(Dispatchers.EDT) { showConsole(handler) }
      Result.success(process.awaitExit())
    })
    val exitCode = result.getOr { return it }
    withContext(Dispatchers.IO) { console?.waitFor(CONSOLE_DRAIN_TIMEOUT_MS) }
    if (exitCode != 0) {
      val report = PythonRestBundle.message("python.rest.sphinx.quickstart.files.not.copied", docsDir) + "\n"
      console?.notifyTextAvailable(report, ProcessOutputTypes.SYSTEM)
      return result
    }
    val kept = try {
      withContext(Dispatchers.IO) { copyNewFiles(outputDir, docsDir) }
    }
    catch (e: IOException) {
      return Result.localizedError(PythonRestBundle.message("python.rest.sphinx.quickstart.copy.failed", docsDir, e.toString()))
    }
    catch (e: UncheckedIOException) {
      return Result.localizedError(PythonRestBundle.message("python.rest.sphinx.quickstart.copy.failed", docsDir, e.cause.toString()))
    }
    var report = PythonRestBundle.message("python.rest.sphinx.quickstart.files.copied", docsDir) + "\n"
    if (kept.isNotEmpty()) {
      report += PythonRestBundle.message("python.rest.sphinx.quickstart.files.kept", kept.joinToString()) + "\n"
    }
    val configuration = withContext(Dispatchers.IO) { findSphinxConfiguration(docsDir) }
    if (configuration != null) {
      report += PythonRestBundle.message("python.rest.sphinx.quickstart.rerun.hint", configuration) + "\n"
    }
    console?.notifyTextAvailable(report, ProcessOutputTypes.SYSTEM)
    return result
  }
  finally {
    withContext(NonCancellable + Dispatchers.IO) {
      try {
        outputDir.deleteRecursively()
      }
      catch (e: IOException) {
        logger.warn("Cannot delete the temporary quickstart directory $outputDir", e)
      }
    }
  }
}

/**
 * Copies each file of [from] into the same place under [to], unless [to] has that file already.
 * Returns the relative paths of the files that [to] had already.
 */
private fun copyNewFiles(from: Path, to: Path): List<String> {
  val kept = mutableListOf<String>()
  Files.walk(from).use { paths ->
    paths.forEach { source ->
      val relativePath = from.relativize(source).toString()
      val target = to.resolve(relativePath)
      if (source.isDirectory()) target.createDirectories()
      else if (target.exists()) kept.add(relativePath)
      else source.copyTo(target)
    }
  }
  return kept
}

private val logger = fileLogger()

private const val CONSOLE_DRAIN_TIMEOUT_MS = 5_000L

/**
 * Runs [runSphinxQuickstart] of [command] in a coroutine of the project, and returns at once.
 *
 * The coroutine finds the Python project of [file], or the main Python project when [file] is `null`. It waits for the
 * first snapshot, so a project with an interpreter never reads as one without. A project without an interpreter gets an
 * error, and no work directory dialog. `ReSTService` is a module service, so the work directory comes from the module
 * of the Python project. A documentation directory that has a `conf.py` already gets an error too, as in Sphinx.
 *
 * A cancellable background progress shows while the coroutine finds the Python project. A cancel there ends the
 * coroutine with no message. A second cancellable progress shows until the process starts. A cancel there stops the
 * start of the process. When the run ends, the coroutine refreshes the documentation directory. Each error goes to
 * [ErrorSink].
 *
 * A new run cancels the active run and waits for it, so at most one quickstart of the project runs at a time. A
 * cancellable progress with the same title shows during the wait. The rerun button of a console starts a new run.
 */
internal fun launchSphinxQuickstart(command: SphinxBaseCommand, project: Project, file: VirtualFile?) {
  val quickstartScope = project.service<SphinxQuickstartScope>()
  quickstartScope.scope.launch {
    val progressTitle = PythonRestBundle.message("python.rest.sphinx.quickstart.starting")
    val evoPyProject = withBackgroundProgress(project, progressTitle, TaskCancellation.cancellable()) {
      if (file == null) project.findMainEvoPyProject() else project.findEvoPyProject(file)
    }
    val interpreter = evoPyProject?.interpreter
    if (interpreter == null) {
      ErrorSink().emit(MessageError(PythonRestBundle.message("python.rest.no.sdk.specified")), project)
      return@launch
    }
    val module = evoPyProject.pyProject.residesOnModule
    val docsDir = withContext(Dispatchers.EDT) { command.askForDocsDir(module) } ?: return@launch
    val thisRun = currentCoroutineContext().job
    withBackgroundProgress(project, progressTitle, TaskCancellation.cancellable()) {
      quickstartScope.replaceActiveRun(thisRun)
    }
    if (withContext(Dispatchers.IO) { findSphinxConfiguration(docsDir) } != null) {
      ErrorSink().emit(MessageError(PythonRestBundle.message("python.rest.sphinx.quickstart.configuration.exists", docsDir)), project)
      return@launch
    }

    val started = CompletableDeferred<Unit>()
    launch {
      try {
        val result = runSphinxQuickstart(interpreter, docsDir) { handler ->
          started.complete(Unit)
          command.showConsole(project, handler) { launchSphinxQuickstart(command, project, file) }
        }
        if (result is Result.Failure) {
          ErrorSink().emit(result.error, project)
        }
      }
      finally {
        started.complete(Unit)
        VfsUtil.markDirtyAndRefresh(true, true, true, docsDir)
      }
    }
    withBackgroundProgress(project, progressTitle, TaskCancellation.cancellable()) {
      started.await()
    }
  }
}

/**
 * Returns the `conf.py` of [docsDir], or `null` when it has none. Sphinx keeps its configuration in `conf.py`, or in
 * `source/conf.py` when the sources and the build are apart.
 */
private fun findSphinxConfiguration(docsDir: Path): Path? =
  listOf(docsDir.resolve("conf.py"), docsDir.resolve("source").resolve("conf.py")).firstOrNull { it.exists() }

/** The scope of the coroutines of [launchSphinxQuickstart]. The project cancels it when it closes. */
@Service(Service.Level.PROJECT)
internal class SphinxQuickstartScope(val scope: CoroutineScope) {
  private val mutex = Mutex()
  private var activeRun: Job? = null

  /** Cancels the active run, waits for it, and makes [run] the active run. */
  suspend fun replaceActiveRun(run: Job) {
    mutex.withLock {
      activeRun?.cancelAndJoin()
      activeRun = run
    }
  }
}
