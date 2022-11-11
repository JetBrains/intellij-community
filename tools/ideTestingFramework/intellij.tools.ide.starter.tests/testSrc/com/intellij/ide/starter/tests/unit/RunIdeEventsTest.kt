package com.intellij.ide.starter.tests.unit

import com.intellij.ide.starter.bus.EventState
import com.intellij.ide.starter.bus.StarterListener
import com.intellij.ide.starter.bus.subscribe
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.ide.InstalledIde
import com.intellij.ide.starter.ide.command.CommandChain
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.path.IDEDataPaths
import com.intellij.ide.starter.runner.IdeLaunchEvent
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
import org.junit.jupiter.api.io.TempDir
import org.kodein.di.direct
import org.kodein.di.instance
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import java.nio.file.Path
import java.util.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds


@ExtendWith(MockitoExtension::class)
class RunIdeEventsTest {

  @TempDir
  lateinit var testDirectory: Path

  @Mock
  private lateinit var testCase: TestCase

  @Mock
  private lateinit var ide: InstalledIde

  @AfterEach
  fun afterEach() {
    StarterListener.unsubscribe()
  }

  @Disabled("Is not stable")
  @RepeatedTest(value = 200)
  fun `events for ide launch should be fired`(testInfo: TestInfo) {
    val firedEvents = Collections.synchronizedList(mutableListOf<IdeLaunchEvent>())
    StarterListener.subscribe { event: IdeLaunchEvent -> synchronized(firedEvents) { firedEvents.add(event) } }

    val testName = testInfo.displayName.hyphenateTestName()
    val paths = IDEDataPaths.createPaths(testName, testDirectory, useInMemoryFs = false)

    val projectHome = testCase.projectInfo?.downloadAndUnpackProject()
    val context = IDETestContext(paths = paths,
                                 ide = ide,
                                 testCase = testCase,
                                 testName = testName,
                                 _resolvedProjectHome = projectHome,
                                 patchVMOptions = { this },
                                 ciServer = di.direct.instance())

    try {
      context.runIDE(commands = CommandChain())
    }
    catch (_: Throwable) {
    }

    runBlocking {
      eventually(duration = 2.seconds, poll = 200.milliseconds) {
        withClue("There should be 2 events fired. Events: ${firedEvents.map { it.state }}") {
          firedEvents.shouldHaveSize(2)
        }
      }
    }

    assertSoftly {
      withClue("During IDE run should be fired 2 events: before ide start and after ide finished. " +
               "Events: ${firedEvents.map { it.state }}") {
        firedEvents.shouldForAtLeastOne { it.state.shouldBe(EventState.BEFORE) }
        firedEvents.shouldForAtLeastOne { it.state.shouldBe(EventState.AFTER) }
      }
    }
  }
}