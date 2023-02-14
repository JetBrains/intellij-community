// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.testFramework

import org.jetbrains.kotlin.idea.perf.profilers.ProfilerConfig

class PerfTestBuilder<SV, TV> {
    private lateinit var stats: Stats
    private lateinit var name: String
    private var warmUpIterations: Int = 5
    private var iterations: Int = 20
    private var fastIterations: Boolean = false
    private var setUp: (TestData<SV, TV>) -> Unit = { }
    private lateinit var test: (TestData<SV, TV>) -> Unit
    private var tearDown: (TestData<SV, TV>) -> Unit = { }
    private var stabilityWatermark: Int? = 20
    internal var profilerConfig: ProfilerConfig = ProfilerConfig()
    private var stopAtException: Boolean = false

    internal fun run() {
        perfTest(
            stats = stats,
            testName = name,
            warmUpIterations = warmUpIterations,
            iterations = iterations,
            fastIterations = fastIterations,
            setUp = setUp,
            test = test,
            tearDown = tearDown,
            stabilityWatermark = stabilityWatermark,
            stopAtException = stopAtException,
        )
    }

    fun stats(stats: Stats) {
        this.stats = stats
    }

    fun name(name: String) {
        this.name = name
    }

    fun warmUpIterations(warmUpIterations: Int) {
        this.warmUpIterations = warmUpIterations
    }

    fun iterations(iterations: Int) {
        this.iterations = iterations
    }

    fun fastIterations(fastIterations: Boolean) {
        this.fastIterations = fastIterations
    }

    fun setUp(setUp: (TestData<SV, TV>) -> Unit) {
        this.setUp = setUp
    }

    fun test(test: (TestData<SV, TV>) -> Unit) {
        this.test = test
    }

    fun tearDown(tearDown: (TestData<SV, TV>) -> Unit) {
        this.tearDown = tearDown
    }

    fun profilerConfig(profilerConfig: ProfilerConfig) {
        this.profilerConfig = profilerConfig
    }

    fun stabilityWatermark(stabilityWatermark: Int?) {
        this.stabilityWatermark = stabilityWatermark
    }

    fun stopAtException(stopAtException: Boolean) {
        this.stopAtException = stopAtException
    }
}

fun <SV, TV> performanceTest(initializer: PerfTestBuilder<SV, TV>.() -> Unit) {
    PerfTestBuilder<SV, TV>().apply(initializer).run()
}
