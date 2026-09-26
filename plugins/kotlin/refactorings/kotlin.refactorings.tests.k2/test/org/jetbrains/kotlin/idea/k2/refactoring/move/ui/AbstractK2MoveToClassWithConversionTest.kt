// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.ui

import com.google.gson.JsonObject
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.BaseRefactoringProcessor
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.base.util.getNullableString
import org.jetbrains.kotlin.idea.base.util.getString
import org.jetbrains.kotlin.idea.base.util.projectScope
import org.jetbrains.kotlin.idea.k2.refactoring.move.AbstractMultifileMoveRefactoringTest
import org.jetbrains.kotlin.idea.k2.refactoring.move.KotlinMoveRefactoringAction
import org.jetbrains.kotlin.idea.refactoring.runRefactoringTest
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType

abstract class AbstractK2MoveToClassWithConversionTest : AbstractMultifileMoveRefactoringTest() {
    override fun runRefactoring(path: String, config: JsonObject, rootDir: VirtualFile, project: Project) {
        runRefactoringTest(path, config, rootDir, project, K2MoveToClassWithConversionAction)
    }
}

private const val TARGET_CLASS_FQ_NAME = "targetClassFqName"
private const val CANDIDATE_KIND = "candidateKind"
private const val CANDIDATE_DISPLAY_NAME = "candidateDisplayName"
private const val IGNORE_CONFLICTS = "ignoreConflicts"

internal object K2MoveToClassWithConversionAction : KotlinMoveRefactoringAction {
    @OptIn(KaAllowAnalysisOnEdt::class)
    override fun runRefactoring(rootDir: VirtualFile, mainFile: PsiFile, elementsAtCaret: List<PsiElement>, config: JsonObject) {
        val project = mainFile.project
        val editor = FileEditorManager.getInstance(project).openFile(mainFile.virtualFile).singleOrNull() ?:
            error("No file editor found for for ${mainFile.name}")

        val functionToMove = elementsAtCaret.single().getNonStrictParentOfType<KtNamedFunction>()
            ?: error("Element at <caret> is not inside a KtNamedFunction")

        val targetClassFqName = config.getString(TARGET_CLASS_FQ_NAME)
        val targetClass = KotlinFullClassNameIndex[targetClassFqName, project, project.projectScope()].firstOrNull()
            ?: error("Target class '$targetClassFqName' not found in project scope")

        val expectedKindName = config.getString(CANDIDATE_KIND)
        val expectedKind = runCatching { TargetClassCandidateKind.valueOf(expectedKindName) }
            .getOrElse { error("Invalid $CANDIDATE_KIND='$expectedKindName'; expected one of ${TargetClassCandidateKind.entries}") }
        val expectedDisplayName = config.getString(CANDIDATE_DISPLAY_NAME)

        val candidates = allowAnalysisOnEdt { findTargetClassCandidates(functionToMove) }
        val candidate = candidates.find { it.kind == expectedKind && it.displayName == expectedDisplayName }
            ?: error(
                "No matching TargetClassCandidate for kind=$expectedKind displayName='$expectedDisplayName'. " +
                        "Found candidates: ${candidates.joinToString { "${it.kind}/${it.displayName}" }}"
            )
        withIgnoredConflictsIfConfigured(config) {
            runMoveToClassWithConversionCommand(project,candidate, functionToMove, targetClass, editor)
        }
    }

    private fun withIgnoredConflictsIfConfigured(config: JsonObject, action: () -> Unit) {
        if (config.getNullableString(IGNORE_CONFLICTS)?.toBoolean() == true) {
            BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts<Throwable> {
                action()
            }
        } else {
            action()
        }
    }
}
