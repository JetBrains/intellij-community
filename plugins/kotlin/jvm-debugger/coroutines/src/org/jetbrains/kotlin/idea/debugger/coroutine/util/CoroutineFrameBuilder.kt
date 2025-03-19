// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.coroutine.util

import com.intellij.debugger.engine.DebuggerManagerThreadImpl
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl
import com.intellij.xdebugger.frame.XNamedValue
import com.sun.jdi.ObjectReference
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.idea.debugger.base.util.evaluate.DefaultExecutionContext
import org.jetbrains.kotlin.idea.debugger.coroutine.data.*
import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.fetchCoroutineStacksInfoData
import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.safeSkipCoroutineStackFrameProxy

class CoroutineFrameBuilder {
    companion object {
        val log by logger
        private const val PRE_FETCH_FRAME_COUNT = 5

        fun build(coroutine: CoroutineInfoData, suspendContext: SuspendContextImpl): CoroutineFrameItemLists? {
            DebuggerManagerThreadImpl.assertIsManagerThread()
            return when {
                coroutine.isRunning -> buildStackFrameForActive(coroutine, suspendContext)
                coroutine.isSuspended -> CoroutineFrameItemLists(coroutine.continuationStackFrames, coroutine.creationStackFrames)
                else -> null
            }
        }

        private fun buildStackFrameForActive(coroutine: CoroutineInfoData, suspendContext: SuspendContextImpl): CoroutineFrameItemLists? {
            val activeThread = coroutine.lastObservedThread ?: return null

            val coroutineStackFrameList = mutableListOf<CoroutineStackFrameItem>()
            val threadReferenceProxyImpl = ThreadReferenceProxyImpl(suspendContext.virtualMachineProxy, activeThread)
            val realFrames = threadReferenceProxyImpl.forceFrames()
            for (runningStackFrameProxy in realFrames) {
                val preflightStackFrame = coroutineExitFrame(runningStackFrameProxy, suspendContext)
                if (preflightStackFrame != null) {
                    buildRealStackFrameItem(preflightStackFrame.stackFrameProxy)?.let {
                        coroutineStackFrameList.add(it)
                    }

                    val coroutineFrameLists = build(preflightStackFrame, suspendContext)
                    coroutineStackFrameList.addAll(coroutineFrameLists.frames)
                    return CoroutineFrameItemLists(coroutineStackFrameList, coroutine.creationStackFrames)
                } else {
                    buildRealStackFrameItem(runningStackFrameProxy)?.let {
                        coroutineStackFrameList.add(it)
                    }
                }
            }
            return CoroutineFrameItemLists(coroutineStackFrameList, coroutine.creationStackFrames)
        }

        /**
         * Used by CoroutineAsyncStackTraceProvider to build XFramesView
         */
        internal fun build(
            preflightFrame: CoroutinePreflightFrame,
            suspendContext: SuspendContextImpl,
            withPreFrames: Boolean = true
        ): CoroutineFrameItemLists {
            val stackFrames = mutableListOf<CoroutineStackFrameItem>()

            val (restoredStackTrace, _) = restoredStackTrace(preflightFrame)
            stackFrames.addAll(restoredStackTrace)

            if (withPreFrames) {
                // @TODO perhaps we need to merge the dropped variables with the frame below...
                stackFrames.addAll(preflightFrame.threadPreCoroutineFrames.mapNotNull(::buildRealStackFrameItem))
            }

            return CoroutineFrameItemLists(stackFrames, preflightFrame.coroutineStacksInfoData.creationStackFrames)
        }

        private fun restoredStackTrace(
            preflightFrame: CoroutinePreflightFrame,
        ): Pair<List<CoroutineStackFrameItem>, List<XNamedValue>> {
            val preflightFrameLocation = preflightFrame.stackFrameProxy.location()
            val coroutineStackFrame = preflightFrame.coroutineStacksInfoData.continuationStackFrames
            val preCoroutineTopFrameLocation = preflightFrame.threadPreCoroutineFrames.firstOrNull()?.location()

            val variablesRemovedFromTopRestoredFrame = mutableListOf<XNamedValue>()
            val stripTopStackTrace = coroutineStackFrame.dropWhile {
                val isFilteredFromTop = it.location.isFilterFromTop(preflightFrameLocation)
                if (isFilteredFromTop) {
                    variablesRemovedFromTopRestoredFrame.addAll(it.spilledVariables)
                }
                isFilteredFromTop
            }
            // @TODO Need to merge variablesRemovedFromTopRestoredFrame into stripTopStackTrace.firstOrNull().spilledVariables
            val variablesRemovedFromBottomRestoredFrame = mutableListOf<XNamedValue>()
            val restoredFrames = when (preCoroutineTopFrameLocation) {
                null -> stripTopStackTrace
                else -> stripTopStackTrace.dropLastWhile {
                    it.location.isFilterFromBottom(preCoroutineTopFrameLocation)
                        .apply { variablesRemovedFromBottomRestoredFrame.addAll(it.spilledVariables) }
                }
            }
            return Pair(restoredFrames, variablesRemovedFromBottomRestoredFrame)
        }

        data class CoroutineFrameItemLists(
            val frames: List<CoroutineStackFrameItem>,
            val creationFrames: List<CreationCoroutineStackFrameItem>
        )

        private fun buildRealStackFrameItem(
            frame: StackFrameProxyImpl
        ): RunningCoroutineStackFrameItem? {
            val location = frame.location() ?: return null
            return if (!location.safeCoroutineExitPointLineNumber())
                RunningCoroutineStackFrameItem(safeSkipCoroutineStackFrameProxy(frame), location)
            else
                null
        }

        /**
         * Used by CoroutineStackFrameInterceptor to check if that frame is 'exit' coroutine frame.
         */
        internal fun coroutineExitFrame(
            frame: StackFrameProxyImpl,
            suspendContext: SuspendContextImpl
        ): CoroutinePreflightFrame? = lookupContinuation(suspendContext, frame)

        // Fast check to get the whole coroutine information only for the first suspend frame
        internal fun isFirstSuspendFrame(frame: StackFrameProxyImpl): Pair<Boolean, Boolean> {
            if (extractContinuation(frame, frame.getSuspendExitMode()) == null) {
                return Pair(false, false)
            }

            return Pair(true, theFollowingFrames(frame).second)
        }

        @ApiStatus.Internal
        @VisibleForTesting
        fun lookupContinuation(suspendContext: SuspendContextImpl, frame: StackFrameProxyImpl): CoroutinePreflightFrame? {
            DebuggerManagerThreadImpl.assertIsManagerThread()
            val mode = frame.getSuspendExitMode()
            if (!mode.isCoroutineFound()) return null

            val continuation = extractContinuation(frame, mode) ?: return null
            if (!threadAndContextSupportsEvaluation(suspendContext, frame)) return null
            val (theFollowingFrames, _) = theFollowingFrames(frame)
            val context = DefaultExecutionContext(suspendContext, frame)
            val coroutineStacksInfo = fetchCoroutineStacksInfoData(context, continuation) ?: return null
            return CoroutinePreflightFrame(
                coroutineStacksInfo,
                frame,
                theFollowingFrames,
                coroutineStacksInfo.topFrameVariables
            )
        }

        private fun getLVTContinuation(frame: StackFrameProxyImpl?) =
            frame?.continuationVariableValue()

        private fun getThisContinuation(frame: StackFrameProxyImpl?): ObjectReference? =
            frame?.thisVariableValue()

        private fun theFollowingFrames(frame: StackFrameProxyImpl): Pair<List<StackFrameProxyImpl>, Boolean> {
            val frames = frame.threadProxy().frames()
            val indexOfCurrentFrame = frames.indexOf(frame)
            if (indexOfCurrentFrame < 0) {
                log.error("Frame isn't found on the thread stack.")
                return Pair(emptyList(), true)
            }
            val indexOfGetCoroutineSuspended = hasGetCoroutineSuspended(frames)
            // @TODO if found - skip this thread stack
            if (indexOfGetCoroutineSuspended < 0 && frames.size > indexOfCurrentFrame + 1) {
                return Pair(
                    frames.asSequence()
                        .drop(indexOfCurrentFrame + 1)
                        .dropWhile { it.getSuspendExitMode() != SuspendExitMode.NONE }
                        .toList(),
                    isFirstSuspendFrame(indexOfCurrentFrame, frames)
                )
            }
            return Pair(emptyList(), true)
        }

        //TODO: there is a difference with org.jetbrains.kotlin.idea.debugger.coroutine.CoroutineStackFrameInterceptor.extractContinuation
        private fun extractContinuation(frame: StackFrameProxyImpl, mode: SuspendExitMode): ObjectReference? {
            return when (mode) {
                SuspendExitMode.SUSPEND_LAMBDA -> getThisContinuation(frame)
                SuspendExitMode.SUSPEND_METHOD_PARAMETER -> getLVTContinuation(frame)
                else -> null
            }
        }

        private fun isFirstSuspendFrame(frameIndex: Int, frames: List<StackFrameProxyImpl>): Boolean {
            var index = frameIndex
            while (index > 0) {
                val frame = frames[--index]
                if (frame.getSuspendExitMode() == SuspendExitMode.NONE) { // found first non-suspend frame
                    return true
                }
                if (extractContinuation(frame, frame.getSuspendExitMode()) != null) { // skip only if the suspend frame is with continuation
                    return false
                }
            }
            return true
        }
    }
}
