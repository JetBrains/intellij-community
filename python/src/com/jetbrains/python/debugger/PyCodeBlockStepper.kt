// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.XSourcePosition
import org.jetbrains.annotations.ApiStatus
import java.util.function.Consumer

/**
 * The state of a step out of a code block, shared by the pydevd and the DAP debug processes. [T] is the thread id of the backend.
 *
 * The step runs to the statement after the block when the block can end only at its end, and steps over otherwise.
 * The backend sends the commands, and gives each stop to [onStop].
 */
@ApiStatus.Internal
class PyCodeBlockStepper<T : Any> {
  /** The command that starts the step. */
  sealed interface Command {
    /** The paused line is directly in a function body. */
    data object StepOut : Command

    /** A run to [exit] leaves the block much faster than a step for each line of the block. */
    class RunTo(val exit: XSourcePosition) : Command

    /** The block can end elsewhere than at its end, so the steps follow it line by line. */
    data object StepOver : Command
  }

  /** The top frame of a stop. [line] is 0-based. [isStepStop] is false for a stop on a breakpoint, an exception or a pause. */
  class Stop(val file: VirtualFile?, val line: Int, val stackDepth: Int, val isStepStop: Boolean)

  private class Stepping<T>(val thread: T, val block: PyCodeBlockStepping)

  @Volatile
  private var stepping: Stepping<T>? = null

  @Volatile
  private var runTarget: XSourcePosition? = null

  /** True when a stop can matter to the step, so the backend must read the stop for [onStop]. */
  val isActive: Boolean
    get() = stepping != null || runTarget != null

  /**
   * Starts the step out of [block] on [thread], and returns the command to send.
   * A null [block] is a step out of the function.
   * The state is set before the command goes out, because the stop of the command can arrive at once.
   */
  fun start(thread: T, block: PyCodeBlockStepping?): Command {
    val exit = block?.exitLine?.let { XDebuggerUtil.getInstance().createPosition(block.file, it) }
    stepping = if (block != null && exit == null) Stepping(thread, block) else null
    runTarget = exit
    return when {
      block == null -> Command.StepOut
      exit != null -> Command.RunTo(exit)
      else -> Command.StepOver
    }
  }

  /** Ends the steps, because a command of the user replaces them. */
  fun cancel() {
    stepping = null
  }

  /**
   * Handles a stop of [thread], and returns true when the backend must step over again and hide the stop from the session.
   * A stop elsewhere than the run target leaves the temporary breakpoint of the run behind, and [removeRunTarget] gets it.
   */
  fun onStop(thread: T, stop: Stop, removeRunTarget: Consumer<XSourcePosition>): Boolean {
    runTarget?.let { target ->
      runTarget = null
      if (stop.file != target.file || stop.line != target.line) removeRunTarget.accept(target)
    }
    val current = stepping ?: return false
    if (current.thread != thread) return false
    stepping = null
    if (!stop.isStepStop || !current.block.keepsStepping(stop.file, stop.line, stop.stackDepth)) return false
    stepping = current
    return true
  }
}
