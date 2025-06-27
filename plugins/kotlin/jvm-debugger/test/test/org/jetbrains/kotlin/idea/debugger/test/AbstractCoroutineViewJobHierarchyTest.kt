// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.test

import com.intellij.debugger.engine.executeOnDMT
import com.intellij.debugger.impl.PrioritizedTask
import org.jetbrains.kotlin.config.JvmClosureGenerationScheme
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
                    val isHierarchyBuilt = coroutineDebugProxy.fetchAndSetJobsAndParentsForCoroutines(cache)
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

    private fun printCoroutinesJobHierarchy(infos: List<CoroutineInfoData>) {
        val parentJobToChildCoroutineInfos = infos.groupBy { it.parentJob }
        val jobToCoroutineInfo = infos.associateBy { it.job }
        val rootJobs = infos.filter { it.parentJob == null }.mapNotNull { it.job }

        out(0, "==== Hierarchy of coroutines =====")
        for (root in rootJobs) {
            val rootCoroutineInfo = jobToCoroutineInfo[root]
            if (rootCoroutineInfo == null) {
                out(0, root)
            } else {
                printInfo(rootCoroutineInfo, parentJobToChildCoroutineInfos, 0)
            }
        }
    }

    private fun printInfo(info: CoroutineInfoData, jobToChildCoroutineInfos: Map<String?, List<CoroutineInfoData>>, indent: Int) {
        out(indent, info.name)
        val children = jobToChildCoroutineInfos[info.job] ?: emptyList()
        for (child in children) {
            printInfo(child, jobToChildCoroutineInfos, indent + 1)
        }
    }
}


abstract class AbstractK1IdeK2CoroutineViewTest : AbstractCoroutineViewJobHierarchyTest() {
    override val compileWithK2 = true

    override fun lambdasGenerationScheme() = JvmClosureGenerationScheme.INDY
}