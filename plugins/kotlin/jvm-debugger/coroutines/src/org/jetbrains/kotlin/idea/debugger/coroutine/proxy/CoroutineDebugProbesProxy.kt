// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.coroutine.proxy

import com.intellij.debugger.engine.DebuggerManagerThreadImpl
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.impl.DebuggerUtilsImpl.logError
import com.intellij.rt.debugger.coroutines.CoroutinesDebugHelper
import com.sun.jdi.ArrayReference
import com.sun.jdi.IntegerValue
import com.sun.jdi.ObjectReference
import com.sun.jdi.StringReference
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.debugger.coroutine.callMethodFromHelper
import org.jetbrains.kotlin.idea.debugger.coroutine.data.CoroutineInfoCache
import org.jetbrains.kotlin.idea.debugger.coroutine.data.CoroutineInfoData
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
            val executionContext = suspendContext.executionContext()
            val coroutineInfos =
                CoroutinesInfoFromJsonAndReferencesProvider(executionContext).dumpCoroutinesWithStacktraces()
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
     * Dumps coroutines via [dumpCoroutines] and then enriches them with Job hierarchy
     * information (job name, job id, and parent job id) via [fetchAndSetJobNamesAndJobUniqueIds].
     *
     * Should be invoked on debugger manager thread.
     *
     * @return a pair of the [CoroutineInfoCache] and a boolean indicating whether
     *   the Job hierarchy was successfully fetched (`false` if the cache is empty
     *   or the hierarchy fetch failed)
     */
    @Synchronized
    fun dumpCoroutinesWithHierarchy(): Pair<CoroutineInfoCache, Boolean> {
        val cache = dumpCoroutines()
        val infos = cache.cache
        if (cache.cache.isEmpty()) return cache to false
        val jobsHierarchyFetched = fetchAndSetJobNamesAndJobUniqueIds(infos)
        return cache to jobsHierarchyFetched
    }

    /**
     * Fetches job hierarchy information and sets
     * [CoroutineInfoData.job], [CoroutineInfoData.jobId], and [CoroutineInfoData.parentJobId]
     * for each coroutine info entry.
     *
     * Invokes [CoroutinesDebugHelper.getCoroutineJobHierarchyInfo] in the debugged JVM,
     * which returns a 3-element array:
     * - `1st element`: job names (String[]) — string representations of each coroutine's Job
     * - `2nd element`: job references (Object[]) — Job object references, used to obtain stable JDI unique IDs
     * - `3rd element`: parent indexes (int[]) — index into the job references array pointing to the parent Job,
     *    or `-1` if the coroutine's job has no parent
     *
     * Returns `true` if hierarchy was successfully fetched and applied.
     */
    @ApiStatus.Internal
    fun fetchAndSetJobNamesAndJobUniqueIds(infos: List<CoroutineInfoData>): Boolean {
        val executionContext = suspendContext.executionContext()
        val debugCoroutineInfos = infos.map { it.debugCoroutineInfoRef }
        val array = callMethodFromHelper(CoroutinesDebugHelper::class.java, executionContext, "getCoroutineJobHierarchyInfo", debugCoroutineInfos)
        if (array == null) return false
        val jobNames = ((array as ArrayReference).values[0] as ArrayReference).values.map { (it as StringReference).value() }
        val jobRefs = (array.values[1] as ArrayReference).values.map { (it as ObjectReference) }
        val parentIndexes = (array.values[2] as ArrayReference).values.map { (it as IntegerValue).value() }
        for ((i, info) in infos.withIndex()) {
            info.job = jobNames[i]
            info.jobId = jobRefs[i].uniqueID()
            val parentIndex = parentIndexes[i]
            if (parentIndex == -1) {
                info.parentJobId = null
            } else {
                info.parentJobId = jobRefs[parentIndex].uniqueID()
            }
        }
        return true
    }
}
