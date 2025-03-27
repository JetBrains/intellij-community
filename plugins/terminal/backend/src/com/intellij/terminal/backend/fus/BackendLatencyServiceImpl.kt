package com.intellij.terminal.backend.fus

import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.rpc.UID
import com.intellij.terminal.session.TerminalContentUpdatedEvent
import com.intellij.terminal.session.TerminalWriteBytesEvent
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.TerminalDataStream
import com.jediterm.terminal.TtyBasedArrayDataStream
import com.jediterm.terminal.TtyConnector
import fleet.multiplatform.shims.ConcurrentHashMap
import org.jetbrains.plugins.terminal.fus.BackendLatencyService
import org.jetbrains.plugins.terminal.fus.BackendLatencyService.Companion.getInstance
import org.jetbrains.plugins.terminal.fus.BackendOutputActivity
import org.jetbrains.plugins.terminal.fus.BackendTypingActivity
import org.jetbrains.plugins.terminal.fus.ReworkedTerminalUsageCollector
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource

internal class BackendLatencyServiceImpl : BackendLatencyService {
  override fun tryStartBackendTypingActivity(event: TerminalWriteBytesEvent) {
    val id = event.id ?: return
    val bytes = event.bytes
    val activity = BackendTypingActivityImpl(id, bytes)
    backendTypingActivityByByteArray[bytes] = activity
  }

  override fun getBackendTypingActivityOrNull(bytes: ByteArray): BackendTypingActivity? {
    return backendTypingActivityByByteArray[bytes]
  }

  override fun startBackendOutputActivity(): BackendOutputActivity {
    return BackendOutputActivityImpl()
  }
}

internal fun enableFus(ttyConnector: TtyConnector, fusActivity: BackendOutputActivity): TtyConnector =
  FusAwareTtyConnector(ttyConnector, fusActivity)

internal fun enableFus(stream: TerminalDataStream, fusActivity: BackendOutputActivity): TerminalDataStream =
  FusAwareTtyBasedDataStream(stream, fusActivity)

private val backendTypingActivityByByteArray = ConcurrentHashMap<ByteArray, BackendTypingActivityImpl>()

private class BackendTypingActivityImpl(override val id: Int, private val bytes: ByteArray) : BackendTypingActivity {
  private val start = TimeSource.Monotonic.markNow()

  override fun reportDuration() {
    val duration = start.elapsedNow()
    ReworkedTerminalUsageCollector.logBackendLatency(
      inputEventId = id,
      duration = duration,
    )
  }

  override fun finishBytesProcessing() {
    backendTypingActivityByByteArray.remove(bytes)
  }
}

private class BackendOutputActivityImpl : BackendOutputActivity {
  private val sessionIdReference = AtomicReference<UID>()

  override var sessionId: UID?
    get() = sessionIdReference.get()
    set(value) { sessionIdReference.set(value) }

  // split into subclasses to simplify reasoning about threads and locks

  private val readingState = TerminalThreadReadingState()
  private val processingState = TerminalThreadProcessingState()
  private val textBufferState = TerminalThreadStateUnderTextBufferLock()
  private val eventFlowState = EventFlowState()

  private data class ReadRange(val range: LongRange, val time: TimeMark)
  private data class LatencyPair(val min: Latency?, val max: Latency?)
  private data class Latency(val index: Long, val duration: Duration)

  /** The part of the state that is affected by the terminal emulator thread when reading and buffering characters from the TTY. */
  private class TerminalThreadReadingState {
    /** The total number of characters read from the TTY stream and buffered. **/
    private var totalCharsRead = 0L
    /** The queue of character index ranges read from the TTY and timestamped. **/
    val readRanges = LinkedBlockingQueue<ReadRange>()

    /** Invoked every time a new buffer is read from the TTY. */
    fun charsRead(count: Int) {
      val from = totalCharsRead
      totalCharsRead += count.toLong()
      val to = totalCharsRead
      readRanges.add(ReadRange(from until to, TimeSource.Monotonic.markNow()))
    }
  }

  /**
   *  The part of the state that is affected by reading characters from the buffer.
   *
   *  Usually accessed outside the text buffer lock, always in the terminal emulator thread.
   */
  private class TerminalThreadProcessingState {
    /** The total number of characters read from the buffer and processed by the emulator. */
    var totalCharsProcessed = 0L
      private set
    /** The index of the first character processed during this iteration. `null` when we're not inside an iteration. */
    var thisProcessingIterationStart: Long? = null
      private set

    /** Invoked at the start of each iteration. */
    fun charProcessingStarted() {
      thisProcessingIterationStart = totalCharsProcessed
    }

    /** Invoked every time some characters are processed or pushed back into the buffer. In the latter case the argument is negative. */
    fun charsProcessed(count: Int) {
      totalCharsProcessed += count.toLong()
    }

