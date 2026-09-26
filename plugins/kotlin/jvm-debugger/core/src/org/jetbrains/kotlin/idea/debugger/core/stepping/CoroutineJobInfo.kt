// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.core.stepping

import com.intellij.debugger.engine.LightOrRealThreadInfo
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.impl.DebuggerUtilsImpl
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.registry.Registry
import com.sun.jdi.ThreadReference
import org.jetbrains.kotlin.idea.debugger.core.StackFrameInterceptor

interface CoroutineFilter {
    fun canRunTo(nextCoroutineFilter: CoroutineFilter): Boolean
    val coroutineFilterName: String
}

data class CoroutineJobInfo(private val coroutineFilter: CoroutineFilter) : LightOrRealThreadInfo {
    override val realThread = null

    override fun checkSameThread(thread: ThreadReference, suspendContext: SuspendContextImpl): Boolean {
        val nextCoroutineFilter = getCoroutineFilter(suspendContext)
        thisLogger().debug("Check thread filter: need $coroutineFilter, current is $nextCoroutineFilter")
        return nextCoroutineFilter != null && coroutineFilter.canRunTo(nextCoroutineFilter)
    }

    override val filterName get() = coroutineFilter.coroutineFilterName

    companion object {
        @JvmStatic
        private fun getCoroutineFilter(suspendContext: SuspendContextImpl): CoroutineFilter? {
            suspendContext.lightThreadFilter?.let {
                return it as CoroutineFilter
            }
            val result = StackFrameInterceptor.instance?.extractCoroutineFilter(suspendContext)
            suspendContext.lightThreadFilter = result
            return result
        }

        @JvmStatic
        fun extractJobInfo(suspendContext: SuspendContextImpl): LightOrRealThreadInfo? {
            if (!Registry.`is`("debugger.filter.breakpoints.by.coroutine.id")) return null
            try {
                return getCoroutineFilter(suspendContext)?.let { CoroutineJobInfo(it) }
            } catch (e: Throwable) {
                DebuggerUtilsImpl.logError(e)
                return null
            }
        }
    }
}
