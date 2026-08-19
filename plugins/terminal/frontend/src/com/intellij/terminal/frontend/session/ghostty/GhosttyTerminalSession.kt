// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.session.ghostty

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.EelDescriptor
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.emulator.ScreenChange
import com.intellij.terminal.emulator.TerminalCustomCommandListener
import com.intellij.terminal.emulator.TerminalEmulator
import com.intellij.terminal.emulator.TerminalListener
import com.intellij.terminal.emulator.TerminalSize
import com.intellij.terminal.emulator.createTerminalEmulator
import com.intellij.terminal.frontend.session.ObservableTtyConnector
import com.intellij.terminal.frontend.session.TerminalShellIntegrationController
import com.intellij.terminal.frontend.session.TerminalShellIntegrationStatisticsListener
import com.intellij.terminal.frontend.session.addWorkingDirectoryListener
import com.intellij.util.AwaitCancellationAndInvoke
import com.intellij.util.awaitCancellationAndInvoke
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.TtyConnector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.terminal.LocalTerminalTtyConnector
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.TerminalEmulatorType
import org.jetbrains.plugins.terminal.TerminalUtil
import org.jetbrains.plugins.terminal.original
import org.jetbrains.plugins.terminal.session.impl.TerminalBeepEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalClearBufferEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalCloseEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalCursorPositionChangedEvent
import org.jetbrains.plugins.terminal.startup.TerminalProcessType
import org.jetbrains.plugins.terminal.session.impl.TerminalInputEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalOutputEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalResizeEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalSession
import org.jetbrains.plugins.terminal.session.impl.TerminalSessionTerminatedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalStateChangedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalWriteBytesEvent
import org.jetbrains.plugins.terminal.session.impl.dto.KeyEventProcessingResultDto
import org.jetbrains.plugins.terminal.session.impl.dto.TerminalStateDto
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Experimental [TerminalSession] driven by the common [TerminalEmulator] API
 * (Ghostty-backed) instead of JediTerm. Selected by `createTerminalSession` when the
 * session's emulator is [TerminalEmulatorType.Ghostty].
 *
 * - a read loop feeds PTY output ([TtyConnector.read]) to [TerminalEmulator.write];
 * - the emulator's grid + scrollback + modes are projected into the
 *   [TerminalOutputEvent] model (content / cursor / state) by
 *   [TerminalEmulatorOutputProjector] and pushed to [getOutputFlow] on a fixed cadence
 *   ([OUTPUT_POLL_INTERVAL], like the JediTerm pipeline), so a burst of writes
 *   coalesces into one delta; the read loop forces an early projection only when
 *   unreported history nears eviction (see
 *   [TerminalEmulatorOutputProjector.isUnemittedHistoryEvictionImminent]);
 * - OSC 1341 shell-integration commands are received via
 *   [TerminalEmulator.customCommandListener], parsed by the shared
 *   [TerminalShellIntegrationController], and turned into
 *   `TerminalShellIntegrationEvent`s (and the shell-integration state flag);
 * - the bell ([TerminalListener.onBell]) becomes a `TerminalBeepEvent`;
 * - emulator responses ([TerminalListener.onRespondToHost]) and input events are
 *   written back to the PTY;
 * - key and mouse events ([processKeyEvent] / [processMouseEvent]) are encoded into
 *   PTY bytes by the emulator's own encoders via [TerminalEmulatorKeyEventEncoder] /
 *   [TerminalEmulatorMouseEventEncoder], which also own the session-layer input policy
 *   (macOS editing chords, alt-as-Escape, which mouse events stay with the IDE).
 *
 * ### Known gaps vs. the JediTerm pipeline (this backend is experimental)
 * - **Partial state**: `isAutoNewLine` and `isAltSendsEscape` are absent from the
 *   emulator API, so they keep their defaults.
 * - **Scrollback overflow**: content updates are incremental (only the changed tail);
 *   a [com.intellij.terminal.emulator.HistoryMark] keeps the appended-line count exact
 *   even past the scrollback cap, so the tail is under-reported only in the extreme
 *   case where a single write scrolls past the entire retained scrollback (see
 *   [TerminalEmulatorOutputProjector.buildContentUpdate]).
 * - **Buffer switch mid-frame**: when one projection window contains both an
 *   alternate-screen switch and drawing, only the newly active buffer's frame is
 *   emitted — the old buffer's last pre-switch changes are not (the JediTerm pipeline
 *   flushes before switching; the emulator has no such hook).
 */
