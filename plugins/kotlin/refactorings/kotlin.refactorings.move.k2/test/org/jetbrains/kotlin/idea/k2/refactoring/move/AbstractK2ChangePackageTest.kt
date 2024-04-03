// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move

import com.google.gson.JsonObject
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.base.util.getString
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2ChangePackageDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.K2ChangePackageRefactoringProcessor
import org.jetbrains.kotlin.idea.refactoring.AbstractMultifileRefactoringTest
import org.jetbrains.kotlin.idea.refactoring.KotlinCommonRefactoringSettings
import org.jetbrains.kotlin.idea.refactoring.runRefactoringTest
import org.jetbrains.kotlin.idea.test.ProjectDescriptorWithStdlibSources
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile

abstract class AbstractK2ChangePackageTest : AbstractMultifileRefactoringTest() {
    override fun getProjectDescriptor() = ProjectDescriptorWithStdlibSources.getInstanceWithStdlibSources()

    override fun isFirPlugin(): Boolean = true

    override fun isEnabled(config: JsonObject): Boolean = config.get("enabledInK2")?.asBoolean == true

    override fun runRefactoring(path: String, config: JsonObject, rootDir: VirtualFile, project: Project) {
        runMoveRefactoring(path, config, rootDir, project)
    }
}

private fun runMoveRefactoring(path: String, config: JsonObject, rootDir: VirtualFile, project: Project) {
    runRefactoringTest(path, config, rootDir, project, K2ChangePackageRefactoringAction)
}

private object K2ChangePackageRefactoringAction : KotlinMoveRefactoringAction {
    override fun runRefactoring(rootDir: VirtualFile, mainFile: PsiFile, elementsAtCaret: List<PsiElement>, config: JsonObject) {
        val newPkgName = config.getString("newPackageName")
        val descriptor = K2ChangePackageDescriptor(
            mainFile.project,
            setOf(mainFile as KtFile),
            FqName(newPkgName),
            searchInComments = config.searchInComments(),
            searchForText = config.searchForText()
        )
        K2ChangePackageRefactoringProcessor(descriptor).run()
    }
}