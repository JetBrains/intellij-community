package com.intellij.performanceTesting.freezes.diogen

import com.intellij.performanceTesting.freezes.diogen.ThreadDumpParser.extractThisLevelLock
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import java.nio.CharBuffer

/**
 * Types of complimentary traces in freeze analysis.
 * Used for both generating trace headers and filtering traces by type.
 */
enum class ComplimentaryTraceType(private val prefix: String) {
  EDT("EDT"),
  BLOCKED("BLOCKED"),
  RA("RA"),
  STARVATION("STARVATION"),

  /**
   * Background thread that initiated `runOnEdtWithTransferredWriteActionAndWait` and is parked
   * in `InternalThreading$TransferredWriteActionEvent.blockingWait`. Surfaced so freeze mappings
   * can distinguish sub-cases by the originator's stack (e.g., VFS refresh vs other triggers)
   * — the EDT stack alone only shows the transferred lambda.
   */
  WA_ORIGINATOR("WA_ORIGINATOR");

  fun matches(key: String): Boolean = key.startsWith(prefix)
  override fun toString(): String = prefix
}

private const val WA_TRANSFER_EVENT_MARKER = $$"InternalThreading$TransferredWriteActionEvent"
private const val WA_BLOCKING_WAIT_MARKER = "$WA_TRANSFER_EVENT_MARKER.blockingWait"

private const val string = ", state: RUNNING"

// Min size of a similar-stack group to surface as a STARVATION complimentary trace.
private const val STARVATION_THRESHOLD = 50

private const val FNV_OFFSET_64: Long = -3750763034362895579L  // 0xCBF29CE484222325
private const val FNV_PRIME_64: Long = 1099511628211L

private fun CharArray.startsWith(at: Int, end: Int, prefix: String): Boolean {
  if (at + prefix.length > end) return false
  for (i in prefix.indices) if (this[at + i] != prefix[i]) return false
  return true
}

// True for `at fqn(...` where the `(...` block is unclosed — the obfuscator split it across lines.
// Bare frames (`at foo.Bar.baz`, no parens) are valid and must not trigger absorption,
// otherwise we'd swallow the gap to the next thread.
private fun CharArray.isUnclosedAtFrame(contentStart: Int, lineEnd: Int): Boolean {
  if (!startsWith(contentStart, lineEnd, "at ")) return false
  var depth = 0
  for (i in contentStart until lineEnd) {
    when (this[i]) {
      '(' -> depth++
      ')' -> if (depth > 0) depth--
    }
  }
  return depth > 0
}

object ThreadDumpParser {

  /**
   * Result of freeze cause analysis containing the cause thread and pre-collected complimentary traces.
   */
  class FreezeCauseResult(
    @JvmField val cause: Trace?,
    @JvmField val edt: Trace?,
    @JvmField val raThreads: List<Trace>,  // RA threads excluding cause and EDT; empty if cause doesn't hold read lock
    @JvmField val blockedThreads: List<Trace> = emptyList(),  // Threads blocked waiting for cause's lock
    @JvmField val starvationGroups: List<StarvationGroup> = emptyList(),  // Groups of >= STARVATION_THRESHOLD threads with identical normalized stacks
    @JvmField val waOriginators: List<Trace> = emptyList(),  // BGT threads waiting on transferred WA when EDT is running it
  )

  class StarvationGroup(@JvmField val representative: Trace, @JvmField val count: Int)

  class Trace(@JvmField val name: Line, @JvmField val lines: List<Line>, @JvmField val dump: CharArray) : CharSequence {
    val isEDT: Boolean get() = isEDT(name)
    val text: String get() = String(dump, name.start, (lines.lastOrNull() ?: name).end - name.start)

    /**
     * Extracts state lines (java.lang.Thread.State: ... and continuation lines) from the raw dump text.
     * Returns null if no state line is found.
     */
    fun extractStateLines(): CharSequence? {
      // Find first actual frame line (starting with "at ")
      val firstFrame = lines.firstOrNull { it.startsWith("at ", it.indent) } ?: return null
      val firstFrameStart = firstFrame.start
      // Search for state line between name.end and first frame
      val searchStart = name.end
      val marker = "java.lang.Thread.State:"
      outer@ for (i in searchStart until firstFrameStart - marker.length) {
        for (j in marker.indices) {
          if (dump[i + j] != marker[j]) continue@outer
        }
        // Found marker at position i, find end (before first frame)
        var end = firstFrameStart
        while (end > i && dump[end - 1] <= ' ') end--
        return Line(i, end, 0, dump)
      }
      return null
    }