internal class GhosttyTerminalSession(
  private val ttyConnector: TtyConnector,
  initialSize: TerminalSize,
  initialWorkingDirectory: String?,
  private val shellIntegrationController: TerminalShellIntegrationController,
  settings: JBTerminalSystemSettingsProviderBase,
  override val coroutineScope: CoroutineScope,
) : TerminalSession {

  private val emulator: TerminalEmulator = createTerminalEmulator(initialSize)

  // Projects emulator state into the output-event DTOs and owns the
  // incremental-emission bookkeeping. Created with the emulator; closed on teardown.
  private val projector = TerminalEmulatorOutputProjector(emulator)

  // Encode AWT key/mouse events into PTY bytes through the emulator; call only under
  // [lock].
  private val keyEncoder = TerminalEmulatorKeyEventEncoder(emulator, settings)
  private val mouseEncoder = TerminalEmulatorMouseEventEncoder(emulator, settings)

  /**
   * The emulator is not thread-safe: serialize the read loop and the resize/input
   * handler.
   */
  private val lock = ReentrantLock()

  private val inputChannel = Channel<TerminalInputEvent>(Channel.UNLIMITED)

  // Buffered and gated like the JediTerm pipeline's output flow (see
  // createTerminalOutputFlow): one element only, so an event emitted in response to a
  // user action (Ctrl+C) reaches the UI right away instead of queueing behind buffered
  // output.
  //
  // SUSPEND, not DROP_OLDEST: output events are incremental deltas, so a collector
  // missing one would desync from every update after it. Emissions wait for room — and
  // for a collector to exist at all (see tryEmitOutput) — while holding [lock], which
  // stalls the read loop and pushes the backpressure into the PTY. Emitting under the
  // lock also serializes the emitters, so a projection cannot overtake an earlier one.
  private val outputFlow = MutableSharedFlow<List<TerminalOutputEvent>>(
    replay = 1,
    extraBufferCapacity = 0,
    onBufferOverflow = BufferOverflow.SUSPEND,
  )

  // Diffing state (guarded by lock).
  private var lastScrollbackRows = -1
  private var lastCursorLine = -1L
  private var lastCursorColumn = -1
  private var lastState: TerminalStateDto? = null

  // Whether anything projection reads (emulator content, modes, the working
  // directory) may have changed since the last projection; guarded by lock. Lets the
  // polling job skip idle ticks outright — otherwise every tick of an idle session
  // pays the FFI dirty poll, state snapshot, and cursor row reads. Starts true so the
  // first tick after a collector appears emits the initial frame.
  private var changedSinceLastProjection = true

  // Synchronized-output (DEC 2026) deferral state, guarded by lock: the watchdog
  // bounding the currently open block (null when none is armed), and the force-paint
  // it requests when the block overstays — consumed by the next projection. See
  // isDeferringForSyncOutputLocked().
  private var syncWatchdogJob: Job? = null
  private var syncOutputForcePaint = false

  // One-shot output events (shell integration, bell) collected during a write and
  // flushed by syncLocked().
  private val pendingEvents = ArrayList<TerminalOutputEvent>()

  // Emulator replies to host queries (DSR, DA, OSC reports), collected during a write
  // and written to the PTY by [flushResponses] *after* [lock] is released. They must
  // not be written inline: the write-pty effect fires synchronously inside
  // emulator.write, so a full PTY buffer would park the read thread both inside
  // ghostty's vt_write (see terminal.h: effects "must not block for too long ... they
  // are blocking further IO processing") and while holding [lock] — freezing resize
  // along with it.
  private val pendingResponses = ArrayList<ByteArray>()

  // The working directory reported in the session state: starts at the requested
  // startup directory and is kept fresh by the working-directory tracker (see
  // createGhosttyTerminalSession). Guarded by lock; the next projection reports the
  // new value.
  private var currentDirectory: String? = initialWorkingDirectory

  @Volatile
  override var isClosed: Boolean = false
    private set

  /**
   * Set once teardown starts, so the read loop stops touching the (about to be closed)
   * emulator.
   */
  @Volatile
  private var disposed: Boolean = false

  private val localTtyConnector: LocalTerminalTtyConnector
    get() = ttyConnector.original as LocalTerminalTtyConnector

  override val eelDescriptor: EelDescriptor
    get() = localTtyConnector.eelDescriptor

  override val processId: Long
    get() = localTtyConnector.shellEelProcess.eelProcess.pid.value

  /**
   * Invoked by the working-directory tracker; the new value is reported by the next
   * projection tick.
   */
  fun updateCurrentDirectory(directory: String) {
    lock.withLock {
      currentDirectory = directory
      changedSinceLastProjection = true
    }
  }

  @OptIn(AwaitCancellationAndInvoke::class)
  fun start() {
    emulator.listener = object : TerminalListener {
      // Fires synchronously inside emulator.write, i.e. under [lock] on the read thread.
      // Queue only: the actual pty write happens in flushResponses(), once the lock is
      // released. See [pendingResponses].
      override fun onRespondToHost(data: ByteArray) {
        pendingResponses.add(data)
      }

      // Fires synchronously inside emulator.write, i.e. under [lock] on the read thread.
      override fun onBell() {
        pendingEvents.add(TerminalBeepEvent)
      }
    }

    // OSC 1341 (JetBrains shell integration): the emulator's custom-command listener
    // fires synchronously inside emulator.write, i.e. under [lock] on the read thread,
    // and the controller delivers the parsed events on the same thread — so the sink
    // only queues; syncLocked() then flushes the events in order.
    shellIntegrationController.addEventSink { event -> pendingEvents.add(event) }
    emulator.customCommandListener = TerminalCustomCommandListener(shellIntegrationController::processCustomCommand)

    // Read the PTY on a dedicated daemon thread rather than a coroutine in the session
    // scope (production uses a plain executor for the same reason): the blocking read()
    // is not a cancellation point, so keeping it off the structured scope lets teardown
    // finish promptly once awaitCancellationAndInvoke closes the connector and thereby
    // unblocks the read.
    thread(name = "GhosttyTerminalSession read loop", isDaemon = true) {
      val buffer = CharArray(4096)
      try {
        while (true) {
          val count = ttyConnector.read(buffer, 0, buffer.size)
          if (count <= 0) break // EOF
          var responses: List<ByteArray> = emptyList()
          lock.withLock {
            if (disposed) break
            emulator.write(String(buffer, 0, count))
            changedSinceLastProjection = true
            projector.noteOutputWritten()
            responses = takeResponsesLocked()
            // Projection is normally the polling job's duty (below); step in only when
            // so much history piled up unreported that further output would evict it
            // unseen. Deferral is bypassed: painting a mid-block frame beats losing
            // scrollback for good, and a program that scrolls this much inside one
            // synchronized-output block is not composing a frame anyway.
            if (projector.isUnemittedHistoryEvictionImminent()) {
              projectAndEmitLocked(bypassSyncOutputDeferral = true)
            }
          }
          flushResponses(responses)
        }
      }
      catch (t: Throwable) {
        if (!disposed) LOG.warn("Terminal emulator read loop failed", t)
      }
      finally {
        isClosed = true
        // The polling job may be cancelled before its next tick: project the final
        // frame (a short-lived command's last output) before announcing termination.
        // requireCollector = false on both emissions — with nothing collecting they
        // must not wait, or teardown would hang; the replay slot keeps the last one
        // on a best effort.
        val finalEvents = runCatching {
          lock.withLock { if (disposed) emptyList() else syncLocked(bypassSyncOutputDeferral = true) }
        }.getOrDefault(emptyList())
        if (finalEvents.isNotEmpty()) emitOutputBlocking(finalEvents, requireCollector = false)
        emitOutputBlocking(listOf(TerminalSessionTerminatedEvent), requireCollector = false)
        coroutineScope.cancel()
      }
    }

    coroutineScope.launch {
      for (event in inputChannel) {
        try {
          handleInput(event)
        }
        catch (e: Exception) {
          LOG.warn("Failed to handle input event $event", e)
        }
      }
    }

    // Projects emulator changes into output events at a fixed cadence, the way the
    // JediTerm pipeline does (see createTerminalOutputFlow): the emulator absorbs any
    // amount of output into bounded state, and each tick emits one coalesced delta, so
    // a program spamming output produces ~50 event batches per second instead of one
    // per PTY read. Dispatchers.IO because the emission deliberately blocks under
    // [lock] while a slow collector catches up (see projectAndEmitLocked).
    coroutineScope.launch(Dispatchers.IO) {
      while (true) {
        delay(OUTPUT_POLL_INTERVAL)
        // Nothing is collecting: leave the changes in the emulator instead of
        // computing an event batch nobody can take (see tryEmitOutput). The read loop
        // keeps feeding the emulator meanwhile, bounded by its history-eviction flush.
        if (outputFlow.subscriptionCount.value == 0) continue
        var responses: List<ByteArray> = emptyList()
        lock.withLock {
          if (disposed) return@launch
          // An idle tick (nothing changed, no force paint pending) costs one lock
          // acquisition and nothing else — no FFI reads.
          val forcePaint = consumeSyncOutputForcePaintLocked()
          if (changedSinceLastProjection || forcePaint) {
            projectAndEmitLocked(bypassSyncOutputDeferral = forcePaint)
          }
          responses = takeResponsesLocked()
        }
        flushResponses(responses)
      }
    }

    coroutineScope.awaitCancellationAndInvoke {
      disposed = true
      runCatching { ttyConnector.close() }
      lock.withLock {
        runCatching { projector.close() }
        runCatching { emulator.close() }
      }
    }
  }

  override fun processMouseEvent(e: MouseEvent, x: Int, y: Int): ByteArray? = lock.withLock {
    if (disposed) null else mouseEncoder.encodeMouseEvent(e, x, y)
  }

  override fun processKeyEvent(e: KeyEvent): KeyEventProcessingResultDto = lock.withLock {
    if (disposed) KeyEventProcessingResultDto.Unhandled else keyEncoder.encodeKeyEvent(e)
  }

  override suspend fun getInputChannel(): SendChannel<TerminalInputEvent> {
    if (isClosed) {
      return Channel<TerminalInputEvent>(capacity = 0).also { it.close() }
    }
    return inputChannel
  }

  override suspend fun getOutputFlow(): Flow<List<TerminalOutputEvent>> {
    if (isClosed) {
      return emptyFlow()
    }
    return outputFlow
  }

  override suspend fun hasRunningCommands(): Boolean {
    return !isClosed && withContext(Dispatchers.IO) {
      TerminalUtil.hasRunningCommands(ttyConnector)
    }
  }

  private fun handleInput(event: TerminalInputEvent) {
    when (event) {
      // Do not log the bytes themselves: they are the user's keystrokes.
      is TerminalWriteBytesEvent -> runCatching { ttyConnector.write(event.bytes) }
        .onFailure { if (!disposed) LOG.warn("Failed to write ${event.bytes.size} bytes to the PTY", it) }
      is TerminalResizeEvent -> {
        var responses: List<ByteArray> = emptyList()
        lock.withLock {
          if (disposed) return
          emulator.resize(TerminalSize(event.newSize.columns, event.newSize.rows))
          changedSinceLastProjection = true
          runCatching { ttyConnector.resize(TermSize(event.newSize.columns, event.newSize.rows)) }
            .onFailure { if (!disposed) LOG.warn("Failed to resize the PTY to ${event.newSize}", it) }
          responses = takeResponsesLocked()
        }
        flushResponses(responses)
        // The reflowed frame is picked up by the next projection tick.
      }
      is TerminalCloseEvent -> runCatching { ttyConnector.close() }
      is TerminalClearBufferEvent -> Unit // no dedicated emulator API in this spike
    }
  }

  /**
   * Must be called under [lock]: hands over the emulator replies queued by the write-pty
   * effect since the last call, leaving [pendingResponses] empty. Allocates nothing on
   * the common path, where no query was answered.
   */
  private fun takeResponsesLocked(): List<ByteArray> {
    if (pendingResponses.isEmpty()) return emptyList()
    val taken = ArrayList(pendingResponses)
    pendingResponses.clear()
    return taken
  }

  /**
   * Must be called *after* releasing [lock]: writes [responses] back to the pty, in the
   * order the emulator produced them. Blocking here is fine — the emulator is no longer
   * mid-parse and no other thread is waiting on us to let go of the lock.
   */
  private fun flushResponses(responses: List<ByteArray>) {
    for (response in responses) {
      runCatching { ttyConnector.write(response) }
        .onFailure { if (!disposed) LOG.warn("Failed to write an emulator reply (${response.size} bytes) to the PTY", it) }
    }
  }

  /**
   * Offers [events] to [outputFlow], reporting whether it accepted them.
   *
   * Fails while nothing is collecting, unless [requireCollector] is false: the single
   * buffered element would otherwise be overwritten by the next emission, and a lost
   * delta desyncs the collector from every update after it. Failing instead makes the
   * caller wait, which stops the read loop and lets the backpressure reach the shell.
   */
  private fun tryEmitOutput(events: List<TerminalOutputEvent>, requireCollector: Boolean): Boolean {
    val mayEmit = !requireCollector || outputFlow.subscriptionCount.value > 0
    return mayEmit && outputFlow.tryEmit(events)
  }

  /**
   * Must be called under [lock]. Projects the current emulator state into output
   * events and emits them, blocking — still under the lock — until the collector
   * takes them.
   *
   * Emitting under the lock is the backpressure, the same shape the JediTerm pipeline
   * gets by emitting under its text-buffer lock: while a slow (or absent) collector
   * keeps this blocked, the read loop cannot take the lock to feed the emulator more
   * output, the PTY buffer fills, and the shell itself stops writing.
   */
  private fun projectAndEmitLocked(bypassSyncOutputDeferral: Boolean = false) {
    val events = syncLocked(bypassSyncOutputDeferral)
    if (events.isNotEmpty()) emitOutputBlocking(events)
  }

  /**
   * Emits [events] on [outputFlow], retrying with a short sleep until accepted and
   * bailing out only once torn down. Blocking rather than suspending is deliberate:
   * the projection sites hold [lock] — a plain lock, which a coroutine must not
   * suspend under — and the read loop is not a coroutine to begin with. See the
   * [outputFlow] declaration for why dropping is not an option here.
   *
   * [requireCollector] is false only for the teardown emissions (the final frame and
   * [TerminalSessionTerminatedEvent]): by then there may be no collector left to wait
   * for, and waiting would hang teardown.
   */
  private fun emitOutputBlocking(events: List<TerminalOutputEvent>, requireCollector: Boolean = true) {
    while (!tryEmitOutput(events, requireCollector)) {
      if (disposed) return
      Thread.sleep(1)
    }
  }

  /**
   * Must be called under [lock]. Projects the current emulator state into output events.
   *
   * [bypassSyncOutputDeferral] paints the mid-block state that
   * [isDeferringForSyncOutputLocked] would otherwise keep holding back. It is set for
   * the watchdog's force-paint request, the read loop's history-eviction flush, and
   * the final frame at EOF — the cases where waiting for the block to close is worse
   * than a mid-block frame.
   */
  private fun syncLocked(bypassSyncOutputDeferral: Boolean = false): List<TerminalOutputEvent> {
    if (!bypassSyncOutputDeferral && isDeferringForSyncOutputLocked()) return emptyList()

    // Cleared only past the deferral gate: a deferred frame leaves the flag set, so
    // the polling job keeps attempting (and keeps the sync-output watchdog armed)
    // until the block closes or the watchdog paints.
    changedSinceLastProjection = false

    val events = ArrayList<TerminalOutputEvent>(2)

    val previousState = lastState
    val state = projector.buildState(shellIntegrationController.isShellIntegrationEnabled, currentDirectory)
    val stateEvent = if (state != previousState) TerminalStateChangedEvent(state) else null
    lastState = state
    // The consumer routes content and cursor updates to the primary or alternate
    // buffer model by the last state it applied, and this frame is already read from
    // the newly active buffer — so when the frame switches buffers, the state change
    // must come first. In every other case the state event stays after the content it
    // accompanies.
    val stateFirst = state.isAlternateScreenBuffer != previousState?.isAlternateScreenBuffer
    if (stateEvent != null && stateFirst) {
      events.add(stateEvent)
    }

    val change = emulator.takeChanges()
    val scrollbackRows = emulator.scrollbackRows
    val contentChanged = change != ScreenChange.None || scrollbackRows != lastScrollbackRows
    lastScrollbackRows = scrollbackRows

    if (contentChanged) {
      val content = projector.buildContentUpdate()
      events.add(content)
      lastCursorLine = content.cursorLogicalLineIndex
      lastCursorColumn = content.cursorColumnIndex
    }
    else if (emulator.cursor.visible) {
      val (line, column) = projector.computeCursor()
      if (line != lastCursorLine || column != lastCursorColumn) {
        events.add(TerminalCursorPositionChangedEvent(line, column))
        lastCursorLine = line
        lastCursorColumn = column
      }
    }

    if (stateEvent != null && !stateFirst) {
      events.add(stateEvent)
    }

    // One-shot events queued during this write (shell integration, bell) are reported
    // last, after the content/cursor/state updates they relate to.
    if (pendingEvents.isNotEmpty()) {
      events.addAll(pendingEvents)
      pendingEvents.clear()
    }

    return events
  }

  /**
   * Must be called under [lock]. Whether this frame has to be held back because the
   * program is inside a synchronized-output block (DEC 2026), and maintains the
   * watchdog that bounds how long that can last.
   *
   * Mode 2026 is a presentation hint only: the emulator keeps applying input to its
   * grid throughout the block, so deferring here just leaves the change set
   * unconsumed until the block ends, and the frame the program was building is then
   * emitted whole instead of half-drawn.
   *
   * The catch is that nothing forces a program to close its block — it may crash,
   * hang, or simply be buggy mid-frame — and once that happens no further output
   * arrives to re-check the flag, so the view would stay frozen for good. So each
   * deferred frame keeps [syncWatchdogJob] armed; if the block is still open
   * [SYNC_OUTPUT_TIMEOUT] after arming, the watchdog requests a force paint (performed
   * by the next projection tick), and the next deferred frame arms it again — a
   * wedged-but-active block repaints at that cadence instead of freezing. Ghostty's
   * own app layer bounds a block with the same 1000 ms timer (`sync_reset_ms` in
   * `termio/Thread.zig`), which is where the value comes from.
   *
   * Deliberately not a per-block state machine: two blocks whose boundary arrives
   * inside one PTY chunk are indistinguishable from here (the mode reads as "on"
   * before and after the write), so deferral tracks only the currently observed mode.
   */
  private fun isDeferringForSyncOutputLocked(): Boolean {
    if (!emulator.synchronizedOutput) {
      // No block open (or it just closed): the pending force-paint is obsolete, this
      // sync paints instead.
      cancelSyncWatchdogLocked()
      return false
    }
    armSyncWatchdogLocked()
    return true
  }

  /**
   * Must be called under [lock]. Schedules the force-paint request that bounds an
   * over-long synchronized-output block, unless one is already pending: the deadline
   * is measured from the first deferred frame since the previous paint, so a program
   * that keeps writing inside a block cannot push the deadline back indefinitely.
   */
  private fun armSyncWatchdogLocked() {
    if (syncWatchdogJob != null) return
    syncWatchdogJob = coroutineScope.launch {
      delay(SYNC_OUTPUT_TIMEOUT)
      lock.withLock {
        if (disposed) return@launch
        // Clear first, so the next deferred frame arms a fresh watchdog.
        syncWatchdogJob = null
        LOG.debug("Synchronized output (DEC 2026) held repaints for $SYNC_OUTPUT_TIMEOUT; painting anyway")
        syncOutputForcePaint = true
      }
    }
  }

  /**
   * Must be called under [lock]. Whether the watchdog requested a force paint;
   * consuming resets it, so one request paints one frame and cannot leak into
   * deferring the frames after it.
   */
  private fun consumeSyncOutputForcePaintLocked(): Boolean {
    val requested = syncOutputForcePaint
    syncOutputForcePaint = false
    return requested
  }

  /** Must be called under [lock]. */
  private fun cancelSyncWatchdogLocked() {
    syncWatchdogJob?.cancel()
    syncWatchdogJob = null
    syncOutputForcePaint = false
  }
}

