@file:Suppress("CompanionObjectInExtension")

package com.jetbrains.performancePlugin

import com.intellij.diagnostic.AbstractMessage
import com.intellij.diagnostic.MessagePool
import com.intellij.diagnostic.ThreadDumper
import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.ApplicationInitializedListener
import com.intellij.ide.lightEdit.LightEditService
import com.intellij.ide.lightEdit.LightEditorInfo
import com.intellij.ide.lightEdit.LightEditorListener
import com.intellij.idea.AppMode
import com.intellij.idea.LoggerFactory
import com.intellij.internal.performanceTests.ProjectInitializationDiagnosticService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.progress.impl.CoreProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.DumbService.Companion.isDumb
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.InitProjectActivity
import com.intellij.openapi.util.Pair
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.ex.StatusBarEx
import com.intellij.platform.diagnostic.startUpPerformanceReporter.StartUpPerformanceReporter.Companion.logStats
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.ApiStatus.Internal
import java.io.IOException
import java.net.ConnectException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.sql.Timestamp
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.function.Function
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

private object ProjectLoadedService {

  @JvmField
  var scriptStarted = false

  @JvmField
  var screenshotJob: kotlinx.coroutines.Job? = null

  fun registerScreenshotTaking(folder: String, coroutineScope: CoroutineScope) {
    screenshotJob = coroutineScope.launch {
      while (true) {
        delay(1.minutes)
        takeScreenshotOfAllWindows(folder)
      }
    }
  }
}

