// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.test

import org.jetbrains.kotlin.idea.debugger.coroutine.data.CoroutineInfoData
import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.CoroutineDebugProbesProxy
import org.jetbrains.kotlin.idea.debugger.test.preference.DebuggerPreferences

abstract class AbstractCoroutineViewJobHierarchyTest : KotlinDescriptorTestCaseWithStackFrames() {
    override fun doMultiFileTest(files: TestFiles, preferences: DebuggerPreferences) {
        doOnBreakpoint {
            val suspendContext = this
            try {
                val coroutineDebugProxy = CoroutineDebugProbesProxy(suspendContext)
                val coroutineCache = coroutineDebugProxy.dumpCoroutines()
                if (coroutineCache.isOk()) {
                    val cache = coroutineCache.cache
                    val isHierarchyBuilt = coroutineDebugProxy.fetchAndSetJobNamesAndJobUniqueIds(cache)
                    if (isHierarchyBuilt) {
                        printCoroutinesJobHierarchy(cache)
                    } else {
                        printCoroutineInfos(cache)
                    }
                }
            } finally {
              resume(suspendContext)
            }
        }
    }

    private fun printCoroutineInfos(infos: List<CoroutineInfoData>) {
        for (info in infos) {
            out(0, info.name + " " + info.state)
        }
    }

    private fun printCoroutinesJobHierarchy(coroutines: List<CoroutineInfoData>) {
        val rootCoroutines = coroutines.filter { it.parentJobId == null }
        val parentIdToChildCoroutines = coroutines.filter { it.parentJobId != null }.groupBy { it.parentJobId!! }

        out(0, "==== Hierarchy of coroutines =====")
        for (rootCoroutine in rootCoroutines) {
            printInfo(rootCoroutine, parentIdToChildCoroutines, 0)
        }
    }

    private fun printInfo(info: CoroutineInfoData, parentIdToChildCoroutines: Map<Long, List<CoroutineInfoData>>, indent: Int) {
        out(indent, info.name)
        val children = parentIdToChildCoroutines[info.jobId] ?: emptyList()
        for (child in children) {
            printInfo(child, parentIdToChildCoroutines, indent + 1)
        }
    }
}


