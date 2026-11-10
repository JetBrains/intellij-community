// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.inline.codeInliner

import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.AbstractCodeToInlineBuilder
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.InlineDataKeys.SIDE_EFFECTS
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.MutableCodeToInline
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.ResolvedImportPath
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.resolveSuccessfulExpressionSymbol
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtArrayAccessExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtDeclarationWithBody
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.resolve.ImportPath

open class CodeToInlineBuilder(
    private val original: KtDeclaration, private val imports: List<String> = emptyList(), fallbackToSuperCall: Boolean = false
) : AbstractCodeToInlineBuilder(original.project, original, fallbackToSuperCall) {

    @OptIn(KaAllowAnalysisFromWriteAction::class, KaAllowAnalysisOnEdt::class) //called under potemkin progress
    override fun prepareMutableCodeToInline(
        mainExpression: KtExpression?, statementsBefore: List<KtExpression>, reformat: Boolean
    ): MutableCodeToInline {
        allowAnalysisOnEdt {
            allowAnalysisFromWriteAction {
                val alwaysKeepMainExpression = mainExpression != null && analyze(mainExpression) {
                    val targetSymbol = mainExpression.resolveSuccessfulExpressionSymbol()
                    when (targetSymbol) {
                        is KaPropertySymbol -> targetSymbol.getter?.isNotDefault == true
                        else -> false
                    }
                }

                markSideEffects(mainExpression)

                val codeToInline = MutableCodeToInline(
                    mainExpression,
                    original,
                    statementsBefore.toMutableList(),
                    imports.map { ResolvedImportPath(ImportPath(FqName(it), false, null), null) }.toMutableSet(),
                    alwaysKeepMainExpression,
                    extraComments = null,
                )

                saveComments(codeToInline, original)
                insertExplicitTypeArguments(codeToInline)
                removeContracts(codeToInline)
                encodeInternalReferences(codeToInline, original)
                specifyFunctionLiteralTypesExplicitly(codeToInline)
                specifyNullTypeExplicitly(codeToInline, original)
                return codeToInline
            }
        }
    }

}

internal fun markSideEffects(mainExpression: KtExpression?) {
    if (mainExpression == null) return
    PsiTreeUtil.findChildrenOfAnyType<KtExpression>(
        mainExpression,
        false,
        KtArrayAccessExpression::class.java,
        KtCallExpression::class.java
    ).forEach { call ->
        if (call is KtArrayAccessExpression) {
            if (call.isCustomOperator()) {
                call.putCopyableUserData(SIDE_EFFECTS, true)
            }
        } else if (call is KtCallExpression) {
            if (call.isContextOfCall()) {
                call.putCopyableUserData(SIDE_EFFECTS, false)
            }
        }
    }
}

private fun KtArrayAccessExpression.isCustomOperator(): Boolean = analyze(this) {
    when (val function = resolveSuccessfulExpressionSymbol()?.psi) {
        is KtDeclarationWithBody -> (function.navigationElement as? KtDeclarationWithBody)?.hasBody() == true
        is PsiMethod -> function.body != null
        else -> false
    }
}

private fun KtCallExpression.isContextOfCall(): Boolean {
    if (valueArguments.isNotEmpty() || lambdaArguments.isNotEmpty() || calleeExpression?.text != "contextOf") return false
    val resolved = calleeExpression?.mainReference?.resolve() as? KtNamedFunction ?: return false
    return resolved.fqName?.asString() == "kotlin.contextOf"
}
