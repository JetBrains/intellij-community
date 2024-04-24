// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.core.stepping

import com.intellij.debugger.engine.LightOrRealThreadInfo
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.registry.Registry
import com.sun.jdi.ThreadReference
import org.jetbrains.kotlin.idea.debugger.core.StackFrameInterceptor

interface CoroutineFilter {
    fun canRunTo(nextCoroutineFilter: CoroutineFilter): Boolean
}

data class CoroutineJobInfo(private val coroutineFilter: CoroutineFilter) : LightOrRealThreadInfo {
    override val realThread = null

    override fun checkSameThread(thread: ThreadReference, suspendContext: SuspendContextImpl): Boolean {
        val nextCoroutineFilter = getCoroutineFilter(suspendContext)
        return nextCoroutineFilter != null && coroutineFilter.canRunTo(nextCoroutineFilter)
    }

    companion object {
        @JvmStatic
        private fun getCoroutineFilter(suspendContext: SuspendContextImpl): CoroutineFilter? {
            return StackFrameInterceptor.instance?.extractCoroutineFilter(suspendContext)
        }

        @JvmStatic
        fun extractJobInfo(suspendContext: SuspendContextImpl): LightOrRealThreadInfo? {
            if (!Registry.`is`("debugger.filter.breakpoints.by.coroutine.id")) return null
            try {
                return getCoroutineFilter(suspendContext)?.let { CoroutineJobInfo(it) }
            } catch (e: Throwable) {
                Logger.getInstance(CoroutineJobInfo::class.java).error(e)
                return null
            }
        }
    }
}
