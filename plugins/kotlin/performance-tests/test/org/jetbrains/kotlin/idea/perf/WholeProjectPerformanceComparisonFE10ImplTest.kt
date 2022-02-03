// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.perf

import org.jetbrains.kotlin.idea.perf.common.AbstractWholeProjectPerformanceComparisonTest
import org.jetbrains.kotlin.idea.testFramework.Stats
import org.jetbrains.kotlin.idea.testFramework.WarmUpProject

class WholeProjectPerformanceComparisonFE10ImplTest : AbstractWholeProjectPerformanceComparisonTest() {
    override val testPrefix: String = "FE10"
    override fun getWarmUpProject(): WarmUpProject = warmUpProject

    fun testRustPluginHighlighting() = doTestRustPluginHighlighting()
    fun testRustPluginCompletion() = doTestRustPluginCompletion()


    companion object {
        private val hwStats: Stats = Stats("FE10 warmup project")
        private val warmUpProject = WarmUpProject(hwStats)
    }
}