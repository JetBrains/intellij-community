// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.inline.codeInliner

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.calls.KtImplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.calls.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.singleVariableAccessCall
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KtClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtClassifierSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtVariableLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.receiverType
import org.jetbrains.kotlin.analysis.api.types.KtFunctionalType
import org.jetbrains.kotlin.idea.codeinsight.utils.addTypeArguments
import org.jetbrains.kotlin.idea.codeinsight.utils.getRenderedTypeArguments
import org.jetbrains.kotlin.idea.k2.refactoring.util.ConvertReferenceToLambdaUtil
import org.jetbrains.kotlin.idea.k2.refactoring.util.getExplicitLambdaSignature
import org.jetbrains.kotlin.idea.k2.refactoring.util.specifyExplicitLambdaSignature
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.CodeToInline
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.MutableCodeToInline
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.forEachDescendantOfType
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.KtValueArgumentName
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getReceiverExpression
import org.jetbrains.kotlin.psi.psiUtil.isNull
import org.jetbrains.kotlin.psi.psiUtil.parameterIndex
import org.jetbrains.kotlin.psi.unpackFunctionLiteral
import org.jetbrains.kotlin.resolve.ImportPath
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import org.jetbrains.kotlin.utils.sure

@OptIn(KtAllowAnalysisFromWriteAction::class, KtAllowAnalysisOnEdt::class)
fun fullyExpandCall(
    usage: KtReferenceExpression
): KtSimpleNameExpression? {
    if (usage !is KtSimpleNameExpression) return null

    when (val usageParent = usage.parent) {
        is KtCallableReferenceExpression -> {
            if (usageParent.callableReference != usage) return null
            allowAnalysisOnEdt {
                allowAnalysisFromWriteAction {
                    analyze(usageParent) {
                        val lambdaExpressionText = ConvertReferenceToLambdaUtil.prepareLambdaExpressionText(usageParent)
                        if (lambdaExpressionText != null) {
                            val referencedName = usage.getReferencedName()
                            val target = usage.mainReference.resolve()
                            val lambdaExpression =
                                ConvertReferenceToLambdaUtil.convertReferenceToLambdaExpression(usageParent, lambdaExpressionText) ?: return null
                            return findNewUsage(lambdaExpression, referencedName, target)
                        }
                    }
                }
            }
        }

        is KtCallElement -> {
            for (valueArgument in usageParent.valueArguments.asReversed()) {
                val argumentExpression = valueArgument.getArgumentExpression() ?: continue
                allowAnalysisOnEdt {
                    allowAnalysisFromWriteAction {
                        analyze(argumentExpression) {
                            when (argumentExpression) {
                                is KtCallableReferenceExpression -> {
                                    val lambdaExpressionText = ConvertReferenceToLambdaUtil.prepareLambdaExpressionText(argumentExpression)
                                    if (lambdaExpressionText != null) {
                                        ConvertReferenceToLambdaUtil.convertReferenceToLambdaExpression(
                                            argumentExpression,
                                            lambdaExpressionText
                                        )
                                    }
                                }
                                is KtCallElement -> {
                                    if (argumentExpression.typeArguments.isEmpty() && argumentExpression.calleeExpression != null) {
                                        val arguments = getRenderedTypeArguments(argumentExpression)
                                        if (arguments != null) {
                                            addTypeArguments(argumentExpression, arguments, usage.project)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val lambdaExpressions = usageParent.valueArguments.mapNotNull { it.getArgumentExpression() as? KtLambdaExpression }
            if (lambdaExpressions.isEmpty()) return null

            val grandParent = usageParent.parent
            val name = usage.getReferencedName()
            val target = usage.mainReference.resolve()
            for (lambdaExpression in lambdaExpressions) {
                allowAnalysisOnEdt {
                    allowAnalysisFromWriteAction {
                        specifyExplicitLambdaSignature(lambdaExpression)
                    }
                }
            }

            return (grandParent as? KtElement)?.let {
                findNewUsage(it, name, target)
            }
        }
    }

    return null
}

private fun findNewUsage(
    element: KtElement,
    targetName: String?,
    target: PsiElement?
): KtSimpleNameExpression? = element.findDescendantOfType {
    it.getReferencedName() == targetName && target == it.mainReference.resolve()
}

internal fun specifyFunctionLiteralTypesExplicitly(codeToInline: MutableCodeToInline) {
    val mainExpression = codeToInline.mainExpression ?: return
    val functionLiteralExpression = mainExpression.unpackFunctionLiteral(true)
    if (functionLiteralExpression != null) {
        val parameterString = analyze(functionLiteralExpression) { getExplicitLambdaSignature(functionLiteralExpression) }
        if (parameterString != null) {
            codeToInline.addPostInsertionAction(mainExpression) { inlinedExpression ->
                val lambdaExpr = inlinedExpression.unpackFunctionLiteral(true).sure {
                    "can't find function literal expression for " + inlinedExpression.text
                }
                specifyExplicitLambdaSignature(lambdaExpr, parameterString)
            }
        }
    }
}

internal fun insertExplicitTypeArguments(codeToInline: MutableCodeToInline) {
    codeToInline.forEachDescendantOfType<KtCallExpression> { callExpression ->
        if (callExpression.typeArguments.isEmpty() && callExpression.calleeExpression != null) {
            val arguments = analyze(callExpression) {
                getRenderedTypeArguments(callExpression)
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

internal fun removeContracts(codeToInline: MutableCodeToInline) {
    for (statement in codeToInline.statementsBefore) {
        analyze(statement) {
            if (statement.resolveCall()?.singleFunctionCallOrNull()?.symbol?.callableIdIfNonLocal?.asSingleFqName()?.asString() == "kotlin.contracts.contract"
            ) {
                codeToInline.addPreCommitAction(statement) {
                    codeToInline.statementsBefore.remove(it)
                }
            }
        }
    }
}

/**
 * Mark parameter/receiver usages inside the function. To use the marks during parameter -> argument substitution
 */
internal fun encodeInternalReferences(codeToInline: MutableCodeToInline, originalDeclaration: KtDeclaration) {
    val isAnonymousFunction = originalDeclaration is KtNamedFunction && originalDeclaration.nameIdentifier == null
    val isAnonymousFunctionWithReceiver = isAnonymousFunction && (originalDeclaration as KtNamedFunction).receiverTypeReference != null

    codeToInline.forEachDescendantOfType<KtSimpleNameExpression> { expression ->
        val parent = expression.parent
        if (parent is KtValueArgumentName || parent is KtCallableReferenceExpression) return@forEachDescendantOfType
        val resolve = expression.mainReference.resolve()
        val target = resolve as? KtNamedDeclaration

        if (target is KtParameter) {
            fun getParameterName(): Name = if (isAnonymousFunction && target.ownerFunction == originalDeclaration) {
                val shift = if (isAnonymousFunctionWithReceiver) 2 else 1
                Name.identifier("p${target.parameterIndex() + shift}")
            } else {
                target.nameAsSafeName
            }
            expression.putCopyableUserData(CodeToInline.PARAMETER_USAGE_KEY, getParameterName())
        } else if (target is KtTypeParameter) {
            expression.putCopyableUserData(CodeToInline.TYPE_PARAMETER_USAGE_KEY, target.nameAsName)
        } else if (resolve == (originalDeclaration as? KtNamedFunction)?.receiverTypeReference && isAnonymousFunctionWithReceiver && expression.getReceiverExpression() == null) {
            expression.putCopyableUserData(CodeToInline.PARAMETER_USAGE_KEY, Name.identifier("p1"))
        }

        val targetParent = target?.parent
        if (targetParent is KtFile) {
            val importableFqName = target.fqName ?: return@forEachDescendantOfType
            val shortName = importableFqName.shortName()
            val ktFile = expression.containingKtFile
            val aliasName = if (shortName.asString() != expression.getReferencedName())
                ktFile.findAliasByFqName(importableFqName)?.name?.let(Name::identifier)
            else
                null

            codeToInline.fqNamesToImport.add(
                ImportPath(
                    fqName = importableFqName,
                    isAllUnder = false,
                    alias = aliasName,
                )
            )
        }

        val receiverExpression = expression.getReceiverExpression()
        if (receiverExpression == null) {
            val (receiverValue, isSameReceiverType) = analyze(expression) {
                val resolveCall = expression.resolveCall()
                val partiallyAppliedSymbol =
                    (resolveCall?.singleFunctionCallOrNull() ?: resolveCall?.singleVariableAccessCall())?.partiallyAppliedSymbol

                val value =
                    (partiallyAppliedSymbol?.extensionReceiver ?: partiallyAppliedSymbol?.dispatchReceiver) as? KtImplicitReceiverValue
                val originalSymbol = originalDeclaration.getSymbol() as? KtCallableSymbol
                val originalSymbolReceiverType = originalSymbol?.receiverType
                val originalSymbolDispatchType = originalSymbol?.getDispatchReceiverType()
                if (value != null) {
                    getThisQualifier(value) to (originalSymbolReceiverType != null && value.type.isEqualTo(originalSymbolReceiverType) ||
                                                 originalSymbolDispatchType != null && value.type.isEqualTo(originalSymbolDispatchType))
                } else {
                    val functionalType = (partiallyAppliedSymbol?.symbol as? KtVariableLikeSymbol)?.returnType as? KtFunctionalType
                    val receiverType = functionalType?.receiverType
                    if (receiverType == null) {
                        null to true
                    } else {
                        val isSame = originalSymbolReceiverType != null && receiverType.isEqualTo(originalSymbolReceiverType) ||
                                originalSymbolDispatchType != null && receiverType.isEqualTo(originalSymbolDispatchType)
                        "this".takeIf { isSame } to isSame
                    }
                }
            }

            if (receiverValue != null) {
                codeToInline.addPreCommitAction(expression) { expr ->
                    val expressionToReplace = expr.parent as? KtCallExpression ?: expr
                    val replaced = codeToInline.replaceExpression(
                        expressionToReplace, KtPsiFactory.contextual(expressionToReplace).createExpressionByPattern(
                            "$receiverValue.$0", expressionToReplace
                        )
                    ) as? KtQualifiedExpression
                    val thisExpression = replaced?.receiverExpression ?: return@addPreCommitAction
                    if (isAnonymousFunctionWithReceiver && isSameReceiverType) {
                        thisExpression.putCopyableUserData(CodeToInline.PARAMETER_USAGE_KEY, Name.identifier("p1"))
                    } else if (!isSameReceiverType) {
                        thisExpression.putCopyableUserData(CodeToInline.SIDE_RECEIVER_USAGE_KEY, Unit)
                    }
                }
            }
        }
    }
}

/**
 * If function consists of single `null`, insert cast to ensure the type
 */
internal fun specifyNullTypeExplicitly(codeToInline: MutableCodeToInline, originalDeclaration: KtDeclaration) {
    val mainExpression = codeToInline.mainExpression
    if (mainExpression?.isNull() == true) {
        val useSiteKtElement = originalDeclaration
        val nullCast = analyze(useSiteKtElement) {
            "null as ${useSiteKtElement.getReturnKtType().render(position = Variance.OUT_VARIANCE)}"
        }

        codeToInline.addPreCommitAction(mainExpression) {
            codeToInline.replaceExpression(it, KtPsiFactory.contextual(it).createExpression(nullCast))
        }
    }
}

context(KtAnalysisSession)
internal fun getThisQualifier(receiverValue: KtImplicitReceiverValue): String {
    val symbol = receiverValue.symbol
    return if ((symbol as? KtClassOrObjectSymbol)?.classKind == KtClassKind.COMPANION_OBJECT) {
        (symbol.getContainingSymbol() as KtClassifierSymbol).name!!.asString() + "." + symbol.name!!.asString()
    }
    else {
        "this"
    }
}