    /** Invoked at the end of each iteration. */
    fun charProcessingFinished() {
      thisProcessingIterationStart = null
    }
  }

  /**
   *  The part of the state that is only updated or accessed under the text buffer lock.
   *
   *  Not necessarily accessed from the terminal emulator thread.
   */
  private class TerminalThreadStateUnderTextBufferLock {
    /**
     *  The index of the first character that will be included in the next buffer collection event.
     *  `null` if there have been no changes in the buffer since the last collection.
     */
    private var nextTextBufferCollectionStart: Long? = null
    /**
     *  The index of the last character that will be included in the next buffer collection event.
     *  `null` if there have been no changes in the buffer since the last collection.
     */
    private var nextTextBufferCollectionEnd: Long? = null

    /**
     * Invoked every time when a processed character affects the text buffer.
     *
     * Invoked on the terminal emulator thread.
     *
     * @param processingIterationStart the index of the first character processed during this processing iteration
     * @param totalCharsProcessed the total number of processed characters, the same as the index of the last character processed so far
     */
    fun processedCharsReachedTextBuffer(processingIterationStart: Long, totalCharsProcessed: Long) {
      // This is a bit complicated. The exact sequence is this:
      // 1. A processing iteration (com.intellij.terminal.backend.StopAwareTerminalStarter.FusAwareEmulator.next) starts.
      // 2. A character is processed.
      // 3. The text buffer may or may not be affected. If it's affected, this function is called.
      // 4. Steps 2-3 continue to repeat until the end of the iteration.
      // 5. The iteration ends.
      // Characters that don't affect the buffer are usually control characters. For example, cursor movement.
      // At any given moment the text buffer may be collected asynchronously from another thread.
      // But this collection happens under the same lock this function is invoked, so it's not THAT asynchronous.
      // The tricky part is to determine the range of character indices that match the buffer collection event.
      // We always know the number of chars already processed, but we don't know which characters actually affected the buffer.
      // We know for sure that when this callback is invoked, all characters processed so far are included in the text buffer.
      // But for the next callback, if we assume that the next range starts where the previous one ended,
      // we may end up with falsely large latencies because no-change characters from the previous iteration will be included as well.
      // To avoid this situation, we ignore the previous range end and start the range from the first character of THIS iteration.
      // But we must also account for the case when several processing iterations happen before the buffer is collected.
      // That's why we only set the range start if it wasn't set yet.
      if (nextTextBufferCollectionStart == null) {
        nextTextBufferCollectionStart = processingIterationStart
      }
      nextTextBufferCollectionEnd = totalCharsProcessed
    }

    /**
     * Invoked every time the text buffer is collected ("scrapped").
     *
     * Invoked _not_ from the terminal emulator thread but from the collecting coroutine.
     *
     * @return the range of the characters that have made their way into the buffer since the last collection
     */
    fun textBufferCollected(): LongRange? {
      val from = nextTextBufferCollectionStart
      val to = nextTextBufferCollectionEnd
      nextTextBufferCollectionStart = null
      nextTextBufferCollectionEnd = null
      if (from == null || to == null) {
        LOG.error("textBufferCollected, but from==$from and to==$to, both should be non-null at this point")
        return null
      }
      return from until to
    }
  }

  /**
   *  The part of the state that is affected by collecting the text buffer and further event processing.
   *
   *  Accessed from different threads, so must be thread-safe.
   */
  private class EventFlowState {
    private val collectedRanges = ConcurrentHashMap<IdentityWrapper<TerminalContentUpdatedEvent>, LongRange>()

    /**
     * Invoked every time the text buffer is collected ("scrapped").
     */
    fun textBufferCollected(event: TerminalContentUpdatedEvent) {
      collectedRanges[event.toIdentity()] = event.firstCharIndex..event.lastCharIndex
    }

    /**
     * Invoked every time the event produced by scrapping the text buffer is collected from the output flow.
     *
     * @return a pair of min/max latencies corresponding to the event char range, all non-`null` unless there's a bug somewhere
     */
    fun eventCollected(event: TerminalContentUpdatedEvent, readRanges: LinkedBlockingQueue<ReadRange>): LatencyPair? {
      val range = collectedRanges.remove(event.toIdentity()) ?: return null
      var firstCharTime: TimeMark? = null
      var lastCharTime: TimeMark? = null
      while (true) {
        val nextRange = readRanges.peek() ?: break
        if (nextRange.range.first > range.last) break // reached the part not collected yet
        if (nextRange.range.last <= range.last) { // the entire range has been collected
          readRanges.remove()
        }
        if (range.first in nextRange.range) {
          firstCharTime = nextRange.time
        }
        if (range.last in nextRange.range) {
          lastCharTime = nextRange.time
          break
        }
      }
      // The first char will have the maximum latency, as it was sitting in the buffer the longest.
      // Compute the minimum latency first, as otherwise when they're essentially equal,
      // we can end up in a situation when the maximum is less than the minimum by some microseconds.
      val minLatency = if (lastCharTime != null) {
        Latency(range.last, lastCharTime.elapsedNow())
      }
      else {
        LOG.warn("The last char ${range.last} was lost somewhere, it's a bug")
        null
      }
      val maxLatency = if (firstCharTime != null) {
        Latency(range.first, firstCharTime.elapsedNow())
      }
      else {
        LOG.warn("The first char ${range.first} was lost somewhere, it's a bug")
        null
      }
      return LatencyPair(minLatency, maxLatency)
    }
  }

