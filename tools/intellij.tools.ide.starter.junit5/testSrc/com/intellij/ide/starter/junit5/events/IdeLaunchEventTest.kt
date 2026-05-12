package com.intellij.ide.starter.junit5.events

import com.intellij.ide.starter.junit5.config.KillOutdatedProcessesAfterEach
import com.intellij.ide.starter.junit5.hyphenateWithClass
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.project.LocalProjectInfo
import com.intellij.ide.starter.project.NoProject
import com.intellij.ide.starter.runner.Starter
import com.intellij.ide.starter.runner.events.IdeAfterLaunchEvent
import com.intellij.ide.starter.runner.events.IdeBeforeKillEvent
import com.intellij.ide.starter.runner.events.IdeBeforeLaunchEvent
import com.intellij.ide.starter.runner.events.IdeLaunchEvent
import com.intellij.tools.ide.performanceTesting.commands.CommandChain
import com.intellij.tools.ide.performanceTesting.commands.exitApp
import com.intellij.tools.ide.starter.bus.EventsBus
import com.intellij.tools.ide.starter.bus.events.Event
import com.intellij.tools.ide.starter.product.idea.ultimate.IdeaUltimate
import examples.data.TestCases
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.timing.eventually
import io.kotest.matchers.collections.shouldHaveSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@ExtendWith(KillOutdatedProcessesAfterEach::class)
class IdeLaunchEventTest {
  @AfterEach
  fun afterEach() {
    EventsBus.unsubscribeAll()
  }

  @RepeatedTest(value = 2)
  fun `events for twice ide launch with runTimeout should be fired`(testInfo: TestInfo) {
    val firedEvents = mutableListOf<Event>()
    EventsBus.subscribe(this) { event: IdeBeforeLaunchEvent -> firedEvents.add(event) }
    EventsBus.subscribe(this) { event: IdeLaunchEvent -> firedEvents.add(event) }
    EventsBus.subscribe(this) { event: IdeBeforeKillEvent -> firedEvents.add(event) }
    EventsBus.subscribe(this) { event: IdeAfterLaunchEvent -> firedEvents.add(event) }

    val context = Starter.newContext(testInfo.hyphenateWithClass(), TestCases.IU.withProject(NoProject).useRelease())

    repeat(2) {
      context.runIDE(
        runTimeout = 5.seconds,
        expectedKill = true
      )
    }
    runBlocking(Dispatchers.IO) {
      eventually(duration = 2.seconds, poll = 100.milliseconds) {
        firedEvents.filterIsInstance<IdeAfterLaunchEvent>().shouldHaveSize(2)
        firedEvents.filterIsInstance<IdeBeforeKillEvent>().shouldHaveSize(2)
      }
    }

    assertSoftly {
      firedEvents.filterIsInstance<IdeBeforeLaunchEvent>().shouldHaveSize(2)
      firedEvents.filterIsInstance<IdeLaunchEvent>().shouldHaveSize(2)
      firedEvents.filterIsInstance<IdeBeforeKillEvent>().shouldHaveSize(2)
      firedEvents.filterIsInstance<IdeAfterLaunchEvent>().shouldHaveSize(2)
      firedEvents.shouldHaveSize(8)
    }
  }


  @RepeatedTest(value = 2)
  fun `events for twice ide launch with exitApp should be fired`(testInfo: TestInfo, @TempDir projectDir: Path) {
    val firedEvents = mutableListOf<Event>()
    EventsBus.subscribe(this) { event: IdeBeforeLaunchEvent -> firedEvents.add(event) }
    EventsBus.subscribe(this) { event: IdeLaunchEvent -> firedEvents.add(event) }
    EventsBus.subscribe(this) { event: IdeBeforeKillEvent -> firedEvents.add(event) }
    EventsBus.subscribe(this) { event: IdeAfterLaunchEvent -> firedEvents.add(event) }

    // A real existing directory is required so that performance plugin's
    // PerformancePluginInitProjectActivity fires (it's project-scoped) and the
    // %exitApp script command actually executes. An empty @TempDir is enough —
    // IDE will open it as a project.
    val context = Starter.newContext(
      testInfo.hyphenateWithClass(),
      TestCase(IdeInfo.IdeaUltimate, LocalProjectInfo(projectDir)).useRelease()
    )

    repeat(2) {
      context.runIDE(
        commands = CommandChain().exitApp(),
      )
    }
    runBlocking(Dispatchers.IO) {
      eventually(duration = 2.seconds, poll = 100.milliseconds) {
        firedEvents.filterIsInstance<IdeAfterLaunchEvent>().shouldHaveSize(2)
      }
    }

    assertSoftly {
      firedEvents.filterIsInstance<IdeBeforeLaunchEvent>().shouldHaveSize(2)
      firedEvents.filterIsInstance<IdeLaunchEvent>().shouldHaveSize(2)
      firedEvents.filterIsInstance<IdeAfterLaunchEvent>().shouldHaveSize(2)
      firedEvents.filterIsInstance<IdeBeforeKillEvent>().shouldHaveSize(0)
      firedEvents.shouldHaveSize(6)
    }
  }
}
