// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.coroutine.proxy

import org.jetbrains.kotlin.idea.debugger.coroutine.data.CoroutineDescriptor
import org.jetbrains.kotlin.idea.debugger.coroutine.data.CoroutineInfoData
import org.jetbrains.kotlin.idea.debugger.coroutine.data.CoroutineStackTraceProvider
import org.jetbrains.kotlin.idea.debugger.coroutine.data.LazyCoroutineInfoData
import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.mirror.DebugProbesImpl
import org.jetbrains.kotlin.idea.debugger.coroutine.util.logger
import org.jetbrains.kotlin.idea.debugger.evaluate.DefaultExecutionContext

class CoroutineLibraryAgent2Proxy(private val executionContext: DefaultExecutionContext) : CoroutineInfoProvider {
    private val debugProbesImpl = DebugProbesImpl.instance(executionContext)
    private val stackTraceProvider = CoroutineStackTraceProvider(executionContext)

    override fun dumpCoroutinesInfo(): List<CoroutineInfoData> {
        val result = debugProbesImpl?.dumpCoroutinesInfo(executionContext) ?: emptyList()
        return result.map {
            LazyCoroutineInfoData(
                CoroutineDescriptor.instance(it),
                it,
                stackTraceProvider
            )
        }
    }

    fun isInstalled(): Boolean {
        return try {
            debugProbesImpl?.isInstalled ?: false
        } catch (e: Exception) {
            log.error("Exception happened while checking agent status.", e)
            false
        }
    }

    companion object {
        fun instance(executionContext: DefaultExecutionContext): CoroutineLibraryAgent2Proxy? {
            val agentProxy = CoroutineLibraryAgent2Proxy(executionContext)
            return if (agentProxy.isInstalled())
                agentProxy
            else
                null
        }

        val log by logger
    }
}
