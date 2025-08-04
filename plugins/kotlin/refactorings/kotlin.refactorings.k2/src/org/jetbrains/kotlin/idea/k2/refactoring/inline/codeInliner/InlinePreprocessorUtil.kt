// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.inline.codeInliner

import com.intellij.psi.*
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.resolution.*
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaDeclarationContainerSymbol
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.idea.base.projectStructure.getKaModule
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.codeinsight.utils.addTypeArguments
import org.jetbrains.kotlin.idea.codeinsight.utils.getRenderedTypeArguments
import org.jetbrains.kotlin.idea.k2.refactoring.getThisQualifier
import org.jetbrains.kotlin.idea.k2.refactoring.util.ConvertReferenceToLambdaUtil
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.CodeToInline
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.InlineDataKeys.USER_CODE_KEY
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.MutableCodeToInline
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.ResolvedImportPath
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.forEachDescendantOfType
import org.jetbrains.kotlin.idea.refactoring.util.getExplicitLambdaSignature
import org.jetbrains.kotlin.idea.refactoring.util.specifyExplicitLambdaSignature
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.resolve.ImportPath
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
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

                                            fun markAsUserCode() {
                                                val typeArgumentList = argumentExpression.typeArgumentList
                                                if (typeArgumentList != null) {
                                                    argumentExpression.putCopyableUserData(USER_CODE_KEY, null)
                                                    for (child in argumentExpression.children) {
                                                        (child as? KtElement)?.putCopyableUserData(USER_CODE_KEY, Unit)
                                                    }
                                                    typeArgumentList.putCopyableUserData(USER_CODE_KEY, null)
                                                }
                                            }

                                            markAsUserCode()
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
                        val typeElement = typeArgument.typeReference?.typeElement
                        val reference = (((typeElement as? KtIntersectionType)?.getLeftTypeRef()?.typeElement ?: typeElement) as? KtUserType)?.referenceExpression
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
    val isAnonymousFunctionWithReceiver = isAnonymousFunction && originalDeclaration.receiverTypeReference != null

    codeToInline.forEachDescendantOfType<KtSimpleNameExpression> { expression ->
        val parent = expression.parent
        if (parent is KtValueArgumentName || parent is KtCallableReferenceExpression) return@forEachDescendantOfType
        val resolve = expression.mainReference.resolve()
        val target = (resolve as? KtObjectDeclaration)?.let { if (it.isCompanion()) it.containingClass() else it } ?: resolve as? KtNamedDeclaration ?: resolve as? PsiMember

        if (target is KtParameter) {
            fun getParameterName(): Name = if (isAnonymousFunction && target.ownerDeclaration == originalDeclaration) {
                val shift = if (isAnonymousFunctionWithReceiver) 2 else 1
                Name.identifier("p${target.parameterIndex() + shift}")
            } else {
                target.nameAsSafeName
            }
            expression.putCopyableUserData(CodeToInline.PARAMETER_USAGE_KEY, getParameterName())
            if (!target.hasValOrVar() && parent !is KtCallElement) {
                return@forEachDescendantOfType
            }
        } else if (target is KtTypeParameter) {
            expression.putCopyableUserData(CodeToInline.TYPE_PARAMETER_USAGE_KEY, target.nameAsName)
        } else if (resolve == (originalDeclaration as? KtNamedFunction)?.receiverTypeReference && isAnonymousFunctionWithReceiver && expression.getReceiverExpression() == null) {
            expression.putCopyableUserData(CodeToInline.PARAMETER_USAGE_KEY, Name.identifier("p1"))
        }

        fun isImportable(t: PsiElement): Boolean {
            val module = t.getKaModule(t.project, useSiteModule = null)
            return analyze(module) {
                val resolvedSymbol = when (t) {
                    is KtNamedDeclaration -> t.symbol
                    is PsiMember -> {
                        if ((t.containingFile as? PsiJavaFile)?.packageName == CommonClassNames.DEFAULT_PACKAGE) {
                            return@analyze false
                        }
                        if (t is PsiClass) {
                            return@analyze t.qualifiedName != null
                        }
                        t.callableSymbol
                    }
                    else -> null
                } ?: return@analyze false
                if (resolvedSymbol is KaEnumEntrySymbol || resolvedSymbol is KaClassSymbol && resolvedSymbol.containingSymbol is KaClassSymbol) {
                    return@analyze false
                }
                val containingSymbol = resolvedSymbol.containingDeclaration ?: return@analyze true
                if (containingSymbol is KaDeclarationContainerSymbol) {
                    val staticScope = containingSymbol.staticMemberScope
                    return@analyze resolvedSymbol in staticScope.declarations
                }
                return@analyze false
            }
        }

        val targetParent = target?.parent
        val isLocalInline =
            originalDeclaration is KtProperty && originalDeclaration.isLocal || originalDeclaration is KtFunction && originalDeclaration.isLocal
        if (!isLocalInline && (targetParent is KtFile ||
                    target is KtConstructor<*> ||
                    (target as? KtCallableDeclaration)?.receiverTypeReference != null ||
                    target != null && isImportable(target))
        ) {
            val importableFqName =
                (target as? KtConstructor<*>)?.containingClass()?.fqName ?: (target as? KtNamedDeclaration)?.fqName ?: (target as? PsiMember)?.kotlinFqName ?: return@forEachDescendantOfType
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

        fun markToDeleteReceiver(receiverExpression: KtThisExpression) {
            analyze(receiverExpression) {
                val originalCallableSymbol = ((originalDeclaration as? KtPropertyAccessor)?.property ?: originalDeclaration).symbol as? KaCallableSymbol ?: return
                val originalDispatchReceiverType = originalCallableSymbol.dispatchReceiverType
                val expressionType = receiverExpression.expressionType ?: return
                val thisAsDispatchReceiver = (receiverExpression.parent as? KtDotQualifiedExpression)?.selectorExpression?.resolveToCall()
                    ?.successfulCallOrNull<KaCallableMemberCall<*, *>>()?.partiallyAppliedSymbol?.dispatchReceiver
                if (thisAsDispatchReceiver is KaSmartCastedReceiverValue) return
                val originalSymbolReceiverType = originalCallableSymbol.receiverType
                if (originalDispatchReceiverType != null &&
                    originalSymbolReceiverType != null && expressionType.semanticallyEquals(originalDispatchReceiverType)
                ) {
                    receiverExpression.putCopyableUserData(CodeToInline.DELETE_RECEIVER_USAGE_KEY, Unit)
                }
                val isSameReceiverType =
                    originalSymbolReceiverType != null && expressionType.semanticallyEquals(originalSymbolReceiverType) ||
                            originalDispatchReceiverType != null && expressionType.semanticallyEquals(originalDispatchReceiverType)
                if (!isSameReceiverType) {
                    receiverExpression.putCopyableUserData(CodeToInline.SIDE_RECEIVER_USAGE_KEY, Unit)
                }
            }
        }

        val receiverExpression = expression.getReceiverExpression()
        if (receiverExpression == null) {
            if (parent is KtThisExpression) {
                markToDeleteReceiver(parent)
            } else {
                val (receiverValue, isSameReceiverType, deleteReceiver) = analyze(expression) {
                    val resolveCall = expression.resolveToCall()
                    val partiallyAppliedSymbol = resolveCall?.calls?.firstIsInstanceOrNull<KaCallableMemberCall<*, *>>()?.partiallyAppliedSymbol

                    val value =
                        (partiallyAppliedSymbol?.extensionReceiver ?: partiallyAppliedSymbol?.dispatchReceiver) as? KaImplicitReceiverValue
                    val originalSymbol =
                        ((originalDeclaration as? KtPropertyAccessor)?.property ?: originalDeclaration).symbol as? KaCallableSymbol
                    val originalSymbolReceiverType = originalSymbol?.receiverType
                    val originalSymbolDispatchType = originalSymbol?.dispatchReceiverType
                    if (value != null && !(resolve is KtParameter && resolve.ownerFunction == originalDeclaration)) {
                        require(partiallyAppliedSymbol != null)
                        val receiverToDelete = originalSymbolReceiverType != null
                                && (partiallyAppliedSymbol.extensionReceiver as? KaImplicitReceiverValue)?.symbol !is KaReceiverParameterSymbol
                                && (partiallyAppliedSymbol.dispatchReceiver as? KaImplicitReceiverValue)?.symbol !is KaReceiverParameterSymbol
                        val isSameReceiverType =
                            originalSymbolReceiverType != null && value.type.semanticallyEquals(originalSymbolReceiverType) ||
                                    originalSymbolDispatchType != null && value.type.semanticallyEquals(originalSymbolDispatchType)
                        Triple(
                            getThisQualifier(value),
                            isSameReceiverType,
                            receiverToDelete
                        )
                    } else {
                        val functionalType = (partiallyAppliedSymbol?.symbol as? KaVariableSymbol)?.returnType as? KaFunctionType
                        val receiverType = functionalType?.receiverType
                        if (receiverType == null) {
                            Triple(null, true, false)
                        } else {
                            val isSame = originalSymbolReceiverType != null && receiverType.semanticallyEquals(originalSymbolReceiverType) ||
                                    originalSymbolDispatchType != null && receiverType.semanticallyEquals(originalSymbolDispatchType)
                            Triple("this".takeIf { isSame }, isSame, false)
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
                        if (deleteReceiver) {
                            thisExpression.putCopyableUserData(CodeToInline.DELETE_RECEIVER_USAGE_KEY, Unit)
                        }
                    }
                }
            }
        } else if (receiverExpression is KtThisExpression) {
            markToDeleteReceiver(receiverExpression)
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
        val useSiteKtElement = originalDeclaration as KtDeclarationWithReturnType
        val nullCast = analyze(useSiteKtElement) {
            "null as ${useSiteKtElement.returnType.render(position = Variance.OUT_VARIANCE)}"
        }

        codeToInline.addPreCommitAction(mainExpression) {
            codeToInline.replaceExpression(it, KtPsiFactory.contextual(it).createExpression(nullCast))
        }
    }
}
