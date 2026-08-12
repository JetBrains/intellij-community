// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("CompanionObjectInExtension")

package com.jetbrains.performancePlugin

import com.intellij.diagnostic.MessagePool
import com.intellij.diagnostic.ThreadDumper
import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.ApplicationInitializedListener
import com.intellij.ide.lightEdit.LightEditService
import com.intellij.ide.lightEdit.LightEditorInfo
import com.intellij.ide.lightEdit.LightEditorListener
import com.intellij.idea.AppMode
import com.intellij.internal.performanceTests.ProjectInitializationDiagnostic
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.progress.impl.CoreProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.DumbService.Companion.isDumb
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.InitProjectActivity
import com.intellij.openapi.util.Pair
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.ex.StatusBarEx
import com.intellij.openapi.wm.ex.WelcomeScreenProjectProvider
import com.intellij.platform.diagnostic.startUpPerformanceReporter.StartUpPerformanceReporter.Companion.logStats
import com.intellij.platform.eel.EelUnavailableException
import com.intellij.platform.eel.provider.EelInitialization
import com.intellij.platform.eel.provider.EelNioFsBackend
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.tools.ide.starter.bus.EventsBus
import com.intellij.util.Alarm
import com.intellij.util.SystemProperties
import com.jetbrains.performancePlugin.commands.CodeAnalysisStateListener
import com.jetbrains.performancePlugin.commands.OpenProjectCommand.Companion.shouldOpenInSmartMode
import com.jetbrains.performancePlugin.commands.takeFullScreenshot
import com.jetbrains.performancePlugin.commands.takeScreenshotOfAllWindows
import com.jetbrains.performancePlugin.events.StopProfilerEvent
import com.jetbrains.performancePlugin.jmxDriver.InvokerService
import com.jetbrains.performancePlugin.profilers.Profiler.Companion.getCurrentProfilerHandler
import com.jetbrains.performancePlugin.profilers.ProfilersController
import com.jetbrains.performancePlugin.utils.ReporterCommandAsTelemetrySpan
import io.opentelemetry.context.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal
import java.io.IOException
import java.net.ConnectException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

private val LOG: Logger
  get() = Logger.getInstance("PerformancePlugin")

private fun getTestFile(): Path {
  val file = Path.of(ProjectLoaded.TEST_SCRIPT_FILE_PATH!!)
  if (!Files.isRegularFile(file)) {
    System.err.println(PerformanceTestingBundle.message("startup.noscript", file.toAbsolutePath().toString()))
    ApplicationManagerEx.getApplicationEx().exit(true, true, 1)
  }
  return file
}

private val PROJECT_CONTENTS_WARMUP_POLL = 500.milliseconds

/**
 * How many consecutive polls a readable but *empty* project directory has to be observed before it counts as ready.
 * The mount is demonstrably serving at that point, so a legitimately empty directory must not burn the whole timeout.
 */
private const val PROJECT_CONTENTS_WARMUP_EMPTY_CONFIRMATIONS = 3

/** Outcome of a host-side `readdir` of the project directory. */
private enum class ProjectDirState {
  /** The listing threw: typically an EEL/IJent transport that is dead or not serving yet. */
  UNREADABLE,

  /** The listing succeeded, but the path is absent or is not a directory: typically a mount that delivers nothing. */
  MISSING,

  EMPTY,
  NON_EMPTY,
}

private fun probeProjectDir(path: Path): ProjectDirState {
  // Files.isDirectory() swallows IOException and answers `false`, which reports a dead transport as a missing
  // directory and hides the real failure. Read the attributes directly, so a transport failure propagates to the
  // caller and is logged as UNREADABLE with a `lastFailure=` field.
  val attributes = try {
    Files.readAttributes(path, BasicFileAttributes::class.java)
  }
  catch (_: java.nio.file.NoSuchFileException) {
    return ProjectDirState.MISSING
  }
  if (!attributes.isDirectory) return ProjectDirState.MISSING
  return Files.newDirectoryStream(path).use {
    if (it.iterator().hasNext()) ProjectDirState.NON_EMPTY else ProjectDirState.EMPTY
  }
}

