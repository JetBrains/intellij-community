// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.perf

import org.jetbrains.kotlin.idea.perf.profilers.ProfilerConfig

data class PerfTest<SV, TV>(
    val name: String,
    val warmUpIterations: Int,
    val iterations: Int,
    val fastIterations: Boolean,
    val checkStability: Boolean,
    val stopAtException: Boolean,
    val stats: Stats,
    val setUp: (TestData<SV, TV>) -> Unit,
    val test: (TestData<SV, TV>) -> Unit,
    val tearDown: (TestData<SV, TV>) -> Unit,
    val profilerConfig: ProfilerConfig,
)

abstract class PerfTestBuilderBase {
    protected var name: String? = null
    protected var warmUpIterations: Int? = null
    protected var iterations: Int? = null
    protected var fastIterations: Boolean? = null
    protected var checkStability: Boolean? = null
    protected var stopAtException: Boolean? = null

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


    fun checkStability(checkStability: Boolean) {
        this.checkStability = checkStability
    }

    fun stopAtException(stopAtException: Boolean) {
        this.stopAtException = stopAtException
    }
}

class PerfTestOverrideBuilder : PerfTestBuilderBase() {
    fun build(): PerfTestSettingsOverride =
        PerfTestSettingsOverride(
            name,
            warmUpIterations,
            iterations,
            fastIterations,
            checkStability,
            stopAtException
        )
}

fun settingsOverride(init: PerfTestOverrideBuilder.() -> Unit): PerfTestSettingsOverride =
    PerfTestOverrideBuilder().apply(init).build()

class PerfTestSettingsOverride(
    val name: String?,
    val warmUpIterations: Int?,
    val iterations: Int?,
    val fastIterations: Boolean?,
    val checkStability: Boolean?,
    val stopAtException: Boolean?,
) {
    fun <SV, TV> applyToPerfTest(perfTest: PerfTest<SV, TV>): PerfTest<SV, TV> {
        return perfTest.copy(
            name = name ?: perfTest.name,
            warmUpIterations = warmUpIterations ?: perfTest.warmUpIterations,
            iterations = iterations ?: perfTest.iterations,
            fastIterations = fastIterations ?: perfTest.fastIterations,
            checkStability = checkStability ?: perfTest.checkStability,
            stopAtException = stopAtException ?: perfTest.stopAtException,
        )
    }
}


class PerfTestBuilder<SV, TV> : PerfTestBuilderBase() {
    private lateinit var stats: Stats
    private var setUp: (TestData<SV, TV>) -> Unit = { }
    private lateinit var test: (TestData<SV, TV>) -> Unit
    private var tearDown: (TestData<SV, TV>) -> Unit = { }
    internal var profilerConfig: ProfilerConfig = ProfilerConfig()

    init {
        warmUpIterations = 5
        iterations = 20
        fastIterations = false
        checkStability = true
        stopAtException = false
    }

    fun stats(stats: Stats) {
        this.stats = stats
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

    fun build(): PerfTest<SV, TV> =
        PerfTest(
            name = name!!,
            warmUpIterations = warmUpIterations!!,
            iterations = iterations!!,
            fastIterations = fastIterations!!,
            checkStability = checkStability!!,
            stopAtException = stopAtException!!,
            stats = stats,
            setUp = setUp,
            test = test,
            tearDown = tearDown,
            profilerConfig = profilerConfig
        )
}

fun <SV, TV> performanceTest(
    overrides: List<PerfTestSettingsOverride> = emptyList(),
    initializer: PerfTestBuilder<SV, TV>.() -> Unit,
) {
    val perfTest = PerfTestBuilder<SV, TV>().apply(initializer).build().applyOverrides(overrides.toList())
    perfTest.stats.perfTest(perfTest.name, perfTest)
}

private fun <SV, TV> PerfTest<SV, TV>.applyOverrides(overrides: List<PerfTestSettingsOverride>): PerfTest<SV, TV> =
    overrides.fold(this) { test, override -> override.applyToPerfTest(test) }
