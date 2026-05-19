package com.intellij.mcpserver.toolsets.general

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.mcpserver.GeneralMcpToolsetTestBase
import com.intellij.mcpserver.util.projectDirectory
import com.intellij.mcpserver.util.relativizeIfPossible
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds

class LintFilesAnalysisSupportTest : GeneralMcpToolsetTestBase() {
  @Test
  fun createLintFilesBatchTimeouts_reserves_clamped_headroom() {
    val shortTimeouts = createLintFilesBatchTimeouts(timeoutMs = 100, currentTimeMs = 1_000)
    assertThat(shortTimeouts.analysisTimeoutMs).isEqualTo(50)
    assertThat(shortTimeouts.timeoutContext.requestDeadlineMs).isEqualTo(1_050)

    val longTimeouts = createLintFilesBatchTimeouts(timeoutMs = 200_000, currentTimeMs = 1_000)
    assertThat(longTimeouts.analysisTimeoutMs).isEqualTo(195_000)
    assertThat(longTimeouts.timeoutContext.requestDeadlineMs).isEqualTo(196_000)
  }

  @Test
  fun collectLintFileResults_serializes_main_passes_within_request(): Unit = runBlocking(Dispatchers.Default) {
    val mainPath = relativePath(mainJavaFile)
    val testPath = relativePath(testJavaFile)
    val firstStarted = CompletableDeferred<String>()
    val releaseFirst = CompletableDeferred<Unit>()
    val secondStarted = CompletableDeferred<String>()
    val active = AtomicInteger()
    val maxActive = AtomicInteger()
    val firstFile = AtomicReference<String?>(null)

    withLintMainPassesRunnerOverride(
      project,
      runner = { request ->
        val concurrent = active.incrementAndGet()
        maxActive.updateAndGet { maxOf(it, concurrent) }
        try {
          if (firstFile.compareAndSet(null, request.filePath)) {
            firstStarted.complete(request.filePath)
            releaseFirst.await()
          }
          else {
            secondStarted.complete(request.filePath)
          }
          emptyList()
        }
        finally {
          active.decrementAndGet()
        }
      },
    ) {
      coroutineScope {
        val resultsJob = async {
          collectResults(listOf(mainPath, testPath))
        }

        firstStarted.await()
        assertThat(withTimeoutOrNull(200.milliseconds) { secondStarted.await() }).isNull()

        releaseFirst.complete(Unit)
        withTimeout(1_000.milliseconds) { secondStarted.await() }
        assertThat(resultsJob.await().map { it.filePath }).containsExactly(mainPath, testPath)
      }
    }

    assertThat(maxActive.get()).isEqualTo(1)
  }

  @Test
  fun collectLintFileResults_allows_other_files_to_progress_before_locked_main_passes(): Unit = runBlocking(Dispatchers.Default) {
    val mainPath = relativePath(mainJavaFile)
    val testPath = relativePath(testJavaFile)
    val firstStarted = CompletableDeferred<Unit>()
    val releaseFirst = CompletableDeferred<Unit>()
    val secondReachedPreMainPasses = CompletableDeferred<Unit>()
    val allowSecondToEnterMainPasses = CompletableDeferred<Unit>()
    val secondStarted = CompletableDeferred<Unit>()

    withLintBeforeMainPassesOverride(
      project,
      actionOverride = { filePath ->
        if (filePath == testPath) {
          secondReachedPreMainPasses.complete(Unit)
          allowSecondToEnterMainPasses.await()
        }
      },
    ) {
      withLintMainPassesRunnerOverride(
        project,
        runner = { request ->
          when (request.filePath) {
            mainPath -> {
              firstStarted.complete(Unit)
              releaseFirst.await()
            }

            testPath -> secondStarted.complete(Unit)
          }
          emptyList()
        },
      ) {
        coroutineScope {
          val resultsJob = async {
            collectResults(listOf(mainPath, testPath))
          }

          firstStarted.await()
          withTimeout(1_000.milliseconds) { secondReachedPreMainPasses.await() }
          allowSecondToEnterMainPasses.complete(Unit)
          assertThat(withTimeoutOrNull(200.milliseconds) { secondStarted.await() }).isNull()

          releaseFirst.complete(Unit)
          withTimeout(1_000.milliseconds) { secondStarted.await() }
          assertThat(resultsJob.await().map { it.filePath }).containsExactly(mainPath, testPath)
        }
      }
    }
  }