/**
 * Wait until [projectPath]'s contents are actually readable over the (possibly remote) EEL/IJent
 * filesystem, so that the command-line project-open detection that runs right after does not race a
 * still-mounting `\\wsl.localhost\...` (WSL) or container (Docker) FS. When detection loses that race
 * it finds no project markers, falls back to a LightEdit frame, and the script then either throws
 * `ReadConstraint.inSmartMode() ... LightEdit mode` on `%waitForSmart` or parks on `%importGradleProject`
 * until the external timeout (IJPL-250122).
 *
 * [EelInitialization.runEelInitialization] only registers the MRFS backend; it does not guarantee the
 * mount is serving directory contents. This forces + awaits a real `readdir` so detection wins the race.
 * Must run *before* [AppMode.setFlags], which is what derives `isLightEdit` from `Files.exists`.
 * Only invoked under the `STARTER_TESTS_SUPPORT_TARGETS` integration-test flag. Bounded by
 * `performance.eel.project.markers.warmup.timeout.ms` (default 120s); on timeout or error it logs and
 * returns so normal open behavior still applies. On a correctly provisioned target the directory is
 * visible on the first attempt, i.e. the overhead is a single `readdir`.
 *
 * Readiness is decided by a host-side listing, because that is exactly what the platform reads. A
 * non-empty listing is the ideal signal; a readable but empty directory is accepted after
 * [PROJECT_CONTENTS_WARMUP_EMPTY_CONFIRMATIONS] polls, since the mount is then provably serving and
 * waiting longer would only delay an outcome that will not change.
 */
private suspend fun awaitProjectContentsVisible(projectPath: Path) {
  val timeoutMs = SystemProperties.getIntProperty("performance.eel.project.markers.warmup.timeout.ms", 120_000).toLong()
  LOG.info("Project contents warmup [IJPL-250122]: path='$projectPath'" +
           ", routedToEelBackend=${EelNioFsBackend.instance?.resolveDescriptor(projectPath) != null}" +
           ", fs=${projectPath.fileSystem.javaClass.name}, timeoutMs=$timeoutMs")

  val start = System.currentTimeMillis()
  var attempt = 0
  var emptyInARow = 0
  var lastFailure: String? = null
  while (true) {
    attempt++
    val state = withContext(Dispatchers.IO) {
      try {
        probeProjectDir(projectPath)
      }
      catch (t: Throwable) {
        lastFailure = "attempt $attempt: $t"
        ProjectDirState.UNREADABLE
      }
    }
    emptyInARow = if (state == ProjectDirState.EMPTY) emptyInARow + 1 else 0
    val elapsed = System.currentTimeMillis() - start

    if (state == ProjectDirState.NON_EMPTY || emptyInARow >= PROJECT_CONTENTS_WARMUP_EMPTY_CONFIRMATIONS) {
      LOG.info("Project contents warmup [IJPL-250122] result=READY ($state) after $attempt attempt(s) / $elapsed ms" +
               "; path='$projectPath'; command-line project detection can win the race")
      return
    }

    if (elapsed >= timeoutMs) {
      LOG.warn("Project contents warmup [IJPL-250122] result=TIMEOUT after $attempt attempt(s) / $elapsed ms (limit $timeoutMs ms)" +
               "; path='$projectPath'; lastState=$state" +
               (lastFailure?.let { "; lastFailure=$it" } ?: "") +
               (if (state == ProjectDirState.MISSING)
                 "; the target never provided this directory - the project was not provisioned into the target" +
                 " (mount or copy), so raising the timeout will not help"
               else "") +
               "; project open may still fall back to LightEdit")
      return
    }
    delay(PROJECT_CONTENTS_WARMUP_POLL)
  }
}

private object ProjectLoadedService {
  @JvmField
  var scriptStarted = false

