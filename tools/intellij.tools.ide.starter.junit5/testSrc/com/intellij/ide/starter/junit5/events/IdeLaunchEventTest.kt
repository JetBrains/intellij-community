package com.intellij.ide.starter.junit5.events

import com.intellij.ide.starter.junit5.config.KillOutdatedProcessesAfterEach
import com.intellij.ide.starter.junit5.hyphenateWithClass
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
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@ExtendWith(KillOutdatedProcessesAfterEach::class)
class IdeLaunchEventTest {

  @AfterEach
  fun afterEach() {
    EventsBus.unsubscribeAll()
  }

  @RepeatedTest(value = 2)
  fun `events for twice ide launch should be fired`(testInfo: TestInfo) {
    val firedEvents = mutableListOf<Event>()
    EventsBus.subscribe(this) {
      event: IdeBeforeLaunchEvent -> firedEvents.add(event)
    }
    EventsBus.subscribe(this) { event: IdeLaunchEvent -> firedEvents.add(event) }
    EventsBus.subscribe(this) { event: IdeBeforeKillEvent -> firedEvents.add(event) }
    EventsBus.subscribe(this) { event: IdeAfterLaunchEvent -> firedEvents.add(event) }

    val context = Starter.newContext(testInfo.hyphenateWithClass(), TestCases.IU.withProject(NoProject).useRelease())

    context.runIDE(
      commands = CommandChain().exitApp(),
      runTimeout = 5.seconds,
      expectedKill = true
    )

    context.runIDE(
      commands = CommandChain().exitApp(),
      runTimeout = 5.seconds,
      expectedKill = true
    )

    runBlocking(Dispatchers.IO) {
      eventually(duration = 2.seconds, poll = 100.milliseconds) {
        firedEvents.shouldHaveSize(8)
      }
    }

    assertSoftly {
      firedEvents.filterIsInstance<IdeBeforeLaunchEvent>().shouldHaveSize(2)
      firedEvents.filterIsInstance<IdeLaunchEvent>().shouldHaveSize(2)
      firedEvents.filterIsInstance<IdeBeforeKillEvent>().shouldHaveSize(2)
      firedEvents.filterIsInstance<IdeBeforeKillEvent>().shouldHaveSize(2)
    }
  }
}