  @Test
  fun collectLintFileResults_serializes_overlapping_requests_per_project(): Unit = runBlocking(Dispatchers.Default) {
    val mainPath = relativePath(mainJavaFile)
    val testPath = relativePath(testJavaFile)
    val firstStarted = CompletableDeferred<Unit>()
    val releaseFirst = CompletableDeferred<Unit>()
    val secondScheduled = CompletableDeferred<Unit>()
    val secondStarted = CompletableDeferred<Unit>()
    val active = AtomicInteger()
    val maxActive = AtomicInteger()

    withLintMainPassesRunnerOverride(
      project,
      runner = { request ->
        val concurrent = active.incrementAndGet()
        maxActive.updateAndGet { maxOf(it, concurrent) }
        try {
          when (request.filePath) {
            mainPath -> {
              firstStarted.complete(Unit)
              releaseFirst.await()
            }

            testPath -> secondStarted.complete(Unit)
          }
          emptyList()
        }
        finally {
          active.decrementAndGet()
        }
      },
    ) {
      coroutineScope {
        val firstRequest = async {
          collectResults(listOf(mainPath))
        }
        firstStarted.await()

        val secondRequest = async {
          secondScheduled.complete(Unit)
          collectResults(listOf(testPath))
        }
        secondScheduled.await()

        assertThat(withTimeoutOrNull(200.milliseconds) { secondStarted.await() }).isNull()

        releaseFirst.complete(Unit)
        withTimeout(1_000.milliseconds) { secondStarted.await() }

        assertThat(firstRequest.await().map { it.filePath }).containsExactly(mainPath)
        assertThat(secondRequest.await().map { it.filePath }).containsExactly(testPath)
      }
    }

    assertThat(maxActive.get()).isEqualTo(1)
  }

  @Test
  fun collectLintFileResults_retries_current_file_after_retriable_pce(): Unit = runBlocking(Dispatchers.Default) {
    val mainPath = relativePath(mainJavaFile)
    val mainAttempts = CopyOnWriteArrayList<Int>()
    val firstObservedMainAttempt = AtomicInteger()

    withLintMainPassesRunnerOverride(
      project,
      runner = { request ->
        if (request.filePath == mainPath) {
          mainAttempts.add(request.attempt)
          val firstAttempt = firstObservedMainAttempt.get()
          if (firstAttempt == 0 && firstObservedMainAttempt.compareAndSet(0, request.attempt)) {
            throw ProcessCanceledException()
          }
        }
        emptyList()
      },
    ) {
      val results = collectResults(listOf(mainPath))
      assertThat(results.map { it.filePath }).containsExactly(mainPath)
    }

    assertThat(mainAttempts).hasSize(2)
    assertThat(mainAttempts[1]).isEqualTo(mainAttempts[0] + 1)
  }

  @Test
  fun collectLintFileResults_waits_for_write_action_completion_before_retry(): Unit = runBlocking(Dispatchers.Default) {
    val mainPath = relativePath(mainJavaFile)
    val firstAttemptStarted = CompletableDeferred<Unit>()
    val allowFirstAttemptToFail = CompletableDeferred<Unit>()
    val writeActionStarted = CompletableDeferred<Unit>()
    val secondAttemptStarted = CompletableDeferred<Unit>()
    val releaseWriteAction = CountDownLatch(1)
    val attempts = CopyOnWriteArrayList<Int>()
    val firstObservedMainAttempt = AtomicInteger()

    withLintMainPassesRunnerOverride(
      project,
      runner = { request ->
        if (request.filePath == mainPath) {
          attempts.add(request.attempt)
          val firstAttempt = firstObservedMainAttempt.get()
          when {
            firstAttempt == 0 && firstObservedMainAttempt.compareAndSet(0, request.attempt) -> {
              firstAttemptStarted.complete(Unit)
              allowFirstAttemptToFail.await()
              throw ProcessCanceledException()
            }

            firstAttempt != 0 && request.attempt == firstAttempt + 1 -> secondAttemptStarted.complete(Unit)
          }
        }
        emptyList()
      },
    ) {
      coroutineScope {
        val resultsJob = async {
          collectResults(listOf(mainPath))
        }

        try {
          firstAttemptStarted.await()

          val writeActionJob = async(Dispatchers.Default) {
            WriteAction.runAndWait<RuntimeException> {
              writeActionStarted.complete(Unit)
              releaseWriteAction.await()
            }
          }

          withTimeout(1_000.milliseconds) { writeActionStarted.await() }
          allowFirstAttemptToFail.complete(Unit)

          assertThat(withTimeoutOrNull(200.milliseconds) { secondAttemptStarted.await() }).isNull()

          releaseWriteAction.countDown()
          withTimeout(1_000.milliseconds) { secondAttemptStarted.await() }

          assertThat(resultsJob.await().map { it.filePath }).containsExactly(mainPath)
          writeActionJob.await()
        }
        finally {
          releaseWriteAction.countDown()
          allowFirstAttemptToFail.complete(Unit)
        }
      }
    }

    assertThat(attempts).hasSize(2)
    assertThat(attempts[1]).isEqualTo(attempts[0] + 1)
  }