  @JvmField
  val screenshotJobs: MutableSet<kotlinx.coroutines.Job> = ConcurrentHashMap.newKeySet()

  fun registerScreenshotTaking(folder: String, coroutineScope: CoroutineScope) {
    val job = coroutineScope.launch {
      while (true) {
        delay(1.minutes)
        takeScreenshotOfAllWindows(folder)
      }
    }
    screenshotJobs += job
    job.invokeOnCompletion {
      screenshotJobs -= job
    }
  }
}

private fun subscribeToStopProfile() {
  try {
    EventsBus.subscribe("ProfileStopSubscriber") { event: StopProfilerEvent ->
      try {
        getCurrentProfilerHandler().stopProfiling(event.data)
      }
      catch (t: Throwable) {
        LOG.info("Error stop profiling", t)
      }
    }
  }
  catch (connectException: ConnectException) {
    // Some integration tests don't start event bus server. e.g com.jetbrains.rdct.cwm.distributed.connectionTypes.LocalRelayTest
    LOG.info("Subscription to stop profiling failed", connectException)
  }
}

private fun runOnProjectInit(project: Project) {
  if (System.getProperty("ide.performance.screenshot") != null) {
    val coroutineScope = project.service<CoreUiCoroutineScopeHolder>().coroutineScope
    (ProjectLoadedService.registerScreenshotTaking(System.getProperty("ide.performance.screenshot"), coroutineScope))
    LOG.info("Option ide.performance.screenshot is initialized, screenshots will be captured")
  }

  if (System.getProperty("ide.performance.run.on.welcome.screen.project") == null
     && WelcomeScreenProjectProvider.isWelcomeScreenProject(project)) {
    LOG.info("Option ide.performance.run.on.welcome.screen.project is not initialized, script will not be executed on welcome screen")
    return
  }

  if (ProjectLoaded.TEST_SCRIPT_FILE_PATH == null || ProjectLoadedService.scriptStarted) {
    if (!ApplicationManager.getApplication().isUnitTestMode) {
      LOG.info(PerformanceTestingBundle.message("startup.silent"))
    }
    return
  }

  ProjectLoadedService.scriptStarted = true

  LOG.info("Start Execution")
  PerformanceTestSpan.startSpan()

  if (ApplicationManagerEx.isInIntegrationTest()) {
    project.service<CoreUiCoroutineScopeHolder>().coroutineScope.launch {
      subscribeToStopProfile()
    }
  }

  val profilerSettings = initializeProfilerSettingsForIndexing()
  if (profilerSettings != null) {
    try {
      ProfilersController.getInstance().currentProfilerHandler.startProfiling(profilerSettings.first, profilerSettings.second)
    }
    catch (e: Exception) {
      System.err.println("Start profile failed: ${e.message}")
      ApplicationManagerEx.getApplicationEx().exit(true, true, 1)
    }
  }

  fun createAlarm(): Alarm = Alarm(project.service<CodeAnalysisStateListener>().cs, Alarm.ThreadToUse.SWING_THREAD)

  if (shouldOpenInSmartMode(project)) {
    runScriptWhenInitializedAndIndexed(project, createAlarm())
  }
  else if (SystemProperties.getBooleanProperty("performance.execute.script.after.scanning", false)) {
    runScriptDuringIndexing(project, createAlarm())
  }
  else {
    runScriptFromFile(project)
  }
}

internal class PerformancePluginInitProjectActivity : InitProjectActivity {
  override val isParallelExecution: Boolean
    get() = true

  override suspend fun run(project: Project) {
    runOnProjectInit(project)
  }
}

private const val TIMEOUT = 500

private fun runScriptWhenInitializedAndIndexed(project: Project, alarm: Alarm) {
  DumbService.getInstance(project).smartInvokeLater(Context.current().wrap(
    Runnable {
      alarm.addRequest(Context.current().wrap(
        Runnable {
          val statusBar = WindowManager.getInstance().getIdeFrame(project)?.statusBar as? StatusBarEx
          val hasUserVisibleIndicators = statusBar != null && statusBar.backgroundProcessModels.isNotEmpty()
          if (isDumb(project) || hasUserVisibleIndicators ||
              !ProjectInitializationDiagnostic.isProjectInitializationAndIndexingFinished(project)) {
            runScriptWhenInitializedAndIndexed(project, alarm)
          }
          else {
            runScriptFromFile(project)
          }
        }), TIMEOUT)
    }))
}