    /**
     * Extracts the thread name from "owned by" clause if this thread is blocked waiting for a lock.
     * Format: `on ... owned by "ThreadName" Id=...`
     * Returns null if no ownership info is found.
     * Works directly on CharSequence to avoid String allocation.
     */
    fun extractLockOwner(): String? {
      val stateLines = extractStateLines() ?: return null
      val marker = "owned by \""
      val idx = stateLines.indexOf(marker)
      if (idx < 0) return null
      val start = idx + marker.length
      val end = stateLines.indexOf('"', start)
      if (end < 0) return null
      return stateLines.subSequence(start, end).toString()
    }

    /**
     * Checks if this thread is blocked waiting for a lock.
     * Detects both BLOCKED state and WAITING state with lock ownership info.
     */
    fun isBlocked(): Boolean {
      val stateLines = extractStateLines() ?: return false
      // BLOCKED state - classic blocking
      if (stateLines.contains("BLOCKED")) return true
      // WAITING state with lock ownership - effectively blocked waiting for a lock
      if (stateLines.contains("WAITING") && stateLines.contains("owned by")) return true
      return false
    }

    override fun get(index: Int): Char = dump[index]
    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = Line(startIndex, endIndex, 0, dump)
    override val length: Int = dump.size

    override fun toString(): String = name.toString()
  }

  class Line(@JvmField val start: Int, @JvmField val end: Int, @JvmField val indent: Int, private val dump: CharArray) : CharSequence {
    override fun get(index: Int): Char = dump[index + start]
    override fun subSequence(startIndex: Int, endIndex: Int): Line = Line(start + startIndex, start + endIndex, 0, dump)
    override val length: Int = end - start

    override fun hashCode(): Int = if (isEmpty()) 0 else get(length / 2).code
    override fun equals(other: Any?) = other is Line && start == other.start && end == other.end && dump === other.dump

    override fun toString(): String = String(dump, start, length)
  }

  fun getStackTraces(originalDump: CharSequence): List<Trace> {
    val chars = (originalDump as? CharBuffer)?.array() ?: originalDump.toString().toCharArray()
    val result = ArrayList<Trace>(100)
    val lines = ArrayList<Line>(originalDump.length / 100)
    var coroutines = false
    var lineStart = 0
    var lineEnd = 0
    var spaces = 0
    var skippedEmptyLine = false

    val finishTrace: () -> Unit = {
      if (lines.isNotEmpty()) {
        result.add(Trace(lines.first(), if (lines.isNotEmpty()) lines.subList(1, lines.size).toList()
        else emptyList(), chars))
        lines.clear()
      }
    }
    while (lineEnd <= chars.size) {
      val ch = if (lineEnd < chars.size) chars[lineEnd] else '\n'
      if (ch <= ' ' && spaces >= 0) spaces++
      else if (ch > ' ' && spaces >= 0) spaces = -spaces - 1
      if (ch != '\n') {
        lineEnd++
        continue
      }
      val skip = if (spaces < 0) -spaces - 1 else spaces
      val line = if (lineStart + skip < lineEnd) {
        // Obfuscators (e.g. Allatori) split a frame's signature across physical lines.
        // Absorb the `\n` and keep accumulating until the closing `)`.
        if (lineEnd < chars.size && chars[lineEnd - 1] != ')'
            && chars.isUnclosedAtFrame(lineStart + skip, lineEnd)) {
          chars[lineEnd] = ' '
          lineEnd++
          continue
        }
        Line(lineStart, lineEnd, skip, chars).also {
          lineStart = ++lineEnd; spaces = 0
        }
      }
      else {
        skippedEmptyLine = true
        if (lineStart == lineEnd) {
          lineStart++
          spaces = 0
        }
        lineEnd++
        continue
      }
      if (line.startsWith("java.lang.Thread.State:", line.indent)) {
        // Skip - state line is not part of the stack trace
      }
      else if (line.startsWith("---------- Coroutine dump ", line.indent)) {
        finishTrace()
        coroutines = true
      }
      else if (line.startsWith("- ", line.indent)) {
        if (coroutines && skippedEmptyLine) finishTrace()
        lines.add(line)
      }
      else if (line.startsWith("at ", line.indent) ||
               line.startsWith("Locked ownable synchronizers:", line.indent)) {
        lines.add(line)
      }
      else if (line.startsWith("---------- Event counts", line.indent)) {
        break
      }
      else {
        if (skippedEmptyLine) finishTrace()
        lines.add(line)
      }
      skippedEmptyLine = false
    }
    finishTrace()
    return result
  }

