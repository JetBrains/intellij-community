package com.intellij.ide.starter.tests.unit

import com.intellij.ide.starter.bus.EventState
import com.intellij.ide.starter.bus.StarterListener
import com.intellij.ide.starter.bus.subscribe
import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.ci.NoCIServer
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.ide.InstalledIde
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.runner.TestContainer
import com.intellij.ide.starter.runner.TestContextInitializedEvent
import com.intellij.ide.starter.utils.hyphenateTestName
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.timing.eventually
import io.kotest.assertions.withClue
import io.kotest.inspectors.shouldForAtLeastOne
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Answers
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import java.util.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@ExtendWith(MockitoExtension::class)
class TestRunnerInitEventsTest {
  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private lateinit var testCase: TestCase

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private lateinit var installedIde: InstalledIde

  private val container = object : TestContainer<Any> {
    override val ciServer: CIServer = NoCIServer
    override var useLatestDownloadedIdeBuild: Boolean = false
    override lateinit var testContext: IDETestContext
    override val setupHooks: MutableList<IDETestContext.() -> IDETestContext> = mutableListOf()

    override fun resolveIDE(ideInfo: IdeInfo): Pair<String, InstalledIde> = Pair("1000.200.30", installedIde)
    override fun installPerformanceTestingPluginIfMissing(context: IDETestContext) {}
  }

  @AfterEach
  fun afterEach() {
    StarterListener.unsubscribe()
  }

  @Disabled("Is not stable")
  @RepeatedTest(value = 200)
  fun `events for test runner init should be fired`(testInfo: TestInfo) {
    val firedEvents = Collections.synchronizedList(mutableListOf<TestContextInitializedEvent>())
    StarterListener.subscribe { event: TestContextInitializedEvent -> synchronized(firedEvents) { firedEvents.add(event) } }

    val testName = testInfo.displayName.hyphenateTestName()

    container.initializeTestContext(testName = testName, testCase = testCase)

    runBlocking {
      eventually(duration = 2.seconds, poll = 200.milliseconds) {
        withClue("There should be 2 events fired. Events: ${firedEvents.map { it.state }}") {
          firedEvents.shouldHaveSize(2)
        }
      }
    }

    assertSoftly {
      withClue("During test runner initialization should be fired 2 events: before and after initialization. " +
               "Events: ${firedEvents.map { it.state }}") {
        firedEvents.shouldForAtLeastOne { it.state.shouldBe(EventState.BEFORE) }
        firedEvents.shouldForAtLeastOne { it.state.shouldBe(EventState.AFTER) }
      }
    }
  }
}