// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.util

import com.google.common.base.Ascii
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.isWindows
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.frontend.session.TerminalSessionsManager
import com.intellij.terminal.frontend.session.createStandardStateAwareTerminalSession
import com.intellij.terminal.frontend.session.createTerminalSession
import com.intellij.terminal.frontend.session.startStandardTerminalSession
import com.intellij.terminal.frontend.session.startTerminalProcess
import com.intellij.util.PathUtil
import com.intellij.util.asDisposable
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.TtyConnector
import com.pty4j.windows.conpty.WinConPtyProcess
import com.pty4j.windows.cygwin.CygwinPtyProcess
import com.pty4j.windows.winpty.WinPtyProcess
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.JBTerminalSystemSettingsProvider
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.TerminalEmulatorType
import org.jetbrains.plugins.terminal.TerminalEngine
import org.jetbrains.plugins.terminal.TerminalOptionsProvider
import org.jetbrains.plugins.terminal.runner.LocalShellIntegrationInjector
import org.jetbrains.plugins.terminal.runner.LocalTerminalStartCommandBuilder
import org.jetbrains.plugins.terminal.session.impl.TerminalContentUpdatedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalCursorPositionChangedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalInitialStateEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalOutputEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalSession
import org.jetbrains.plugins.terminal.session.impl.TerminalStartupOptionsImpl
import org.jetbrains.plugins.terminal.session.impl.TerminalStateChangedEvent
import org.jetbrains.plugins.terminal.session.impl.dto.toState
import org.jetbrains.plugins.terminal.startup.TerminalProcessType
import org.jetbrains.plugins.terminal.util.ShellEelProcess
import org.jetbrains.plugins.terminal.view.impl.MutableTerminalOutputModel
import org.jetbrains.plugins.terminal.view.impl.updateContent
import org.junit.Assert
import org.junit.Assume
import org.junit.jupiter.api.Assumptions
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object TerminalSessionTestUtil {
  /** Starts the same terminal session used in production */
  fun startTestTerminalSession(
    project: Project,
    shellPath: String,
    workingDirectory: String?,
    coroutineScope: CoroutineScope,
  ): TestTerminalSessionResult {
    val shellCommand = createShellCommand(shellPath)
    val options = ShellStartupOptions.Builder()
      .shellCommand(shellCommand)
      .workingDirectory(workingDirectory)
      .build()
    return startTestTerminalSession(project, options, isLowLevelSession = false, coroutineScope)
  }

  /**
   * @param options should already contain configured [ShellStartupOptions.shellCommand].
   * Use [createShellCommand] to create the default command from the shell path.
   *
   * @param isLowLevelSession whether the same session should be used as in the production or its low-level JediTerm implementation.
   * Low-level session outputs the events in their natural order,
   * while production one replaces some initial events with [org.jetbrains.plugins.terminal.session.impl.TerminalInitialStateEvent].
   * Prefer low-level session in exceptional cases only when you need to test the exact sequences of events.
   * Use production session (specify `false`) in all other cases.
   */
  fun startTestTerminalSession(
    project: Project,
    options: ShellStartupOptions,
    isLowLevelSession: Boolean,
    coroutineScope: CoroutineScope,
  ): TestTerminalSessionResult {
    assert(options.shellCommand != null) { "shellCommand should be configured in the provided options" }

    TerminalTestUtil.setTerminalEngineForTest(TerminalEngine.REWORKED, coroutineScope.asDisposable())

    val allOptions = options.builder()
      .envVariables(options.envVariables + mapOf("DISABLE_AUTO_UPDATE" to "true", "HISTFILE" to "/dev/null"))
      .initialTermSize(options.initialTermSize ?: TermSize(80, 24))
      .build()

    return if (isLowLevelSession) {
      val (ttyConnector, configuredOptions) = startTerminalProcess(project, allOptions)
      val session = createTerminalSession(project, ttyConnector, configuredOptions, JBTerminalSystemSettingsProvider(), coroutineScope)
      TestTerminalSessionResult(session, ttyConnector)
    }
    else {
      val manager = TerminalSessionsManager.getInstance(project)
      val sessionStartResult = manager.startSession(allOptions, coroutineScope)
      val session = manager.getSession(sessionStartResult.sessionId)!!
      TestTerminalSessionResult(session, sessionStartResult.ttyConnector)
    }
  }

  fun createShellCommand(shellPath: String): List<String> {
    return LocalTerminalStartCommandBuilder.convertShellPathToCommand(shellPath)
  }

  /**
   * Creates the production [TerminalSession] the same way [startStandardTerminalSession] does
   * but backed by an in-memory [LoopbackTtyConnector] instead of a real shell process.
   *
   * The returned connector is the injection point: pass raw ANSI/VT sequences to
   * [LoopbackTtyConnector.feed] and observe the resulting events in
   * [TerminalSession.getOutputFlow].
   *
   * The session lifecycle is bound to [coroutineScope]; cancel it to stop the emulation.
   *
   * [emulatorType] is the emulator explicitly requested for the session; null (the default) leaves the
   * choice to [TerminalEmulatorType.default], matching production behavior.
   *
   * [isLowLevelSession] mirrors [startTestTerminalSession]'s own parameter of the same name: the low-level
   * session outputs events in their natural order, while the production one (the default) replaces some
   * initial events with [TerminalInitialStateEvent]. Prefer the low-level session only when a test needs to
   * assert the exact order or content of specific output events; use the production session in all other
   * cases, and always when driving a real view (see `TerminalViewFixture`).
   */
  fun createLoopbackTerminalSession(
    project: Project,
    coroutineScope: CoroutineScope,
    emulatorType: TerminalEmulatorType? = null,
    isLowLevelSession: Boolean = false,
  ): Pair<TerminalSession, LoopbackTtyConnector> {
    val connector = LoopbackTtyConnector()
    val workingDirectory = System.getProperty("user.home")
    val options = ShellStartupOptions.Builder()
      .initialTermSize(TermSize(80, 24))
      .processType(TerminalProcessType.SHELL)
      .workingDirectory(workingDirectory)
      .emulatorType(emulatorType)
      .build()
    val delegateScope = coroutineScope.childScope("original session")
    val delegate = createTerminalSession(project, connector, options, JBTerminalSystemSettingsProvider(), delegateScope)
    if (isLowLevelSession) {
      return delegate to connector
    }
    val startupOptions = TerminalStartupOptionsImpl(
      shellCommand = listOf("/bin/bash"),
      workingDirectory = workingDirectory,
      envVariables = emptyMap(),
      processType = TerminalProcessType.SHELL,
      pid = null,
    )
    val session = createStandardStateAwareTerminalSession(delegate, startupOptions, coroutineScope)
    return session to connector
  }

  suspend fun TerminalSession.awaitOutputEvent(targetEvent: TerminalOutputEvent) {
    return coroutineScope {
      val promptFinishedEventDeferred = CompletableDeferred<Unit>()

      val flowCollectionJob = launch {
        getOutputFlow().collect { events ->
          if (events.any { it == targetEvent }) {
            promptFinishedEventDeferred.complete(Unit)
          }
        }
      }

      promptFinishedEventDeferred.await()
      flowCollectionJob.cancel()
    }
  }

  fun assumeCommandBlockShellIntegration(shellCommand: List<String>) {
    assertThat(TerminalOptionsProvider.instance.shellIntegration).isTrue()
    val shellName = PathUtil.getFileName(shellCommand.first())
    Assume.assumeTrue(LocalShellIntegrationInjector.supportsBlocksShellIntegration(shellName, LocalEelDescriptor))
  }

  fun getShellPaths(): List<Path> {
    val traditionalUnixShells = listOf(
      "/bin/zsh",
      "/urs/bin/zsh",
      "/urs/local/bin/zsh",
      "/opt/homebrew/bin/zsh",
      "/bin/bash",
      "/opt/homebrew/bin/bash"
    ).mapNotNull { path ->
      Path.of(path).takeIf { Files.isRegularFile(it) }
    }

    return traditionalUnixShells + getPowerShellPaths()
  }

  fun getPowerShellPaths(): List<Path> {
    return listOf(
      "powershell",
      "powershell.exe",
      "pwsh",
      "pwsh.exe"
    ).mapNotNull {
      PathEnvironmentVariableUtil.findFirst(it)
    }
  }

  /**
   * To have stable tests, we need a reliable VT/ANSI sequences supplier.
   * Windows: let's restrict different Windows PTY-emulators to
   * require the bundled ConPTY library only.
   */
  fun assumeTestableProcess(shellEelProcess: ShellEelProcess) {
    val descriptor = shellEelProcess.eelApi.descriptor
    Assumptions.assumeFalse(
      descriptor.osFamily.isWindows && descriptor != LocalEelDescriptor,
      "Remote Windows may not support shell integration (latest ConPTY is required)"
    )
    val javaProcess = shellEelProcess.ptyProcess
    if (javaProcess is WinPtyProcess || javaProcess is CygwinPtyProcess) {
      Assert.fail("Shell integration on Windows requires ConPTY, but ${javaProcess::class.java} was supplied")
    }
    if (javaProcess is WinConPtyProcess) {
      Assumptions.assumeTrue(javaProcess.isBundledConPtyLibrary, "Shell integration on Windows requires latest version of ConPTY")
    }
  }

  val ENTER_BYTES: ByteArray = byteArrayOf(Ascii.CR)
}

