// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.debugger.coroutine.proxy

import com.intellij.debugger.engine.DebuggerManagerThreadImpl
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.kotlin.idea.debugger.coroutine.CoroutinesInfoFromJsonAndReferencesProvider
import org.jetbrains.kotlin.idea.debugger.coroutine.data.CoroutineInfoCache
import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.mirror.DebugProbesImpl
import org.jetbrains.kotlin.idea.debugger.coroutine.util.executionContext
import org.jetbrains.kotlin.idea.debugger.coroutine.util.logger
import org.jetbrains.kotlin.idea.debugger.base.util.evaluate.DefaultExecutionContext

class CoroutineDebugProbesProxy(val suspendContext: SuspendContextImpl) {
    /**
     * Invokes DebugProbes from debugged process's classpath and returns states of coroutines
     * Should be invoked on debugger manager thread
     */
    @Synchronized
    fun dumpCoroutines(): CoroutineInfoCache {
        DebuggerManagerThreadImpl.assertIsManagerThread()
        val coroutineInfoCache = CoroutineInfoCache()
        try {
            val executionContext = suspendContext.executionContext() ?: return coroutineInfoCache.fail()
            val libraryAgentProxy = findProvider(executionContext) ?: return coroutineInfoCache.ok()
            val infoList = libraryAgentProxy.dumpCoroutinesInfo()
            coroutineInfoCache.ok(infoList)
        } catch (e: Throwable) {
            log.error("Exception is thrown by calling dumpCoroutines.", e)
            coroutineInfoCache.fail()
        }
        return coroutineInfoCache
    }

    private fun findProvider(executionContext: DefaultExecutionContext): CoroutineInfoProvider? {
        val debugProbesImpl = DebugProbesImpl.instance(executionContext)
        return if (debugProbesImpl != null && debugProbesImpl.isInstalled) {
            CoroutinesInfoFromJsonAndReferencesProvider.instance(executionContext, debugProbesImpl) ?:
            CoroutineLibraryAgent2Proxy(executionContext, debugProbesImpl)
        } else null
    }

    companion object {
        private val log by logger
    }
}
