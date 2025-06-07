// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.rename.handlers

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.utils.getFunctionLiteralByImplicitLambdaParameter
import org.jetbrains.kotlin.idea.codeinsight.utils.isReferenceToImplicitLambdaParameter
import org.jetbrains.kotlin.idea.refactoring.rename.KotlinVariableInplaceRenameHandler
import org.jetbrains.kotlin.idea.refactoring.rename.findElementForRename
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPsiFactory

class RenameKotlinImplicitLambdaParameter : KotlinVariableInplaceRenameHandler() {
    override fun isAvailable(element: PsiElement?, editor: Editor, file: PsiFile): Boolean {
        val nameExpression = findElementToRename(file, editor)
        return nameExpression != null && nameExpression.isReferenceToImplicitLambdaParameter()
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?, dataContext: DataContext) {
        if (editor == null || file == null) return
        val itExpression = findElementToRename(file, editor) ?: return
        val explicitItParameter = project.executeWriteCommand(
            KotlinBundle.message("text.convert._it_.to.explicit.lambda.parameter"),
            groupId = null,
            command = { itExpression.createExplicitLambdaParameterByImplicitOne(editor) },
        ) ?: return

        doRename(explicitItParameter, editor, dataContext)
    }

    private fun findElementToRename(
        file: PsiFile,
        editor: Editor,
    ) = file.findElementForRename<KtNameReferenceExpression>(editor.caretModel.offset)

    override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext) {
        // Do nothing: this method is called not from editor
    }
}


private fun KtNameReferenceExpression.createExplicitLambdaParameterByImplicitOne(editor: Editor): KtParameter? {
    if (getReferencedNameAsName() != StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME) return null
    val functionLiteral = @OptIn(KaAllowAnalysisFromWriteAction::class) allowAnalysisFromWriteAction {
        getFunctionLiteralByImplicitLambdaParameter() ?: return null
    }

    val project = project

    val newExpr = KtPsiFactory(project).createExpression("{ it -> }") as KtLambdaExpression
    val arrow = newExpr.functionLiteral.arrow ?: return null
    runWriteAction {
        functionLiteral.addRangeAfter(
            newExpr.functionLiteral.valueParameterList,
            arrow,
            functionLiteral.lBrace,
        )
    }

    PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.document)

    return functionLiteral.valueParameters.singleOrNull()
}