  @Test
  fun collectLintFileResults_times_out_current_file_without_consuming_next_file_budget(): Unit = runBlocking(Dispatchers.Default) {
    val mainPath = relativePath(mainJavaFile)
    val testPath = relativePath(testJavaFile)
    val mainStarted = CompletableDeferred<Unit>()
    val allowQueuedFile = CompletableDeferred<Unit>()

    withLintBeforeMainPassesOverride(
      project,
      actionOverride = { filePath ->
        if (filePath == testPath) {
          mainStarted.await()
          allowQueuedFile.await()
        }
      },
    ) {
      withLintMainPassesRunnerOverride(
        project,
        runner = { request ->
          when (request.filePath) {
            mainPath -> {
              mainStarted.complete(Unit)
              allowQueuedFile.complete(Unit)
              awaitCancellation()
            }

            testPath -> emptyList()
            else -> emptyList()
          }
        },
      ) {
        val results = collectResults(
          filePaths = listOf(mainPath, testPath),
          timeoutContext = LintFilesTimeoutContext(
            requestDeadlineMs = System.currentTimeMillis() + 400,
            perFileTimeoutMs = 100,
          ),
        )

        assertThat(results.map { it.filePath }).containsExactly(mainPath, testPath)
        assertThat(results[0].timedOut).isTrue()
        assertThat(results[0].problems).isEmpty()
        assertThat(results[1].timedOut).isFalse()
      }
    }
  }

  @Test
  fun lint_files_returns_partial_results_when_runner_times_out() = runBlocking(Dispatchers.Default) {
    val mainPath = relativePath(mainJavaFile)

    withLintMainPassesRunnerOverride(
      project,
      runner = {
        awaitCancellation()
      },
    ) {
      testMcpTool(
        AnalysisToolset::lint_files.name,
        buildJsonObject {
          put("files", buildJsonArray {
            add(JsonPrimitive(mainPath))
          })
          put("timeout", JsonPrimitive(100))
        },
      ) { result ->
        val text = result.textContent.text
        assertThat(text).contains(""""items":[]""")
        assertThat(text).contains(""""more":true""")
      }
    }
  }

  private suspend fun collectResults(
    filePaths: List<String>,
    timeoutContext: LintFilesTimeoutContext? = null,
  ): List<AnalysisToolset.LintFileResult> {
    val requestedFiles = prepareRequestedLintFiles(project, filePaths)
    val resolvedFiles = prepareLintFiles(requestedFiles)
    val inspectionProfile = project.serviceAsync<InspectionProjectProfileManager>().currentProfile
    val results = ConcurrentHashMap<String, AnalysisToolset.LintFileResult>()
    collectLintFileResults(
      project = project,
      resolvedFiles = resolvedFiles,
      minSeverity = HighlightSeverity.WARNING,
      inspectionProfile = inspectionProfile,
      onFileResult = { result ->
        results[result.filePath] = result
      },
      timeoutContext = timeoutContext,
    )
    return filePaths.mapNotNull(results::get)
  }

  private fun relativePath(file: com.intellij.openapi.vfs.VirtualFile): String {
    return project.projectDirectory.relativizeIfPossible(file)
  }
}
