// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.test.projectStructureTest

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
import org.jetbrains.kotlin.idea.test.AbstractMultiModuleTest
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.jetbrains.kotlin.idea.test.KotlinCompilerStandalone
import org.jetbrains.kotlin.idea.test.addDependency
import org.jetbrains.kotlin.idea.test.addRoot
import org.jetbrains.kotlin.psi.KtFile
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.Path

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
abstract class AbstractProjectStructureTest<S : TestProjectStructure> : AbstractMultiModuleTest() {
    private val caretProvider = CaretProvider()

    protected fun initializeProjectStructure(
        testDirectory: String,
        parser: TestProjectStructureParser<S>,
    ): Triple<S, ProjectLibrariesByName, ModulesByName> {
        val testStructure = TestProjectStructureReader.readToTestStructure(
            Paths.get(testDirectory),
            testProjectStructureParser = parser,
        )

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
            moduleData.name to createModuleWithSources(moduleData.name, testDirectory)
        }

        val duplicateNames = projectLibrariesByName.keys.intersect(modulesByName.keys)
        if (duplicateNames.isNotEmpty()) {
            error("Test project libraries and modules may not share names. Duplicate names: ${duplicateNames.joinToString()}.")
        }

        testStructure.modules.forEach { moduleData ->
            val module = modulesByName.getValue(moduleData.name)
            moduleData.dependsOnModules.forEach { dependencyName ->
                projectLibrariesByName[dependencyName]
                    ?.let { library -> module.addDependency(library) }
                    ?: module.addDependency(modulesByName.getValue(dependencyName))
            }
        }

        return Triple(testStructure, projectLibrariesByName, modulesByName)
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

    private fun createModuleWithSources(moduleName: String, testDirectory: String): Module {
        val tmpDir = createTempDirectory().toPath()
        val module: Module = createModule("$tmpDir/$moduleName", moduleType)
        val srcDir = tmpDir.createDirectory("src")

        processModuleSources(moduleName, testDirectory, srcDir)

        val srcRoot = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(srcDir.toFile())!!
        WriteCommandAction.writeCommandAction(module.project).run<RuntimeException> {
            srcRoot.refresh(false, true)
        }

        PsiTestUtil.addSourceContentToRoots(module, srcRoot)
        return module
    }

    /**
     * If the [moduleName] is also a directory in the test case's test data, we should include these sources.
     */
    private fun processModuleSources(moduleName: String, testDirectory: String, destinationSrcDir: Path) {
        val existingSourcesPath = Path(testDirectory, moduleName)
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
