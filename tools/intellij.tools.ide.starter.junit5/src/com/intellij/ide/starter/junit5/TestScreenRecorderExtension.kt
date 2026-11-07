package com.intellij.ide.starter.junit5

import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.ci.reportTestInfraFailure
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.ide.isRemDevContext
import com.intellij.ide.starter.runner.CurrentTestMethod
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.ide.starter.runner.events.IdeAfterLaunchEvent
import com.intellij.ide.starter.runner.events.IdeLaunchEvent
import com.intellij.ide.starter.runner.events.MetricsPublishedEvent
import com.intellij.ide.starter.screenRecorder.IDEScreenRecorder
import com.intellij.ide.starter.utils.FileSystem.listDirectoryEntriesQuietly
import com.intellij.ide.starter.utils.catchAll
import com.intellij.ide.starter.utils.flattenedTestName
import com.intellij.tools.ide.starter.bus.EventsBus
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.AfterTestExecutionCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import java.lang.annotation.Inherited
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.jvm.optionals.getOrNull

@Inherited
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class WithScreenRecording

internal class TestScreenRecorderExtension(
  private val isRunningOnCI: () -> Boolean = { CIServer.instance.isBuildRunningOnCI },
  private val reportTestInfraFailure: (testName: String, message: String) -> Unit = { testName, message ->
    CIServer.instance.reportTestInfraFailure(testName, message)
  },
  private val createRecorder: (IDERunContext, Path, String) -> IDEScreenRecorder = { runContext, directory, prefix ->
    IDEScreenRecorder.create(runContext, recordingDir = directory, recordingFilePrefix = prefix)
  },
) : BeforeAllCallback, BeforeEachCallback, AfterTestExecutionCallback, AfterEachCallback, AfterAllCallback {

  private var recording: Recording? = null
  private var liveRunContext: IDERunContext? = null
  private var currentTestRecordingEnabled: Boolean? = null
  private var currentTest: ExtensionContext? = null
  private var missingPerformanceTestReported: Boolean = false

  override fun beforeAll(context: ExtensionContext) {
    subscribeToIdeLaunchEvents()
  }

  override fun beforeEach(context: ExtensionContext) {
    subscribeToIdeLaunchEvents() // to handle StarterConfigurationStorage.afterEachMessageBusCleanup
    currentTest = context
    missingPerformanceTestReported = false
    // Every test is recorded, except a performance test: a recorder would distort its measurements.
    currentTestRecordingEnabled = !context.isPerformanceTest()
    liveRunContext?.let { startRecorderIfNeeded(it) }
  }

  /**
   * Stops the recording as early as possible, right after the test body: JUnit runs every `AfterTestExecutionCallback` ahead of every
   * `AfterEachCallback`, so whatever reads the test's artifacts next finds a finished file.
   */
  override fun afterTestExecution(context: ExtensionContext) {
    currentTestRecordingEnabled = null
    stopAndPublishRecording()
  }

  /**
   * Stops a recording [afterTestExecution] was never told about: JUnit skips that callback when a `@BeforeEach` fails, and a recording
   * left running would keep the next test from starting one of its own.
   */
  override fun afterEach(context: ExtensionContext) {
    currentTestRecordingEnabled = null
    currentTest = null
    stopAndPublishRecording()
  }

  // No unsubscribe: StarterBusAllTestCallback wipes the bus after every container anyway.
  override fun afterAll(context: ExtensionContext) {
    currentTestRecordingEnabled = null
    currentTest = null
    liveRunContext = null
    stopAndPublishRecording()
  }

  /**
   * Subscribes under [IDEScreenRecorder]'s class rather than under this extension, because [IDERunContext.withScreenRecording] subscribes
   * under that same key. The bus keeps one subscription per subscriber and event and silently drops the second, so the two ways of
   * recording a run can never both record it: whichever subscribes first takes it, and the other does nothing.
   *
   * This extension subscribes from [beforeAll], ahead of any `configure {}` block, so within this repository it is the one that records,
   * and `withScreenRecording` is left for callers outside it, who have no extension to record for them.
   *
   * Called from [beforeEach] as well, because `StarterBusEachTestCallback` wipes the bus after every test.
   *
   * The reminder about a missing [PerformanceTest] subscribes to [MetricsPublishedEvent] under the class of this extension.
   * The key of [IDEScreenRecorder] is for the recording only.
   */
  private fun subscribeToIdeLaunchEvents() {
    EventsBus.subscribe(TestScreenRecorderExtension::class.java) { _: MetricsPublishedEvent ->
      reportMissingPerformanceTestAnnotation()
    }
    EventsBus.subscribe(IDEScreenRecorder::class.java) { event: IdeLaunchEvent ->
      if (event.runContext.testContext.isRemDevContext()) return@subscribe // skip remdev backend

      liveRunContext = event.runContext
      startRecorderIfNeeded(event.runContext)
    }
    EventsBus.subscribe(IDEScreenRecorder::class.java) { event: IdeAfterLaunchEvent ->
      if (liveRunContext === event.runContext) liveRunContext = null
    }
  }

  private fun startRecorderIfNeeded(runContext: IDERunContext) {
    if (currentTestRecordingEnabled != true || recording != null) return

    catchAll("start screen recording") {
      val testContext = runContext.testContext
      val testName = checkNotNull(CurrentTestMethod.get()).displayName
      val directory = IDEScreenRecorder.recordingDirIn(testContext.paths.testHome).resolve(testName.flattenedTestName())
      val artifactPath = runContext.lastIdeReportingData.artifactPath
      val recorder = createRecorder(runContext, directory, IDEScreenRecorder.recordingFilePrefix())
      recording = Recording(testContext, directory, artifactPath, recorder)
      recorder.start()
    }
  }

  private fun stopAndPublishRecording() {
    val completedRecording = recording ?: return
    recording = null
    catchAll("stop screen recording") { completedRecording.recorder.stop() }
    catchAll("publish screen recording") {
      if (completedRecording.directory.listDirectoryEntriesQuietly()?.isNotEmpty() == true) {
        completedRecording.testContext.publishArtifact(
          source = completedRecording.directory,
          artifactPath = "${completedRecording.artifactPath}/screenRecording",
          artifactName = completedRecording.directory.name,
        )
      }
    }
  }

  private fun reportMissingPerformanceTestAnnotation() {
    val test = currentTest ?: return
    if (missingPerformanceTestReported || test.isPerformanceTest() || isRunningOnCI()) return
    missingPerformanceTestReported = true
    catchAll("report missing @PerformanceTest") {
      val testName = listOfNotNull(test.testClass.getOrNull()?.name, test.testMethod.getOrNull()?.name).joinToString("#")
      reportTestInfraFailure(
        "${test.uniqueId}.performanceTest",
        "Test $testName publishes performance metrics but has no @${PerformanceTest::class.qualifiedName} annotation. " +
        "Without the annotation, the screen of the test is recorded, and the recording can distort the metrics. " +
        "Add @PerformanceTest to the test class or the test method.",
      )
    }
  }

  private fun ExtensionContext.isPerformanceTest(): Boolean =
    isPerformanceTest(testClass.getOrNull(), testMethod.getOrNull())

  private data class Recording(
    val testContext: IDETestContext,
    val directory: Path,
    val artifactPath: String,
    val recorder: IDEScreenRecorder,
  )
}