class TestTerminalSessionResult(
  val session: TerminalSession,
  val ttyConnector: TtyConnector,
)

/**
 * Subscribes to [TerminalSession.getOutputFlow] once (for the whole lifetime of [scope]) and accumulates every
 * emitted [TerminalOutputEvent], so tests can await specific events with [awaitEvent] without losing earlier ones
 * or churning subscriptions.
 *
 * Keeping a single active subscription also keeps the emulation running: the session only reads its output while
 * there is at least one collector of the output flow.
 *
 * Besides the raw events, every event is applied to real output models the way `StateAwareTerminalSession`
 * applies them in production, so a test can assert the document the whole event stream produces — see
 * [documentText] and [alternateBufferText] — or the document at one point in the stream, which is how a test
 * states where an event that carries no position of its own landed — see [stateBefore].
 *
 * Pass a low-level `session` (see `TerminalSessionTestUtil.createLoopbackTerminalSession`'s
 * `isLowLevelSession`) when the test asserts the exact order or content of specific output events: a
 * production, `StateAwareTerminalSession`-wrapped session replaces some initial events with a snapshot,
 * so the discrete event a test awaits right after subscribing may never arrive.
 */
internal class TerminalOutputEventCollector(
  session: TerminalSession,
  scope: CoroutineScope,
) {
  // Unbounded replay: the await and history queries below index this history, so nothing may be dropped.
  // Private on purpose: the session's own flow replays one batch and is consumed exactly once, so awaiting
  // and history queries only work through this accumulator — see the class KDoc.
  private val events = MutableSharedFlow<TerminalOutputEvent>(replay = Int.MAX_VALUE)

  // Guarded by modelLock: the collection coroutine writes, tests read.
  private val modelLock = ReentrantLock()
  private val mirror = TerminalOutputModelMirror()

  init {
    scope.launch {
      session.getOutputFlow().collect { batch ->
        for (event in batch) {
          modelLock.withLock { mirror.apply(event) }
          events.emit(event)
        }
      }
    }
  }

  /**
   * Suspends until an event of [type] matching [predicate] arrives, checking already-collected events
   * first and ignoring the first [skipCount] of them (see [currentEventCount]). Prefer the reified
   * [awaitEvent] and [awaitEventAfter] extensions.
   */
  suspend fun <T : TerminalOutputEvent> awaitEvent(type: Class<T>, skipCount: Int = 0, predicate: (T) -> Boolean = { true }): T {
    return events.drop(skipCount)
      .mapNotNull { event -> if (type.isInstance(event)) type.cast(event) else null }
      .first(predicate)
  }

  /**
   * Number of events collected so far. Pass the returned value to [awaitEventAfter] or [eventsSince] to
   * address only the events emitted *after* this point — useful when an action re-emits events that look
   * identical to earlier ones (for example, a resize re-reporting existing content).
   */
  fun currentEventCount(): Int = events.replayCache.size

  /** The events collected after the first [startIndex] ones (see [currentEventCount]), in emission order. */
  fun eventsSince(startIndex: Int): List<TerminalOutputEvent> = events.replayCache.drop(startIndex)

  /** The [TerminalContentUpdatedEvent]s collected so far, in emission order. */
  fun contentUpdates(): List<TerminalContentUpdatedEvent> = events.replayCache.filterIsInstance<TerminalContentUpdatedEvent>()

  /**
   * The number of events collected before the first event of [type] matching [predicate], which is also that
   * event's index. Returns -1 when no such event arrived. Prefer the reified [indexOfEvent] extension.
   */
  fun <T : TerminalOutputEvent> indexOfEvent(type: Class<T>, predicate: (T) -> Boolean = { true }): Int =
    events.replayCache.indexOfFirst { event -> type.isInstance(event) && predicate(type.cast(event)) }

  /**
   * The primary-buffer state the frontend reads when it handles the event at [eventIndex]: every event before
   * it applied, and that event and everything after it not applied yet.
   *
   * This is how `TerminalShellIntegrationEventsHandler` positions a shell-integration command. It flushes the
   * pending content updates, then reads [org.jetbrains.plugins.terminal.view.TerminalOutputModel.cursorOffset].
   * So a session that emits the command *after* the content that follows its OSC sequence shows up here as a
   * cursor offset that is too far forward, which is what a wrong block boundary is made of.
   */
  fun stateBefore(eventIndex: Int): TerminalOutputSnapshot {
    require(eventIndex >= 0) { "No such event was collected, so it has no position in the stream" }
    val replay = TerminalOutputModelMirror()
    for (event in events.replayCache.take(eventIndex)) {
      replay.apply(event)
    }
    return replay.primarySnapshot()
  }

  /**
   * The primary-buffer document produced by applying every collected event to a real output model —
   * what the UI would show (modulo rendering) after this session's whole event stream.
   */
  fun documentText(): String = modelLock.withLock { mirror.primaryText() }

  /** [documentText] split into logical lines: a soft-wrapped line stays a single entry. */
  fun documentLines(): List<String> = documentText().split('\n')

  /** The alternate-buffer document; see [documentText]. */
  fun alternateBufferText(): String = modelLock.withLock { mirror.alternateText() }
}