  /**
   * Loosely based on com.intellij.diagnostic.IdeaFreezeReporter.getCauseThread.
   *
   * Make sure to bump org.jetbrains.kotlin.diogen.process.render.OutputGeneratorKt.SITE_VERSION otherwise the new inferred stack
   * won't be used for old reports.
   */
  fun getFreezeCauseThread(threads: List<Trace>): Trace? = analyzeFreeze(threads).cause

  /**
   * Analyzes thread dump to find freeze cause, collect complimentary traces, and surface
   * thread-starvation groups (clusters of >= [STARVATION_THRESHOLD] similarly-stuck threads).
   */
  fun analyzeFreeze(threads: List<Trace>): FreezeCauseResult {
    val base = findFreezeCause(threads)
    val cause = base.cause ?: return base
    val excluded = HashSet<Trace>(base.raThreads.size + base.blockedThreads.size + base.waOriginators.size + 2).apply {
      add(cause)
      base.edt?.let { add(it) }
      addAll(base.raThreads)
      addAll(base.blockedThreads)
      addAll(base.waOriginators)
    }
    val starvation = collectStarvationGroups(threads, excluded)
    return if (starvation.isEmpty()) base
    else FreezeCauseResult(cause, base.edt, base.raThreads, base.blockedThreads, starvation, base.waOriginators)
  }

