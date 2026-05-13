package com.intellij.ide.starter.junit5

import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.path.IDEDataPaths
import com.intellij.ide.starter.runner.CurrentTestMethod
import com.intellij.ide.starter.runner.IDEHandle
import com.intellij.ide.starter.runner.IDEReportingData
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.ide.starter.runner.TestMethod
import com.intellij.ide.starter.runner.events.IdeAfterLaunchEvent
import com.intellij.ide.starter.runner.events.IdeLaunchEvent
import com.intellij.ide.starter.screenRecorder.IDEScreenRecorder
import com.intellij.tools.ide.starter.bus.EventsBus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import java.nio.file.Path
import java.util.Optional
import kotlin.io.path.createDirectories
import kotlin.io.path.name
import kotlin.io.path.writeText

private const val SCREEN_RECORDING_ENABLED = "SCREEN_RECORDING_ENABLED"

/**
 * Drives [TestScreenRecorderExtension]'s JUnit callbacks by hand, so that a case can post IDE launch events at the moment a real run would:
 * before the test for an IDE shared by the class, during the test for one the test starts itself, or twice over for a restart.
 */
internal class TestScreenRecorderExtensionTest {
  private data class CreatedRecording(
    val runContext: IDERunContext,
    val directory: Path,
    val prefix: String,
    val recorder: IDEScreenRecorder,
  )

  @TempDir
  lateinit var testHome: Path

  private val recordings = mutableListOf<CreatedRecording>()
  private val extension = TestScreenRecorderExtension { runContext, directory, prefix -> createRecorder(runContext, directory, prefix) }
  private val context = testContext()

  @BeforeEach
  fun setUp() {
    // A case starts from a clean bus rather than inheriting the subscriptions another case left behind.
    EventsBus.unsubscribeAll()
    extension.beforeAll(context)
  }

  @AfterEach
  fun tearDown() {
    try {
      extension.afterAll(context)
    }
    finally {
      CurrentTestMethod.set(null)
      EventsBus.unsubscribeAll()
      ConfigurationStorage.instance().resetToDefault()
    }
  }

  @Test
  fun `records a shared IDE started before the test`() {
    val sharedRun = runContext()
    ideLaunched(sharedRun)
    assertTrue(recordings.isEmpty(), "class setup is not a test and must not be recorded")

    listOf(testContext(), testContext(methodName = "anotherRecordedTest")).forEach { test ->
      startTest(test)
      extension.afterTestExecution(test)
    }

    assertEquals(2, recordings.size, "each test gets a recording of its own")
    recordings.forEach { recording ->
      assertTrue(recording.runContext === sharedRun, "the shared IDE is the one recorded")
      assertEquals(testHome.resolve("screenRecording"), recording.directory.parent)
      val order = inOrder(recording.recorder, sharedRun.testContext)
      order.verify(recording.recorder).start()
      order.verify(recording.recorder).stop()
      order.verify(sharedRun.testContext).publishArtifact(recording.directory, "test/screenRecording", recording.directory.name)
    }
    assertEquals(
      listOf("recorded-test", "another-recorded-test"), recordings.map { it.directory.name },
      "a test names the directory its recordings go into",
    )

    ideLaunched(runContext())
    extension.afterAll(context)
    assertEquals(2, recordings.size, "class teardown is not a test and must not be recorded")
    recordings.forEach {
      verify(it.recorder, times(1)).stop()
      verify(sharedRun.testContext, times(1)).publishArtifact(it.directory, "test/screenRecording", it.directory.name)
    }
  }

  /**
   * A user's `@BeforeEach` runs after this extension's [TestScreenRecorderExtension.beforeEach], so an IDE started there arrives by event
   * just like one started in the test body: both are this one path.
   */
  @Test
  fun `records an IDE started after the test began`() {
    val test = testContext()
    startTest(test)
    assertTrue(recordings.isEmpty(), "there is nothing to record until an IDE is launched")

    val runContext = runContext()
    ideLaunched(runContext)

    val recording = recordings.single()
    verify(recording.recorder).start()
    // JUnit spells a display name with its parentheses, and the directory is named after the test alone: the file adds the timestamp.
    assertEquals("recorded-test", recording.directory.name)
    assertTrue(recording.prefix.startsWith("ScreenRecording"), "unexpected prefix ${recording.prefix}")

    extension.afterTestExecution(test)
    verify(recording.recorder).stop()
    verify(runContext.testContext).publishArtifact(recording.directory, "test/screenRecording", recording.directory.name)
  }