/**
 * The primary output model at one point in the event stream — see [TerminalOutputEventCollector.stateBefore].
 *
 * [cursorOffset] counts characters from the start of the model, so an expectation is a plain character count.
 */
internal class TerminalOutputSnapshot(val text: String, val cursorOffset: Int) {
  override fun toString(): String = "cursor at $cursorOffset in ${text.replace("\n", "\\n")}"
}

/**
 * Applies output events to real output models the way `StateAwareTerminalSession` does: a content or cursor
 * update goes to the model of the buffer that the last applied state reports as active.
 */
private class TerminalOutputModelMirror {
  private val outputModel = TerminalTestUtil.createOutputModel()
  private val alternateBufferModel = TerminalTestUtil.createOutputModel()
  private var isAlternateScreenBuffer = false

  fun apply(event: TerminalOutputEvent) {
    when (event) {
      is TerminalInitialStateEvent -> {
        outputModel.restoreFromState(event.outputModelState.toState())
        alternateBufferModel.restoreFromState(event.alternateBufferState.toState())
        isAlternateScreenBuffer = event.sessionState.isAlternateScreenBuffer
      }
      is TerminalStateChangedEvent -> isAlternateScreenBuffer = event.state.isAlternateScreenBuffer
      is TerminalContentUpdatedEvent -> currentModel().updateContent(event)
      is TerminalCursorPositionChangedEvent -> currentModel().updateCursorPosition(event.logicalLineIndex, event.columnIndex)
      else -> Unit
    }
  }

