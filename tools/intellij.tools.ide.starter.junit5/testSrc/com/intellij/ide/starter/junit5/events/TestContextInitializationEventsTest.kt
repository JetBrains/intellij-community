package com.intellij.ide.starter.junit5.events

import com.intellij.ide.starter.junit5.config.KillOutdatedProcessesAfterEach
import com.intellij.ide.starter.junit5.hyphenateWithClass
import com.intellij.ide.starter.project.NoProject
import com.intellij.ide.starter.runner.Starter
import com.intellij.ide.starter.runner.TestContextInitializedEvent
import com.intellij.tools.ide.starter.bus.EventsBus
import examples.data.TestCases
import io.kotest.assertions.timing.eventually
import io.kotest.matchers.collections.shouldHaveSize
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@ExtendWith(MockitoExtension::class)
@ExtendWith(KillOutdatedProcessesAfterEach::class)
class TestContextInitializationEventsTest {

  @AfterEach
  fun afterEach() {
    EventsBus.unsubscribeAll()
  }

  @Test
  fun `events for test runner init should be fired`(testInfo: TestInfo) {
    val firedEvents = mutableListOf<TestContextInitializedEvent>()
    EventsBus.subscribe(this) { event: TestContextInitializedEvent -> firedEvents.add(event) }

    Starter.newContext(testInfo.hyphenateWithClass(), TestCases.IU.withProject(NoProject).useRelease())

    runBlocking {
      eventually(duration = 2.seconds, poll = 100.milliseconds) {
        firedEvents.shouldHaveSize(1)
      }
    }
  }
}