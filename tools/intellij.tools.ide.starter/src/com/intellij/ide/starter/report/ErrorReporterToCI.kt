package com.intellij.ide.starter.report

import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.report.ErrorReporter.Companion.MESSAGE_FILENAME
import com.intellij.ide.starter.report.ErrorReporter.Companion.STACKTRACE_FILENAME
import com.intellij.ide.starter.report.ErrorReporter.Companion.SYNTHETIC_TESTNAME_FILENAME
import com.intellij.ide.starter.runner.IDEReportingData
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.platform.testFramework.teamCity.TeamCityReporter
import com.intellij.util.SystemProperties
import org.jetbrains.annotations.ApiStatus
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
   * Take a look at `com.jetbrains.performancePlugin.ScriptErrorReporter`.
   */
  override fun reportErrorsAsFailedTests(runContext: IDERunContext) {
    runContext.registeredIdeReportingData().forEach { ideReportingData ->
      reportErrors(ideReportingData)
    }
  }

  @ApiStatus.Internal
  fun collectErrors(runContext: IDERunContext): List<Error> {
    return runContext.registeredIdeReportingData().flatMap { ideReportingData -> collectErrors(ideReportingData) }
  }

  private fun collectErrors(ideReportingData: IDEReportingData): List<Error> {
    return collectErrors(ideReportingData.logsDir, ideReportingData.allowedIdeErrorReportFiles)
  }

  fun collectErrors(logsDir: Path, allowedFiles: Set<Path>? = null): List<Error> {
    if (SystemProperties.getBooleanProperty("DO_NOT_REPORT_ERRORS", false)) return emptyList()
    return collectExceptions(getErrorsDir(logsDir), allowedFiles) +
           collectExceptions(getScriptErrorsDir(logsDir), allowedFiles)
  }

  fun getErrorsDir(logsDir: Path): Path? {
    return findReportDir(logsDir, ErrorReporter.ERRORS_DIR_NAME)
  }

  private fun getScriptErrorsDir(logsDir: Path): Path? {
    return findReportDir(logsDir, "script-${ErrorReporter.ERRORS_DIR_NAME}")
  }

  private fun findReportDir(logsDir: Path, directoryName: String): Path? {
    // Client logs may be nested, for example log/2024-04-11_at_11-06-10/errors.
    return Files.find(logsDir, 3, { path, _ -> path.name == directoryName }).use { paths ->
      paths.findFirst().getOrNull()
    }
  }

  /**
   * Method only collects exceptions from [ErrorReporter.ERRORS_DIR_NAME] and skip freezes
   */
  private fun collectExceptions(rootErrorsDir: Path?, allowedFiles: Set<Path>?): List<Error> {
    if (rootErrorsDir == null || !rootErrorsDir.isDirectory()) {
      return emptyList()
    }
    val errors = mutableListOf<Error>()
    val errorsDirectories = rootErrorsDir.listDirectoryEntries()
    for (errorDir in errorsDirectories) {
      val messageFile = errorDir.resolve(MESSAGE_FILENAME)
      if (!messageFile.exists()) continue
      if (allowedFiles != null && messageFile.toAbsolutePath().normalize() !in allowedFiles) continue

      val messageText = messageFile.readText().trimIndent().trim()
      val syntheticTestNameFile = errorDir.resolve(SYNTHETIC_TESTNAME_FILENAME)
      val syntheticTestName = if (syntheticTestNameFile.exists()) syntheticTestNameFile.readText().trim() else null

      val errorType = ErrorType.fromMessage(messageText)
      if (errorType == ErrorType.ERROR) {
        val stacktraceFile = errorDir.resolve(STACKTRACE_FILENAME)
        if (!stacktraceFile.exists()) continue
        val stackTrace = stacktraceFile.readText().trimIndent().trim()
        errors.add(Error(messageText, stackTrace, "", errorType, syntheticTestName = syntheticTestName))
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

  private fun reportErrors(ideReportingData: IDEReportingData) {
    val failureDetailsProvider = DetailsOnCI.instance
    for (error in collectErrors(ideReportingData)) {
      reportError(
        error = error,
        failureDetailsMessage = failureDetailsProvider.getDetails(ideReportingData),
        urlToLogs = failureDetailsProvider.getLinkToCIArtifacts(ideReportingData),
        allureContextName = ideReportingData.humanReadableTestName,
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
