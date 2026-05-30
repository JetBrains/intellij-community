// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move

import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.vfs.VFileProperty
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.psi.JavaDirectoryService
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.refactoring.MultiFileTestCase
import com.intellij.refactoring.PackageWrapper
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesProcessor
import com.intellij.refactoring.move.moveClassesOrPackages.SingleSourceRootMoveDestination
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.PlatformTestUtil
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR

/**
 * Regression test for KTIJ-37556: Move Java class from one module to another doesn't update import statements in Kotlin files.
 */
class K2MoveJavaClassToAnotherModuleTest : MultiFileTestCase() {

    override fun getTestRoot(): String = "/refactoring/moveClassToAnotherModule/"

    override fun getTestDataPath(): String = IDEA_TEST_DATA_DIR.path

    override fun prepareProject(rootDir: VirtualFile) {
        val model = ModuleManager.getInstance(project).getModifiableModel()
        VfsUtilCore.visitChildrenRecursively(rootDir, object : VirtualFileVisitor<Any>() {
            override fun visitFile(file: VirtualFile): Boolean {
                if (!file.isDirectory && file.name.endsWith(ModuleFileType.DOT_DEFAULT_EXTENSION)) {
                    model.loadModule(file.path)
                    return false
                }
                return true
            }
        })
        runWriteAction { model.commit() }
        IndexingTestUtil.waitUntilIndexesAreReady(project)
    }

    fun testJavaClassUpdateKotlinImports() {
        doTest { _, _ ->
            val fooClass =
                requireNotNull(JavaPsiFacade.getInstance(project).findClass("foo.Foo", GlobalSearchScope.projectScope(project))) {
                    "Class foo.Foo not found"
                }

            val barModule = requireNotNull(ModuleManager.getInstance(project).findModuleByName("bar")) {
                "Module 'bar' not found"
            }

            val barPackage = requireNotNull(JavaPsiFacade.getInstance(project).findPackage("bar")) {
                "Package bar not found"
            }
            val barDirs = barPackage.getDirectories(GlobalSearchScope.moduleScope(barModule))
            assertEquals("Expected exactly one 'bar' directory in bar module", 1, barDirs.size)
            val targetDir = barDirs[0]

            MoveClassesOrPackagesProcessor(
                project,
                arrayOf(fooClass),
                SingleSourceRootMoveDestination(
                    PackageWrapper.create(JavaDirectoryService.getInstance().getPackage(targetDir)),
                    targetDir
                ),
                true, true, null
            ).run()
        }
    }

    override fun compareResults(rootAfter: VirtualFile, rootDir: VirtualFile) {
        PlatformTestUtil.assertDirectoriesEqual(rootAfter, rootDir) { file ->
            !file.isFileHidden()
        }
    }

    private fun VirtualFile.isFileHidden(): Boolean = name.startsWith(".") || `is`(VFileProperty.HIDDEN)
}