  @Test
  fun `does not record a test without the annotation`() {
    val unrecorded = testContext(methodName = "unrecordedTest")
    ideLaunched(runContext())
    startTest(unrecorded)
    ideLaunched(runContext())
    extension.afterTestExecution(unrecorded)
    assertTrue(recordings.isEmpty(), "recording an unannotated test would run a recorder for every test in the repository")

    ConfigurationStorage.instance().put(SCREEN_RECORDING_ENABLED, true)
    startTest(unrecorded)
    ideLaunched(runContext())
    extension.afterTestExecution(unrecorded)
    assertEquals(1, recordings.size, "the setting records a test that carries no annotation")
  }

  @Test
  fun `keeps one recording across an IDE restart`() {
    val firstRun = runContext()
    val restartedRun = runContext()
    startTest(context)
    ideLaunched(firstRun)
    ideClosed(firstRun)
    ideLaunched(restartedRun)
    ideClosed(restartedRun)
    val recording = recordings.single()
    verify(recording.recorder, never()).stop()

    extension.afterTestExecution(context)
    verify(recording.recorder, times(1)).start()
    verify(recording.recorder, times(1)).stop()
  }

  @Test
  fun `leaves the run to whoever subscribed first`() {
    // IDERunContext.withScreenRecording subscribes under the same key as this extension does, and the bus keeps one subscription per
    // subscriber and event, so a run something else already records is not recorded a second time here.
    EventsBus.unsubscribeAll()
    EventsBus.subscribe(IDEScreenRecorder::class.java) { _: IdeLaunchEvent -> }
    extension.beforeAll(context)

    startTest(context)
    ideLaunched(runContext())
    extension.afterTestExecution(context)

    assertTrue(recordings.isEmpty(), "one IDE is only ever recorded once")
  }

  private fun ideLaunched(runContext: IDERunContext) {
    EventsBus.postAndWaitProcessing(IdeLaunchEvent(runContext, mock(IDEHandle::class.java)), ignoreExceptions = false)
  }

  private fun startTest(test: ExtensionContext) {
    CurrentTestMethod.set(TestMethod(test.testMethod.get().name, test.displayName, test.testClass.get()))
    extension.beforeEach(test)
  }

  private fun ideClosed(runContext: IDERunContext) {
    EventsBus.postAndWaitProcessing(IdeAfterLaunchEvent(runContext, isRunSuccessful = true), ignoreExceptions = false)
  }

  private fun createRecorder(runContext: IDERunContext, directory: Path, prefix: String): IDEScreenRecorder {
    directory.createDirectories().resolve("$prefix.mkv").writeText("test recording")
    val recorder = mock(IDEScreenRecorder::class.java)
    recordings.add(CreatedRecording(runContext, directory, prefix, recorder))
    return recorder
  }

  private fun runContext(testContext: IDETestContext = mock(IDETestContext::class.java)): IDERunContext {
    val paths = mock(IDEDataPaths::class.java)
    doReturn(testHome).`when`(paths).testHome
    doReturn(paths).`when`(testContext).paths
    doReturn("test").`when`(testContext).testName
    val reportingData = mock(IDEReportingData::class.java)
    doReturn("test").`when`(reportingData).artifactPath
    return mock(IDERunContext::class.java).also {
      doReturn(testContext).`when`(it).testContext
      doReturn(reportingData).`when`(it).lastIdeReportingData
    }
  }

  /**
   * JUnit spells a display name with its parentheses - `recordedTest()` - and a parameterized one with its arguments, so a case gives the
   * display name apart from the method that carries the annotation.
   */
  private fun testContext(
    methodName: String = "recordedTest",
    displayName: String = "$methodName()",
    testClass: Class<*> = TestMethods::class.java,
  ): ExtensionContext =
    mock(ExtensionContext::class.java).also {
      doReturn(displayName).`when`(it).displayName
      doReturn(Optional.of(testClass)).`when`(it).testClass
      doReturn(Optional.of(testClass.getMethod(methodName))).`when`(it).testMethod
    }

  private class TestMethods {
    @WithScreenRecording
    fun recordedTest() {}

    @WithScreenRecording
    fun anotherRecordedTest() {}

    fun unrecordedTest() {}
  }
}
