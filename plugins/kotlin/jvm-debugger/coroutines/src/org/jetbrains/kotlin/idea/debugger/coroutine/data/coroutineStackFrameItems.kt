// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.coroutine.data

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.JavaValue
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.debugger.memory.utils.StackFrameItem
import com.intellij.xdebugger.frame.XStackFrame
import com.sun.jdi.Location
import org.jetbrains.kotlin.idea.debugger.base.util.safeKotlinPreferredLineNumber
import org.jetbrains.kotlin.idea.debugger.base.util.safeLineNumber
import org.jetbrains.kotlin.idea.debugger.base.util.safeMethod
import org.jetbrains.kotlin.idea.debugger.base.util.safeSourceName
import org.jetbrains.kotlin.idea.debugger.core.invokeInManagerThread
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
            val position = location.findPosition(debugProcess)
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
            val position = location.findPosition(debugProcess)
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
            val position = location.findPosition(debugProcess)
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
            val position = location.findPosition(debugProcess)
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
            val position = location.findPosition(debugProcess)
            CoroutineStackFrame(frame, position, spilledVariables, false, location)
        }
    }

    fun uniqueId() =
        location.safeSourceName() + ":" + location.safeMethod().toString() + ":" +
                location.safeLineNumber() + ":" + location.safeKotlinPreferredLineNumber()
}

fun DebugProcessImpl.findFirstFrame(): StackFrameProxyImpl? =
    suspendManager.pausedContext.thread?.forceFrames()?.firstOrNull()
