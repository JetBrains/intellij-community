package com.intellij.ide.starter.junit5

import com.intellij.ide.starter.junit5.config.KillOutdatedProcessesAfterEach
import com.intellij.ide.starter.process.ProcessKiller
import com.intellij.ide.starter.project.NoProject
import com.intellij.ide.starter.runner.Starter
import com.intellij.ide.starter.runner.events.IdeLaunchEvent
import com.intellij.tools.ide.starter.bus.EventsBus
import examples.data.TestCases
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTimedValue

/**
 * End-to-end check that [ProcessKiller] correctly stops a *running IDE* — not a synthetic
 * shell wrapper. Exercises the real process topology `ProcessExecutor` creates on each OS:
 *  - Linux: `xvfb-run`/`dash` → `Xvfb` + idea JVM (the topology that motivated the
 *    descendants-snapshot logic in [ProcessKiller.gracefulStop]).
 *  - macOS: idea launcher → JVM, no shell wrapper.
 *  - Windows: `idea64.exe` launcher → JVM (+ fsnotifier). Graceful kill is delivered via
 *    Ctrl+C through WinP; forceful kill goes through WinP/Job-Objects (see the MRI-4085
 *    note in [ProcessKiller]).
 *
 * Verifies, for both `gracefullyAtFirst = true` and `gracefullyAtFirst = false`:
 *   1. [ProcessKiller.killProcess] returns true (confirmed dead).
 *   2. The IDE wrapper process is dead afterwards.
 *   3. Every descendant captured before the kill is also dead.
 *   4. The kill completes within `killBudget` — guards against a graceful first step that
 *      is silently a no-op and only finishes after the full graceful timeout fires.
 *
 * The IDE is launched with `NoProject` and without any test script — it just opens the
 * Welcome Screen and idles. Everything related to termination is driven from the test:
 * one coroutine runs the IDE (via [com.intellij.ide.starter.ide.IDETestContext.runIdeSuspending]),
 * another waits for the wrapper PID + descendants and calls `ProcessKiller`. The IDE coroutine
 * unblocks naturally once the wrapper exits.
 */
@ExtendWith(KillOutdatedProcessesAfterEach::class)
class RunningIdeProcessKillerTest {

  @AfterEach
  fun afterEach() {
    EventsBus.unsubscribeAll()
  }

  @Test
  fun `ProcessKiller with graceful-first kills running IDE and its descendants`(testInfo: TestInfo) {
    // Generous budget for JVM shutdown hooks; anything close to this means graceful was a no-op.
    runProcessKillerOnLiveIde(testInfo, gracefullyAtFirst = true, killBudget = 20.seconds)
  }

  @Test
  fun `ProcessKiller with forceful-only kills running IDE and its descendants`(testInfo: TestInfo) {
    // SIGKILL is essentially instant — only kernel reap + Java exit observation.
    runProcessKillerOnLiveIde(testInfo, gracefullyAtFirst = false, killBudget = 5.seconds)
  }

  private fun runProcessKillerOnLiveIde(
    testInfo: TestInfo,
    gracefullyAtFirst: Boolean,
    killBudget: Duration,
  ) = runBlocking {
    val context = Starter.newContext(
      testInfo.hyphenateWithClass(),
      TestCases.IU.withProject(NoProject).useRelease(),
    )

    // The wrapper PID is published via IdeLaunchEvent (fired from onProcessCreated in
    // ProcessExecutor, right after ProcessBuilder.start()).
    val wrapperHandleDeferred = CompletableDeferred<ProcessHandle>()
    EventsBus.subscribe(this@RunningIdeProcessKillerTest) { event: IdeLaunchEvent ->
      ProcessHandle.of(event.ideProcess.id.toLong())
        .ifPresent { wrapperHandleDeferred.complete(it) }
    }

    lateinit var wrapper: ProcessHandle
    lateinit var descendantsBeforeKill: List<ProcessHandle>

    // Run the IDE and the killer concurrently in the same coroutine scope:
    // - ideJob blocks until the wrapper exits (which happens when the killer reaps it).
    // - the outer body waits for the wrapper to come up + its children, then kills it.
    coroutineScope {
      val ideJob = launch(Dispatchers.IO) {
        context.runIdeSuspending(
          runTimeout = 5.minutes,
          expectedKill = true,
          // JVM killed by SIGTERM/SIGKILL exits with 143/137 — irrelevant for what we test.
          analyzeProcessExit = false,
        )
      }

      wrapper = withTimeout(2.minutes) { wrapperHandleDeferred.await() }
      // We don't enumerate descendants on Windows — PID rotation makes captured handles
      // unreliable to assert on later (see the MRI-4085 note in ProcessKiller). The forceful
      // path on Windows goes through WinP/Job-Objects which reaps the whole tree internally,
      // so wrapper-death + timing is enough to validate the end result.
      descendantsBeforeKill = if (OS.WINDOWS.isCurrentOs) emptyList() else awaitDescendants(wrapper)

      // Measure the kill itself — the whole point is verifying it's not a no-op that times out.
      // `killBudget` is the upper bound on the path that actually runs (the other timeout is
      // never reached: if graceful succeeds we never enter forceful; if graceful is skipped
      // entirely, gracefulTimeout doesn't matter). Set both to the same value so the assertion
      // below cleanly says "wrapped up in time".
      val (killed, elapsed) = measureTimedValue {
        ProcessKiller.killProcess(
          wrapper,
          cleanUpDescendants = true,
          gracefullyAtFirst = gracefullyAtFirst,
          gracefulTimeout = killBudget,
          forcefulTimeout = killBudget,
        )
      }
      assertTrue(killed, "ProcessKiller.killProcess should return true (confirmed dead)")
      assertTrue(
        elapsed < killBudget,
        "ProcessKiller.killProcess took $elapsed, which is >= killBudget ($killBudget). " +
        if (gracefullyAtFirst) {
          "The graceful first attempt looks like a no-op — we waited the full gracefulTimeout before " +
          "the forceful fallback kicked in. This is exactly the regression this test guards against."
        }
        else {
          "Forceful kill (SIGKILL) should reap the process tree essentially instantly; hitting " +
          "forcefulTimeout means the forceful path is broken."
        }
      )

      ideJob.join() // unblocks once the wrapper exits
    }

    assertFalse(wrapper.isAlive, "IDE wrapper PID ${wrapper.pid()} should be dead after ProcessKiller.killProcess")
    descendantsBeforeKill.forEach {
      assertFalse(
        it.isAlive,
        "IDE descendant PID ${it.pid()} (${it.info().command().orElse("?")}) should be dead after ProcessKiller.killProcess"
      )
    }
  }

  /** Suspends until [wrapper] has at least one descendant, or throws on timeout. */
  private suspend fun awaitDescendants(wrapper: ProcessHandle, timeout: Duration = 60.seconds): List<ProcessHandle> =
    withTimeout(timeout) {
      var descendants = wrapper.descendants().toList()
      while (descendants.isEmpty()) {
        delay(300.milliseconds)
        descendants = wrapper.descendants().toList()
      }
      descendants
    }
}
