package com.intellij.ide.starter.report

import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.report.ErrorReporter.Companion.MESSAGE_FILENAME
import com.intellij.ide.starter.report.ErrorReporter.Companion.STACKTRACE_FILENAME
import com.intellij.ide.starter.report.ErrorReporter.Companion.SYNTHETIC_TESTNAME_FILENAME
import com.intellij.ide.starter.report.ErrorReporter.Companion.ACTIVE_TESTNAME_FILENAME
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.platform.testFramework.teamCity.TeamCityReporter
import com.intellij.util.SystemProperties
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.jvm.optionals.getOrNull

object ErrorReporterToCI : ErrorReporter {
  /**
   * Read files from errors directories, written by performance testing plugin and report them as errors.
   * Read threadDumps folders and report them as freezes.
   * Take a look at [com.jetbrains.performancePlugin.ScriptErrorReporter]
   */
  override fun reportErrorsAsFailedTests(runContext: IDERunContext) {
    reportErrors(runContext, collectErrors(runContext.logsDir) + collectScriptErrors(runContext.logsDir))
  }

  fun collectErrors(logsDir: Path): List<Error> {
    if (SystemProperties.getBooleanProperty("DO_NOT_REPORT_ERRORS", false)) return listOf()
    return collectExceptions(getErrorsDir(logsDir))
  }

  fun getErrorsDir(logsDir: Path): Path? {
    //client has structure log/2024-04-11_at_11-06-10/script-errors so we need to look deeeper
    return Files.find(logsDir, 3, { path, _ -> path.name == ErrorReporter.ERRORS_DIR_NAME }).findFirst().getOrNull()
  }

  /**
   * To support legacy formant of errors reporting in "script-errors" dir
   */
  fun collectScriptErrors(logsDir: Path): List<Error> {
    val rootErrorsDir = Files.find(logsDir, 3, { path, _ -> path.name == "script-" + ErrorReporter.ERRORS_DIR_NAME })
      .findFirst().getOrNull()

    if (SystemProperties.getBooleanProperty("DO_NOT_REPORT_ERRORS", false)) return listOf()
    return collectExceptions(rootErrorsDir)
  }

  /**
   * Method only collects exceptions from [ErrorReporter.ERRORS_DIR_NAME] and skip freezes
   */
  private fun collectExceptions(rootErrorsDir: Path?): List<Error> {
    if (rootErrorsDir == null || !rootErrorsDir.isDirectory()) {
      return emptyList()
    }
    val errors = mutableListOf<Error>()
    val errorsDirectories = rootErrorsDir.listDirectoryEntries()
    for (errorDir in errorsDirectories) {
      val messageFile = errorDir.resolve(MESSAGE_FILENAME)
      if (!messageFile.exists()) continue

      val messageText = messageFile.readText().trimIndent().trim()
      val syntheticTestNameFile = errorDir.resolve(SYNTHETIC_TESTNAME_FILENAME)
      val syntheticTestName = if (syntheticTestNameFile.exists()) syntheticTestNameFile.readText().trim() else null

      val errorType = ErrorType.fromMessage(messageText)
      if (errorType == ErrorType.ERROR) {
        val stacktraceFile = errorDir.resolve(STACKTRACE_FILENAME)
        if (!stacktraceFile.exists()) continue
        val stackTrace = stacktraceFile.readText().trimIndent().trim()
        val activeTestNameFile = errorDir.resolve(ACTIVE_TESTNAME_FILENAME)
        val activeTestName = if (activeTestNameFile.exists()) activeTestNameFile.readText().trim().takeIf { it.isNotEmpty() } else null
        errors.add(Error(messageText, stackTrace, "", errorType, syntheticTestName, activeTestName))
      }
      else if (errorType == ErrorType.FREEZE) {
        errorDir.listDirectoryEntries("dump*").firstOrNull()?.let { threadDump ->
          val dumpContent = Files.readString(threadDump)
          val fallbackName = "Not analyzed freeze: " + (inferClassMethodNamesFromFolderName(threadDump)
                                                        ?: inferFallbackNameFromThreadDump(dumpContent))
          errors.add(Error(fallbackName, "", dumpContent, ErrorType.FREEZE))
        }
      }
    }
    return errors
  }

