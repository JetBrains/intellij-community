// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move

import com.google.gson.JsonObject
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.PackageWrapper
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesProcessor
import com.intellij.refactoring.move.moveClassesOrPackages.MultipleRootsMoveDestination
import org.jetbrains.kotlin.idea.base.util.getString
import org.jetbrains.kotlin.idea.refactoring.runRefactoringTest

abstract class AbstractK2MovePackageTest : AbstractMultifileMoveRefactoringTest() {
    override fun runRefactoring(path: String, config: JsonObject, rootDir: VirtualFile, project: Project) {
        runRefactoringTest(path, config, rootDir, project, K2MovePackageRefactoringAction)
    }
}

internal object K2MovePackageRefactoringAction : KotlinMoveRefactoringAction {
    override fun runRefactoring(rootDir: VirtualFile, mainFile: PsiFile, elementsAtCaret: List<PsiElement>, config: JsonObject) {
        val project = mainFile.project
        val sourcePackageName = config.getString("sourcePackage")
        val targetPackageName = config.getString("targetPackage")

        val sourcePackage = JavaPsiFacade.getInstance(project).findPackage(sourcePackageName)!!

        val targetPackageWrapper = PackageWrapper(mainFile.manager, targetPackageName)
        val moveDestination = MultipleRootsMoveDestination(targetPackageWrapper)

        MoveClassesOrPackagesProcessor(
            project,
            arrayOf(sourcePackage),
            moveDestination,
            /* searchInComments = */ false,
            /* searchInNonJavaFiles = */ true,
            /* moveCallback = */ null
        ).run()
    }
}