// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.perf.suite

import org.jetbrains.kotlin.idea.testFramework.Stats
import org.jetbrains.kotlin.idea.testFramework.performanceTest

open class MeasurementScope<T>(
    val name: String,
    val stats: Stats,
    val config: StatsScopeConfig,
    var before: () -> Unit = {},
    var test: (() -> T?)? = null,
    var after: (() -> Unit)? = null
) {
    open fun run(): List<T?> {
        val t = test ?: error("test procedure isn't set")
        val value = mutableListOf<T?>()
        doPerformanceTest(before, test = { value.add(t.invoke()) }, tearDown = after?: {})
        return value
    }

    protected fun <V> doPerformanceTest(setUp: () -> Unit = {}, test:() -> V?, tearDown: () -> Unit = {}) {
        performanceTest<Unit, T> {
            name(name)
            stats(stats)
            warmUpIterations(config.warmup)
            iterations(config.iterations)
            fastIterations(config.fastIterations)
            stabilityWatermark(config.stabilityWatermark)
            setUp { setUp() }
            test { test() }
            tearDown { tearDown() }
            profilerConfig(config.profilerConfig)
        }
    }
}