private fun runScriptDuringIndexing(project: Project, alarm: Alarm) {
  ApplicationManager.getApplication().executeOnPooledThread(Context.current().wrap(
    Runnable {
      alarm.addRequest(Context.current().wrap(Runnable {
        val indicators = CoreProgressManager.getCurrentIndicators()
        var indexingInProgress = false
        for (indicator in indicators) {
          val indicatorText = indicator.text
          @Suppress("HardCodedStringLiteral")
          if (indicatorText != null && indicatorText.contains("Analyzing")) {
            indexingInProgress = true
            break
          }
        }
        if (indexingInProgress) {
          runScriptFromFile(project)
        }
        else {
          runScriptDuringIndexing(project, alarm)
        }
      }), TIMEOUT)
    }))
}

@Internal
class ProjectLoaded : ApplicationInitializedListener {
  override suspend fun execute() {
    // TODO: Under flag since a proper solution should be implemented in the platform later
    if (SystemProperties.getBooleanProperty("STARTER_TESTS_SUPPORT_TARGETS", false)
        || System.getenv("STARTER_TESTS_SUPPORT_TARGETS").toBoolean()) {
      IntegrationTestApplicationLoadListener.data?.let {
        val projectPath = Path.of(it.projectPath)
        try {
          EelInitialization.runEelInitialization(projectPath.getEelDescriptor())
        }
        catch (e: EelUnavailableException) {
          // Report it here rather than letting it escape from ApplicationInitializedListener.execute(): without a
          // working backend the warmup below cannot succeed either, and the run ends up in LightEdit anyway, so a
          // named error is far easier to triage than a failed application listener.
          LOG.error(e)
        }
        // Warm the EEL/IJent remote FS mount BEFORE re-evaluating the flags, so detection does not race a
        // still-mounting WSL/Docker FS and fall back to a LightEdit frame (IJPL-250122). setFlags is what
        // derives isLightEdit from mayHappenToBeAFile -> Files.exists, so warming up after it has no effect
        // on the decision it already made.
        awaitProjectContentsVisible(projectPath)
        // Re-evaluate AppMode flags: the first setFlags call in Main.kt runs before EPs are loaded,
        // so MultiRoutingFileSystemBackend (Docker/WSL) isn't registered yet and mayHappenToBeAFile
        // can't resolve remote paths, incorrectly setting isLightEdit=true.
        AppMode.setFlags(it.args)
      }
    }

    if (System.getProperty("com.sun.management.jmxremote") == "true") {
      serviceAsync<InvokerService>().register({ PerformanceTestSpan.TRACER },
                                               { PerformanceTestSpan.getContext() },
                                               { takeFullScreenshot(it) })
    }
    if (AppMode.isLightEdit()) {
      serviceAsync<LightEditService>().editorManager.addListener(object : LightEditorListener {
        override fun afterSelect(editorInfo: LightEditorInfo?) {
          runWithModalProgressBlocking(ModalTaskOwner.guess(), "") {
            logStats("LightEditor")
          }
          runOnProjectInit(LightEditService.getInstance().project!!)
        }
      })
    }
    if (ApplicationManagerEx.isInIntegrationTest() && AppMode.isHeadless() && AppMode.isCommandLine()) {
      MessagePool.getInstance().addAdvisor(toErrorDirReporter)
      sweepExistingErrors()
      LOG.info("Error watcher has started in headless mode")
    }
  }

  internal class MyAppLifecycleListener : AppLifecycleListener {
    init {
      if (TEST_SCRIPT_FILE_PATH == null) {
        throw ExtensionNotApplicableException.create()
      }
    }

