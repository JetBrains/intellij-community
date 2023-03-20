// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.perf.stats

import org.jetbrains.kotlin.idea.perf.profilers.ProfilerConfig
import org.jetbrains.kotlin.idea.perf.synthetic.AbstractPerformanceBasicCompletionHandlerTest
import org.jetbrains.kotlin.idea.perf.util.OutputConfig

abstract class AbstractPerformanceBasicCompletionHandlerStatNamesTest: AbstractPerformanceBasicCompletionHandlerTest() {

    override fun profilerConfig(): ProfilerConfig = statsProfilerConfig

    override fun outputConfig(): OutputConfig = statsOutputConfig
}