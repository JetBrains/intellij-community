// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.test.navigation

import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiNamedElement
import com.intellij.testFramework.JavaModuleTestCase
import org.junit.Assert
import java.io.File

abstract class AbstractNavigationWithMultipleLibrariesTest : JavaModuleTestCase() {
    abstract fun getTestDataDirectory(): File

    protected fun module(name: String, srcPath: String): Module =
        createModuleFromTestData(srcPath, name, JavaModuleType.getModuleType(), true)

    protected fun checkReferencesInModule(module: Module, libraryName: String, expectedFileName: String) {
        NavigationChecker.checkAnnotatedCode(findSourceFile(module), File(getTestDataDirectory(), expectedFileName)) {
            checkLibraryName(it, libraryName)
        }
    }

    protected fun findSourceFile(module: Module): PsiFile {
        val moduleDirPath = ModuleUtilCore.getModuleDirPath(module)
        val ioFile = File(moduleDirPath).listFiles().orEmpty().first()
        val vFile = LocalFileSystem.getInstance().findFileByIoFile(ioFile)!!
        return PsiManager.getInstance(module.project).findFile(vFile)!!
    }
}

private fun checkLibraryName(referenceTarget: PsiElement, expectedName: String) {
    val navigationElement = referenceTarget.navigationElement
    val navigationFile = navigationElement.containingFile?.virtualFile ?: return
    val project = referenceTarget.project
    val fileIndex = ProjectFileIndex.getInstance(project)
    val orderEntries = fileIndex.getOrderEntriesForFile(navigationFile)
    val libraryName = orderEntries
        .filterIsInstance<LibraryOrderEntry>()
        .firstOrNull()
        ?.libraryName

    if (libraryName != null) {
        Assert.assertEquals(
            "Referenced code from unrelated library: ${(referenceTarget as? PsiNamedElement)?.name ?: referenceTarget.text}",
            expectedName,
            libraryName
        )
    }
}