    override fun appFrameCreated(commandLineArgs: List<String>) {
      val messagePool = MessagePool.getInstance()
      LOG.info("Error watcher has started")
      messagePool.addAdvisor(toErrorDirReporter)
      @Suppress("RAW_RUN_BLOCKING")
      runBlocking { sweepExistingErrors() }
    }

    override fun appClosing() {
      ProjectLoadedService.screenshotJobs.forEach { it.cancel() }
      PerformanceTestSpan.endSpan()
    }
  }

  companion object {
    @JvmField
    val TEST_SCRIPT_FILE_PATH: String? = System.getProperty("testscript.filename")
  }
}

internal fun runPerformanceScript(project: Project?, script: String?, mustExitOnFailure: Boolean) {
  val playback = PlaybackRunnerExtended(script, CommandLogger(), project!!)
  val scriptCallback = playback.run()
  CommandsRunner.setActionCallback(scriptCallback)
  registerOnFinishRunnables(scriptCallback, mustExitOnFailure)
}

private const val INDEXING_PROFILER_PREFIX = "%%profileIndexing"

private fun initializeProfilerSettingsForIndexing(): Pair<String, List<String>>? {
  try {
    val lines = Files.readAllLines(getTestFile())
    for (line in lines) {
      if (line.startsWith(INDEXING_PROFILER_PREFIX)) {
        val command = line.substring(INDEXING_PROFILER_PREFIX.length).trim().split("\\s+".toRegex(), limit = 2)
        val indexingActivity = command[0]
        val profilingParameters = if (command.size > 1) {
          command[1].trim().split(',').dropLastWhile { it.isEmpty() }
        }
        else {
          ArrayList()
        }
        return Pair(indexingActivity, profilingParameters)
      }
    }
  }
  catch (_: IOException) {
    System.err.println(PerformanceTestingBundle.message("startup.script.read.error"))
    ApplicationManagerEx.getApplicationEx().exit(true, true, 1)
  }
  return null
}

private fun runScriptFromFile(project: Project) {
  val playback = PlaybackRunnerExtended("%include " + getTestFile(), CommandLogger(), project)
  playback.scriptDir = getTestFile().parent.toFile()
  if (SystemProperties.getBooleanProperty(ReporterCommandAsTelemetrySpan.USE_SPAN_WRAPPER_FOR_COMMAND, false)) {
    playback.setCommandStartStopProcessor(ReporterCommandAsTelemetrySpan())
  }
  val scriptCallback = playback.run()
  CommandsRunner.setActionCallback(scriptCallback)
  registerOnFinishRunnables(future = scriptCallback, mustExitOnFailure = true)
}

@Suppress("RAW_RUN_BLOCKING")
private fun registerOnFinishRunnables(future: CompletableFuture<*>, mustExitOnFailure: Boolean) {
  future
    .thenRun { LOG.info("Execution of the script has been finished successfully") }
    .exceptionally(Function { e ->
      ApplicationManager.getApplication().executeOnPooledThread {
        if (ApplicationManagerEx.isInIntegrationTest()) {
          storeFailureToFile(e)
        }
        runBlocking {
          takeScreenshotOfAllWindows("onFailure")
        }
        val threadDump = """
            Thread dump before IDE termination:
            ${ThreadDumper.dumpThreadsToString()}
            """.trimIndent()
        LOG.info(threadDump)
        if (mustExitOnFailure) {
          ApplicationManagerEx.getApplicationEx().exit(true, true, 1)
        }
      }
      null
    })
}

/**
 * Starter framework reads the file failure_cause.txt to fail the test if a command failed.
 */
private fun storeFailureToFile(errorMessage: Throwable) {
  try {
    val failureCauseFile = LogDirHandler.currentLogDir().resolve("failure_cause.txt")
    Files.writeString(failureCauseFile, errorMessage.message + "\n" + errorMessage.stackTraceToString())
  }
  catch (e: Exception) {
    LOG.error(e.message)
  }
}
