// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.test.projectStructureTest

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.PsiTestUtil
import com.intellij.util.io.jarFile
import org.jetbrains.kotlin.idea.test.AbstractMultiModuleTest
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.jetbrains.kotlin.idea.test.addDependency
import org.jetbrains.kotlin.idea.test.addRoot
import java.nio.file.Paths

typealias ProjectLibrariesByName = Map<String, Library>
typealias ModulesByName = Map<String, Module>

/**
 * A multi-module test which builds the project structure from a test data file `structure.json`.
 */
abstract class AbstractProjectStructureTest<S : TestProjectStructure> : AbstractMultiModuleTest() {
    protected fun initializeProjectStructure(
        path: String,
        parser: TestProjectStructureParser<S>,
    ): Triple<S, ProjectLibrariesByName, ModulesByName> {
        val testStructure = TestProjectStructureReader.readToTestStructure(
            Paths.get(path),
            testProjectStructureParser = parser,
        )

        val jarFilesByRootLabel = testStructure.libraries
            .flatMapTo(mutableSetOf()) { it.roots }
            .associate { it to jarFile { }.generateInTempDir().toFile() }

        val projectLibrariesByName = testStructure.libraries.associate { libraryData ->
            libraryData.name to ConfigLibraryUtil.addProjectLibrary(project, libraryData.name) {
                libraryData.roots.forEach { rootLabel ->
                    addRoot(jarFilesByRootLabel.getValue(rootLabel), OrderRootType.CLASSES)
                }
                commit()
            }
        }

        val modulesByName = testStructure.modules.associate { moduleData ->
            moduleData.name to createEmptyModule(moduleData.name)
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

    private fun createEmptyModule(name: String): Module {
        val tmpDir = createTempDirectory().toPath()
        val module: Module = createModule("$tmpDir/$name", moduleType)
        val root = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tmpDir.toFile())!!
        WriteCommandAction.writeCommandAction(module.project).run<RuntimeException> {
            root.refresh(false, true)
        }

        PsiTestUtil.addSourceContentToRoots(module, root)
        return module
    }
}