private fun subscribeToStopProfile() {
  if (ApplicationManagerEx.isInIntegrationTest()) {
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
}

private fun runOnProjectInit(project: Project) {
  if (System.getProperty("ide.performance.screenshot") != null) {
    (ProjectLoadedService.registerScreenshotTaking(System.getProperty("ide.performance.screenshot"),
                                                   (project as ComponentManagerEx).getCoroutineScope()))
    LOG.info("Option ide.performance.screenshot is initialized, screenshots will be captured")
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

  subscribeToStopProfile()

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

private class PerformancePluginInitProjectActivity : InitProjectActivity {
  override suspend fun run(project: Project) {
    blockingContext {
      runOnProjectInit(project)
    }
  }
}

private const val TIMEOUT = 500

private fun runScriptWhenInitializedAndIndexed(project: Project, alarm: Alarm) {
  DumbService.getInstance(project).smartInvokeLater(Context.current().wrap(
    Runnable {
      alarm.addRequest(Context.current().wrap(
        Runnable {
          val statusBar = WindowManager.getInstance().getIdeFrame(project)?.statusBar as? StatusBarEx
          val hasUserVisibleIndicators = statusBar != null && statusBar.backgroundProcesses.isNotEmpty()
          if (isDumb(project) || hasUserVisibleIndicators ||
              !ProjectInitializationDiagnosticService.getInstance(project).isProjectInitializationAndIndexingFinished) {
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
          if (indicatorText != null && indicatorText.contains("Indexing")) {
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

@Suppress("SpellCheckingInspection")
@Internal
class ProjectLoaded : ApplicationInitializedListener {
  override suspend fun execute() {
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
      MessagePool.getInstance().addListener { reportErrorsFromMessagePool() }
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
      messagePool.addListener { reportErrorsFromMessagePool() }
    }

    override fun appClosing() {
      ProjectLoadedService.screenshotJob?.cancel()
      PerformanceTestSpan.endSpan()
      reportErrorsFromMessagePool()
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

internal fun generifyErrorMessage(originalMessage: String): String {
  return originalMessage // text@3ba5aac, text => text<ID>, text
    .replace("[$@#][A-Za-z0-9-_]+".toRegex(), "<ID>") // java-design-patterns-master.db451f59 => java-design-patterns-master.<HASH>
    .replace("[.]([A-Za-z]+[0-9]|[0-9]+[A-Za-z])[A-Za-z0-9]*".toRegex(), ".<HASH>") // 0x01 => <HEX>
    .replace("0x[0-9a-fA-F]+".toRegex(), "<HEX>") // text1234text => text<NUM>text
    .replace("[0-9]+".toRegex(), "<NUM>")
}

fun reportErrorsFromMessagePool() {
  val messagePool = MessagePool.getInstance()
  val ideErrors = messagePool.getFatalErrors(false, true)
  for (message in ideErrors) {
    try {
      reportScriptError(message)
    }
    catch (e: IOException) {
      LOG.error(e)
    }
    finally {
      message.isRead = true
    }
  }
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

@Throws(IOException::class)
private fun reportScriptError(errorMessage: AbstractMessage) {
  val throwable = errorMessage.throwable
  var cause: Throwable? = throwable
  var causeMessage: String? = ""
  while (cause!!.cause != null) {
    cause = cause.cause
    causeMessage = cause!!.message
  }
  if (causeMessage.isNullOrEmpty()) {
    causeMessage = errorMessage.message
    if (causeMessage.isNullOrEmpty()) {
      val throwableMessage = getNonEmptyThrowableMessage(throwable)
      val index = throwableMessage.indexOf("\tat ")
      causeMessage = if (index == -1) throwableMessage else throwableMessage.substring(0, index)
    }
  }
  val scriptErrorsDir = Path.of(PathManager.getLogPath(), "errors")
  Files.createDirectories(scriptErrorsDir)
  Files.walk(scriptErrorsDir).use { stream ->
    val finalCauseMessage = causeMessage
    val isDuplicated = stream
      .filter { path -> path.fileName.toString() == "message.txt" }
      .anyMatch { path ->
        try {
          return@anyMatch Files.readString(path) == finalCauseMessage
        }
        catch (e: IOException) {
          LOG.error(e.message)
          return@anyMatch false
        }
      }
    if (isDuplicated) {
      return
    }
  }

  for (i in 1..999) {
    val errorDir = scriptErrorsDir.resolve("error-$i")
    if (Files.exists(errorDir)) {
      continue
    }

    Files.createDirectories(errorDir)
    Files.writeString(errorDir.resolve("message.txt"), causeMessage)
    Files.writeString(errorDir.resolve("stacktrace.txt"), errorMessage.throwableText)
    val attachments = errorMessage.allAttachments
    for (j in attachments.indices) {
      val attachment = attachments[j]
      writeAttachmentToErrorDir(attachment, errorDir.resolve("$j-${attachment.name}"))
    }
    return
  }

  LOG.error("Too many errors have been reported during script execution. See $scriptErrorsDir")
}

private fun writeAttachmentToErrorDir(attachment: Attachment, path: Path) {
  try {
    Files.writeString(path, attachment.displayText, StandardOpenOption.APPEND, StandardOpenOption.CREATE)
    Files.writeString(path, System.lineSeparator(), StandardOpenOption.APPEND, StandardOpenOption.CREATE)
  }
  catch (e: Exception) {
    LOG.warn("Failed to write attachment `display text`", e)
  }
}

private fun getNonEmptyThrowableMessage(throwable: Throwable): String {
  if (throwable.message != null && !throwable.message!!.isEmpty()) {
    return throwable.message!!
  }
  else {
    return throwable.javaClass.name
  }
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
          storeFailureToFile(e.message)
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

private fun storeFailureToFile(errorMessage: String?) {
  //TODO: if errorMessage = null -> very unclear message about 'String.codec()' is printed
  try {
    val logDir = Path.of(PathManager.getLogPath())
    val ideaLogContent = Files.readString(logDir.resolve(LoggerFactory.LOG_FILE_NAME))
    val substringBegin = ideaLogContent.substring(ideaLogContent.indexOf(errorMessage!!))
    val timestamp = Timestamp(System.currentTimeMillis())
    val date = timestamp.toString().substring(0, 10)
    val endIndex = substringBegin.indexOf(date)
    val errorMessageFromLog = if (endIndex == -1) substringBegin else substringBegin.substring(0, endIndex)
    val failureCause = logDir.resolve("failure_cause.txt")
    Files.writeString(failureCause, errorMessageFromLog)
  }
  catch (e: Exception) {
    LOG.error(e.message)
  }
}
