// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.core.stepping

import com.intellij.debugger.engine.LightOrRealThreadInfo
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.util.registry.Registry
import com.sun.jdi.ThreadReference
import org.jetbrains.kotlin.idea.debugger.core.StackFrameInterceptor

interface ContinuationFilter

data class CoroutineJobInfo(private val continuationFilter: ContinuationFilter) : LightOrRealThreadInfo {
    override val realThread = null

    override fun checkSameThread(thread: ThreadReference, suspendContext: SuspendContextImpl): Boolean {
        val jobFromContext = getContinuationObject(suspendContext)
        return jobFromContext == continuationFilter
    }

    companion object {
        @JvmStatic
        private fun getContinuationObject(suspendContext: SuspendContextImpl): ContinuationFilter? {
            val stackFrameInterceptor: StackFrameInterceptor = suspendContext.debugProcess.project.serviceOrNull() ?: return null
            return stackFrameInterceptor.extractContinuationFilter(suspendContext)
        }

        @JvmStatic
        fun extractJobInfo(suspendContext: SuspendContextImpl): LightOrRealThreadInfo? {
            if (!Registry.`is`("debugger.filter.breakpoints.by.coroutine.id")) return null
            return getContinuationObject(suspendContext)?.let { CoroutineJobInfo(it) }
        }
    }
}
