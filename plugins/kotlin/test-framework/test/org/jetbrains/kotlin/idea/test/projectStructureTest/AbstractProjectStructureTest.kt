// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.test.projectStructureTest

import com.google.gson.JsonObject
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.utils.io.createDirectory
import com.intellij.util.io.jarFile
import com.intellij.util.io.write
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.test.AbstractMultiModuleTest
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.jetbrains.kotlin.idea.test.KotlinCompilerStandalone
import org.jetbrains.kotlin.idea.test.addDependency
import org.jetbrains.kotlin.idea.test.addRoot
import org.jetbrains.kotlin.idea.test.createMultiplatformFacetM3
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.tooling.core.closure
import org.jetbrains.kotlin.tooling.core.withClosure
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.Path
import kotlin.io.path.div

typealias ProjectLibrariesByName = Map<String, Library>
typealias ModulesByName = Map<String, Module>

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

        val modulesByName = testStructure.modules.associate { moduleData ->
            moduleData.name to createModuleWithSources(moduleData, testDirectory)
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
    }

    private class LibraryRoot(val classRoot: File, val sourceRoot: File?)

    private fun createLibraryRoot(rootLabel: String, testDirectory: String): LibraryRoot {
        // If the root label is also a directory in the test case's test data, we should compile the JAR from those sources.
        val librarySources = Path(testDirectory, rootLabel).toFile()

        return if (librarySources.isDirectory) {
            val jarFile = KotlinCompilerStandalone(
                listOf(librarySources),
                target = this.createTempFile("$rootLabel.jar", null),
            ).compile()

            LibraryRoot(jarFile, librarySources)
        } else {
            LibraryRoot(jarFile { }.generateInTempDir().toFile(), null)
        }
    }

    private fun createModuleWithSources(testModule: TestProjectModule, testDirectory: String): Module {
        val tmpDir = createTempDirectory().toPath()
        val module = createModule("$tmpDir/${testModule.name}", moduleType)

        (testModule.sourceSets ?: listOf(null)).forEach { sourceSet ->
            val contentRoot = tmpDir.createDirectory(sourceSet?.directory ?: "src")
            val existingSourcesPath = run {
                val base = Paths.get(testDirectory) / module.name
                if (sourceSet == null) base else base / sourceSet.directory
            }
            processModuleSources(existingSourcesPath , contentRoot)

            val srcRoot = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(contentRoot.toFile())!!
            WriteCommandAction.writeCommandAction(module.project).run<RuntimeException> {
                srcRoot.refresh(false, true)
            }

            PsiTestUtil.addSourceContentToRoots(module, srcRoot, /* testSource = */ sourceSet == SourceSet.TEST)
        }
        return module
    }

    /**
     * If the [existingSourcesPath] is also a directory in the test case's test data, we should include these sources.
     */
    private fun processModuleSources(existingSourcesPath: Path, destinationSrcDir: Path) {
        val existingSources = existingSourcesPath.toFile()
        if (existingSources.isDirectory) {
            existingSources.walk().forEach { file ->
                if (file.isDirectory) return@forEach

                val relativePath = existingSourcesPath.relativize(file.toPath())
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
                ?.let { library -> module.addDependency(library) }
                ?: module.addDependency(modulesByName.getValue(dependency.name))
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
