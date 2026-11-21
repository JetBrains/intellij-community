// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator.impl.ghostty.winStackCommitter

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout.ADDRESS
import java.lang.foreign.ValueLayout.JAVA_INT
import java.lang.foreign.ValueLayout.JAVA_LONG
import java.lang.invoke.MethodHandle

/**
 * Measures the committed part of the stack of the current thread. Works on 64-bit Windows only.
 */
internal object WindowsThreadStack {
  /** Returns how many bytes [action] adds to the committed part of the stack of the current thread. */
  fun committedGrowth(action: () -> Unit): Long {
    val before = committedSize()
    action()
    return committedSize() - before
  }

  /** Runs [measure] on a new thread with a large stack, and returns its result. */
  fun onNewThread(measure: () -> Long): Long {
    // The first measurement runs deep JVM code. It must not run on the new thread.
    committedSize()
    var result: Result<Long>? = null
    val thread = Thread(null, { result = runCatching(measure) }, "stack committer test", THREAD_STACK_SIZE)
    thread.start()
    thread.join()
    return checkNotNull(result).getOrThrow()
  }

  /** Runs [action] below [depth] more frames. */
  fun <T> atDepth(depth: Int, action: () -> T): T {
    return if (depth == 0) action() else atDepth(depth - 1, action)
  }

  /** A depth for [atDepth] that puts a call below the part of the stack that a stack commit committed. */
  const val DEEP_CALL_DEPTH: Int = 50_000

  /** The smallest recursion of a stack commit takes 64 KB. The limit leaves room for the frames of the measurement. */
  const val MIN_COMMIT_GROWTH: Long = 56L * 1024

  /** A call that commits nothing can still move the committed part by some pages. */
  const val MAX_IDLE_GROWTH: Long = 16L * 1024

  /** Holds [DEEP_CALL_DEPTH] frames also in the interpreter. */
  private const val THREAD_STACK_SIZE: Long = 64L * 1024 * 1024

  private val linker: Linker = Linker.nativeLinker()
  private val kernel32: SymbolLookup = SymbolLookup.libraryLookup("kernel32", Arena.global())

  // void GetCurrentThreadStackLimits(PULONG_PTR LowLimit, PULONG_PTR HighLimit)
  private val getCurrentThreadStackLimits: MethodHandle = linker.downcallHandle(
    kernel32.find("GetCurrentThreadStackLimits").orElseThrow(),
    FunctionDescriptor.ofVoid(ADDRESS, ADDRESS),
  )

  // SIZE_T VirtualQuery(LPCVOID lpAddress, PMEMORY_BASIC_INFORMATION lpBuffer, SIZE_T dwLength)
  private val virtualQuery: MethodHandle = linker.downcallHandle(
    kernel32.find("VirtualQuery").orElseThrow(),
    FunctionDescriptor.of(JAVA_LONG, ADDRESS, ADDRESS, JAVA_LONG),
  )

  /**
   * Returns the size in bytes of the committed memory that starts at the stack base. A guard page ends it.
   */
  fun committedSize(): Long {
    Arena.ofConfined().use { arena ->
      val lowLimit = arena.allocate(JAVA_LONG)
      val highLimit = arena.allocate(JAVA_LONG)
      getCurrentThreadStackLimits.invokeExact(lowLimit, highLimit)
      val stackLimit = lowLimit.get(JAVA_LONG, 0L)
      val stackBase = highLimit.get(JAVA_LONG, 0L)

      val info = arena.allocate(MEMORY_INFO_SIZE)
      var committedStart = stackBase
      while (committedStart > stackLimit) {
        val written = virtualQuery.invokeExact(MemorySegment.ofAddress(committedStart - 1), info, MEMORY_INFO_SIZE) as Long
        check(written != 0L) { "VirtualQuery failed" }
        val committed = info.get(JAVA_INT, STATE_OFFSET) == MEM_COMMIT && info.get(JAVA_INT, PROTECT_OFFSET) and PAGE_GUARD == 0
        if (!committed) break
        committedStart = info.get(JAVA_LONG, BASE_ADDRESS_OFFSET)
      }
      return stackBase - committedStart
    }
  }

  // The 64-bit layout of MEMORY_BASIC_INFORMATION.
  private const val MEMORY_INFO_SIZE: Long = 48
  private const val BASE_ADDRESS_OFFSET: Long = 0
  private const val STATE_OFFSET: Long = 32
  private const val PROTECT_OFFSET: Long = 36

  private const val MEM_COMMIT: Int = 0x1000
  private const val PAGE_GUARD: Int = 0x100
}
