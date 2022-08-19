package com.intellij.ide.starter.tests.unit

import com.intellij.ide.starter.ci.NoCIServer
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.ide.InstalledIde
import com.intellij.ide.starter.ide.command.CommandChain
import com.intellij.ide.starter.junit5.hyphenateWithClass
import com.intellij.ide.starter.path.IDEDataPaths
import com.intellij.ide.starter.profiler.ProfilerInjector
import com.intellij.ide.starter.profiler.ProfilerType
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.ide.starter.tests.examples.data.TestCases
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.extension.ExtendWith
import org.kodein.di.direct
import org.kodein.di.instance
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension


@ExtendWith(MockitoExtension::class)
class ProfilerInjectionTest {

  @Mock
  lateinit var ideDataPaths: IDEDataPaths

  @Mock
  lateinit var installedIDE: InstalledIde

  @Disabled("AT-57")
  @Test
  fun `async profiler should be set without exception`(testInfo: TestInfo) {
    val asyncProfiler: ProfilerInjector = di.direct.instance(tag = ProfilerType.ASYNC)
    val profilerSpy = Mockito.spy(asyncProfiler)
    val commandChain = CommandChain()

    val ideTestContext = IDETestContext(
      paths = ideDataPaths,
      ide = installedIDE,
      testCase = TestCases.IC.GradleJitPackSimple,
      testName = testInfo.hyphenateWithClass(),
      _resolvedProjectHome = null,
      ciServer = NoCIServer,
      patchVMOptions = { this }
    )

    val ideRunContext = IDERunContext(ideTestContext)

    val spyIdeRunContext = Mockito.spy(ideRunContext)
    val spyTestContext = Mockito.spy(ideTestContext)

    //WHEN
    Mockito.doReturn(spyIdeRunContext).`when`(spyTestContext).runContext(commands = commandChain)

    //THEN
    spyTestContext.runIDE(commands = commandChain)

    //ASSERT
    Mockito.verify(profilerSpy, Mockito.times(1)).injectProfiler(spyIdeRunContext)
  }
}