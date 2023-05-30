@file:Suppress("CompanionObjectInExtension")

package com.jetbrains.performancePlugin

import com.intellij.diagnostic.AbstractMessage
import com.intellij.diagnostic.MessagePool
import com.intellij.diagnostic.ThreadDumper
import com.intellij.diagnostic.startUpPerformanceReporter.StartUpPerformanceReporter.Companion.logStats
import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.ApplicationInitializedListener
import com.intellij.ide.lightEdit.LightEditService
import com.intellij.ide.lightEdit.LightEditorInfo
import com.intellij.ide.lightEdit.LightEditorListener
import com.intellij.idea.LoggerFactory
import com.intellij.internal.performanceTests.ProjectInitializationDiagnosticService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.progress.impl.CoreProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.DumbService.Companion.isDumb
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.InitProjectActivityJavaShim
import com.intellij.openapi.ui.playback.PlaybackRunner
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.util.Alarm
import com.intellij.util.SystemProperties
import com.jetbrains.performancePlugin.commands.OpenProjectCommand.Companion.shouldOpenInSmartMode
import com.jetbrains.performancePlugin.commands.takeScreenshotOfFrame
import com.jetbrains.performancePlugin.profilers.ProfilersController
import com.jetbrains.performancePlugin.utils.ReporterCommandAsTelemetrySpan
import io.opentelemetry.context.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.sql.Timestamp
import java.util.*
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.function.Function
import kotlin.math.min
import kotlin.time.Duration.Companion.minutes

private const val MAX_DESCRIPTION_LENGTH = 7500
private val LOG: Logger
  get() = Logger.getInstance("PerformancePlugin")

/**
 * If an IDE error occurs and this flag is true, a failure of a TeamCity test will be printed
 * to stdout using TeamCity Service Messages (see [.reportTeamCityFailedTestAndBuildProblem]).
 * The name of the failed test will be inferred from the name of the script file
 * (its name without an extension, see [.getTeamCityFailedTestName]).
 */
@Suppress("SpellCheckingInspection")
private val MUST_REPORT_TEAMCITY_TEST_FAILURE_ON_IDE_ERROR =
  System.getProperty("testscript.must.report.teamcity.test.failure.on.error", "true").toBoolean()

private val teamCityFailedTestName: String
  get() = FileUtilRt.getNameWithoutExtension(getTestFile().name)

private fun getTestFile(): File {
  val file = File(ProjectLoaded.TEST_SCRIPT_FILE_PATH!!)
  if (!file.isFile) {
    System.err.println(PerformanceTestingBundle.message("startup.noscript", file.absolutePath))
    ApplicationManagerEx.getApplicationEx().exit(true, true, 1)
  }
  return file
}

@Suppress("SpellCheckingInspection")
class ProjectLoaded : InitProjectActivityJavaShim(), ApplicationInitializedListener {
  private val alarm = Alarm()

  override fun runActivity(project: Project) {
    if (TEST_SCRIPT_FILE_PATH == null || ourScriptStarted) {
      if (!ApplicationManager.getApplication().isUnitTestMode) {
        LOG.info(PerformanceTestingBundle.message("startup.silent"))
      }
      return
    }

    ourScriptStarted = true
    if (System.getProperty("ide.performance.screenshot") != null) {
      @Suppress("DEPRECATION")
      registerScreenshotTaking(System.getProperty("ide.performance.screenshot"), project.coroutineScope)
    }

    LOG.info("Start Execution")
    PerformanceTestSpan.startSpan()
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
    if (shouldOpenInSmartMode(project)) {
      runScriptWhenInitializedAndIndexed(project)
    }
    else if (SystemProperties.getBooleanProperty("performance.execute.script.after.scanning", false)) {
      runScriptDuringIndexing(project)
    }
    else {
      runScriptFromFile(project)
    }
  }

  override suspend fun execute(asyncScope: CoroutineScope) {
    if (ApplicationManagerEx.getApplicationEx().isLightEditMode) {
      LightEditService.getInstance().editorManager.addListener(object : LightEditorListener {
        override fun afterSelect(editorInfo: LightEditorInfo?) {
          logStats("LightEditor")
          runActivity(LightEditService.getInstance().project!!)
        }
      })
    }
  }

  private fun runScriptWhenInitializedAndIndexed(project: Project) {
    DumbService.getInstance(project).smartInvokeLater(Context.current().wrap(
      Runnable {
        alarm.addRequest(Context.current().wrap(
          Runnable {
            if (isDumb(project) || !CoreProgressManager.getCurrentIndicators().isEmpty() ||
                !ProjectInitializationDiagnosticService.getInstance(project).isProjectInitializationAndIndexingFinished) {
              runScriptWhenInitializedAndIndexed(project)
            }
            else {
              runScriptFromFile(project)
            }
          }), TIMEOUT)
      }))
  }