private val LOG = logger<GhosttyTerminalSession>()

/**
 * How long a DEC 2026 synchronized-output block may hold repaints back before the
 * session paints anyway.
 */
private val SYNC_OUTPUT_TIMEOUT: Duration = 1000.milliseconds

/**
 * How often emulator changes are projected into output events — the same cadence
 * as the JediTerm pipeline's output polling (see `createTerminalOutputFlow`).
 */
private val OUTPUT_POLL_INTERVAL: Duration = 20.milliseconds

internal fun createGhosttyTerminalSession(
  project: Project?,
  ttyConnector: TtyConnector,
  options: ShellStartupOptions,
  settings: JBTerminalSystemSettingsProviderBase,
  coroutineScope: CoroutineScope,
): TerminalSession {
  val initialTermSize = options.initialTermSize ?: error("Initial term size must be set")
  val shellIntegrationController = TerminalShellIntegrationController()
  if (project != null) {
    shellIntegrationController.addListener(TerminalShellIntegrationStatisticsListener(project))
  }
  // The observable wrapper is what the session writes through, so the heuristic
  // working-directory tracker below can watch for Enter presses in the written bytes.
  val observableTtyConnector = ttyConnector as? ObservableTtyConnector ?: ObservableTtyConnector(ttyConnector)
  val session = GhosttyTerminalSession(
    ttyConnector = observableTtyConnector,
    initialSize = TerminalSize(initialTermSize.columns, initialTermSize.rows),
    initialWorkingDirectory = options.workingDirectory,
    shellIntegrationController = shellIntegrationController,
    settings = settings,
    coroutineScope = coroutineScope,
  )
  if (options.processType == TerminalProcessType.SHELL) {
    val workingDirectoryTrackingScope = coroutineScope.childScope("Working directory tracking")
    addWorkingDirectoryListener(observableTtyConnector, shellIntegrationController, workingDirectoryTrackingScope) { directory ->
      session.updateCurrentDirectory(directory)
    }
  }
  session.start()
  return session
}
