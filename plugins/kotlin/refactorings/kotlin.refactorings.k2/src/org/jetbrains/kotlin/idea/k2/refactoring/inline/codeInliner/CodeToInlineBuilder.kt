// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.inline.codeInliner

import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.calls.KtImplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.calls.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.singleVariableAccessCall
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.receiverType
import org.jetbrains.kotlin.idea.codeinsight.utils.getRenderedTypeArguments
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.AbstractCodeToInlineBuilder
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.CodeToInline
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.MutableCodeToInline
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.forEachDescendantOfType
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getReceiverExpression
import org.jetbrains.kotlin.psi.psiUtil.isNull
import org.jetbrains.kotlin.resolve.ImportPath
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

@OptIn(KtAllowAnalysisFromWriteAction::class, KtAllowAnalysisOnEdt::class) //called under potemkin progress
class CodeToInlineBuilder(
    private val original: KtDeclaration,
    fallbackToSuperCall: Boolean = false
) : AbstractCodeToInlineBuilder(original.project, original, fallbackToSuperCall) {

    override fun prepareMutableCodeToInline(
        mainExpression: KtExpression?,
        statementsBefore: List<KtExpression>,
        reformat: Boolean
    ): MutableCodeToInline {
        val codeToInline = super.prepareMutableCodeToInline(mainExpression, statementsBefore, reformat)
        insertExplicitTypeArguments(codeToInline)
        specifyFunctionLiteralTypesExplicitly()
        specifyNullTypeExplicitly(mainExpression, codeToInline)
        preprocessReferences(codeToInline)
        return codeToInline
    }

    private fun preprocessReferences(codeToInline: MutableCodeToInline) {
        codeToInline.forEachDescendantOfType<KtSimpleNameExpression> { expression ->
            val parent = expression.parent
            if (parent is KtValueArgumentName || parent is KtCallableReferenceExpression) return@forEachDescendantOfType
            val target = expression.mainReference.resolve() as? KtNamedDeclaration

            if (target is KtParameter) {
                expression.putCopyableUserData(CodeToInline.PARAMETER_USAGE_KEY, target.nameAsName)
            }

            val targetParent = target?.parent
            if (targetParent is KtFile) {
                val importableFqName = targetParent.packageFqName
                val shortName = target.nameAsName
                if (shortName != null && !shortName.isSpecial) {
                    codeToInline.fqNamesToImport.add(
                        ImportPath(
                            fqName = importableFqName.child(shortName),
                            isAllUnder = false,
                            alias = null,
                        )
                    )
                }
            }

            val receiverExpression = expression.getReceiverExpression()
            if (receiverExpression == null) {
                val (receiverValue, isSameReceiverType) = allowAnalysisFromWriteAction {
                    allowAnalysisOnEdt {
                        analyze(expression) {
                            val resolveCall = expression.resolveCall()
                            val partiallyAppliedSymbol =
                                (resolveCall?.singleFunctionCallOrNull() ?: resolveCall?.singleVariableAccessCall())?.partiallyAppliedSymbol
                            val value = (partiallyAppliedSymbol?.extensionReceiver
                                ?: partiallyAppliedSymbol?.dispatchReceiver) as? KtImplicitReceiverValue
                            value to (value?.type == (original.getSymbol() as? KtCallableSymbol)?.receiverType)
                        }
                    }
                }
                if (receiverValue != null) {
                    codeToInline.addPreCommitAction(expression) { expr ->
                        val expressionToReplace = expr.parent as? KtCallExpression ?: expr
                        val replaced = codeToInline.replaceExpression(
                            expressionToReplace,
                            psiFactory.createExpressionByPattern(
                                "this.$0", expressionToReplace
                            )
                        ) as? KtQualifiedExpression
                        val thisExpression = replaced?.receiverExpression ?: return@addPreCommitAction
                        if (!isSameReceiverType) {
                            thisExpression.putCopyableUserData(CodeToInline.SIDE_RECEIVER_USAGE_KEY, Unit)
                        }
                    }
                }
            }
        }
    }


    private fun specifyNullTypeExplicitly(mainExpression: KtExpression?, codeToInline: MutableCodeToInline) {
        if (mainExpression?.isNull() == true) {
            val useSiteKtElement = original
            val nullCast = allowAnalysisFromWriteAction {
                allowAnalysisOnEdt {
                    analyze(useSiteKtElement) {
                        "null as ${useSiteKtElement.getReturnKtType().render(position = Variance.OUT_VARIANCE)}"
                    }
                }
            }
            codeToInline.addPreCommitAction(mainExpression) {
                codeToInline.replaceExpression(it, psiFactory.createExpression(nullCast))
            }
        }
    }

    private fun specifyFunctionLiteralTypesExplicitly() {
        //todo https://youtrack.jetbrains.com/issue/KTIJ-27431
        // val functionLiteralExpression = mainExpression?.unpackFunctionLiteral(true)
    }

    private fun insertExplicitTypeArguments(codeToInline: MutableCodeToInline) {
        codeToInline.forEachDescendantOfType<KtCallExpression> { callExpression ->
            if (callExpression.typeArguments.isEmpty() && callExpression.calleeExpression != null) {
                val arguments = allowAnalysisFromWriteAction {
                    allowAnalysisOnEdt {
                        analyze(callExpression) {
                            getRenderedTypeArguments(callExpression)
                        }
                    }
                }
                if (arguments != null) {
                    codeToInline.addPreCommitAction(callExpression) { expr ->
                        expr.addAfter(KtPsiFactory(expr.project).createTypeArguments(arguments), expr.calleeExpression)
                        expr.typeArguments.forEach { typeArgument ->
                            val reference = typeArgument.typeReference?.typeElement?.safeAs<KtUserType>()?.referenceExpression
                            reference?.putCopyableUserData(CodeToInline.TYPE_PARAMETER_USAGE_KEY, Name.identifier(reference.text))
                        }
                    }
                }
            }
        }
    }
}