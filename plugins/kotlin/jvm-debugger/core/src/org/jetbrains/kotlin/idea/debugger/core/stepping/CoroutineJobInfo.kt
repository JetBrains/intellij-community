// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.core.stepping

import com.intellij.debugger.engine.LightOrRealThreadInfo
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.openapi.util.registry.Registry
import com.sun.jdi.ThreadReference
import org.jetbrains.kotlin.idea.debugger.core.StackFrameInterceptor

interface ContinuationFilter {
    fun canRunTo(nextContinuationFilter: ContinuationFilter): Boolean
}

data class CoroutineJobInfo(private val continuationFilter: ContinuationFilter) : LightOrRealThreadInfo {
    override val realThread = null

    override fun checkSameThread(thread: ThreadReference, suspendContext: SuspendContextImpl): Boolean {
        val nextContinuationFilter = getContinuationObject(suspendContext)
        return nextContinuationFilter != null && continuationFilter.canRunTo(nextContinuationFilter)
    }

    companion object {
        @JvmStatic
        private fun getContinuationObject(suspendContext: SuspendContextImpl): ContinuationFilter? {
            return StackFrameInterceptor.instance?.extractContinuationFilter(suspendContext)
        }

        @JvmStatic
        fun extractJobInfo(suspendContext: SuspendContextImpl): LightOrRealThreadInfo? {
            if (!Registry.`is`("debugger.filter.breakpoints.by.coroutine.id")) return null
            return getContinuationObject(suspendContext)?.let { CoroutineJobInfo(it) }
        }
    }
}
