// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.starter.junit5

import com.intellij.platform.testFramework.teamCity.TeamCityReporter
import com.intellij.platform.testFramework.teamCity.TeamCityReporter.SyntheticTestKind
import com.intellij.platform.testFramework.teamCity.TeamCityReporter.TestOutcome
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class TeamCityReporterTest {

  private fun captureStdout(block: () -> Unit): List<String> {
    val originalOut = System.out
    val captured = ByteArrayOutputStream()
    System.setOut(PrintStream(captured))
    try {
      block()
    }
    finally {
      System.out.flush()
      System.setOut(originalOut)
    }
    return captured.toString().lineSequence().filter { it.startsWith("##teamcity[") }.toList()
  }

  private fun extractName(serviceMessage: String): String {
    val match = Regex("name='((?:[^']|\\|')*)'").find(serviceMessage)
      ?: error("no name attribute in: $serviceMessage")
    return match.groupValues[1]
  }

  @Test
  fun `lifecycle emits testStarted then testFailed then testFinished`() {
    val lines = captureStdout {
      TeamCityReporter.reportTestLifecycle(
        testName = "MyTest",
        outcome = TestOutcome.FAILED,
        message = "boom",
        details = "stack",
        flowId = "flow-1",
        generifyTestName = false,
      )
    }

    lines.size.shouldBe(3)
    lines[0].shouldContain("##teamcity[testStarted")
    lines[1].shouldContain("##teamcity[testFailed")
    lines[2].shouldContain("##teamcity[testFinished")
    lines.forEach { it.shouldContain("flowId='flow-1'") }
    lines.forEach { it.shouldContain("name='MyTest'") }
  }

  @Test
  fun `SUCCESS outcome does not emit testFailed`() {
    val lines = captureStdout {
      TeamCityReporter.reportTestLifecycle(
        testName = "Pass",
        outcome = TestOutcome.SUCCESS,
        flowId = "f",
        generifyTestName = false,
      )
    }

    lines.map { it.substringAfter("##teamcity[").substringBefore(' ') }
      .shouldContainInOrder("testStarted", "testFinished")
    lines.none { it.contains("testFailed") }.shouldBe(true)
    lines.none { it.contains("testIgnored") }.shouldBe(true)
  }

  @Test
  fun `IGNORED outcome emits testIgnored between started and finished`() {
    val lines = captureStdout {
      TeamCityReporter.reportTestLifecycle(
        testName = "Skip",
        outcome = TestOutcome.IGNORED,
        message = "muted",
        flowId = "f",
        generifyTestName = false,
      )
    }

    lines.map { it.substringAfter("##teamcity[").substringBefore(' ') }
      .shouldContainInOrder("testStarted", "testIgnored", "testFinished")
  }

  @Test
  fun `generifyTestName=true replaces volatile tokens before reporting`() {
    val lines = captureStdout {
      TeamCityReporter.reportTestLifecycle(
        testName = "Failure with hash 1106204646 in Thread[#37]",
        outcome = TestOutcome.FAILED,
        flowId = "f",
        generifyTestName = true,
      )
    }

    val started = lines.first { it.contains("testStarted") }
    extractName(started).shouldBe("Failure with hash <NUM> in Thread|[<ID>|]")
  }

  @Test
  fun `generifyTestName=false leaves the name untouched`() {
    val lines = captureStdout {
      TeamCityReporter.reportTestLifecycle(
        testName = "Failure with hash 1106204646",
        outcome = TestOutcome.FAILED,
        flowId = "f",
        generifyTestName = false,
      )
    }

    extractName(lines.first { it.contains("testStarted") })
      .shouldBe("Failure with hash 1106204646")
  }

  @Test
  fun `same exception shape with different number widths produces identical name`() {
    // Regression for AT-4370 / commit fca8627a: take(250) must run AFTER generifyErrorMessage,
    // otherwise two reports of the same exception with different numeric token widths
    // (e.g. 9-digit vs 10-digit identity hash) end up bucketed under different test names.
    val template = { hash: String, threadId: String ->
      "PluginException: Access from EDT not allowed; Thread[#$threadId,AWT-EventQueue-0,6,main] $hash " +
      "(EventQueue.isDispatchThread()=true) [Plugin: com.intellij.swagger]"
    }
    val nineDigit = template("179660864", "37")
    val tenDigit = template("1106204646", "35")

    val nineNames = captureStdout {
      TeamCityReporter.reportTestLifecycle(nineDigit, TestOutcome.FAILED, flowId = "f1")
    }.map(::extractName).distinct()
    val tenNames = captureStdout {
      TeamCityReporter.reportTestLifecycle(tenDigit, TestOutcome.FAILED, flowId = "f2")
    }.map(::extractName).distinct()

    nineNames.shouldBe(tenNames)
  }

  @Test
  fun `truncation happens after generification, not before`() {
    // A message that is > 250 chars raw but < 250 chars after generification (because <NUM>
    // shrinks the long numeric tokens) must NOT be truncated mid-content.
    val longRaw = buildString {
      append("Error: ")
      // 30 ten-digit numbers separated by spaces ⇒ ~330 chars raw, but each becomes <NUM> (5
      // chars) after generification ⇒ well under 250.
      repeat(30) { append("1234567890 ") }
      append("END")
    }
    require(longRaw.length > 250) { "test data must exceed 250 chars raw" }

    val name = captureStdout {
      TeamCityReporter.reportTestLifecycle(longRaw, TestOutcome.FAILED, flowId = "f")
    }.first { it.contains("testStarted") }.let(::extractName)

    name.shouldContain("END")               // would be missing if take(250) ran before generify
    name.length.shouldBe(longRaw.length - 30 * (10 - "<NUM>".length).also { /* per-token shrink */ } + 0)
  }

  @Test
  fun `truncation still applies when generified output exceeds the limit`() {
    val longLiteral = "X".repeat(400)

    val name = captureStdout {
      TeamCityReporter.reportTestLifecycle(longLiteral, TestOutcome.FAILED, flowId = "f")
    }.first { it.contains("testStarted") }.let(::extractName)

    name.length.shouldBe(250)
    name.shouldBe("X".repeat(250))
  }

  @Test
  fun `syntheticTestKind wraps the test name with the kind prefix`() {
    val lines = captureStdout {
      TeamCityReporter.reportTestLifecycle(
        testName = "boom",
        outcome = TestOutcome.FAILED,
        flowId = "f",
        generifyTestName = false,
        syntheticTestKind = SyntheticTestKind.IDE_EXCEPTION,
      )
    }

    lines.forEach { extractName(it).shouldBe("(IdeException boom)") }
  }

  @Test
  fun `syntheticTestKind wraps the truncated and generified name`() {
    val numberString = "1106204646"
    val raw = "Failure with hash $numberString inside " + "Y".repeat(300)

    val lines = captureStdout {
      TeamCityReporter.reportTestLifecycle(
        testName = raw,
        outcome = TestOutcome.FAILED,
        flowId = "f",
        syntheticTestKind = SyntheticTestKind.TEST_INFRA_EXCEPTION,
      )
    }
    val name = extractName(lines.first { it.contains("testStarted") })

    name.startsWith("(TestInfraException ").shouldBe(true)
    name.endsWith(")").shouldBe(true)
    name.shouldContain("<NUM>")           // generified
    name.shouldNotContain(numberString)   // raw number gone
    val inner = name.removePrefix("(TestInfraException ").removeSuffix(")")
    inner.length.shouldBe(250)            // post-generify truncation hit the cap
  }

  @Test
  fun `block runs between started and finished and its stdout is captured by the test`() {
    val lines = captureStdout {
      TeamCityReporter.reportTestLifecycle(
        testName = "BlockTest",
        outcome = TestOutcome.SUCCESS,
        flowId = "f",
        generifyTestName = false,
      ) {
        println("##teamcity[message text='from-block' status='NORMAL']")
      }
    }

    val kinds = lines.map { it.substringAfter("##teamcity[").substringBefore(' ') }
    kinds.shouldContainInOrder("testStarted", "message", "testFinished")
    lines.first { it.contains("testStarted") }.shouldContain("captureStandardOutput='true'")
  }

  @Test
  fun `testFinished is emitted even when block throws`() {
    val lines = captureStdout {
      runCatching {
        TeamCityReporter.reportTestLifecycle(
          testName = "ThrowingBlock",
          outcome = TestOutcome.SUCCESS,
          flowId = "f",
          generifyTestName = false,
        ) {
          error("intentional")
        }
      }
    }

    lines.any { it.contains("testStarted") }.shouldBe(true)
    lines.any { it.contains("testFinished") }.shouldBe(true)
  }
}