  private fun runScriptDuringIndexing(project: Project) {
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
            runScriptDuringIndexing(project)
          }
        }), TIMEOUT)
      }))
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
      screenshotJob?.cancel()
      PerformanceTestSpan.endSpan()
      reportErrorsFromMessagePool()
    }
  }

  companion object {
    private const val TIMEOUT = 500
    @JvmField
    val TEST_SCRIPT_FILE_PATH: String? = System.getProperty("testscript.filename")

    private const val INDEXING_PROFILER_PREFIX = "%%profileIndexing"
    private var screenshotJob: kotlinx.coroutines.Job? = null
    private var ourScriptStarted = false

    private fun registerScreenshotTaking(fileName: String, coroutineScope: CoroutineScope) {
      screenshotJob = coroutineScope.launch {
        while (true) {
          delay(1.minutes)
          takeScreenshotOfFrame(fileName)
        }
      }
    }

    private fun initializeProfilerSettingsForIndexing(): Pair<String, List<String>>? {
      try {
        val lines = FileUtil.loadLines(getTestFile())
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
      catch (ignored: IOException) {
        System.err.println(PerformanceTestingBundle.message("startup.script.read.error"))
        ApplicationManagerEx.getApplicationEx().exit(true, true, 1)
      }
      return null
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

    internal fun generifyErrorMessage(originalMessage: String): String {
      return originalMessage // text@3ba5aac, text => text<ID>, text
        .replace("[$@#][A-Za-z0-9-_]+".toRegex(), "<ID>") // java-design-patterns-master.db451f59 => java-design-patterns-master.<HASH>
        .replace("[.]([A-Za-z]+[0-9]|[0-9]+[A-Za-z])[A-Za-z0-9]*".toRegex(), ".<HASH>") // 0x01 => <HEX>
        .replace("0x[0-9a-fA-F]+".toRegex(), "<HEX>") // text1234text => text<NUM>text
        .replace("[0-9]+".toRegex(), "<NUM>")
    }

    @JvmStatic
    fun runScript(project: Project?, script: String?, mustExitOnFailure: Boolean) {
      val playback: PlaybackRunner = PlaybackRunnerExtended(script, CommandLogger(), project!!)
      val scriptCallback = playback.run()
      val future = CompletableFuture<Any>()
      scriptCallback.doWhenDone { future.complete(null) }
      scriptCallback.doWhenRejected { s: String? ->
        if (s.isNullOrEmpty()) {
          future.cancel(false)
        }
        else {
          future.completeExceptionally(CancellationException(s))
        }
      }
      CommandsRunner.setActionCallback(future)
      registerOnFinishRunnables(future, mustExitOnFailure)
    }
  }
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
  val scriptErrorsDir = Path.of(PathManager.getLogPath(), "script-errors")
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
  playback.scriptDir = getTestFile().parentFile
  if (SystemProperties.getBooleanProperty(ReporterCommandAsTelemetrySpan.USE_SPAN_WRAPPER_FOR_COMMAND, false)) {
    playback.setCommandStartStopProcessor(ReporterCommandAsTelemetrySpan())
  }
  val scriptCallback = playback.run()

  val future = CompletableFuture<Any>()
  scriptCallback.doWhenDone { future.complete(null) }
  scriptCallback.doWhenRejected { s ->
    if (s.isNullOrEmpty()) {
      future.cancel(false)
    }
    else {
      future.completeExceptionally(CancellationException(s))
    }
  }
  CommandsRunner.setActionCallback(future)
  registerOnFinishRunnables(future = future, mustExitOnFailure = true)
}

private fun encodeStringForTC(line: String): String {
  return line.substring(0, min(MAX_DESCRIPTION_LENGTH.toDouble(), line.length.toDouble()).toInt())
    .replace("\\|", "||")
    .replace("\\[", "|[")
    .replace("]", "|]")
    .replace("\n", "|n")
    .replace("'", "|'")
    .replace("\r", "|r")
}

private fun reportTeamCityFailedTestAndBuildProblem(testName: String, failureMessage: String, @Suppress("SameParameterValue") failureDetails: String) {
  val generifiedTestName = ProjectLoaded.generifyErrorMessage(testName)
  System.out.printf("##teamcity[testFailed name='%s' message='%s' details='%s']\n",
                    encodeStringForTC(generifiedTestName),
                    encodeStringForTC(failureMessage),
                    encodeStringForTC(failureDetails))
  System.out.printf("##teamcity[buildProblem description='%s' identity='%s'] ",
                    encodeStringForTC(failureMessage),
                    encodeStringForTC(generifiedTestName))
}

@Suppress("RAW_RUN_BLOCKING")
private fun registerOnFinishRunnables(future: CompletableFuture<*>, mustExitOnFailure: Boolean) {
  future
    .thenRun { LOG.info("Execution of the script has been finished successfully") }
    .exceptionally(Function { e ->
      val message = "IDE will be terminated because some errors are detected while running the startup script: $e"
      if (MUST_REPORT_TEAMCITY_TEST_FAILURE_ON_IDE_ERROR) {
        val testName = teamCityFailedTestName
        reportTeamCityFailedTestAndBuildProblem(testName, message, "")
      }
      if (SystemProperties.getBooleanProperty("startup.performance.framework", false)) {
        storeFailureToFile(e.message)
      }
      LOG.error(message)
      if (System.getProperty("ide.performance.screenshot.on.failure") != null) {
        runBlocking {
          takeScreenshotOfFrame(System.getProperty("ide.performance.screenshot.on.failure"))
        }
      }
      val threadDump = """
            Thread dump before IDE termination:
            ${ThreadDumper.dumpThreadsToString()}
            """.trimIndent()
      LOG.info(threadDump)
      if (mustExitOnFailure) {
        ApplicationManagerEx.getApplicationEx().exit(true, true, 1)
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
