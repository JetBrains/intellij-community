// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.test.projectStructureTest

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.utils.io.createDirectory
import com.intellij.util.io.jarFile
import org.jetbrains.kotlin.idea.test.AbstractMultiModuleTest
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.jetbrains.kotlin.idea.test.KotlinCompilerStandalone
import org.jetbrains.kotlin.idea.test.addDependency
import org.jetbrains.kotlin.idea.test.addRoot
import java.io.File
import java.nio.file.Paths
import kotlin.io.path.Path

typealias ProjectLibrariesByName = Map<String, Library>
typealias ModulesByName = Map<String, Module>

/**
 * A multi-module test which builds the project structure from a test data file `structure.json`.
 *
 * Library and module sources are discovered automatically. If a test data directory with the same name as a library root or module exists,
 * it will be used as a source directory for the library root/module.
 */
abstract class AbstractProjectStructureTest<S : TestProjectStructure> : AbstractMultiModuleTest() {
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

    private fun createModuleWithSources(name: String, testDirectory: String): Module {
        val tmpDir = createTempDirectory().toPath()
        val module: Module = createModule("$tmpDir/$name", moduleType)
        val srcDir = tmpDir.createDirectory("src")

        // If the module name is also a directory in the test case's test data, we should include these sources.
        val existingSources = Path(testDirectory, name).toFile()
        if (existingSources.isDirectory) {
            existingSources.copyRecursively(srcDir.toFile(), overwrite = true)
        }

        val srcRoot = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(srcDir.toFile())!!
        WriteCommandAction.writeCommandAction(module.project).run<RuntimeException> {
            srcRoot.refresh(false, true)
        }

        PsiTestUtil.addSourceContentToRoots(module, srcRoot)
        return module
    }
}