  override fun charsRead(count: Int) = readingState.charsRead(count)

  override fun charProcessingStarted() = processingState.charProcessingStarted()

  override fun charsProcessed(count: Int) = processingState.charsProcessed(count)

  override fun processedCharsReachedTextBuffer() {
    // cross-state safe interaction: this function is called in the same thread that updates processingState,
    // and under the same lock that is always used to access textBufferState
    val processingIterationStart = processingState.thisProcessingIterationStart
    if (processingIterationStart == null) {
      LOG.error("processedCharsReachedTextBuffer should not be called outside of a processing iteration")
      return
    }
    textBufferState.processedCharsReachedTextBuffer(processingIterationStart, processingState.totalCharsProcessed)
  }

  override fun charProcessingFinished() = processingState.charProcessingFinished()

  // cross-state safe interaction: these two functions are called under the same text buffer lock textBufferState is updated under

  override fun textBufferCharacterIndices(): LongRange {
    return textBufferState.textBufferCollected() ?: LongRange.EMPTY
  }

  override fun textBufferCollected(event: TerminalContentUpdatedEvent) {
    eventFlowState.textBufferCollected(event)
  }

  override fun eventCollected(event: TerminalContentUpdatedEvent) {
    // cross-state safe interaction: using the shared queue to transfer read ranges
    val latency = eventFlowState.eventCollected(event, readingState.readRanges) ?: return
    // If the sessionId is not known yet, we still collect statistics to ensure a consistent state but skip reporting.
    // This can only happen very early during the session startup.
    val sessionId = this.sessionId ?: return
    if (latency.min != null) {
      ReworkedTerminalUsageCollector.logBackendMinOutputLatency(sessionId, latency.min.index, latency.min.duration)
    }
    if (latency.max != null) {
      ReworkedTerminalUsageCollector.logBackendMaxOutputLatency(sessionId, latency.max.index, latency.max.duration)
    }
  }
}

private class FusAwareTtyConnector(private val original: TtyConnector, private val outputActivity: BackendOutputActivity) : TtyConnector {
  override fun read(buf: CharArray, offset: Int, length: Int): Int = original.read(buf, offset, length).also { charsRead ->
    outputActivity.charsRead(charsRead)
  }

  override fun write(bytes: ByteArray) {
    val typingActivity = getInstance().getBackendTypingActivityOrNull(bytes)
    try {
      original.write(bytes)
      typingActivity?.reportDuration()
    }
    finally {
      typingActivity?.finishBytesProcessing()
    }
  }

  override fun write(string: String) {
    original.write(string)
  }

  override fun isConnected(): Boolean = original.isConnected

  override fun waitFor(): Int = original.waitFor()

  override fun ready(): Boolean = original.ready()

  override fun getName(): String? = original.name

  override fun close() {
    original.close()
  }

  override fun resize(termSize: TermSize) {
    original.resize(termSize)
  }
}

// used to track individual instances of data classes
private class IdentityWrapper<T : Any>(private val instance: T) {
  override fun equals(other: Any?): Boolean = instance === (other as? IdentityWrapper<T>)?.instance
  override fun hashCode(): Int = System.identityHashCode(instance)
}

private class FusAwareTtyBasedDataStream(
  private val original: TerminalDataStream,
  private val fusActivity: BackendOutputActivity,
) : TerminalDataStream {
  override fun getChar(): Char {
    @Suppress("UsePropertyAccessSyntax")
    val result = original.getChar()
    fusActivity.charsProcessed(1)
    return result
  }

  override fun pushChar(c: Char) {
    fusActivity.charsProcessed(-1)
    original.pushChar(c)
  }

  override fun readNonControlCharacters(maxChars: Int): String? {
    val result = original.readNonControlCharacters(maxChars)
    fusActivity.charsProcessed(result.length)
    return result
  }

  override fun pushBackBuffer(bytes: CharArray, length: Int) {
    fusActivity.charsProcessed(-length)
    original.pushBackBuffer(bytes, length)
  }

  override fun isEmpty(): Boolean = original.isEmpty
}

private fun <T : Any> T.toIdentity(): IdentityWrapper<T> = IdentityWrapper(this)

private val LOG = logger<BackendLatencyService>()
