// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.coroutine.proxy

import com.intellij.debugger.engine.DebuggerManagerThreadImpl
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.impl.DebuggerUtilsImpl.logError
import org.jetbrains.kotlin.idea.debugger.base.util.evaluate.DefaultExecutionContext
import org.jetbrains.kotlin.idea.debugger.coroutine.data.CoroutineInfoCache
import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.mirror.DebugProbesImpl
import org.jetbrains.kotlin.idea.debugger.coroutine.util.executionContext

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
            logError("Exception is thrown by calling dumpCoroutines.", e)
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
}
