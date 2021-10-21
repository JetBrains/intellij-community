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
    val reportCompilerCounters: Boolean,
    val stats: Stats,
    val setUp: (TestData<SV, TV>) -> Unit,
    val test: (TestData<SV, TV>) -> Unit,
    val tearDown: (TestData<SV, TV>) -> Unit,
    val profilerConfig: ProfilerConfig,
    val afterTestCheck: (TestData<SV, TV>) -> TestCheckResult
)

abstract class PerfTestBuilderBase<SV, TV> {
    protected var name: String? = null
    protected var warmUpIterations: Int? = null
    protected var iterations: Int? = null
    protected var fastIterations: Boolean? = null
    protected var checkStability: Boolean? = null
    protected var stopAtException: Boolean? = null
    protected var reportCompilerCounters: Boolean? = null
    protected var afterTestCheck: ((TestData<SV, TV>) -> TestCheckResult)? = null

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

    fun reportCompilerCounters(reportCompilerCounters: Boolean) {
        this.reportCompilerCounters = reportCompilerCounters
    }

    fun afterTestCheck(check: (TestData<SV, TV>) -> TestCheckResult) {
        this.afterTestCheck = check
    }
}

class PerfTestOverrideBuilder<SV, TV> : PerfTestBuilderBase<SV, TV>() {
    fun build(): PerfTestSettingsOverride<SV, TV> =
        PerfTestSettingsOverride(
            name = name,
            warmUpIterations = warmUpIterations,
            iterations = iterations,
            fastIterations = fastIterations,
            checkStability = checkStability,
            stopAtException =stopAtException ,
            reportCompilerCounters = reportCompilerCounters,
            afterTestCheck = afterTestCheck,
        )
}

fun <SV, TV> settingsOverride(init: PerfTestOverrideBuilder<SV, TV>.() -> Unit): PerfTestSettingsOverride<SV, TV> =
    PerfTestOverrideBuilder<SV, TV>().apply(init).build()

class PerfTestSettingsOverride<SV, TV>(
    val name: String?,
    val warmUpIterations: Int?,
    val iterations: Int?,
    val fastIterations: Boolean?,
    val checkStability: Boolean?,
    val reportCompilerCounters: Boolean?,
    val stopAtException: Boolean?,
    val afterTestCheck: ((TestData<SV, TV>) -> TestCheckResult)?
) {
    fun applyToPerfTest(perfTest: PerfTest<SV, TV>): PerfTest<SV, TV> {
        return perfTest.copy(
            name = name ?: perfTest.name,
            warmUpIterations = warmUpIterations ?: perfTest.warmUpIterations,
            iterations = iterations ?: perfTest.iterations,
            fastIterations = fastIterations ?: perfTest.fastIterations,
            checkStability = checkStability ?: perfTest.checkStability,
            reportCompilerCounters = reportCompilerCounters ?: perfTest.reportCompilerCounters,
            stopAtException = stopAtException ?: perfTest.stopAtException,
            afterTestCheck = afterTestCheck?.and(perfTest.afterTestCheck) ?: perfTest.afterTestCheck,
        )
    }
}


class PerfTestBuilder<SV, TV> : PerfTestBuilderBase<SV, TV>() {
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
        reportCompilerCounters = true
        afterTestCheck = { TestCheckResult.Success }
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
            reportCompilerCounters = reportCompilerCounters!!,
            stats = stats,
            setUp = setUp,
            test = test,
            tearDown = tearDown,
            profilerConfig = profilerConfig,
            afterTestCheck = afterTestCheck!!,
        )
}

fun <SV, TV> performanceTest(
    overrides: List<PerfTestSettingsOverride<SV, TV>> = emptyList(),
    initializer: PerfTestBuilder<SV, TV>.() -> Unit,
) {
    val perfTest = PerfTestBuilder<SV, TV>().apply(initializer).build().applyOverrides(overrides.toList())
    perfTest.stats.perfTest(perfTest.name, perfTest)
}

private fun <SV, TV> PerfTest<SV, TV>.applyOverrides(overrides: List<PerfTestSettingsOverride<SV, TV>>): PerfTest<SV, TV> =
    overrides.fold(this) { test, override -> override.applyToPerfTest(test) }
