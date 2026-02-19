// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.test

import com.intellij.openapi.module.Module

class CoroutineAgentAttachImlProjectTest : AbstractCoroutineAgentAttachTest() {

    override val projectName = "attachCoroutineAgentTest_iml"

    override fun attachLibraries(module: Module) {
        when(module.name) {
            "module1" -> {
                module.attachKotlinStdlib()
                module.attachKotlinxCoroutines()
            }
            "module2" -> {
                module.attachKotlinStdlib()
            }
            "java_module_with_coroutines_in_test" -> {
                module.attachKotlinxCoroutines(true)
            }
        }
    }

    fun testDebugKotlinModuleWithCoroutineDependency() {
        connectDebuggerAndCheckVmParams(
            findRunConfiguration("AKt"),
            coroutineAgentShouldBeAttached = true
        )
    }

    fun testDebugKotlinModuleWithCoroutineTransitiveDependency() {
        connectDebuggerAndCheckVmParams(
            findRunConfiguration("BKt"),
            coroutineAgentShouldBeAttached = true
        )
    }

    fun testDebugRootModuleKotlinWithNoCoroutineDependency() {
        connectDebuggerAndCheckVmParams(
            findRunConfiguration("MainKt"),
            coroutineAgentShouldBeAttached = false
        )
    }

    // In this case, when the Java module transitively dependends to coroutines,
    // it's ok to attach the agent, because all the Kotlin classes will be loaded and this problem will not occur (IDEA-374658).
    fun testDebugJavaModuleWithKotlinModuleDependency() {
        connectDebuggerAndCheckVmParams(
            findRunConfiguration("JavaMain"),
            coroutineAgentShouldBeAttached = true
        )
    }

    // Coroutine debug agent should not be applied to the non-test run configuration,
    // if the corotuine dependency is test-only
    fun testDebugJavaModuleWithCoroutinesDependencyInTests() {
        connectDebuggerAndCheckVmParams(
            findRunConfiguration("JavaMainWithCoroutinesInTests"),
            coroutineAgentShouldBeAttached = false
        )
    }
}