  /**
   * Finds the freeze cause and its complimentary EDT/RA/BLOCKED traces.
   * Returns starvation-free results — [analyzeFreeze] augments them.
   */
  private fun findFreezeCause(threads: List<Trace>): FreezeCauseResult {
    val edt = threads.firstOrNull { isEDT(it.name) } ?: run {
      threads.firstOrNull { tr ->
        tr.lines.any { l ->
          l.contains("java.awt.EventQueue.dispatch") ||
          l.contains("java.awt.EventDispatchThread")
        }
      }
    }
    if (edt == null) {
      return FreezeCauseResult(null, null, emptyList())
    }
    val waOriginators = collectWaOriginators(threads, edt)
    var edtLockName = edt.lines.firstOrNull()?.takeIf { it.startsWith("on ", it.indent) }
    edtLockName =
      edt.lines.firstOrNull { it.contains("SuvorovProgress") } ?: edtLockName
      ?: return FreezeCauseResult(edt, edt, emptyList(), waOriginators = waOriginators)
    val edtLockOwnedByThread = edtLockName.toString().substringBetween("by \"", "\"", missingDelimiterValue = "").takeIf { it.isNotEmpty() }

    val checkMode: Int = when {
      checkMode1Trie.hasMatches(edtLockName) -> 1
      edtLockName.contains("javax.swing.text.") &&
      edt.lines.any { it.contains("javax.swing.text.AbstractDocument.readLock") } -> 2
      edtLockName.contains("kotlinx.coroutines.BlockingCoroutine") &&
      edt.lines.any { checkMode3Trie.hasMatches(it) } -> 3
      edtLockOwnedByThread != null -> 4
      edt.lines.any { it.contains("AWTThreading.executeWaitToolkit") } -> 5
      edt.lines.any { checkMode6EdtTrie.hasMatches(it) } -> 6
      else -> return FreezeCauseResult(edt, edt, emptyList(), waOriginators = waOriginators)
    }

    // Collect RA threads in the same pass as finding the cause
    val raThreads = mutableListOf<Trace>()
    val nonRunnableCandidates = mutableListOf<Trace>()
    var runnableCause: Trace? = null

    for (thread in threads) {
      if (thread == edt) continue
      val isCoroutine = thread.name.startsWith("- ", thread.name.indent)

      // Collect RA threads for complimentary traces. WA originators are surfaced under a
      // dedicated complimentary type — exclude them here so they don't double-appear under RA
      // when their outer frame is `runReadAction`.
      if (!isCoroutine && thread !in waOriginators && isWithReadLock(thread)) {
        raThreads.add(thread)
      }

      // Skip cause detection if we already found a runnable cause
      // (but continue loop to collect all RA threads)
      if (runnableCause != null) continue

      val candidate = when (checkMode) {
        1 -> !isCoroutine && isWithReadLock(thread) || isWithWriteLock(thread)
        2 -> !isCoroutine && thread.lines.any { it.contains("javax.swing.text.") }
        // In Mode 3, accept coroutines matching the specific runWithInputEventEdtDispatcher pattern,
        // or RUNNING coroutines with thisLevelLock (filtering out unrelated always-running
        // coroutines like "selector" or "async freeze dumper")
        3 -> isCoroutine && (thread.name.contains("\"runWithInputEventEdtDispatcher\":BlockingCoroutine")
                             || thread.name.contains(string) && thread.name.contains("thisLevelLock"))
        4 -> !isCoroutine && thread.name.contains(edtLockOwnedByThread!!)
        5 -> !isCoroutine && thread.name.contains("AWTThreading pool-")
        6 -> !isCoroutine && !thread.name.contains("TimerQueue") && thread.lines.any { checkMode6Trie.hasMatches(it) }
        else -> throw AssertionError()
      }
      if (!candidate) continue
      if (thread.name.contains("runnable") || thread.name.contains(string)) {
        runnableCause = thread
        // Don't break - continue to collect all RA threads
      }
      else {
        nonRunnableCandidates.add(thread)
      }
    }

    // Prefer candidates that lead to a resolvable chain: a lock owner (ownership chain) or a
    // BlockingCoroutine waiter (coroutine chain). This avoids picking victims like threads stuck
    // in runWriteAction waiting on RunSuspend when a better candidate exists further in the dump.
    val nonRunnableCause = nonRunnableCandidates.firstOrNull { it.extractLockOwner() != null }
                           ?: nonRunnableCandidates.firstOrNull { extractBlockingCoroutineId(it) != null }
                           ?: nonRunnableCandidates.firstOrNull()

    val blockedThreads = mutableListOf<Trace>()
    var cause = when {
      runnableCause != null -> runnableCause
      nonRunnableCause == null || nonRunnableCause.lines.any { it.contains(WA_BLOCKING_WAIT_MARKER) } -> edt
      else -> nonRunnableCause
    }

    val threadsByName by lazy { threads.associateBy { extractThreadName(it.name) } }

    // If cause is blocked, follow the ownership chain to find the actual cause
    if (cause.isBlocked()) {
      val (chainEnd, chain) = followLockOwnershipChain(cause, threadsByName, edt)
      blockedThreads.addAll(chain)
      cause = chainEnd

      // Collect ALL threads blocked waiting for the cause's lock (not just the chain we followed)
      val causeName = extractThreadName(cause.name).toString()
      for (thread in threads) {
        if (thread == cause || thread == edt || thread in blockedThreads) continue
        val lockOwner = thread.extractLockOwner()
        if (lockOwner == causeName) {
          blockedThreads.add(thread)
        }
      }
    }

    // Follow BlockingCoroutine chains to find actual cause
    val coroutineChainResult = followBlockingCoroutineChain(cause, threads)
    if (coroutineChainResult != null) {
      blockedThreads.addAll(coroutineChainResult.blockedChain)
      cause = coroutineChainResult.actualCause
    }
    else if (checkMode == 3 && cause.name.contains(", state: RUNNING")) {
      // For checkMode 3: capture EDT's BlockingCoroutine chain if it leads to our RUNNING cause
      val edtChainResult = followBlockingCoroutineChain(edt, threads)
      if (edtChainResult?.actualCause == cause) {
        // Add the SUSPENDED coroutine (EDT is tracked separately via result.edt)
        blockedThreads.addAll(edtChainResult.blockedChain.filter { it != edt })
      }
    }

    // Mode 3 fallback: when coroutine chain resolution fails, look for regular threads
    // doing service initialization (LazyInstanceHolder.initialize). These are very likely threads
    // executing work on behalf of the BlockingCoroutine that EDT is waiting on.
    // If any are BLOCKED, follow their lock chain to find the actual cause.
    // If none are BLOCKED but some are RUNNING, use the RUNNING one as the cause.
    if (checkMode == 3 && (coroutineChainResult == null || cause.name.startsWith("- ", cause.name.indent))) {
      val serviceInitThreads = threads.filter { thread ->
        thread != edt && !thread.name.startsWith("- ", thread.name.indent) &&
        thread.lines.any { it.contains("LazyInstanceHolder.initialize") }
      }
      for (initThread in serviceInitThreads) {
        if (initThread.isBlocked()) {
          val (chainEnd, chain) = followLockOwnershipChain(initThread, threadsByName, edt)
          if (chain.isNotEmpty()) {
            cause = chainEnd
            blockedThreads.addAll(chain)
            break
          }
        }
      }
      if (blockedThreads.isEmpty()) {
        val runningInit = serviceInitThreads.firstOrNull {
          it.name.contains("runnable") || it.name.contains("RUNNABLE")
        }
        if (runningInit != null) {
          cause = runningInit
        }
      }

    }

    // Only include RA threads if cause holds read lock, excluding cause itself and blocked threads
    val finalRaThreads = if (isWithReadLock(cause)) {
      raThreads.filter { it != cause && it !in blockedThreads }
    }
    else {
      emptyList()
    }

    return FreezeCauseResult(cause, edt, finalRaThreads, blockedThreads, waOriginators = waOriginators)
  }

