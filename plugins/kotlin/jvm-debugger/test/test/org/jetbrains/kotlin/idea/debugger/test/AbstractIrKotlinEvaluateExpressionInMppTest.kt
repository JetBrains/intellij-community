// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.test

import com.intellij.execution.ExecutionTestCase
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.doWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.containers.MultiMap
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor
import org.jetbrains.kotlin.config.JvmClosureGenerationScheme
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.test.addRoot
import org.jetbrains.kotlin.idea.test.createMultiplatformFacetM3
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
 * Project structure can be set by defining dependencies between modules via parentheses.
 * Transitive refinement dependencies should **NOT** be declared.
 * ```
 * // MODULE: <module_name>(<dependency_module_name1>[, <dependency_module_name2>, ...])
 * ```
 *
 * // PLATFORM: directive should belong to any file inside a module (after MODULE and FILE directives).
 * Multiples of the same directive in any one module in one or several of its files are forbidden.
 *
 * A single leaf JVM module with a name starting with 'jvm' is expected in a test with multiple modules,
 * all other modules are considered common, can have arbitrary name and can be referenced by their name
 * in a dependency list of another module.
 * The resulting dependency graph is not checked for cycles or hanging nodes, so be careful when defining dependencies.
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
 * // MODULE: intermediate(common)
 * // FILE: intermediateFile.kt
 * // PLATFORM: jvm
 * /* optionally code and test directives for debugger */
 *
 * // MODULE: jvm(intermediate)
 * // FILE: leafJvmFile.kt
 * // PLATFORM: jvm
 * /* optionally code and test directives for debugger */
 * ```
 */
abstract class AbstractIrKotlinEvaluateExpressionInMppTest : AbstractIrKotlinEvaluateExpressionTest() {
    private lateinit var context: ConfigurationContext
    private lateinit var perModuleLibraryOutputDirectory: File
    private lateinit var perModuleLibrarySourceDirectory: File

    override fun fragmentCompilerBackend() =
        FragmentCompilerBackend.JVM_IR

    override fun configureProjectByTestFiles(testFiles: List<TestFileWithModule>, testAppDirectory: File) {
        perModuleLibraryOutputDirectory = File(testAppDirectory, "perModuleLibs").apply { mkdirs() }
        perModuleLibrarySourceDirectory = File(testAppDirectory, "perModuleLibsSrc").apply { mkdirs() }

        context = ConfigurationContext(
            filesByModules = testFiles.groupBy(TestFileWithModule::module),
            dependsOnEdges = MultiMap.create(),
            workspaceModuleMap = mutableMapOf(),
            librariesByModule = mutableMapOf()
        )

        check(context.filesByModules.keys.filterIsInstance<DebuggerTestModule.Jvm>().size == 1) {
            "Exactly one leaf JVM module expected"
        }

        for ((module, files) in context.filesByModules) {
            configureModuleByTestFiles(module, files, context)
        }

        for ((module, library) in context.librariesByModule) {
            configureModuleLibraryDependency(library, context.workspaceModuleMap[module]!!)
        }

        setUpExtraModuleDependenciesFromDependsOnEdges(context)
    }

    private fun configureModuleLibraryDependency(library: String, module: Module) {
        if (library.startsWith("maven(")) {
            configureModuleMavenLibraryDependency(library, module)
        } else {
            configureModuleCustomBuiltLibraryDependency(library, module)
        }
    }

    private fun configureModuleCustomBuiltLibraryDependency(library: String, module: Module) {
        val libraryPath = File(perModuleLibraryOutputDirectory, module.name).apply { mkdirs() }
        runInEdtAndWait {
            ConfigLibraryUtil.addLibrary(module, library) {
                addRoot(libraryPath, OrderRootType.CLASSES)
            }
        }
    }

    private fun configureModuleMavenLibraryDependency(library: String, module: Module) {
        val regex = Regex(MAVEN_DEPENDENCY_REGEX)
        val match = regex.matchEntire(library)
        check(match != null) {
            "Cannot parse maven dependency: '$library'"
        }
        val (_, groupId: String, artifactId: String, version: String) = match.groupValues
        val description = JpsMavenRepositoryLibraryDescriptor(groupId, artifactId, version)
        val artifacts = loadDependencies(description)
        runInEdtAndWait {
            ConfigLibraryUtil.addLibrary(module, "ARTIFACTS") {
                for (artifact in artifacts) {
                    addRoot(artifact.file, artifact.type)
                }
            }
        }
    }

    private fun configureModuleByTestFiles(
        module: DebuggerTestModule,
        files: List<TestFileWithModule>,
        context: ConfigurationContext,
    ) {
        val (platformName, dependsOnModuleNames) = handleDirectives(module, files, context)

        when (module) {
            is DebuggerTestModule.Common -> createAndConfigureCommonModule(context, module, platformName, dependsOnModuleNames)
            is DebuggerTestModule.Jvm -> configureLeafJvmModule(context, module, platformName, dependsOnModuleNames)
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
        module: DebuggerTestModule,
        platformName: PlatformName?,
        dependsOnModuleNames: List<ModuleName>,
    ) {
        check(platformName != COMMON_PLATFORM) { "Leaf JVM module cannot have common platform" }

        context.workspaceModuleMap[module] = myModule
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

        val library = findAtMostOneDirectiveInModuleFiles(moduleFiles, ATTACH_LIBRARY_TO_IDE_MODULE.toPrefix())

        if (library != null) {
            context.librariesByModule[module] = library
        }

        val allModuleNames = context.filesByModules.keys.map(DebuggerTestModule::name)
        val dependsOnModuleNames = module.dependenciesSymbols

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

    override fun createDebuggerTestCompilerFacility(
        testFiles: TestFiles,
        jvmTarget: JvmTarget,
        compileConfig: TestCompileConfiguration
    ): DebuggerTestCompilerFacility {
        return MppDebuggerCompilerFacility(project, testFiles, jvmTarget, compileConfig)
    }

    // Compile the ATTACH_LIBRARY_TO_IDE_MODULE libraries into per module output directories
    // so that individual modules can depend on a subset of the libraries.
    override fun compileAdditionalLibraries(compilerFacility: DebuggerTestCompilerFacility) {
        for ((module, library) in context.librariesByModule) {
            val moduleLibOutput = File(perModuleLibraryOutputDirectory, module.name).apply { mkdirs() }
            compilerFacility.compileExternalLibrary(library, perModuleLibrarySourceDirectory, moduleLibOutput)
        }
    }

    private class ConfigurationContext(
        val filesByModules: Map<DebuggerTestModule, List<TestFileWithModule>>,
        val dependsOnEdges: MultiMap<DebuggerTestModule, DebuggerTestModule>,
        val workspaceModuleMap: MutableMap<DebuggerTestModule, Module>,
        val librariesByModule: MutableMap<DebuggerTestModule, String>
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

        private const val PLATFORM_DIRECTIVE = "PLATFORM"
        private const val JVM_PLATFORM = "JVM"
        private const val COMMON_PLATFORM = "COMMON"

        // This directive attaches a library to the IDE module only. It does not add the library
        // to the initial compilation classpath. This allows the creation of modules with
        // dependencies in the project that are independent of the code being compiled and run.
        //
        // The expected format of the directive is either:
        //
        //   ATTACH_LIBRARY_TO_IDE_MODULE: directory
        //
        // where `directory` is a directory under `plugins/kotlin/jvm-debugger/test/testData/lib`
        // that contains the code for the library. The directive itself must appear in the module
        // to which the library should be added as a dependency.
        //
        // Or:
        //
        //  ATTACH_LIBRARY_TO_IDE_MODULE: maven(maven:coordinates:version)
        //
        // with maven coordinates for the library to attach to the IDE module the directive
        // appears in.
        private const val ATTACH_LIBRARY_TO_IDE_MODULE = "ATTACH_LIBRARY_TO_IDE_MODULE"

        private fun String.toPrefix() = "// $this:"
    }
}

private typealias PlatformName = String
private typealias ModuleName = String

abstract class AbstractK1IdeK2CodeKotlinEvaluateExpressionInMppTest : AbstractIrKotlinEvaluateExpressionInMppTest() {
    override val compileWithK2 = true

    override fun lambdasGenerationScheme() = JvmClosureGenerationScheme.INDY
}


private class MppDebuggerCompilerFacility(
    project: Project,
    files: List<TestFileWithModule>,
    jvmTarget: JvmTarget,
    compileConfig: TestCompileConfiguration,
) : DebuggerTestCompilerFacility(project, files, jvmTarget, compileConfig) {

    override fun getCompileOptionsForMainSources(jvmSrcDir: File, commonSrcDir: File): List<String> {
        return super.getCompileOptionsForMainSources(jvmSrcDir, commonSrcDir) +
                getExtraOptionsForMultiplatform(jvmSrcDir, commonSrcDir)
    }

    private fun getExtraOptionsForMultiplatform(jvmSrcDir: File, commonSrcDir: File): List<String> {
        if (mainFiles.kotlinCommon.isEmpty()) {
            return emptyList()
        }

        val sources = mainFiles.kotlinJvm + mainFiles.kotlinCommon
        val modules = sources.map { it.module }
        val fragments = modules.map { it.name }.toSet()
        val fragmentDependencies = modules
            .flatMap { module -> module.dependencies.map { module.name to it.name } }
            .toSet()

        fun getFileForSource(source: TestFileWithModule): File {
            val baseDir = if (source in mainFiles.kotlinJvm) {
                jvmSrcDir
            } else {
                commonSrcDir.resolve(source.module.name)
            }
            return baseDir.resolve(source.name)
        }

        val fragmentSources = sources
            .map { it.module.name to getFileForSource(it).absolutePath }

        return listOf(
            "-Xmulti-platform",
            "-Xfragments=" + fragments.joinToString(",")
        ) + fragmentDependencies.map { (module, dependency) -> "-Xfragment-refines=$module:$dependency" } +
                fragmentSources.map { (module, src) -> "-Xfragment-sources=$module:$src" }
    }
}