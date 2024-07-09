// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.inline.codeInliner

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.resolution.KaImplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.singleVariableAccessCall
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaDeclarationContainerSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaSymbolWithMembers
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.idea.codeinsight.utils.addTypeArguments
import org.jetbrains.kotlin.idea.codeinsight.utils.getRenderedTypeArguments
import org.jetbrains.kotlin.idea.k2.refactoring.getThisQualifier
import org.jetbrains.kotlin.idea.k2.refactoring.util.ConvertReferenceToLambdaUtil
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.CodeToInline
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.MutableCodeToInline
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.ResolvedImportPath
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.forEachDescendantOfType
import org.jetbrains.kotlin.idea.refactoring.util.getExplicitLambdaSignature
import org.jetbrains.kotlin.idea.refactoring.util.specifyExplicitLambdaSignature
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getReceiverExpression
import org.jetbrains.kotlin.psi.psiUtil.isNull
import org.jetbrains.kotlin.psi.psiUtil.parameterIndex
import org.jetbrains.kotlin.resolve.ImportPath
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import org.jetbrains.kotlin.utils.sure

@OptIn(KaAllowAnalysisFromWriteAction::class, KaAllowAnalysisOnEdt::class)
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
                        val parameterString = analyze(lambdaExpression) {
                            getExplicitLambdaSignature(lambdaExpression)
                        }
                        if (!parameterString.isNullOrEmpty()) {
                            specifyExplicitLambdaSignature(lambdaExpression, parameterString)
                        }
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
            if (statement.resolveToCall()?.singleFunctionCallOrNull()?.symbol?.callableId?.asSingleFqName()?.asString() == "kotlin.contracts.contract"
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

        fun isImportable(t: KtNamedDeclaration): Boolean {
            analyze(t) {
                val resolvedSymbol = t.symbol
                val containingSymbol = resolvedSymbol.containingDeclaration ?: return true
                if (containingSymbol is KaDeclarationContainerSymbol) {
                    val staticScope = containingSymbol.staticMemberScope
                    return resolvedSymbol in staticScope.declarations
                }
                return false
            }
        }

        val targetParent = target?.parent
        if (targetParent is KtFile ||
            (target as? KtCallableDeclaration)?.receiverTypeReference != null ||
            target != null && isImportable(target)
        ) {
            val importableFqName = target.fqName ?: return@forEachDescendantOfType
            val shortName = importableFqName.shortName()
            val ktFile = expression.containingKtFile
            val aliasName = if (shortName.asString() != expression.getReferencedName())
                ktFile.findAliasByFqName(importableFqName)?.name?.let(Name::identifier)
            else
                null

            codeToInline.fqNamesToImport.add(
                ResolvedImportPath(
                    ImportPath(
                        fqName = importableFqName,
                        isAllUnder = false,
                        alias = aliasName,
                    ),
                    target
                )
            )
        }

        val receiverExpression = expression.getReceiverExpression()
        if (receiverExpression == null) {
            val (receiverValue, isSameReceiverType) = analyze(expression) {
                val resolveCall = expression.resolveToCall()
                val partiallyAppliedSymbol =
                    (resolveCall?.singleFunctionCallOrNull() ?: resolveCall?.singleVariableAccessCall())?.partiallyAppliedSymbol

                val value =
                    (partiallyAppliedSymbol?.extensionReceiver ?: partiallyAppliedSymbol?.dispatchReceiver) as? KaImplicitReceiverValue
                val originalSymbol = originalDeclaration.symbol as? KaCallableSymbol
                val originalSymbolReceiverType = originalSymbol?.receiverType
                val originalSymbolDispatchType = originalSymbol?.dispatchReceiverType
                if (value != null) {
                    getThisQualifier(value) to (originalSymbolReceiverType != null && value.type.isEqualTo(originalSymbolReceiverType) ||
                                                originalSymbolDispatchType != null && value.type.isEqualTo(originalSymbolDispatchType))
                } else {
                    val functionalType = (partiallyAppliedSymbol?.symbol as? KaVariableSymbol)?.returnType as? KaFunctionType
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
@OptIn(KaExperimentalApi::class)
internal fun specifyNullTypeExplicitly(codeToInline: MutableCodeToInline, originalDeclaration: KtDeclaration) {
    val mainExpression = codeToInline.mainExpression
    if (mainExpression?.isNull() == true) {
        val useSiteKtElement = originalDeclaration
        val nullCast = analyze(useSiteKtElement) {
            "null as ${useSiteKtElement.returnType.render(position = Variance.OUT_VARIANCE)}"
        }

        codeToInline.addPreCommitAction(mainExpression) {
            codeToInline.replaceExpression(it, KtPsiFactory.contextual(it).createExpression(nullCast))
        }
    }
}
