// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.coroutine.proxy

import com.intellij.debugger.engine.DebuggerManagerThreadImpl
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.impl.DebuggerUtilsImpl.logError
import com.intellij.rt.debugger.coroutines.CoroutinesDebugHelper
import com.sun.jdi.ArrayReference
import com.sun.jdi.StringReference
import org.jetbrains.kotlin.idea.debugger.base.util.evaluate.DefaultExecutionContext
import org.jetbrains.kotlin.idea.debugger.coroutine.callMethodFromHelper
import org.jetbrains.kotlin.idea.debugger.coroutine.data.CoroutineInfoCache
import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.mirror.DebugProbesImpl
import org.jetbrains.kotlin.idea.debugger.coroutine.util.executionContext
import org.jetbrains.kotlin.idea.debugger.coroutine.data.CoroutineInfoData
import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.mirror.CoroutineInfo

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
            val coroutineInfos =
                CoroutinesInfoFromJsonAndReferencesProvider(executionContext).dumpCoroutinesInfo()
                    ?: CoroutineLibraryAgent2Proxy.instance(executionContext)?.dumpCoroutinesInfo()
                    ?: emptyList()
            coroutineInfoCache.ok(coroutineInfos)
        } catch (e: Throwable) {
            logError("Exception is thrown by calling dumpCoroutines.", e)
            coroutineInfoCache.fail()
        }
        return coroutineInfoCache
    }

    /**
     * This method aims to reduce the overhead of obtaining the whole parent hierarchy for every DebugCoroutineInfo.
     * It is invoked only when the coroutines hierarchy is requested in the Coroutines View.
     *
     * The Helper method getJobAndParentForCoroutines returns the array of Strings with size = infos.size * 2 and
     * array[2 * i] = info.job
     * array[2 * i + 1] = info.parent
     *
     * NOTE: only coroutines which are captured in the coroutine dump will be shown in the Coroutine View,
     * jobs which do not correspond to any captured coroutine will not be shown (e.g., jobs of completing coroutines or scope coroutines).
     *
     * The corresponding properties [CoroutineInfoData.job] and [CoroutineInfoData.parentJob] are set to the obtained values.
     */
    internal fun fetchAndSetJobsAndParentsForCoroutines(infos: List<CoroutineInfoData>): Boolean {
        val executionContext = suspendContext.executionContext() ?: return false
        val debugCoroutineInfos = infos.map { it.debugCoroutineInfoRef }
        val array = callMethodFromHelper(CoroutinesDebugHelper::class.java, executionContext, "getJobsAndParentsForCoroutines", debugCoroutineInfos)
        val jobsWithParents = (array as? ArrayReference)?.values?.map { (it as? StringReference)?.value() }
            ?: fallBackToMirrorFetchJobsAndParentsForCoroutines(executionContext, infos)
        if (jobsWithParents.isEmpty()) return false
        for (i in 0 until jobsWithParents.size step 2) {
            infos[i / 2].job = jobsWithParents[i]
            infos[i / 2].parentJob = jobsWithParents[i + 1]
        }
        return true
    }

    private fun fallBackToMirrorFetchJobsAndParentsForCoroutines(
        executionContext: DefaultExecutionContext,
        infos: List<CoroutineInfoData>
    ): List<String?> {
        val debugProbesImpl = DebugProbesImpl.instance(executionContext) ?: return emptyList()
        if (!debugProbesImpl.isInstalled) return emptyList()
        val debugCoroutineInfoImpl = CoroutineInfo.instance(executionContext) ?: return emptyList()
        val jobsWithParents = arrayOfNulls<String>(infos.size * 2)
        for (i in 0 until jobsWithParents.size step 2) {
            val info = infos[i / 2]
            val debugCoroutineInfoMirror = debugCoroutineInfoImpl.mirror(info.debugCoroutineInfoRef, executionContext)
            val job = debugCoroutineInfoMirror?.context?.job
            jobsWithParents[i] = job?.details
            jobsWithParents[i + 1] = job?.parent?.getJob()?.details
        }
        return jobsWithParents.toList()
    }
}
