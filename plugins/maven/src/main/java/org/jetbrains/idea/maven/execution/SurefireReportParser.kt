// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.JDOMUtil
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries

private val LOG = logger<SurefireReportParser>()

/**
 * Parses Maven Surefire XML reports into TeamCity service message strings for the SM test runner.
 *
 * Suite-level `system-out` is emitted as plain text before `testSuiteStarted`, so it is visible when
 * clicking the root "Test Results" node in the SM runner tree.  Per-test output is emitted as
 * `testStdOut`/`testStdErr` events and is visible when clicking individual test nodes.
 */
@ApiStatus.Internal
object SurefireReportParser {

  const val SUREFIRE_REPORTS_PATH = "target/surefire-reports"

  fun collectMessages(testModuleDirectory: Path, reportSuffix: String? = null): List<String> {
    val reportsDir = testModuleDirectory.resolve(SUREFIRE_REPORTS_PATH)
    if (!reportsDir.exists() || !reportsDir.isDirectory()) return emptyList()

    val glob = if (reportSuffix != null) "*-$reportSuffix.xml" else "*.xml"
    val xmlFiles = try {
      reportsDir.listDirectoryEntries(glob)
    }
    catch (e: Exception) {
      LOG.warn("Cannot list surefire reports in $reportsDir", e)
      return emptyList()
    }
    if (xmlFiles.isEmpty()) return emptyList()

    val messages = mutableListOf<String>()
    for (xmlFile in xmlFiles.sortedBy { it.fileName.toString() }) {
      try {
        parseReport(xmlFile, messages, reportSuffix)
      }
      catch (e: Exception) {
        LOG.warn("Failed to parse surefire report $xmlFile", e)
      }
    }
    return messages
  }

  private fun parseReport(xmlFile: Path, messages: MutableList<String>, reportSuffix: String?) {
    val root = xmlFile.inputStream().use { JDOMUtil.load(it) } ?: return
    if (root.name != "testsuite") return

    // Surefire appends "(<reportNameSuffix>)" to the suite name and classname attributes when
    // reportNameSuffix is set.  Strip it so the SM runner shows the real class name.
    val suffixTag = if (reportSuffix != null) "($reportSuffix)" else null
    fun stripSuffix(s: String) = if (suffixTag != null) s.removeSuffix(suffixTag) else s

    val suiteName = stripSuffix(root.getAttributeValue("name") ?: return)

    messages += "##teamcity[testSuiteStarted name='${escape(suiteName)}' locationHint='java:suite://${escape(suiteName)}']"

    // Suite-level output emitted inside the open suite block is attributed to the suite node in the SM runner.
    root.getChild("system-out")?.textTrim?.takeIf { it.isNotEmpty() }?.let { messages += it }
    root.getChild("system-err")?.textTrim?.takeIf { it.isNotEmpty() }?.let { messages += it }

    // Track the currently open method-level suite for parameterized test grouping.
    var openMethodSuite: String? = null

    for (testcase in root.getChildren("testcase")) {
      val name = testcase.getAttributeValue("name") ?: continue
      val classname = stripSuffix(testcase.getAttributeValue("classname") ?: suiteName)
      val durationMs = testcase.getAttributeValue("time")?.toDoubleOrNull()?.let { (it * 1000).toLong() }

      // Detect parameterized invocations by the "[" marker Surefire uses for indices.
      val bracketIdx = name.indexOf('[')
      val methodSuite: String?
      val displayName: String
      val locationHint: String

      if (bracketIdx >= 0) {
        // Parameterized invocation: group under a method-level suite.
        // baseMethod may include parameter types, e.g. "calculateDiscount(int, int, int)".
        val baseMethod = name.substring(0, bracketIdx).trimEnd()
        val invocationSuffix = name.substring(bracketIdx)
        methodSuite = "$classname.$baseMethod"
        // Add a dot before the invocation suffix so the SM runner strips the method suite
        // prefix when forming the display label, e.g. "[1] 100, 10, 90".
        displayName = "$methodSuite.$invocationSuffix"
        // Use the bare method name (no param types) for navigation.
        val bareMethod = baseMethod.substringBefore('(').trimEnd()
        locationHint = "java:test://${escape(classname)}/${escape(bareMethod)}"
      }
      else {
        // Non-parameterized test: emit directly under the class suite.
        methodSuite = null
        displayName = "$classname.$name"
        locationHint = "java:test://${escape(classname)}/${escape(name)}"
      }

      // Close the previous method suite when the current test belongs to a different group.
      if (openMethodSuite != null && openMethodSuite != methodSuite) {
        messages += "##teamcity[testSuiteFinished name='${escape(openMethodSuite)}']"
        openMethodSuite = null
      }
      // Open a new method suite for the first invocation of a parameterized method.
      if (methodSuite != null && openMethodSuite == null) {
        val bareMethod = name.substring(0, bracketIdx).trimEnd().substringBefore('(').trimEnd()
        messages += "##teamcity[testSuiteStarted name='${escape(methodSuite)}' locationHint='java:test://${escape(classname)}/${escape(bareMethod)}']"
        openMethodSuite = methodSuite
      }

      messages += "##teamcity[testStarted name='${escape(displayName)}' locationHint='$locationHint']"

      testcase.getChild("system-out")?.textTrim?.takeIf { it.isNotEmpty() }?.let {
        messages += "##teamcity[testStdOut name='${escape(displayName)}' out='${escape(it)}']"
      }
      testcase.getChild("system-err")?.textTrim?.takeIf { it.isNotEmpty() }?.let {
        messages += "##teamcity[testStdErr name='${escape(displayName)}' out='${escape(it)}']"
      }

      val failure = testcase.getChild("failure")
      val error = testcase.getChild("error")
      val skipped = testcase.getChild("skipped")

      when {
        failure != null -> {
          val msg = failure.getAttributeValue("message") ?: ""
          val details = failure.textTrim ?: ""
          messages += "##teamcity[testFailed name='${escape(displayName)}' message='${escape(msg)}' details='${escape(details)}']"
        }
        error != null -> {
          val msg = error.getAttributeValue("message") ?: ""
          val details = error.textTrim ?: ""
          messages += "##teamcity[testFailed name='${escape(displayName)}' message='${escape(msg)}' details='${escape(details)}' error='true']"
        }
        skipped != null -> {
          val msg = skipped.getAttributeValue("message") ?: skipped.textTrim ?: ""
          messages += "##teamcity[testIgnored name='${escape(displayName)}' message='${escape(msg)}']"
        }
      }

      val durationAttr = if (durationMs != null) " duration='$durationMs'" else ""
      messages += "##teamcity[testFinished name='${escape(displayName)}'$durationAttr]"
    }

    // Close any method suite still open after the last testcase.
    if (openMethodSuite != null) {
      messages += "##teamcity[testSuiteFinished name='${escape(openMethodSuite)}']"
    }

    messages += "##teamcity[testSuiteFinished name='${escape(suiteName)}']"
  }

  private fun escape(s: String): String = buildString {
    for (c in s) {
      when (c) {
        '|' -> append("||")
        '\'' -> append("|'")
        '\n' -> append("|n")
        '\r' -> append("|r")
        '[' -> append("|[")
        ']' -> append("|]")
        else -> append(c)
      }
    }
  }
}
