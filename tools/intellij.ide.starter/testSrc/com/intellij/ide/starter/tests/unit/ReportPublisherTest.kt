package com.intellij.ide.starter.tests.unit

import com.intellij.ide.starter.ci.NoCIServer
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.ide.InstalledIDE
import com.intellij.ide.starter.ide.command.CommandChain
import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.ide.starter.path.IDEDataPaths
import com.intellij.ide.starter.report.publisher.ReportPublisher
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.ide.starter.tests.examples.data.TestCases
import com.intellij.ide.starter.tests.examples.junit4.initStarterRule
import org.junit.Rule
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.kodein.di.direct
import org.kodein.di.instance
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class ReportPublisherTest {

  @Mock
  lateinit var ideDataPaths: IDEDataPaths
  @Mock
  lateinit var installedIDE: InstalledIDE
  @Mock
  lateinit var ideStartResult: IDEStartResult

  @Test
  fun `test that report publishers were invoked successfully`() {
    //GIVEN
    //TODO(Find a way to mock on stage of init of di, due to it little bit dirty approach to put them directly in IDETestContext publishers)
    val publishers: List<ReportPublisher> = di.direct.instance()
    val publisherSpy = spy(publishers[0])
    val commandChain = CommandChain()

    val ideTestContext = IDETestContext(
      ideDataPaths,
      installedIDE,
      TestCases.IJ.GradleJitPackSimple,
      "Test method",
      null,
      patchVMOptions = { this },
      NoCIServer,
      publishers = listOf(publisherSpy),
      isReportPublishingEnabled = true
    )

    val ideRunContext = IDERunContext(ideTestContext)
    val spyIdeRunContext = spy(ideRunContext)
    val spyTestContext = spy(ideTestContext)

    //WHEN
    doReturn(spyIdeRunContext).`when`(spyTestContext).runContext(commands = commandChain)
    doReturn(ideStartResult).`when`(spyIdeRunContext).runIDE()

    spyTestContext.runIDE (commands = commandChain)
    //THEN
    verify(publisherSpy, times(1)).publish(ideStartResult)
  }

}