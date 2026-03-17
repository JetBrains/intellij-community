// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.test

import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import com.intellij.tools.ide.metrics.collector.MetricsCollector
import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics
import com.jetbrains.jdi.VirtualMachineImpl
import org.jetbrains.kotlin.idea.debugger.test.preference.DebuggerPreferenceKeys
import org.jetbrains.kotlin.idea.debugger.test.preference.DebuggerPreferences
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

abstract class AbstractKotlinSteppingPacketsNumberTest : AbstractIrKotlinSteppingTest() {
    private var isFrameTest = false
    private val allPackets = mutableListOf<Int>()
    private val allMethods = mutableListOf<Int>()
    private val packets = AtomicInteger()
    private val methods = AtomicInteger()
    private var first = true

    override fun setUp() {
        super.setUp()
        setUpPacketsMeasureTest()
    }

    override fun doMultiFileTest(files: TestFiles, preferences: DebuggerPreferences) {
        isFrameTest = preferences[DebuggerPreferenceKeys.PRINT_FRAME]
        super.doMultiFileTest(files, preferences)
    }

    override fun doOnBreakpoint(action: SuspendContextImpl.() -> Unit) {
        val wrapperAction: SuspendContextImpl.() -> Unit = {
            collectMetrics(this)
            action()
        }
        super.doOnBreakpoint {
            if (isFrameTest) {
                printFrame(this, wrapperAction)
            } else {
                wrapperAction()
            }
        }
    }

    protected fun runBenchmark(test: () -> Unit) {
        val meterCollector: MetricsCollector = object : MetricsCollector {
            override fun collect(logsDirPath: Path): List<PerformanceMetrics.Metric> {
                return listOf(
                    PerformanceMetrics.newCounter("packets.avg", allPackets.average().roundToInt()),
                    PerformanceMetrics.newCounter("packets.max", allPackets.max()),
                    PerformanceMetrics.newCounter("methods.avg", allMethods.average().roundToInt()),
                    PerformanceMetrics.newCounter("methods.max", allMethods.max()),
                )
            }
        }

        test()

        Benchmark.newBenchmark("benchmark") {
            runProcessAndAwaitCompleted()
        }.warmupIterations(0).attempts(1).withMetricsCollector(meterCollector).runAsStressTest().start()
    }

    private fun collectMetrics(context: SuspendContextImpl) {
        val totalPacketsNumber = (context.virtualMachineProxy.virtualMachine as VirtualMachineImpl).waitPacketsNumber
        val totalMethodsNumber = context.debugProcess.methodInvocationsCount
        val previousPacketsNumber = packets.getAndSet(totalPacketsNumber)
        val previousMethodsNumber = methods.getAndSet(totalMethodsNumber)
        if (first) {
            first = false
            return
        }

        allPackets += totalPacketsNumber - previousPacketsNumber
        allMethods += totalMethodsNumber - previousMethodsNumber
    }
}
