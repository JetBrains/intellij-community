// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.perf.stats

import org.jetbrains.kotlin.idea.testFramework.Stats
import org.jetbrains.kotlin.idea.perf.synthetic.AbstractPerformanceHighlightingTest

abstract class AbstractPerformanceHighlightingStatNamesTest: AbstractPerformanceHighlightingTest() {
    companion object {
        @JvmStatic
        val stats: Stats = Stats("highlight", profilerConfig = statsProfilerConfig, outputConfig = statsOutputConfig)
    }

    override fun stats() = stats

}