// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move

import com.google.gson.JsonObject
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.refactoring.move.moveInner.MoveInnerProcessor
import com.intellij.refactoring.move.moveMembers.MockMoveMembersOptions
import com.intellij.refactoring.move.moveMembers.MoveMembersProcessor
import org.jetbrains.kotlin.idea.base.util.getString
import org.jetbrains.kotlin.idea.jsonUtils.getNullableString
import org.jetbrains.kotlin.idea.refactoring.runRefactoringTest
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType

abstract class AbstractK2MoveNestedTest : AbstractMultifileMoveRefactoringTest() {
    override fun runRefactoring(path: String, config: JsonObject, rootDir: VirtualFile, project: Project) {
        runRefactoringTest(path, config, rootDir, project, K2MoveNestedRefactoringAction)
    }
}

internal object K2MoveNestedRefactoringAction : KotlinMoveRefactoringAction {
    override fun runRefactoring(rootDir: VirtualFile, mainFile: PsiFile, elementsAtCaret: List<PsiElement>, config: JsonObject) {
        val project = mainFile.project
        val type = config.getString("type")
        when (type) {
            "MOVE_MEMBERS" -> {
                val members = elementsAtCaret.map { it.getNonStrictParentOfType<PsiMember>()!! }
                val targetClassName = config.getString("targetClass")
                val visibility = config.getNullableString("visibility")

                val options = MockMoveMembersOptions(targetClassName, members.toTypedArray())
                if (visibility != null) {
                    options.memberVisibility = visibility
                }
                MoveMembersProcessor(project, options).run()
            }

            "MOVE_INNER_CLASS" -> {
                val classToMove = elementsAtCaret.single().getNonStrictParentOfType<PsiClass>()!!
                val newClassName = config.getNullableString("newClassName") ?: classToMove.name!!
                val outerInstanceParameterName = config.getNullableString("outerInstanceParameterName")
                val targetPackage = config.getString("targetPackage")
                MoveInnerProcessor(
                    project,
                    classToMove,
                    newClassName,
                    outerInstanceParameterName != null,
                    outerInstanceParameterName,
                    JavaPsiFacade.getInstance(project).findPackage(targetPackage)!!.directories[0]
                ).run()
            }
        }
    }
}