// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator.impl.ghostty

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.system.LowLevelLocalMachineAccess
import com.intellij.util.system.OS
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Prevents a JVM crash on Windows when libghostty-vt calls back a JVM function (IJPL-261768).
 * Call [commit] before each native call to libghostty-vt that can call back.
 *
 * This class is a workaround for the JVM defect JDK-8394484.
 * Remove the class and its `terminal.ghostty.commit.stack.on.each.call` registry key when the runtime of the IDE has the fix.
 *
 * ### The problem
 *
 * At each entry to Java code, the JVM writes to the stack 32 KB ahead of the stack pointer. This distance
 * is the shadow zone of the JVM (`StackShadowPages`). The write checks for a stack overflow in advance,
 * and it makes the JVM commit the memory. So the stack is committed 32 KB ahead of the deepest Java frame.
 *
 * A JVM function that libghostty-vt calls back starts deeper, below the native frames of libghostty-vt.
 * The callback stub of the JVM enters Java code there, so the JVM writes 32 KB ahead of that point.
 * The first call of a new callback stub does this write in VM code. There the JVM does not handle
 * the fault of a write to stack memory that is not committed, and the JVM crashes.
 * The write is a stack probe of the JVM. It occurs before the called function runs,
 * for example before `TerminalListener.onRespondToHost`.
 *
 * The crash is rare, because it needs a new callback stub. The JVM usually takes the stub from its callback cache.
 * It makes a new stub only after the garbage collector clears that cache.
 *
 * The pictures show the stack for the native call in `GhosttyTerminalEmulator.writeToVt`.
 * All distances count from the frame of that function.
 *
 * ```
 * stack base
 * │   frames of the callers:
 * │     Thread.run
 * │     GhosttyTerminalSession.start
 * │     GhosttyTerminalEmulator.write
 * ├── (1) frame of GhosttyTerminalEmulator.writeToVt                     0 KB
 * │       native frames of ghostty_terminal_vt_write                 20-28 KB
 * ├── (2) frame of the callback stub of the JVM
 * │
 * ├── the JVM writes here, 32 KB ahead of (1), and commits the stack    32 KB
 * │
 * │   the JVM writes here, 32 KB ahead of (2), and crashes           52-60 KB
 * ▼
 * stack limit
 * ```
 *
 * ### The workaround
 *
 * [commit] runs a deep recursion in Java code, where the JVM handles the fault and commits the memory.
 * The memory stays committed after the recursion returns. The write of the callback stub then lands
 * in committed memory.
 *
 * ```
 * stack base
 * │   frames of the callers:
 * │     Thread.run
 * │     GhosttyTerminalSession.start
 * │     GhosttyTerminalEmulator.write
 * ├── (1) frame of GhosttyTerminalEmulator.writeToVt                     0 KB
 * │       frames of the recursion
 * ├── (2) deepest frame of the recursion                                64 KB or more
 * │
 * ├── the JVM writes here, 32 KB ahead of (2), and commits the stack    96 KB or more
 * ▼
 * stack limit
 * ```
 *
 * ### The limit
 *
 * An instance commits the stack once for each thread. So each emulator holds one instance for each place
 * that makes such a native call. A later call of the same place from a much deeper frame has no protection.
 * With the `terminal.ghostty.commit.stack.on.each.call` registry key on, each call of [commit] commits the stack.
 *
 * Affects Windows only, does nothing on other operating systems.
 */
internal class GhosttyWindowsCallbackStackCommitter {
  /** Holds a value on each thread whose stack this instance committed. */
  private val committed: ThreadLocal<Unit> = ThreadLocal()

  // The registry has the key only on Windows.
  private val commitOnEachCall: Boolean = isJvmOnWindows && Registry.`is`("terminal.ghostty.commit.stack.on.each.call", false)

  fun commit() {
    if (!isJvmOnWindows) return
    if (!commitOnEachCall) {
      if (committed.get() != null) return
      committed.set(Unit)
    }
    try {
      descend(STACK_COMMIT_CALLS)
    }
    catch (_: StackOverflowError) {
      // Not expected: with `STACK_COMMIT_CALLS=4096` the recursion takes 384 KB at most,
      // and the default stack of a thread is 1 MB.
      logStackOverflow()
    }
  }
}

private fun descend(depth: Int): Int = if (depth == 0) 0 else descend(depth - 1) + 1

/**
 * Logs one warning for a process. The stack is committed, so the crash of IJPL-261768 cannot occur.
 * But the thread is near the end of its stack, and a callback of libghostty-vt can cause a real stack overflow.
 */
private fun logStackOverflow() {
  if (stackOverflowLogged.get()) return
  LOG.warn("The stack of the '${Thread.currentThread().name}' thread has no space for the full stack commit. " +
           "A callback of libghostty-vt can overflow the stack.")
  stackOverflowLogged.set(true)
}

private val LOG: Logger = logger<GhosttyWindowsCallbackStackCommitter>()

private val stackOverflowLogged: AtomicBoolean = AtomicBoolean()

@OptIn(LowLevelLocalMachineAccess::class)
private val isJvmOnWindows: Boolean
  get() = OS.CURRENT == OS.Windows

/**
 * The recursion depth of [GhosttyWindowsCallbackStackCommitter.commit]. A call of the recursion takes 16 bytes
 * in C2 code, 40 bytes in C1 code, and 96 bytes in the interpreter. So the recursion extends the committed part
 * of the stack by 64-384 KB. The first callback of a new stub needs an extension of 20-28 KB.
 */
private const val STACK_COMMIT_CALLS: Int = 4096
