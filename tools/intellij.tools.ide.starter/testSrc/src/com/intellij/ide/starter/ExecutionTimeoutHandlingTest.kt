package com.intellij.ide.starter

import com.intellij.ide.starter.data.IdeaUltimateCases
import com.intellij.ide.starter.process.exec.ExecTimeoutException
import com.intellij.ide.starter.project.NoProject
import com.intellij.ide.starter.report.DetailsOnCI
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.ide.starter.runner.Starter
import com.intellij.ide.starter.runner.events.IdeBeforeLaunchEvent
import com.intellij.ide.starter.utils.hyphenateTestName
import com.intellij.tools.ide.performanceTesting.commands.CommandChain
import com.intellij.tools.ide.performanceTesting.commands.delay
import com.intellij.tools.ide.performanceTesting.commands.exitApp
import com.intellij.tools.ide.starter.bus.EventsBus
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import kotlin.time.Duration.Companion.seconds

class ExecutionTimeoutHandlingTest {

  @Test
  fun `runIDE timeout should not throw raw exception without details`(testInfo: TestInfo) {
    val testName = "${testInfo.testClass.get().simpleName}/${testInfo.testMethod.get().name}".hyphenateTestName()
    val context = Starter.newContext(testName, IdeaUltimateCases.withProject(NoProject))
    val timeout = 5.seconds

    var runContext: IDERunContext? = null

    EventsBus.subscribeOnce(this) { event: IdeBeforeLaunchEvent ->
      runContext = event.runContext
    }

    try {
      context.runIDE(
        commands = CommandChain().delay(10_000).exitApp(),
        runTimeout = timeout
      )
    }
    catch (e: ExecTimeoutException) {
      val ciFailureDetails = DetailsOnCI.instance.getLinkToCIArtifacts(runContext!!.lastIdeReportingData)?.let { "Link on CI artifacts ${it}" }
      e.message.shouldBe(
        "Timeout of IDE run '$testName' for $timeout" + System.lineSeparator() + (ciFailureDetails ?: "")
      )
      e.stackTrace.shouldBeEmpty()
    }
  }
}