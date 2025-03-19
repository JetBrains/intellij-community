// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.test

import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.execution.process.ProcessOutputTypes
import com.jetbrains.jdi.VirtualMachineImpl
import org.jetbrains.kotlin.config.JvmClosureGenerationScheme
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import java.util.concurrent.atomic.AtomicInteger

abstract class AbstractKotlinSteppingPacketsNumberTest : AbstractIrKotlinSteppingTest() {
    private val packets = AtomicInteger()
    private val methods = AtomicInteger()
    private var firstAccessSkipped = false
    override fun setUp() {
        super.setUp()
        setUpPacketsMeasureTest()
    }

    override fun extraPrintContext(context: SuspendContextImpl) {
        val totalPacketsNumber = (context.virtualMachineProxy.virtualMachine as VirtualMachineImpl).waitPacketsNumber
        val totalMethodsNumber = context.debugProcess.methodInvocationsCount
        val previousPacketsNumber = packets.getAndSet(totalPacketsNumber)
        val previousMethodsNumber = methods.getAndSet(totalMethodsNumber)
        if (!firstAccessSkipped) {
            firstAccessSkipped = true
            return
        }
        val stepPacketNumber = totalPacketsNumber - previousPacketsNumber
        val stepMethodNumber = totalMethodsNumber - previousMethodsNumber
        println("Packets sent: $stepPacketNumber, methods called: $stepMethodNumber", ProcessOutputTypes.SYSTEM)
    }
}

abstract class AbstractK1IdeK2CodeKotlinSteppingPacketsNumberTest : AbstractKotlinSteppingPacketsNumberTest() {
    override fun lambdasGenerationScheme() = JvmClosureGenerationScheme.INDY
    override val compileWithK2 = true
    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K1
}
