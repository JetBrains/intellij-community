// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.test

import com.intellij.execution.RunManager
import com.intellij.execution.RunManager.Companion.getInstance
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.ModuleBasedConfiguration
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.testFramework.JavaModuleTestCase
import org.jetbrains.kotlin.idea.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.debugger.coroutine.DebuggerConnection
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.jetbrains.kotlin.idea.test.addRoot
import java.nio.file.Path
import kotlin.io.path.Path

abstract class AbstractCoroutineAgentAttachTest : JavaModuleTestCase() {

    abstract val projectName: String

    override fun getProjectDirOrFile(isDirectoryBasedProject: Boolean): Path = Path(TEST_PROJECTS_ROOT_DIR).resolve(projectName)

    // Attach library dependencies to modules manually using bazel labels
    // to prevent loading dependencies from mavenLocal repo because it's prone to corruption.
    abstract fun attachLibraries(module: Module)

    override fun setUpProject() {
        super.setUpProject()
        RunManager.getInstance(project).allConfigurationsList
            .filterIsInstance<ModuleBasedConfiguration<*, *>>()
            .forEach { configuration ->
                configuration.modules.forEach(::attachLibraries)
            }
    }

    private fun attachLibrary(model: ModifiableRootModel, libraryName: String, classes: List<Path>, testOnly: Boolean) {
        ConfigLibraryUtil.addLibrary(model, libraryName) {
            classes.forEach { addRoot(it, OrderRootType.CLASSES) }
        }

        if (testOnly) {
            model.orderEntries.filterIsInstance<LibraryOrderEntry>()
                .filter { it.libraryName == libraryName }.forEach {
                    it.scope = DependencyScope.TEST
                }
        }
    }

    protected fun Module.attachLibrary(libraryName: String, classes: List<Path>, testOnly: Boolean = false) {
        runWriteAction {
            val model = ModuleRootManager.getInstance(this).modifiableModel
            try {
                attachLibrary(model, libraryName, classes, testOnly)
            }
            finally {
                model.commit()
            }
        }
    }

    protected fun Module.attachKotlinStdlib(testOnly: Boolean = false) =
        attachLibrary(
            KOTLIN_LIBRARY_NAME,
            listOf(
                TestKotlinArtifacts.kotlinStdlibJdk8_2_1_21,
                TestKotlinArtifacts.kotlinStdlib_2_1_21,
                TestKotlinArtifacts.annotations13
            ),
            testOnly
        )

    protected fun Module.attachKotlinxCoroutines(testOnly: Boolean = false) =
        attachLibrary(
            KOTLINX_COROUTINES_LIBRARY_NAME,
            listOf(
                TestKotlinArtifacts.kotlinxCoroutinesCoreJvm_1_10_2
            ),
            testOnly
        )


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
        private val KOTLINX_COROUTINES_LIBRARY_NAME = "jetbrains.kotlinx.coroutines.core"
    }
}

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