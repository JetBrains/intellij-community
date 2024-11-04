// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.test.projectStructureTest

import com.google.gson.JsonObject
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.utils.io.createDirectory
import com.intellij.util.io.jarFile
import com.intellij.util.io.write
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.test.*
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.tooling.core.closure
import org.jetbrains.kotlin.tooling.core.withClosure
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

typealias ProjectLibrariesByName = Map<String, Library>
typealias ModulesByName = Map<String, Module>
typealias ModuleContentRoots = Map<Module, List<VirtualFile>>

/**
 * A multi-module test which builds the project structure from a test data file `structure.json`.
 *
 * Library and module sources are discovered automatically. If a test data directory with the same name as a library root or module exists,
 * it will be used as a source directory for the library root/module.
 *
 * Each source module file may contain an optional `<caret>`. Its position will be memorized by the test (see [getCaretPosition]), and it
 * will be removed from the file for test execution.
 */
abstract class AbstractProjectStructureTest<S : TestProjectStructure>(
    private val testProjectStructureParser: TestProjectStructureParser<S>,
) : AbstractMultiModuleTest() {
    private val caretProvider = CaretProvider()

    private lateinit var _testProjectStructure: S

    protected val testProjectStructure: S get() = _testProjectStructure

    private lateinit var _projectLibrariesByName: ProjectLibrariesByName

    protected val projectLibrariesByName: ProjectLibrariesByName get() = _projectLibrariesByName

    private lateinit var _modulesByName: ModulesByName

    protected val modulesByName: ModulesByName get() = _modulesByName

    private lateinit var _moduleContentRoots: ModuleContentRoots

    protected val moduleContentRoots: ModuleContentRoots get() = _moduleContentRoots

    /**
     * Executes the test with a parsed and initialized [testProjectStructure], [projectLibrariesByName], and [modulesByName].
     */
    protected abstract fun doTestWithProjectStructure(testDirectory: String)

    protected fun doTest(testDirectory: String) {
        val jsonFile = Paths.get(testDirectory).resolve("structure.json")
        val json = TestProjectStructureReader.readJsonFile(jsonFile)
        val isDisabled = json.getAsJsonPrimitive(TestProjectStructureFields.IS_DISABLED_FIELD)?.asBoolean == true

        IgnoreTests.runTestIfEnabled(isEnabled = !isDisabled, jsonFile) {
            initializeProjectStructure(testDirectory, json, testProjectStructureParser)
            doTestWithProjectStructure(testDirectory)
        }
    }

    private fun initializeProjectStructure(
        testDirectory: String,
        json: JsonObject,
        parser: TestProjectStructureParser<S>,
    ) {
        val testStructure = TestProjectStructureReader.parseTestStructure(json, parser)

        val libraryRootsByLabel = testStructure.libraries
            .flatMapTo(mutableSetOf()) { it.roots }
            .associate { it to createLibraryRoot(it, testDirectory) }

        val projectLibrariesByName = testStructure.libraries.associate { libraryData ->
            libraryData.name to ConfigLibraryUtil.addProjectLibrary(project, libraryData.name) {
                libraryData.roots.forEach { rootLabel ->
                    val libraryRoot = libraryRootsByLabel.getValue(rootLabel)
                    addRoot(libraryRoot.classRoot, OrderRootType.CLASSES)
                    libraryRoot.sourceRoot?.let { addRoot(it, OrderRootType.SOURCES) }
                }
                commit()
            }
        }

        val modulesByName = mutableMapOf<String, Module>()
        val moduleContentRoots = mutableMapOf<Module, List<VirtualFile>>()

        testStructure.modules.forEach { testModule ->
            val contentRootVirtualFiles = mutableListOf<VirtualFile>()
            val module = createModuleWithSources(testModule, testDirectory, contentRootVirtualFiles)

            modulesByName[testModule.name] = module
            moduleContentRoots[module] = contentRootVirtualFiles
        }

        val duplicateNames = projectLibrariesByName.keys.intersect(modulesByName.keys)
        if (duplicateNames.isNotEmpty()) {
            error("Test project libraries and modules may not share names. Duplicate names: ${duplicateNames.joinToString()}.")
        }

        val refinementMap: Map<String, List<String>> = testStructure.modules.associate { testModule ->
            testModule.name to testModule.dependencies.filter { it.kind == DependencyKind.REFINEMENT }.map { it.name }
        }

        testStructure.modules.forEach { moduleData ->
            val module = modulesByName.getValue(moduleData.name)
            val dependenciesByKind = moduleData.dependencies.groupBy { it.kind }

            dependenciesByKind[DependencyKind.REGULAR].orEmpty().let { regularDependencies ->
                addRegularDependencies(module, regularDependencies, modulesByName, projectLibrariesByName)
            }
            val directFriendDependencies = dependenciesByKind[DependencyKind.FRIEND].orEmpty().map(Dependency::name)
            setUpSpecialDependenciesAndPlatform(module, moduleData.targetPlatform, modulesByName, refinementMap, directFriendDependencies)
        }

        _testProjectStructure = testStructure
        _projectLibrariesByName = projectLibrariesByName
        _modulesByName = modulesByName
        _moduleContentRoots = moduleContentRoots
    }

    private class LibraryRoot(val classRoot: VirtualFile, val sourceRoot: VirtualFile?)

    private fun createLibraryRoot(rootLabel: String, testDirectory: String): LibraryRoot {
        // If the root label is also a directory in the test case's test data, we should compile the JAR from those sources.
        val librarySources = Path(testDirectory, rootLabel).toFile()

        val jarFile = if (librarySources.isDirectory) {
            KotlinCompilerStandalone(
                listOf(librarySources),
                target = this.createTempFile("$rootLabel.jar", null),
            ).compile()
        } else {
            jarFile { }.generateInTempDir().toFile()
        }

        // We need to convert the JAR virtual file into a JAR file system root virtual file. Generally, in IntelliJ, the root of a library
        // is not the JAR *file* (`file:///.../abc.jar`) but rather the JAR *file system root* (`jar:///.../abc.jar!/`).
        val jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(getVirtualFile(jarFile))!!

        return LibraryRoot(jarRoot, librarySources.takeIf { it.isDirectory }?.let { getVirtualFile(it) })
    }

    private fun createModuleWithSources(
        testModule: TestProjectModule,
        testDirectory: String,
        contentRootVirtualFiles: MutableList<VirtualFile>,
    ): Module {
        val tmpPath = createTempDirectory().toPath()
        val module: Module = createModule("$tmpPath/${testModule.name}", moduleType)
        val rootPath = tmpPath.createDirectory(testModule.name)

        copyModuleContent(testModule, testDirectory, rootPath)

        val root = getVirtualFile(rootPath.toFile())
        WriteCommandAction.writeCommandAction(module.project).run<RuntimeException> {
            root.refresh(false, true)
        }

        val contentRoots =
            testModule.contentRoots.takeIf { it.isNotEmpty() }
                ?: listOf(TestContentRoot(null, TestContentRootKind.PRODUCTION))

        contentRoots.forEach { testContentRoot ->
            val contentRootPath = testContentRoot.path?.let { rootPath.resolve(it) } ?: rootPath

            // Create an empty directory to allow tests to easily specify content roots without associated test data files.
            if (!contentRootPath.exists()) {
                contentRootPath.createDirectory()
            }

            require(contentRootPath.isDirectory()) {
                "Expected the content root directory `$contentRootPath` to be a directory."
            }

            val contentRoot = getVirtualFile(contentRootPath.toFile())
            when (testContentRoot.kind) {
                TestContentRootKind.PRODUCTION -> PsiTestUtil.addSourceRoot(module, contentRoot)
                TestContentRootKind.TESTS -> PsiTestUtil.addSourceRoot(module, contentRoot, true)
            }

            contentRootVirtualFiles.add(contentRoot)
        }

        return module
    }

    /**
     * If the [testModule] is also a directory in the test case's test data, we should include these sources.
     */
    private fun copyModuleContent(testModule: TestProjectModule, testDirectory: String, destinationSrcDir: Path) {
        val moduleRootPath = Path(testDirectory, testModule.name)
        val moduleRoot = moduleRootPath.toFile()
        if (moduleRoot.isDirectory) {
            moduleRoot.walk().forEach { file ->
                if (file.isDirectory) return@forEach

                val relativePath = moduleRootPath.relativize(file.toPath())
                val destinationPath = destinationSrcDir.resolve(relativePath)

                val processedFileText = caretProvider.processFile(file, destinationPath)
                destinationPath.write(processedFileText)
            }
        }
    }

    private fun addRegularDependencies(
        module: Module,
        dependencies: List<Dependency>,
        modulesByName: Map<String, Module>,
        librariesByName: Map<String, Library>,
    ) {
        dependencies.forEach { dependency ->
            check(dependency.kind == DependencyKind.REGULAR)
            librariesByName[dependency.name]
                ?.let { library -> module.addDependency(library, exported = dependency.isExported) }
                ?: module.addDependency(modulesByName.getValue(dependency.name), exported = dependency.isExported)
        }
    }

    /**
     * Setting refinement and friend dependencies includes two parts:
     * - creating normal module dependencies (IJ workspace model);
     * - writing information in the Kotlin Facet Settings.
     *
     * Friend dependencies allow everything the regular dependencies allow + internal declarations become visible.
     * Refinement dependencies allow everything the regular dependencies allow + internals + expect-actual matching.
     */
    private fun setUpSpecialDependenciesAndPlatform(
        module: Module,
        platform: TargetPlatform,
        modulesByName: Map<String, Module>,
        refinementMap: Map<String, List<String>>,
        directFriendDependencies: List<String>,
    ) {
        val refinementDependencyClosure = module.name.closure(refinementMap::getValue)

        refinementDependencyClosure.forEach { refinementDependencyModuleName ->
            module.addDependency(modulesByName.getValue(refinementDependencyModuleName))
        }

        // KGP provides friend modules as a flat list: a production source set + its dependsOn closure.
        // E.g., friends of a jvmTest source set will be jvmMain and commonMain.
        // To not write them all explicitly in test data, we calculate them here from direct friend modules.
        val friendModulesClosure = directFriendDependencies.flatMap { moduleName ->
            moduleName.withClosure(refinementMap::getValue)
        }.toSet()

        friendModulesClosure.forEach { friendModuleName ->
            module.addDependency(modulesByName.getValue(friendModuleName))
        }

        module.createMultiplatformFacetM3(
            platformKind = platform,
            useProjectSettings = true,
            dependsOnModuleNames = refinementDependencyClosure.toList(),
            pureKotlinSourceFolders = emptyList(),
            additionalVisibleModuleNames = friendModulesClosure,
        )
    }

    protected fun getCaretPosition(ktFile: KtFile): Int = getCaretPositionOrNull(ktFile) ?: error("Expected `<caret>` in file: $ktFile")

    protected fun getCaretPositionOrNull(ktFile: KtFile): Int? = caretProvider.getCaretPosition(ktFile.virtualFile)
}

private const val CARET_TEXT = "<caret>"

private class CaretProvider {
    private val caretPositionByFilePath = mutableMapOf<String, Int>()

    /**
     * Extracts a caret position from [file] and returns the file text without the `<caret>` marker.
     */
    fun processFile(file: File, destinationPath: Path): String {
        val fileText = file.readText()

        val caretPosition = fileText.indexOf(CARET_TEXT)
        if (caretPosition < 0) return fileText

        caretPositionByFilePath[destinationPath.toString()] = caretPosition

        return fileText.removeRange(caretPosition ..< caretPosition + CARET_TEXT.length).also { processedText ->
            if (processedText.contains(CARET_TEXT)) {
                error("The following file contains more than one `$CARET_TEXT`: $file")
            }
        }
    }

    fun getCaretPosition(virtualFile: VirtualFile): Int? {
        // `AbstractProjectStructureTest` only needs to support the local file system, so the virtual file's path should be equal to the
        // processed file's path.
        return caretPositionByFilePath[virtualFile.path]
    }
}
