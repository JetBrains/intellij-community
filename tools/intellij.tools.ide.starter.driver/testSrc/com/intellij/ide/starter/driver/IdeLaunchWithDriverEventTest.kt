package com.intellij.ide.starter.driver

import com.intellij.ide.starter.data.TestCases
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.junit5.className
import com.intellij.ide.starter.junit5.config.KillOutdatedProcessesAfterEach
import com.intellij.ide.starter.junit5.getName
import com.intellij.ide.starter.runner.CurrentTestMethod
import com.intellij.ide.starter.runner.Starter
import com.intellij.ide.starter.runner.events.IdeAfterLaunchEvent
import com.intellij.ide.starter.runner.events.IdeBeforeKillEvent
import com.intellij.ide.starter.runner.events.IdeBeforeLaunchEvent
import com.intellij.ide.starter.runner.events.IdeLaunchEvent
import com.intellij.ide.starter.utils.hyphenateTestName
import com.intellij.tools.ide.starter.bus.EventsBus
import com.intellij.tools.ide.starter.bus.events.Event
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.timing.eventually
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldHaveSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.RepetitionInfo
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@ExtendWith(KillOutdatedProcessesAfterEach::class)
class IdeLaunchWithDriverEventTest {

  @AfterEach
  fun beforeEach() {
    System.setProperty("eventbus.debug", "false")
  }

  @AfterEach
  fun afterEach() {
    System.setProperty("eventbus.debug", "false")
    EventsBus.unsubscribeAll()
  }


  @RepeatedTest(value = 2)
  fun eventsForIdeLaunchesAreFired(testInfo: RepetitionInfo) {
    val firedEvents = mutableListOf<Event>()
    EventsBus.subscribe(this) { event: IdeBeforeLaunchEvent -> firedEvents.add(event) }
    EventsBus.subscribe(this) { event: IdeLaunchEvent -> firedEvents.add(event) }
    EventsBus.subscribe(this) { event: IdeBeforeKillEvent -> firedEvents.add(event) }
    EventsBus.subscribe(this) { event: IdeAfterLaunchEvent -> firedEvents.add(event) }

    val testName = (CurrentTestMethod.className() + "/" + CurrentTestMethod.getName()).hyphenateTestName()
    val context = Starter.newContext(testName = "${testName}-${testInfo.currentRepetition}", testCase = TestCases.IU.JitPackAndroidExample)
      .skipIndicesInitialization()
      .enableEventBusDebugLogs()

    context.runIdeWithDriver(launchName = "first-run").useDriverAndCloseIde {}

    context.runIdeWithDriver(launchName = "second-run").useDriverAndCloseIde {}

    runBlocking(Dispatchers.IO) {
      eventually(duration = 2.seconds, poll = 100.milliseconds) {
        withClue("Events: ${firedEvents.joinToString("\n")}") {
          firedEvents.shouldHaveSize(6)
        }
      }
    }

    assertSoftly {
      firedEvents.filterIsInstance<IdeBeforeLaunchEvent>().shouldHaveSize(2)
      firedEvents.filterIsInstance<IdeLaunchEvent>().shouldHaveSize(2)
      firedEvents.filterIsInstance<IdeBeforeKillEvent>().shouldHaveSize(0)
      firedEvents.filterIsInstance<IdeAfterLaunchEvent>().shouldHaveSize(2)
    }
  }
}