  /**
   * There are two types of names for folders with freezes:
   * ```
   *  threadDumps-freeze-20240206-155640-IU-241.11817
   *  threadDumps-freeze-20240206-155640-IU-241.11817-JBIterator.peekNext-5sec
   *  ```
   *
   *  Return `null` if folder has the first type.
   *
   *  Infer the class and method name from the second type taking the part before the latest - `nameparts[7]`.
   */
  private fun inferClassMethodNamesFromFolderName(path: Path): String? {
    val nameParts = path.name.split("-")
    return if (nameParts.size == 8) nameParts[7] else null
  }

  /**
   * Takes the first line that looks like at com.intellij.util.containers.JBIterator.peekNext(JBIterator.java:132)
   * @return className.methodName (e.g., JBIterator.peekNext)
   */
  private fun inferFallbackNameFromThreadDump(dumpContent: String): String {
    val regex = Regex("at (.*)\\(.*:\\d+\\)")
    dumpContent.lineSequence()
      .mapNotNull { line ->
        regex.find(line.trim())?.let { match ->
          match.groupValues[1].split(".").takeLast(2).joinToString(".")
        }
      }
      .firstOrNull()?.let { return it }

    throw Exception("Thread dump file without methods!")
  }

  fun reportErrors(runContext: IDERunContext, errors: List<Error>) {
    val failureDetailsProvider = FailureDetailsOnCI.instance
    for (error in errors) {
      reportError(
        error = error,
        failureDetailsMessage = failureDetailsProvider.getFailureDetails(runContext, error),
        urlToLogs = failureDetailsProvider.getLinkToCIArtifacts(runContext),
        allureContextName = runContext.contextName,
      )
    }
  }

  fun reportError(
    error: Error,
    failureDetailsMessage: String,
    urlToLogs: String? = null,
    allureContextName: String? = null,
  ) {
    val messageText = error.messageText
    val stackTraceContent = error.stackTraceContent
    val syntheticTestName = when (error.type) {
      ErrorType.ERROR -> {
        error.syntheticTestName ?: generateTestNameFromException(stackTraceContent, messageText)
      }
      ErrorType.FREEZE, ErrorType.TIMEOUT -> {
        messageText
      }
    }

    val linkToMuteArticle = "\nThis test fail is an exception! \n" +
                            "You can find instructions about muting this error in this link https://youtrack.jetbrains.com/articles/IJPL-A-1185/How-to-create-a-new-mapping"
    if (CIServer.instance.isTestFailureShouldBeIgnored(messageText) || CIServer.instance.isTestFailureShouldBeIgnored(stackTraceContent)) {
      CIServer.instance.ignoreTestFailure(testName = syntheticTestName,
                                          message = failureDetailsMessage,
                                          kind = TeamCityReporter.SyntheticTestKind.IDE_EXCEPTION)
    }
    else {
      CIServer.instance.reportTestFailure(testName = syntheticTestName,
                                          message = failureDetailsMessage + linkToMuteArticle,
                                          details = stackTraceContent,
                                          linkToLogs = urlToLogs,
                                          kind = TeamCityReporter.SyntheticTestKind.IDE_EXCEPTION)
      if (allureContextName != null) {
        AllureReport.reportFailure(allureContextName, messageText + linkToMuteArticle,
                                   stackTraceContent,
                                   links = AllureLink.single("Link to Logs and artifacts", urlToLogs ?: "fail to get link"))
      }
    }
  }

  private fun generateTestNameFromException(stackTraceContent: String, messageText: String): String {
    val testName = if (stackTraceContent.startsWith(messageText)) {
      stackTraceContent
    }
    else {
      messageText
    }
    return testName.trim()
  }
}
