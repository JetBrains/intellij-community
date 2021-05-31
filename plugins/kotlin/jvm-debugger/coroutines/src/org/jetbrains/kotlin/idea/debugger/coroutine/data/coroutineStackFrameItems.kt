// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.coroutine.data

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.JavaValue
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.debugger.memory.utils.StackFrameItem
import com.intellij.xdebugger.frame.XStackFrame
import com.sun.jdi.Location
import org.jetbrains.kotlin.idea.debugger.*
import org.jetbrains.kotlin.idea.debugger.coroutine.util.findPosition
import org.jetbrains.kotlin.idea.debugger.coroutine.util.logger

/**
 * Creation frame of coroutine either in RUNNING or SUSPENDED state.
 */
class CreationCoroutineStackFrameItem(
    val stackTraceElement: StackTraceElement,
    location: Location,
    val first: Boolean
) : CoroutineStackFrameItem(location, emptyList()) {

    override fun createFrame(debugProcess: DebugProcessImpl): XStackFrame? {
        return debugProcess.invokeInManagerThread {
            val frame = debugProcess.findFirstFrame() ?: return@invokeInManagerThread null
            val position = location.findPosition(debugProcess.project)
            CreationCoroutineStackFrame(frame, position, first, location)
        }
    }
}

/**
 * Restored frame in SUSPENDED coroutine, not attached to any thread.
 */
class SuspendCoroutineStackFrameItem(
    val stackTraceElement: StackTraceElement,
    location: Location,
    spilledVariables: List<JavaValue> = emptyList()
) : CoroutineStackFrameItem(location, spilledVariables) {
    override fun createFrame(debugProcess: DebugProcessImpl): XStackFrame? {
        return debugProcess.invokeInManagerThread {
            val frame = debugProcess.findFirstFrame() ?: return@invokeInManagerThread null
            val position = location.findPosition(debugProcess.project)
            CoroutineStackFrame(frame, position, spilledVariables, includeFrameVariables = false, location)
        }
    }
}


/**
 * Restored from memory dump
 */
class DefaultCoroutineStackFrameItem(location: Location, spilledVariables: List<JavaValue>) :
    CoroutineStackFrameItem(location, spilledVariables) {

    override fun createFrame(debugProcess: DebugProcessImpl): XStackFrame? {
        return debugProcess.invokeInManagerThread {
            val frame = debugProcess.findFirstFrame() ?: return@invokeInManagerThread null
            val position = location.findPosition(debugProcess.project)
            CoroutineStackFrame(frame, position, spilledVariables, false, location)
        }
    }
}

/**
 * Original frame appeared before resumeWith call.
 *
 * Sequence is the following
 *
 * - KotlinStackFrame
 * - invokeSuspend(KotlinStackFrame) -|
 *                                    | replaced with CoroutinePreflightStackFrame
 * - resumeWith(KotlinStackFrame) ----|
 * - Kotlin/JavaStackFrame -> PreCoroutineStackFrameItem : CoroutinePreflightStackFrame.threadPreCoroutineFrames
 *
 */
open class RunningCoroutineStackFrameItem(
    val frame: StackFrameProxyImpl,
    location: Location,
    spilledVariables: List<JavaValue> = emptyList()
) : CoroutineStackFrameItem(location, spilledVariables) {
    override fun createFrame(debugProcess: DebugProcessImpl): XStackFrame? {
        return debugProcess.invokeInManagerThread {
            val position = location.findPosition(debugProcess.project)
            CoroutineStackFrame(frame, position)
        }
    }
}

sealed class CoroutineStackFrameItem(val location: Location, val spilledVariables: List<JavaValue>) :
    StackFrameItem(location, spilledVariables) {
    val log by logger

    override fun createFrame(debugProcess: DebugProcessImpl): XStackFrame? {
        return debugProcess.invokeInManagerThread {
            val frame = debugProcess.findFirstFrame() ?: return@invokeInManagerThread null
            val position = location.findPosition(debugProcess.project)
            CoroutineStackFrame(frame, position, spilledVariables, false, location)
        }
    }

    fun uniqueId() =
        location.safeSourceName() + ":" + location.safeMethod().toString() + ":" +
                location.safeLineNumber() + ":" + location.safeKotlinPreferredLineNumber()
}

fun DebugProcessImpl.findFirstFrame(): StackFrameProxyImpl? =
    suspendManager.pausedContext.thread?.forceFrames()?.firstOrNull()