  /**
   * Collects BGT threads parked in [WA_BLOCKING_WAIT_MARKER]. Returns empty unless [edt] is
   * currently dispatching a transferred write action (its frames contain [WA_TRANSFER_EVENT_MARKER]).
   */
  private fun collectWaOriginators(threads: List<Trace>, edt: Trace): List<Trace> {
    if (edt.lines.none { it.contains(WA_TRANSFER_EVENT_MARKER) }) return emptyList()
    var result: MutableList<Trace>? = null
    for (thread in threads) {
      if (thread === edt) continue
      // Skip coroutine entries — they're a parallel representation of the same work
      // (e.g., the BlockingCoroutine that the underlying thread is running), not a separate originator.
      if (thread.name.startsWith("- ", thread.name.indent)) continue
      if (thread.lines.any { it.contains(WA_BLOCKING_WAIT_MARKER) }) {
        if (result == null) result = mutableListOf()
        result.add(thread)
      }
    }
    return result ?: emptyList()
  }

  /**
   * Groups non-coroutine threads (excluding cause/EDT/RA/blocked) by their normalized stack. Any
   * group reaching [STARVATION_THRESHOLD] is surfaced so mappings can match thread-starvation
   * patterns where the IDE UI shows e.g. "DefaultDispatcher-worker-48 and 200 similar".
   */
  private fun collectStarvationGroups(
    threads: List<Trace>,
    excluded: Set<Trace>,
  ): List<StarvationGroup> {
    val candidates = threads.filter {
      it !in excluded && !it.name.startsWith("- ", it.name.indent)
    }
    // Cheap early-out: with fewer candidates than the threshold, no group can reach it.
    if (candidates.size < STARVATION_THRESHOLD) return emptyList()

    // Primitive-keyed map + small accumulator: we never need the full per-group thread list,
    // only the count and one representative. Saves ~MutableList per unique stack and ~String
    // per candidate vs the obvious HashMap<normalizedString, List<Trace>>.
    val byStack = Long2ObjectOpenHashMap<GroupAcc>()
    for (thread in candidates) {
      val key = normalizedStackHash(thread)
      if (key == 0L) continue
      val acc = byStack.get(key)
      if (acc == null) byStack.put(key, GroupAcc(thread)) else acc.count++
    }
    return byStack.values
      .filter { it.count >= STARVATION_THRESHOLD }
      .map { StarvationGroup(it.representative, it.count) }
      .sortedByDescending { it.count }
  }

  private class GroupAcc(@JvmField val representative: Trace) {
    @JvmField
    var count: Int = 1
  }

  // Volatile bits (lambda class ids `$$Lambda/0xHEX`, hash-coded `@HEX` refs) are stripped from
  // the hash so threads with the same code but different ids land in the same starvation bucket.
  // FNV-1a over chars (skipping volatile id runs) instead of materializing a normalized String —
  // saves ~3 KB allocation per candidate at scale. Returns 0 if the trace has no `at ` frames
  // (used as the "skip" sentinel by the caller).
  //
  // Collision risk for 64-bit FNV-1a at our scale (~500 unique stacks/dump, 76K dumps):
  // ~1.4×10⁻¹⁴ per dump, ~10⁻⁹ across a full run. Worst case is one falsely-merged group;
  // the threshold filter limits the impact further.
  private fun normalizedStackHash(trace: Trace): Long {
    var h = FNV_OFFSET_64
    var hasFrames = false
    for (line in trace.lines) {
      if (!line.startsWith("at ", line.indent)) continue
      hasFrames = true
      var i = 0
      val n = line.length
      while (i < n) {
        val c = line[i]
        if (c == '@' && i + 1 < n && line[i + 1].isHex()) {
          i++
          while (i < n && line[i].isHex()) i++
          continue
        }
        if (c == '0' && i + 2 < n && line[i + 1] == 'x' && line[i + 2].isHex()) {
          i += 2
          while (i < n && line[i].isHex()) i++
          continue
        }
        h = (h xor c.code.toLong()) * FNV_PRIME_64
        i++
      }
      h = (h xor '\n'.code.toLong()) * FNV_PRIME_64
    }
    return if (hasFrames) h else 0L
  }

