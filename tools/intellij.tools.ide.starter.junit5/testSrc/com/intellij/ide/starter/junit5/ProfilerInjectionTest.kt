package com.intellij.ide.starter.junit5

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.ide.InstalledIde
import com.intellij.ide.starter.junit5.config.KillOutdatedProcessesAfterEach
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.path.IDEDataPaths
import com.intellij.ide.starter.profiler.ProfilerInjector
import com.intellij.ide.starter.profiler.ProfilerType
import com.intellij.ide.starter.project.LocalProjectInfo
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.tools.ide.performanceTesting.commands.CommandChain
import com.intellij.tools.ide.starter.product.idea.ultimate.IdeaUltimate
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.kodein.di.direct
import org.kodein.di.instance
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import java.nio.file.Path


@ExtendWith(MockitoExtension::class)
@ExtendWith(KillOutdatedProcessesAfterEach::class)
class ProfilerInjectionTest {

  @Mock
  lateinit var ideDataPaths: IDEDataPaths

  @Mock
  lateinit var installedIDE: InstalledIde

  @Disabled("AT-57")
  @Test
  fun `async profiler should be set without exception`(testInfo: TestInfo, @TempDir projectDir: Path) {
    val asyncProfiler: ProfilerInjector = di.direct.instance(tag = ProfilerType.ASYNC_ON_START)
    val profilerSpy = Mockito.spy(asyncProfiler)
    val commandChain = CommandChain()

    val ideTestContext = IDETestContext(
      paths = ideDataPaths,
      ide = installedIDE,
      testCase = TestCase(IdeInfo.IdeaUltimate, LocalProjectInfo(projectDir)).useRelease(),
      testName = testInfo.hyphenateWithClass(),
      _resolvedProjectHome = null
    )

    val ideRunContext = IDERunContext(ideTestContext)

    val spyIdeRunContext = Mockito.spy(ideRunContext)
    val spyTestContext = Mockito.spy(ideTestContext)

    //WHEN
    //Mockito.doReturn(spyIdeRunContext).`when`(spyTestContext).runContext(commands = commandChain)

    //THEN
    spyTestContext.runIDE(commands = commandChain)

    //ASSERT
    Mockito.verify(profilerSpy, Mockito.times(1)).injectProfiler(spyIdeRunContext)
  }
}