  fun primaryText(): String = outputModel.document.text

  fun alternateText(): String = alternateBufferModel.document.text

  fun primarySnapshot(): TerminalOutputSnapshot = TerminalOutputSnapshot(
    text = primaryText(),
    cursorOffset = (outputModel.cursorOffset - outputModel.startOffset).toInt(),
  )

  private fun currentModel(): MutableTerminalOutputModel =
    if (isAlternateScreenBuffer) alternateBufferModel else outputModel
}

/** Suspends until an event of type [T] matching [predicate] is emitted (checking already-received events too). */
internal suspend inline fun <reified T : TerminalOutputEvent> TerminalOutputEventCollector.awaitEvent(
  noinline predicate: (T) -> Boolean = { true },
): T = awaitEvent(T::class.java, predicate = predicate)

/**
 * Like [awaitEvent], but skips the first [skipCount] already-collected events
 * (see [TerminalOutputEventCollector.currentEventCount]).
 */
internal suspend inline fun <reified T : TerminalOutputEvent> TerminalOutputEventCollector.awaitEventAfter(
  skipCount: Int,
  noinline predicate: (T) -> Boolean = { true },
): T = awaitEvent(T::class.java, skipCount, predicate)

/** The index of the first event of type [T] matching [predicate]; -1 when no such event was collected. */
internal inline fun <reified T : TerminalOutputEvent> TerminalOutputEventCollector.indexOfEvent(
  noinline predicate: (T) -> Boolean = { true },
): Int = indexOfEvent(T::class.java, predicate)

/**
 * Suspends until an event of type [T] matching [predicate] arrives, then returns the output-model state the
 * frontend reads when it handles that event — see [TerminalOutputEventCollector.stateBefore].
 */
internal suspend inline fun <reified T : TerminalOutputEvent> TerminalOutputEventCollector.awaitStateBefore(
  noinline predicate: (T) -> Boolean = { true },
): TerminalOutputSnapshot {
  awaitEvent<T>(predicate)
  return stateBefore(indexOfEvent<T>(predicate))
}
