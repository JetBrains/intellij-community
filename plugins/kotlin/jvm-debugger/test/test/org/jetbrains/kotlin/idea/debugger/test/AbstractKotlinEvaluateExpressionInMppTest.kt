// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.test

import com.intellij.execution.ExecutionTestCase
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.doWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.idea.stubs.createMultiplatformFacetM3
import org.jetbrains.kotlin.idea.test.InTextDirectivesUtils
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.js.JsPlatforms
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.platform.konan.NativePlatforms
import org.jetbrains.kotlin.types.typeUtil.closure
import java.io.File

/**
 * This test allows to imitate a project with hierarchical multiplatform structure for the JVM debugger.
 *
 * Since non-JVM debuggers are not covered by the test, only one of two platforms can be declared for a module:
 * ```
 * // PLATFORM: jvm
 * or
 * // PLATFORM: common
 * ```
 *
 * Platform of the leaf JVM module ('jvm') is JVM; for all other modules default platform is COMMON, but can be changed to JVM as well.
 *
 * Project structure can be set by defining dependencies between modules via DEPENDS_ON.
 * Transitive refinement dependencies should **NOT** be declared.
 * ```
 * // DEPENDS_ON: <module_name>[, <module_name>, ...]
 * ```
 *
 * Both directives should belong to any file inside a module (after MODULE and FILE directives).
 * Multiples of the same directive in any one module in one or several of its files are forbidden.
 *
 * A single leaf JVM module named 'jvm' is expected in a test with multiple modules, all other modules are considered common,
 * can have arbitrary name and can be referenced by their name in a DEPENDS_ON list of another module.
 * The resulting DEPENDS_ON graph is not checked for cycles or hanging nodes, so be careful when defining dependencies.
 *
 * A small clarification on common modules with JVM platform.
 * In hierarchical multiplatform projects a platform of a common module is determined by all leaf platform modules depending on it.
 * Therefore, if only a JVM module depends on a common module, the latter one will have the JVM platform too.
 *
 * In this test non-JVM modules are not described (as they are not related to the JVM debugger), but it is implicitly considered,
 * that common modules with 'COMMON' platform have other non-JVM modules depending on them. Since they have no
 * other impact apart from changing common module's platform, files of these modules are irrelevant and not described in test data.
 *
 * Sample test data for a project with three modules consequently depending on one another.
 * Root common module has non-jvm descendants not mentioned in the test, intermediate module only has a jvm descendant.
 * ```
 * // MODULE: common
 * // FILE: commonFile.kt
 * // PLATFORM: common
 * /* optionally code and test directives for debugger */
 *
 * // MODULE: intermediate
 * // FILE: intermediateFile.kt
 * // PLATFORM: jvm
 * // DEPENDS_ON: common
 * /* optionally code and test directives for debugger */
 *
 * // MODULE: jvm
 * // FILE: leafJvmFile.kt
 * // PLATFORM: jvm
 * // DEPENDS_ON: intermediate
 * /* optionally code and test directives for debugger */
 * ```
 */
abstract class AbstractKotlinEvaluateExpressionInMppTest : AbstractKotlinEvaluateExpressionTest() {
    override fun useIrBackend() = true

    override fun fragmentCompilerBackend() =
        FragmentCompilerBackend.JVM_IR

    override fun configureProjectByTestFiles(testFiles: List<TestFileWithModule>) {
        val context = ConfigurationContext(
            filesByModules = testFiles.groupBy(TestFileWithModule::module),
            dependsOnEdges = MultiMap.create(),
            workspaceModuleMap = mutableMapOf(),
        )

        check(context.filesByModules.keys.filterIsInstance<DebuggerTestModule.Jvm>().size == 1) {
            "Exactly one leaf JVM module expected"
        }

        for ((module, files) in context.filesByModules) {
            configureModuleByTestFiles(module, files, context)
        }

        setUpExtraModuleDependenciesFromDependsOnEdges(context)
    }

    private fun configureModuleByTestFiles(
        module: DebuggerTestModule,
        files: List<TestFileWithModule>,
        context: ConfigurationContext,
    ) {
        val (platformName, dependsOnModuleNames) = handleDirectives(module, files, context)

        when (module) {
            is DebuggerTestModule.Common -> createAndConfigureCommonModule(context, module, platformName, dependsOnModuleNames)
            is DebuggerTestModule.Jvm -> configureLeafJvmModule(context, platformName, dependsOnModuleNames)
        }
    }

    private fun createAndConfigureCommonModule(
        context: ConfigurationContext,
        module: DebuggerTestModule.Common,
        platformName: PlatformName?,
        dependsOnModuleNames: List<ModuleName>,
    ) {
        val newWorkspaceModule = createModule(module.name).also { context.workspaceModuleMap[module] = it }
        val commonModuleSrcPath = listOf(testAppPath, COMMON_SOURCES_DIR, module.name).joinToString(File.separator)
        val commonModuleSrcDir = File(commonModuleSrcPath).also { it.mkdirs() }.refreshAndToVirtualFile()
            ?: error("Can't find virtual file $commonModuleSrcPath for module ${module.name}")

        val targetPlatform = if (platformName == JVM_PLATFORM) JvmPlatforms.jvm8 else COMMON_MODULE_TARGET_PLATFORM

        doWriteAction {
            PsiTestUtil.addSourceRoot(newWorkspaceModule, commonModuleSrcDir)
            newWorkspaceModule.createMultiplatformFacetM3(
                targetPlatform,
                true,
                dependsOnModuleNames,
                listOf(commonModuleSrcPath)
            )
        }
    }

    private fun configureLeafJvmModule(
        context: ConfigurationContext,
        platformName: PlatformName?,
        dependsOnModuleNames: List<ModuleName>,
    ) {
        check(platformName != COMMON_PLATFORM) { "Leaf JVM module cannot have common platform" }

        context.workspaceModuleMap[DebuggerTestModule.Jvm] = myModule
        val jvmSrcPath = listOf(testAppPath, ExecutionTestCase.SOURCES_DIRECTORY_NAME).joinToString(File.separator)
        doWriteAction {
            myModule.createMultiplatformFacetM3(JvmPlatforms.jvm8, true, dependsOnModuleNames, listOf(jvmSrcPath))
        }
    }

    private fun handleDirectives(
        module: DebuggerTestModule,
        moduleFiles: List<TestFileWithModule>,
        context: ConfigurationContext
    ): Pair<PlatformName?, List<ModuleName>> {
        check(moduleFiles.all { file -> file.module == module })

        val platform = findAtMostOneDirectiveInModuleFiles(moduleFiles, PLATFORM_DIRECTIVE.toPrefix())?.trim()?.uppercase()?.also {
            check(it == JVM_PLATFORM || it == COMMON_PLATFORM) {
                "Unknown platform notation in test directive: $it. Only $JVM_PLATFORM and $COMMON_PLATFORM are allowed"
            }
        }

        val allModuleNames = context.filesByModules.keys.map(DebuggerTestModule::name)
        val dependsOnModuleNames = findAtMostOneDirectiveInModuleFiles(moduleFiles, DEPENDS_ON_DIRECTIVE.toPrefix())
            ?.split(", ")?.map(String::trim).orEmpty()

        dependsOnModuleNames.forEach { name ->
            val dependsOnModule = context.filesByModules.keys.find { it.name == name } ?:
                error("Unknown module in depends on list. Known modules: $allModuleNames; found: $name for module ${module.name}")
            context.dependsOnEdges.putValue(module, dependsOnModule)
        }

        return platform to dependsOnModuleNames
    }

    private fun findAtMostOneDirectiveInModuleFiles(files: List<TestFileWithModule>, directivePrefix: String): String? {
        val dependsOnDirectivesForModule = files.flatMap { testFile ->
            InTextDirectivesUtils.findLinesWithPrefixesRemoved(testFile.content, directivePrefix)
        }

        assert(dependsOnDirectivesForModule.size <= 1) { "At most one $directivePrefix expected per module" }

        return dependsOnDirectivesForModule.singleOrNull()
    }

    private fun setUpExtraModuleDependenciesFromDependsOnEdges(context: ConfigurationContext) {
        for ((module, directDependsOnModules) in context.dependsOnEdges.entrySet()) {
            directDependsOnModules.closure { context.dependsOnEdges[it] }.forEach { transitiveDependsOnDependency ->
                doWriteAction {
                    ModuleRootModificationUtil.addDependency(
                        context.workspaceModuleMap[module]!!,
                        context.workspaceModuleMap[transitiveDependsOnDependency]!!,
                        DependencyScope.COMPILE,
                        false
                    )
                }
            }
        }
    }

    private class ConfigurationContext(
        val filesByModules: Map<DebuggerTestModule, List<TestFileWithModule>>,
        val dependsOnEdges: MultiMap<DebuggerTestModule, DebuggerTestModule>,
        val workspaceModuleMap: MutableMap<DebuggerTestModule, Module>
    )

    companion object {
        private val COMMON_MODULE_TARGET_PLATFORM =
            TargetPlatform(
                setOf(
                    JvmPlatforms.jvm8.single(),
                    JsPlatforms.defaultJsPlatform.single(),
                    NativePlatforms.unspecifiedNativePlatform.single()
                )
            )

        private const val DEPENDS_ON_DIRECTIVE = "DEPENDS_ON"
        private const val PLATFORM_DIRECTIVE = "PLATFORM"
        private const val JVM_PLATFORM = "JVM"
        private const val COMMON_PLATFORM = "COMMON"

        private fun String.toPrefix() = "// $this:"
    }
}

private typealias PlatformName = String
private typealias ModuleName = String

