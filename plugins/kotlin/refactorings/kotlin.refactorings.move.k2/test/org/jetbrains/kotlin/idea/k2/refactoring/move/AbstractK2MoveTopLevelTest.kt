// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move

import com.google.gson.JsonObject
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findDirectory
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.refactoring.PackageWrapper
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesProcessor
import com.intellij.refactoring.move.moveClassesOrPackages.MultipleRootsMoveDestination
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.base.util.getNullableString
import org.jetbrains.kotlin.idea.base.util.getString
import org.jetbrains.kotlin.idea.core.util.toPsiDirectory
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveOperationDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.K2MoveDeclarationsRefactoringProcessor
import org.jetbrains.kotlin.idea.refactoring.runRefactoringTest
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.util.capitalizeDecapitalize.capitalizeAsciiOnly

abstract class AbstractK2MoveTopLevelTest : AbstractMultifileMoveRefactoringTest() {
    override fun runRefactoring(path: String, config: JsonObject, rootDir: VirtualFile, project: Project) {
        runRefactoringTest(path, config, rootDir, project, K2MoveTopLevelRefactoringAction)
    }
}

internal object K2MoveTopLevelRefactoringAction : KotlinMoveRefactoringAction {
    @OptIn(KaAllowAnalysisOnEdt::class)
    override fun runRefactoring(rootDir: VirtualFile, mainFile: PsiFile, elementsAtCaret: List<PsiElement>, config: JsonObject) {
        val project = mainFile.project
        if (mainFile.name.endsWith(".java")) {
            val classesToMove = elementsAtCaret.map { it.getNonStrictParentOfType<PsiClass>()!! }
            val targetPackage = config.getString("targetPackage")
            MoveClassesOrPackagesProcessor(
                project,
                classesToMove.toTypedArray(),
                MultipleRootsMoveDestination(PackageWrapper(mainFile.manager, targetPackage)),
                /* searchInComments = */ false,
                /* searchInNonJavaFiles = */ true,
                /* moveCallback = */ null
            ).run()
        } else {
            val targetSourceRoot = config.getNullableString("targetSourceRoot") ?: ""
            val targetPackage = config.getNullableString("targetPackage")
            val declarationsToMove = elementsAtCaret.filterIsInstance<KtNamedDeclaration>()
            val (fileName, pkgName, baseDir) = if (targetPackage != null) {
                val fileName = if (declarationsToMove.size == 1) {
                    declarationsToMove.first().name?.capitalizeAsciiOnly() + ".kt"
                } else {
                    declarationsToMove.first().containingKtFile.name
                }
                Triple(fileName, FqName(targetPackage), rootDir.findDirectory(targetSourceRoot)?.toPsiDirectory(project)!!)
            } else {
                val targetFile = PsiManager.getInstance(project)
                    .findFile(rootDir.findFileByRelativePath(config.getString("targetFile"))!!) as KtFile
                Triple(targetFile.name, targetFile.packageFqName, targetFile.containingDirectory!!)
            }
            val moveOperationDescriptor = allowAnalysisOnEdt {
                K2MoveOperationDescriptor.Declarations(
                    project = project,
                    declarations = declarationsToMove,
                    baseDir = baseDir,
                    fileName = fileName,
                    pkgName = pkgName,
                    searchForText = config.searchForText(),
                    searchReferences = config.searchReferences(),
                    searchInComments = config.searchInComments(),
                    mppDeclarations = config.moveExpectedActuals(),
                    isMoveToExplicitPackage = config.moveExplicitPackage(),
                    dirStructureMatchesPkg = true
                )
            }
            K2MoveDeclarationsRefactoringProcessor(moveOperationDescriptor).run()
        }
    }
}