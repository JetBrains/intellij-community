// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.perf.stats

import org.jetbrains.kotlin.idea.perf.profilers.ProfilerConfig
import org.jetbrains.kotlin.idea.perf.synthetic.PerformanceStressTest
import org.jetbrains.kotlin.idea.perf.util.OutputConfig

class PerformanceStressStatNamesTest: PerformanceStressTest() {

    override fun profileConfig(): ProfilerConfig = statsProfilerConfig

    override fun outputConfig(): OutputConfig = statsOutputConfig
}