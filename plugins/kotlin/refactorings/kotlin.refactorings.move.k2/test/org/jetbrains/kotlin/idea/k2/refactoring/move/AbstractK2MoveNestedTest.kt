// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move

import com.google.gson.JsonObject
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.refactoring.move.moveInner.MoveInnerProcessor
import com.intellij.refactoring.move.moveMembers.MockMoveMembersOptions
import com.intellij.refactoring.move.moveMembers.MoveMembersProcessor
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.base.util.getNullableString
import org.jetbrains.kotlin.idea.base.util.getString
import org.jetbrains.kotlin.idea.core.util.toPsiDirectory
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveOperationDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveSourceDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveTargetDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.K2MoveDeclarationDelegate
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.K2MoveDeclarationsRefactoringProcessor
import org.jetbrains.kotlin.idea.refactoring.runRefactoringTest
import org.jetbrains.kotlin.idea.util.sourceRoot
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType

abstract class AbstractK2MoveNestedTest : AbstractMultifileMoveRefactoringTest() {
    override fun runRefactoring(path: String, config: JsonObject, rootDir: VirtualFile, project: Project) {
        runRefactoringTest(path, config, rootDir, project, K2MoveNestedRefactoringAction)
    }
}

internal object K2MoveNestedRefactoringAction : KotlinMoveRefactoringAction {
    @OptIn(KaAllowAnalysisOnEdt::class)
    override fun runRefactoring(rootDir: VirtualFile, mainFile: PsiFile, elementsAtCaret: List<PsiElement>, config: JsonObject) {
        val project = mainFile.project
        val type = config.getString("type")
        when (type) {
            "MOVE_KOTLIN_NESTED_CLASS" -> {
                val project = mainFile.project
                val elementToMove = elementsAtCaret.single().getNonStrictParentOfType<KtClassOrObject>()!!
                val targetClassName = config.getNullableString("targetClass")
                val fileName = (elementToMove.name!!) + ".kt"
                val targetPackageFqName = config.getNullableString("targetPackage")?.let {
                    FqName(it)
                } ?: (mainFile as KtFile).packageFqName
                val targetDir = mainFile.sourceRoot?.toPsiDirectory(project)!!
                val dirStructureMatchesPkg = mainFile.sourceRoot != mainFile.virtualFile.parent
                val moveDescriptor = K2MoveDescriptor.Declarations(
                    project,
                    source = K2MoveSourceDescriptor.ElementSource(listOf(elementToMove)),
                    target = K2MoveTargetDescriptor.File(fileName, targetPackageFqName, targetDir),
                )
                val moveDelegate = K2MoveDeclarationDelegate.NestedClass(
                    newClassName = null,
                    outerInstanceParameterName = config.getNullableString("outerInstanceParameter")
                )
                val moveOperationDescriptor = allowAnalysisOnEdt {
                    K2MoveOperationDescriptor.Declarations(
                        project = project,
                        moveDescriptors = listOf(moveDescriptor),
                        searchForText = config.searchForText(),
                        searchInComments = config.searchInComments(),
                        searchReferences = config.searchReferences(),
                        dirStructureMatchesPkg = dirStructureMatchesPkg,
                        moveDeclarationsDelegate = moveDelegate,
                        moveCallBack = null,
                    )
                }
                K2MoveDeclarationsRefactoringProcessor(moveOperationDescriptor).run()
            }
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