// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.test

import com.intellij.execution.RunManager.Companion.getInstance
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.testFramework.JavaModuleTestCase
import org.jetbrains.kotlin.idea.debugger.coroutine.DebuggerConnection
import java.nio.file.Path
import kotlin.io.path.Path

abstract class AbstractCoroutineAgentAttachTest : JavaModuleTestCase() {

    abstract val projectName: String

    override fun getProjectDirOrFile(isDirectoryBasedProject: Boolean): Path = Path(TEST_PROJECTS_ROOT_DIR).resolve(projectName)

    fun connectDebuggerAndCheckVmParams(runConfig: RunConfigurationBase<*>, coroutineAgentShouldBeAttached: Boolean) {
        val params = JavaParameters()
        DebuggerConnection(
            project = project,
            configuration = runConfig,
            params = params,
            shouldAttachCoroutineAgent = true,
            alwaysShowPanel = true
        )
        // Check that -javaagent parameter was actually added
        val vmParameters = params.vmParametersList.parameters
        val hasCoroutineDebugAgent = vmParameters.any { it.startsWith("-javaagent:") && it.contains("kotlinx-coroutines-core") }
        check(hasCoroutineDebugAgent == coroutineAgentShouldBeAttached) {
            if (coroutineAgentShouldBeAttached) {
                "Coroutine debug agent was expected to be attached, but javaagent parameter was not found in VM parameters."
            } else {
                "Coroutine debug agent should not have been attached, but javaagent parameter was found in VM parameters: $vmParameters."
            }
        }
    }

    fun findRunConfiguration(mainClassName: String): RunConfigurationBase<*> =
        getInstance(project).allConfigurationsList.find { it.name == mainClassName } as? RunConfigurationBase<*>
            ?: error("Could not find run configuration with main class name: $mainClassName")

    companion object {
        private val TEST_PROJECTS_ROOT_DIR = "$DEBUGGER_TESTDATA_PATH_BASE/projects/"
    }
}

class CoroutineAgentAttachImlProjectTest : AbstractCoroutineAgentAttachTest() {

    override val projectName = "attachCoroutineAgentTest_iml"

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
}