  private fun Char.isHex(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

  /**
   * Extracts thread name from the thread header line.
   * Format: `"ThreadName" prio=...` or `"ThreadName" #N ...`
   */
  private fun extractThreadName(name: CharSequence): CharSequence {
    val start = name.indexOf('"')
    if (start < 0) return name
    val end = name.indexOf('"', start + 1)
    if (end < 0) return name
    return name.substring(start + 1, end)
  }

  /**
   * Result of following the BlockingCoroutine chain.
   * @param actualCause the RUNNING coroutine that is the actual cause
   * @param blockedChain the chain of blocked traces (original thread, SUSPENDED coroutine)
   */
  data class CoroutineChainResult(
    val actualCause: Trace,
    val blockedChain: List<Trace>,
  )

  /**
   * Follows the lock ownership chain from a blocked thread to find the actual lock holder.
   * Returns the thread at the end of the chain and the list of blocked threads along the way.
   */
  private fun followLockOwnershipChain(
    start: Trace,
    threadsByName: Map<CharSequence, Trace>,
    edt: Trace,
  ): Pair<Trace, List<Trace>> {
    val chain = mutableListOf<Trace>()
    val visited = mutableSetOf<Trace>()
    var current = start
    while (current !in visited) {
      visited.add(current)
      val ownerName = current.extractLockOwner() ?: break
      val owner = threadsByName[ownerName]
      if (owner != null && owner != edt) {
        chain.add(current)
        current = owner
      }
      else {
        break
      }
    }
    return current to chain
  }

  /**
   * If a thread is waiting on a BlockingCoroutine, follow the coroutine chain to find the actual cause.
   *
   * When a thread waits on a BlockingCoroutine that is SUSPENDED with a thisLevelLock,
   * find the RUNNING coroutine with the same lock - that's the actual cause.
   *
   * Coroutines may be top-level traces or nested inside parent coroutine traces in the dump.
   *
   * @return the result containing the RUNNING coroutine and the blocked chain, or null if not applicable
   */
  private fun followBlockingCoroutineChain(cause: Trace, threads: List<Trace>): CoroutineChainResult? {
    // Check if cause is waiting on a BlockingCoroutine
    val coroutineId = extractBlockingCoroutineId(cause) ?: return null

    // Find the SUSPENDED coroutine with this ID (may be top-level or nested)
    val (suspendedTrace, suspendedLine) = findCoroutineById(threads, "@$coroutineId", ", state: SUSPENDED")
                                          ?: return null

    // Extract the thisLevelLock from the SUSPENDED coroutine's context
    val lockId = extractThisLevelLock(suspendedLine) ?: return null

    // Find a RUNNING coroutine with the same lock (may be top-level or nested)
    val (runningTrace, _) = findRunningCoroutineByLock(threads, lockId) ?: return null

    // Return the RUNNING coroutine as actual cause, with original cause and SUSPENDED coroutine as blocked chain
    return CoroutineChainResult(
      actualCause = runningTrace,
      blockedChain = listOf(cause, suspendedTrace)
    )
  }

  /**
   * Finds a coroutine by ID and state, searching both top-level traces and nested coroutine lines.
   * For nested coroutines, extracts a sub-trace containing only that coroutine's frames.
   * @return the trace and the matching coroutine header line, or null if not found
   */
  private fun findCoroutineById(threads: List<Trace>, idMarker: String, stateMarker: String): Pair<Trace, CharSequence>? {
    for (trace in threads) {
      val name = trace.name
      if (name.startsWith("- ", name.indent) && name.contains(idMarker) && name.contains(stateMarker)) {
        return trace to name
      }
      // Search nested coroutines in trace lines — only coroutine traces can have nested coroutines
      if (!name.startsWith("- ", name.indent)) continue
      val lines = trace.lines
      for (i in lines.indices) {
        val line = lines[i]
        if (line.startsWith("- ", line.indent) && line.contains(idMarker) && line.contains(stateMarker)) {
          return extractSubTrace(trace, i) to line
        }
      }
    }
    return null
  }

  /**
   * Finds a RUNNING coroutine with the given thisLevelLock.
   * Skips coroutines with no stack frames (scope wrappers).
   * Uses lockId substring check to avoid expensive [extractThisLevelLock] parsing on non-matching lines.
   */
  private fun findRunningCoroutineByLock(threads: List<Trace>, lockId: String): Pair<Trace, CharSequence>? {
    for (trace in threads) {
      val name = trace.name
      if (!name.startsWith("- ", name.indent)) continue // only coroutine traces
      if (name.contains(string) && name.contains(lockId) && extractThisLevelLock(name) == lockId &&
          trace.lines.any { it.startsWith("at ", it.indent) }) {
        return trace to name
      }
      val lines = trace.lines
      for (i in lines.indices) {
        val line = lines[i]
        if (!line.startsWith("- ", line.indent) || !line.contains(string)) continue
        // Quick substring check before expensive lock extraction
        if (!line.contains(lockId)) continue
        if (extractThisLevelLock(line) != lockId) continue
        // Check next line has a frame before building sub-trace
        if (i + 1 < lines.size && lines[i + 1].startsWith("at ", lines[i + 1].indent)) {
          return extractSubTrace(trace, i) to line
        }
      }
    }
    return null
  }

  /**
   * Extracts a sub-trace from a parent trace starting at the given line index.
   * The line at [lineIndex] becomes the name, subsequent "at " lines become the frames,
   * stopping at the next "- " line or end.
   * Strips the nesting indentation (name's indent level), preserving relative indent for child lines.
   */
  private fun extractSubTrace(parent: Trace, lineIndex: Int): Trace {
    val dump = parent.dump
    val nameLine = parent.lines[lineIndex]
    val strip = nameLine.indent
    val name = Line(nameLine.start + strip, nameLine.end, 0, dump)
    val parentLines = parent.lines
    val subLines = ArrayList<Line>(16)
    for (j in lineIndex + 1 until parentLines.size) {
      val line = parentLines[j]
      if (line.startsWith("- ", line.indent)) break
      val lineStrip = minOf(strip, line.indent)
      subLines.add(Line(line.start + lineStrip, line.end, line.indent - lineStrip, dump))
    }
    return Trace(name, subLines, dump)
  }

  /**
   * Extracts a substring from [start] until a delimiter character is found.
   */
  private inline fun CharSequence.extractUntil(start: Int, isDelimiter: (Char) -> Boolean): String {
    var end = start
    while (end < length && !isDelimiter(this[end])) end++
    return subSequence(start, end).toString()
  }

  /**
   * Extracts BlockingCoroutine ID from a thread that is waiting on one.
   * Format: ` on kotlinx.coroutines.BlockingCoroutine@<id>`
   * @return the coroutine ID (hex string) or null if not waiting on a BlockingCoroutine
   */
  private fun extractBlockingCoroutineId(trace: Trace): String? {
    val marker = "on kotlinx.coroutines.BlockingCoroutine@"
    for (line in trace.lines) {
      val idx = line.indexOf(marker)
      if (idx >= 0) return line.extractUntil(idx + marker.length) { it <= ' ' }
    }
    return null
  }

  /**
   * Extracts thisLevelLock ID from a coroutine's context info.
   * Format: `[..., ComputationState(level=0,thisLevelLock=com.intellij.core.rwmutex.RWMutexIdeaImpl@<id>,isParallelizedRead=false), ...]`
   * @return the lock ID (e.g., "37fba1") or null if not found
   */
  private fun extractThisLevelLock(name: CharSequence): String? {
    val marker = "thisLevelLock="
    val idx = name.indexOf(marker)
    if (idx < 0) return null
    val atIdx = name.indexOf('@', idx + marker.length)
    if (atIdx < 0) return null
    return name.extractUntil(atIdx + 1) { it in ",) ]" }.ifEmpty { null }
  }

  // Trie for checkMode 1: EDT waiting on read/write lock synchronization
  private val checkMode1Trie = buildTrie(listOf(
    "RunSuspend",
    "ReadMostlyRWLock",
    "SuvorovProgress",
    "EternalEventStealer"
  ))

  // Trie for checkMode 3: EDT waiting on BlockingCoroutine with parallelism compensation
  private val checkMode3Trie = buildTrie(listOf(
    "Utils.runWithInputEventEdtDispatcher",
    "IntelliJCoroutinesFacade.runBlockingWithParallelismCompensation",
    "IntellijCoroutines.runBlockingWithParallelismCompensation"
  ))

  // Trie for checkMode 6 EDT condition: EDT blocked on Swing document lock
  private val checkMode6EdtTrie = buildTrie(listOf(
    "javax.swing.text.AbstractDocument.readLock",
    "javax.swing.text.AbstractDocument.writeLock"
  ))

  // Trie for checkMode 6 candidate: background thread working with java.awt/javax.swing
  private val checkMode6Trie = buildTrie(listOf(
    "java.awt.",
    "javax.swing."
  ))

  // from com.intellij.diagnostic.IdeaFreezeReporter.isWithReadLock
  private val writeLockTrie = buildTrie(listOf(
    "CoroutinesKt.backgroundWriteAction",
    "ApplicationImpl.runWriteAction",
    "NestedLocksThreadingSupport.runWriteAction"
  ))

  private fun isWithWriteLock(trace: Trace): Boolean {
    return trace.lines.any { writeLockTrie.hasMatches(it) }
  }

  fun isWithReadLock(trace: Trace): Boolean {
    val s0 = "getReadPermit"
    val s1 = "waitABit"
    val s2 = "runReadAction"
    val s3 = "tryRunReadAction"
    val s4 = "insideReadAction"
    trace.lines.forEach {
      if (it.startsWith(" on ") && it.contains("RunSuspend")) return false
      if (!"at ".regionMatches(0, it, it.indent, 3)) return@forEach
      val parenIdx = it.lastIndexOf('(')
      if (parenIdx < 0) return@forEach
      val dotIndex = it.lastIndexOf('.', parenIdx)
      val len = parenIdx - dotIndex - 1
      if (s0.length == len && s0.regionMatches(0, it, dotIndex + 1, len) ||
          s1.length == len && s1.regionMatches(0, it, dotIndex + 1, len)) {
        return false
      }
      if (s2.length == len && s2.regionMatches(0, it, dotIndex + 1, len) ||
          s3.length == len && s3.regionMatches(0, it, dotIndex + 1, len) ||
          s4.length == len && s4.regionMatches(0, it, dotIndex + 1, len)) {
        return true
      }
    }
    if (trace.lines.any { it.contains("ReadActionProcessor") }) return true
    return false
  }

  private fun isEDT(name: CharSequence): Boolean = when {
    java.lang.Boolean.getBoolean("jb.dispatching.on.main.thread") -> name.contains("AppKit")
    else -> name.contains("AWT-EventQueue")
  }

  /**
   * Builds a trace string from a pre-analyzed freeze result.
   */
  fun buildTraceWithComplimentary(result: FreezeCauseResult): String? {
    val cause = result.cause ?: return null
    return buildString {
      append(cause.text)
      appendComplimentaryTraces(cause, result)
    }
  }

  /**
   * Appends complimentary traces using pre-collected data from [FreezeCauseResult].
   */
  fun StringBuilder.appendComplimentaryTraces(cause: Trace, result: FreezeCauseResult) {
    // Add EDT as complimentary trace if cause is not EDT
    if (!cause.isEDT && result.edt != null) {
      appendFramesWithHeaderAndState(result.edt) { append("\n---------- "); append(ComplimentaryTraceType.EDT); append(":\n") }
    }

    // Add blocked threads (threads that were blocked waiting for cause's lock)
    for ((blockedIndex, thread) in result.blockedThreads.withIndex()) {
      appendFramesWithHeaderAndState(thread) {
        append("\n---------- "); append(ComplimentaryTraceType.BLOCKED); append(blockedIndex); append(' '); append(thread.name); append(":\n")
      }
    }

    // Add transferred-write-action originators (BGT threads parked in blockingWait while EDT runs their lambda)
    for ((waIndex, thread) in result.waOriginators.withIndex()) {
      appendFramesWithHeaderAndState(thread) {
        append("\n---------- "); append(ComplimentaryTraceType.WA_ORIGINATOR); append(waIndex); append(' '); append(thread.name); append(":\n")
      }
    }

    // Add pre-collected RA threads
    for ((raIndex, thread) in result.raThreads.withIndex()) {
      appendFramesWithHeaderAndState(thread) {
        append("\n---------- "); append(ComplimentaryTraceType.RA); append(raIndex); append(' '); append(thread.name); append(":\n")
      }
    }

    for ((groupIndex, group) in result.starvationGroups.withIndex()) {
      val name = extractThreadName(group.representative.name)
      appendFramesWithHeaderAndState(group.representative) {
        append("\n---------- "); append(ComplimentaryTraceType.STARVATION); append(groupIndex)
        append(" \""); append(name); append("\" and "); append(group.count - 1); append(" similar:\n")
      }
    }
  }

  private inline fun StringBuilder.appendFramesWithHeaderAndState(thread: Trace, appendHeader: StringBuilder.() -> Unit): Boolean {
    var first = true
    // Include state line if available
    val stateLine = thread.extractStateLines()
    if (stateLine != null) {
      appendHeader()
      append(stateLine)
      first = false
    }
    // Include stack frames
    for (l in thread.lines) {
      if (!l.startsWith("at ", l.indent)) continue
      if (first) {
        appendHeader()
        first = false
      }
      else {
        append('\n')
      }
      append(l)
    }
    return !first
  }
}