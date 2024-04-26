// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move

import com.google.gson.JsonObject
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassToInnerProcessor
import org.jetbrains.kotlin.idea.base.util.allScope
import org.jetbrains.kotlin.idea.base.util.getString
import org.jetbrains.kotlin.idea.refactoring.runRefactoringTest
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType

abstract class AbstractK2MoveTopLevelToInnerTest : AbstractMultifileMoveRefactoringTest() {
    override fun runRefactoring(path: String, config: JsonObject, rootDir: VirtualFile, project: Project) {
        runRefactoringTest(path, config, rootDir, project, K2MoveTopLevelToInnerRefactoringAction)
    }
}

internal object K2MoveTopLevelToInnerRefactoringAction : KotlinMoveRefactoringAction {
    override fun runRefactoring(rootDir: VirtualFile, mainFile: PsiFile, elementsAtCaret: List<PsiElement>, config: JsonObject) {
        val project = mainFile.project
        if (mainFile.name.endsWith(".java")) {
            val classesToMove = elementsAtCaret.map { it.getNonStrictParentOfType<PsiClass>()!! }
            val targetClass = config.getString("targetClass")
            MoveClassToInnerProcessor(
                project,
                classesToMove.toTypedArray(),
                JavaPsiFacade.getInstance(project).findClass(targetClass, project.allScope())!!,
                /* searchInComments = */ false,
                /* searchInNonJavaFiles = */ true,
                /* moveCallback = */ null
            ).run()
        }
    }
}