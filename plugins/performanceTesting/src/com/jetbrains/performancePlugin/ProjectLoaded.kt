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
import com.intellij.openapi.project.DumbService.Companion.getInstance
import com.intellij.openapi.project.DumbService.Companion.isDumb
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.InitProjectActivityJavaShim
import com.intellij.openapi.ui.playback.PlaybackRunner
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.util.Alarm
import com.intellij.util.ConcurrencyUtil
import com.intellij.util.SystemProperties
import com.jetbrains.performancePlugin.commands.OpenProjectCommand.Companion.shouldOpenInSmartMode
import com.jetbrains.performancePlugin.commands.takeScreenshotOfFrame
import com.jetbrains.performancePlugin.profilers.ProfilersController
import com.jetbrains.performancePlugin.utils.ReporterCommandAsTelemetrySpan
import io.opentelemetry.context.Context
import kotlinx.coroutines.CoroutineScope
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.sql.Timestamp
import java.util.*
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.function.Function
import kotlin.math.min

@Suppress("SpellCheckingInspection")
class ProjectLoaded : InitProjectActivityJavaShim(), ApplicationInitializedListener {
  private val myAlarm = Alarm()

  override fun runActivity(project: Project) {
    if (TEST_SCRIPT_FILE_PATH != null && !ourScriptStarted) {
      ourScriptStarted = true
      if (System.getProperty("ide.performance.screenshot") != null) {
        registerScreenshotTaking(System.getProperty("ide.performance.screenshot"))
      }
      LOG.info("Start Execution")
      PerformanceTestSpan.startSpan()
      val profilerSettings = initializeProfilerSettingsForIndexing()
      if (profilerSettings != null) {
        try {
          ProfilersController.getInstance().currentProfilerHandler
            .startProfiling(profilerSettings.first, profilerSettings.second)
        }
        catch (e: Exception) {
          System.err.println("Start profile failed: " + e.message)
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
    else {
      if (!ApplicationManager.getApplication().isUnitTestMode) {
        LOG.info(PerformanceTestingBundle.message("startup.silent"))
      }
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
    getInstance(project).smartInvokeLater(Context.current().wrap(
      Runnable {
        myAlarm.addRequest(Context.current().wrap(
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
        myAlarm.addRequest(Context.current().wrap(Runnable {
          val indicators = CoreProgressManager.getCurrentIndicators()
          var indexingInProgress = false
          for (indicator in indicators) {
            val indicatorText = indicator.text
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
      val executor = screenshotExecutor
      executor?.shutdown()
      PerformanceTestSpan.endSpan()
      reportErrorsFromMessagePool()
    }
  }

  companion object {
    private val LOG = Logger.getInstance("PerformancePlugin")
    private const val TIMEOUT = 500
    val TEST_SCRIPT_FILE_PATH = System.getProperty("testscript.filename")

    /**
     * If an IDE error occurs and this flag is true, a failure of a TeamCity test will be printed
     * to stdout using TeamCity Service Messages (see [.reportTeamCityFailedTestAndBuildProblem]).
     * The name of the failed test will be inferred from the name of the script file
     * (its name without extension, see [.getTeamCityFailedTestName]).
     */
    private val MUST_REPORT_TEAMCITY_TEST_FAILURE_ON_IDE_ERROR =
      System.getProperty("testscript.must.report.teamcity.test.failure.on.error", "true")
        .toBoolean()
    private const val INDEXING_PROFILER_PREFIX = "%%profileIndexing"
    private var screenshotExecutor: ScheduledExecutorService? = null
    private var ourScriptStarted = false
    private fun registerScreenshotTaking(fileName: String) {
      screenshotExecutor = ConcurrencyUtil.newSingleScheduledThreadExecutor("Performance plugin screenshoter")
      screenshotExecutor!!.scheduleWithFixedDelay(Runnable { takeScreenshotOfFrame(fileName) }, 0, 1, TimeUnit.MINUTES)
    }

    private fun initializeProfilerSettingsForIndexing(): Pair<String, List<String>>? {
      try {
        val lines = FileUtil.loadLines(
          testFile)
        for (line in lines) {
          if (line.startsWith(INDEXING_PROFILER_PREFIX)) {
            val command = line.substring(INDEXING_PROFILER_PREFIX.length).trim { it <= ' ' }.split("\\s+".toRegex(),
                                                                                                   limit = 2).toTypedArray()
            val indexingActivity = command[0]
            val profilingParameters = if (command.size > 1) Arrays.asList(
              *command[1].trim { it <= ' ' }.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray())
            else ArrayList()
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

    private val testFile: File
      private get() {
        val file = File(TEST_SCRIPT_FILE_PATH)
        if (!file.isFile) {
          System.err.println(PerformanceTestingBundle.message("startup.noscript", file.absolutePath))
          ApplicationManagerEx.getApplicationEx().exit(true, true, 1)
        }
        return file
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

    fun generifyErrorMessage(originalMessage: String): String {
      return originalMessage // text@3ba5aac, text => text<ID>, text
        .replace("[$@#][A-Za-z0-9-_]+".toRegex(), "<ID>") // java-design-patterns-master.db451f59 => java-design-patterns-master.<HASH>
        .replace("[.]([A-Za-z]+[0-9]|[0-9]+[A-Za-z])[A-Za-z0-9]*".toRegex(), ".<HASH>") // 0x01 => <HEX>
        .replace("0x[0-9a-fA-F]+".toRegex(), "<HEX>") // text1234text => text<NUM>text
        .replace("[0-9]+".toRegex(), "<NUM>")
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
      if (causeMessage == null || causeMessage.isEmpty()) {
        causeMessage = errorMessage.message
        if (causeMessage == null || causeMessage.isEmpty()) {
          val throwableMessage = getNonEmptyThrowableMessage(throwable)
          val index = throwableMessage.indexOf("\tat ")
          causeMessage = if (index != -1) {
            throwableMessage.substring(0, index)
          }
          else {
            throwableMessage
          }
        }
      }
      val scriptErrorsDir = Paths.get(PathManager.getLogPath(), "script-errors")
      Files.createDirectories(scriptErrorsDir)
      Files.walk(scriptErrorsDir).use { stream ->
        val finalCauseMessage = causeMessage
        val isDuplicated = stream.filter { path: Path -> path.fileName.toString() == "message.txt" }
          .anyMatch { path: Path? ->
            try {
              return@anyMatch Files.readString(path) == finalCauseMessage
            }
            catch (ex: IOException) {
              LOG.error(ex.message)
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
          writeAttachmentToErrorDir(attachment, errorDir.resolve(j.toString() + "-" + attachment.name))
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
      return if (throwable.message != null && !throwable.message!!.isEmpty()) {
        throwable.message!!
      }
      else throwable.javaClass.name
    }

    private val teamCityFailedTestName: String
      private get() = FileUtilRt.getNameWithoutExtension(testFile.name)

    private fun reportTeamCityFailedTestAndBuildProblem(testName: String,
                                                        failureMessage: String,
                                                        failureDetails: String) {
      var testName = testName
      testName = generifyErrorMessage(testName)
      System.out.printf("##teamcity[testFailed name='%s' message='%s' details='%s']\n",
                        encodeStringForTC(testName),
                        encodeStringForTC(failureMessage),
                        encodeStringForTC(failureDetails))
      System.out.printf("##teamcity[buildProblem description='%s' identity='%s'] ",
                        encodeStringForTC(failureMessage),
                        encodeStringForTC(testName))
    }

    private fun encodeStringForTC(line: String): String {
      val MAX_DESCRIPTION_LENGTH = 7500
      return line.substring(0, min(MAX_DESCRIPTION_LENGTH.toDouble(), line.length.toDouble()).toInt()).replace("\\|".toRegex(),
                                                                                                               "||").replace(
        "\\[".toRegex(), "|[").replace("]".toRegex(), "|]").replace("\n".toRegex(), "|n").replace("'".toRegex(), "|'").replace(
        "\r".toRegex(), "|r")
    }

    fun runScript(project: Project?, script: String?, mustExitOnFailure: Boolean) {
      val playback: PlaybackRunner = PlaybackRunnerExtended(script, CommandLogger(), project!!)
      val scriptCallback = playback.run()
      val future: CompletableFuture<*> = CompletableFuture<Any>()
      scriptCallback.doWhenDone { future.complete(null) }
      scriptCallback.doWhenRejected { s: String? ->
        if (s == null || s.isEmpty()) {
          future.cancel(false)
        }
        else {
          future.completeExceptionally(CancellationException(s))
        }
      }
      CommandsRunner.setActionCallback(future)
      registerOnFinishRunnables(future, mustExitOnFailure)
    }

    private fun runScriptFromFile(project: Project) {
      val playback: PlaybackRunner = PlaybackRunnerExtended("%include " + testFile, CommandLogger(), project)
      playback.scriptDir = testFile.parentFile
      if (SystemProperties.getBooleanProperty(ReporterCommandAsTelemetrySpan.USE_SPAN_WRAPPER_FOR_COMMAND, false)) {
        playback.setCommandStartStopProcessor(ReporterCommandAsTelemetrySpan())
      }
      val scriptCallback = playback.run()
      CommandsRunner.setActionCallback(scriptCallback)
      registerOnFinishRunnables(scriptCallback, true)
    }

    private fun registerOnFinishRunnables(future: CompletableFuture<*>, mustExitOnFailure: Boolean) {
      future
        .thenRun { LOG.info("Execution of the script has been finished successfully") }
        .exceptionally(Function<Throwable, Void> { e: Throwable ->
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
            takeScreenshotOfFrame(System.getProperty("ide.performance.screenshot.on.failure"))
          }
          val threadDump = """
                Thread dump before IDE termination:
                ${ThreadDumper.dumpThreadsToString()}
                """.trimIndent()
          LOG.info(threadDump)
          if (mustExitOnFailure) {
            ApplicationManagerEx.getApplicationEx().exit(true, true, 1)
          }
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
        val errorMessageFromLog: String
        errorMessageFromLog = if (endIndex != -1) {
          substringBegin.substring(0, endIndex)
        }
        else {
          substringBegin
        }
        val failureCause = logDir.resolve("failure_cause.txt")
        Files.writeString(failureCause, errorMessageFromLog)
      }
      catch (ex: Exception) {
        LOG.error(ex.message)
      }
    }
  }
}
